package com.mystic.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mystic.booking.dto.CreateReservationRequest;
import com.mystic.booking.exception.ReservationConflictException;
import com.mystic.booking.repository.ReservationRepository;
import com.mystic.booking.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 整合測試:用真 Postgres 容器、跑完整 Spring context,端到端驗證建立 / 衝突 / 併發。
 * 自帶容器,不依賴外部 postgres。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("整合 + 併發測試(@SpringBootTest + Testcontainers)")
class ReservationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    ReservationService reservationService;
    @Autowired
    ReservationRepository reservationRepository;

    @Test
    @DisplayName("建立預約 API → 201,且真的寫進資料庫")
    void createReservation_writesToDatabase() throws Exception {
        LocalDateTime start = LocalDateTime.of(2099, 3, 1, 10, 0);
        CreateReservationRequest req = new CreateReservationRequest(
                1L, 1L, start, start.plusHours(1), "IT-create", "p", 5);

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        assertThat(reservationRepository.countByRoomAndStart(1L, start)).isEqualTo(1);
    }

    @Test
    @DisplayName("衝突的預約 → 409,且不會寫入第二筆")
    void conflictingReservation_doesNotWriteSecondRow() throws Exception {
        LocalDateTime start = LocalDateTime.of(2099, 4, 1, 10, 0);
        CreateReservationRequest req = new CreateReservationRequest(
                1L, 1L, start, start.plusHours(1), "IT-conflict", "p", 5);
        String body = objectMapper.writeValueAsString(req);
        String token = bearerToken();

        mockMvc.perform(post("/api/reservations").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/reservations").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        assertThat(reservationRepository.countByRoomAndStart(1L, start)).isEqualTo(1);   // 還是只有一筆
    }

    @Test
    @DisplayName("併發:兩條 thread 同時搶同房同時段 → 只成功一筆,DB 也只有一筆")
    void concurrentCreate_onlyOneSucceeds() throws InterruptedException {
        LocalDateTime start = LocalDateTime.of(2099, 5, 1, 10, 0);
        CreateReservationRequest req = new CreateReservationRequest(
                1L, 1L, start, start.plusHours(1), "IT-race", "p", 5);

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);   // 等兩條都就位
        CountDownLatch startGate = new CountDownLatch(1);     // 起跑閘:同時發令
        CountDownLatch done = new CountDownLatch(threads);    // 等兩條都跑完
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    startGate.await();                 // 卡在起跑線
                    reservationService.create(req);    // ← 兩條同時撞 DB
                    success.incrementAndGet();
                } catch (ReservationConflictException e) {
                    conflict.incrementAndGet();        // 被擋的那條(應用層或 DB constraint 轉成的 409)
                } catch (Exception ignored) {
                    // 非預期例外:留著不計數,讓下面的斷言失敗以暴露問題
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        startGate.countDown();   // 發令,兩條一起跑
        done.await();
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);                                   // 只成功一筆
        assertThat(conflict.get()).isEqualTo(1);                                  // 另一筆被擋
        assertThat(reservationRepository.countByRoomAndStart(1L, start)).isEqualTo(1);  // DB 真的只有一筆
    }

    @Test
    @DisplayName("未帶 JWT 建立預約 → 401")
    void createReservation_withoutToken_returns401() throws Exception {
        CreateReservationRequest req = new CreateReservationRequest(
                1L, 1L,
                LocalDateTime.of(2099, 6, 1, 10, 0), LocalDateTime.of(2099, 6, 1, 11, 0),
                "no-auth", "p", 5);

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    /** 用種子使用者登入取得 "Bearer &lt;jwt&gt;"(seed 密碼 = password123,見 V9)。 */
    private String bearerToken() throws Exception {
        String json = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wang@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(json).get("token").asText();
    }
}
