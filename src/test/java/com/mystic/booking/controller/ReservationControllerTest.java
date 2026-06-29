package com.mystic.booking.controller;

import com.mystic.booking.dto.ReservationResponse;
import com.mystic.booking.exception.ReservationConflictException;
import com.mystic.booking.exception.ResourceNotFoundException;
import com.mystic.booking.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller test:只載入 web 層,service 被 mock,驗證「HTTP 請求 → 對的狀態碼 + JSON」。
 * 不碰 DB,跑很快。@RestControllerAdvice 會一起載入,所以例外 → 狀態碼的對應也一併驗到。
 */
@WebMvcTest(ReservationController.class)
@DisplayName("ReservationController(@WebMvcTest)測試")
class ReservationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ReservationService reservationService;

    private static final String VALID_BODY = """
            {"roomId":1,"userId":1,"startTime":"2099-01-01T10:00:00","endTime":"2099-01-01T11:00:00","subject":"Test","purpose":"p","attendeeCount":5}
            """;

    @Test
    @DisplayName("POST /api/reservations 合法 → 201 + 回傳預約")
    void create_returns201_whenValid() throws Exception {
        given(reservationService.create(any())).willReturn(
                new ReservationResponse(1L, "會議室 101", "王小明",
                        LocalDateTime.of(2099, 1, 1, 10, 0),
                        LocalDateTime.of(2099, 1, 1, 11, 0),
                        "Test", "processing"));

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("processing"))
                .andExpect(jsonPath("$.roomName").value("會議室 101"));
    }

    @Test
    @DisplayName("POST 缺欄位 / attendeeCount=0 → 400(且 service 不被呼叫)")
    void create_returns400_whenInvalid() throws Exception {
        String invalid = """
                {"roomId":1,"userId":1,"startTime":"2099-01-01T10:00:00","endTime":"2099-01-01T11:00:00","attendeeCount":0}
                """;

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest());

        verify(reservationService, never()).create(any());
    }

    @Test
    @DisplayName("POST 衝突(service 丟 ReservationConflictException)→ 409")
    void create_returns409_whenConflict() throws Exception {
        given(reservationService.create(any()))
                .willThrow(new ReservationConflictException("此會議室在該時段已被預約"));

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    @DisplayName("POST 查無 room/user(service 丟 ResourceNotFoundException)→ 404")
    void create_returns404_whenNotFound() throws Exception {
        given(reservationService.create(any()))
                .willThrow(new ResourceNotFoundException("Room not found: 99999"));

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }
}
