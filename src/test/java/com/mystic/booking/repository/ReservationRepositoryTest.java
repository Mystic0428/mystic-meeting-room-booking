package com.mystic.booking.repository;

import com.mystic.booking.entity.ReservationEntity;
import com.mystic.booking.entity.RoomEntity;
import com.mystic.booking.entity.UserEntity;
import com.mystic.booking.enums.ReservationStatus;
import com.mystic.booking.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository test:用「真的 Postgres 容器」測 existsOverlapping 的查詢行為
 * (含 status 過濾、room 範圍),這些是 unit test(mock 掉 query)測不到的。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)   // 不要換成 H2,用下面的容器
@Testcontainers
@DisplayName("ReservationRepository(真 Postgres)測試")
class ReservationRepositoryTest {

    @Container
    @ServiceConnection                                   // 容器自動接到 Spring 的 datasource
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    TestEntityManager em;

    @Autowired
    ReservationRepository reservationRepository;

    // 「佔用中」狀態(跟 service 一致)
    private static final List<ReservationStatus> OCCUPYING = List.of(
            ReservationStatus.PROCESSING, ReservationStatus.APPROVED, ReservationStatus.CANCEL_REQUESTED);

    private static final LocalDateTime START = LocalDateTime.of(2099, 1, 1, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2099, 1, 1, 11, 0);

    @Test
    @DisplayName("情境 12:同房有 approved 重疊 → 視為衝突(true)")
    void existsOverlapping_returnsTrue_whenApprovedOverlaps() {
        RoomEntity room = persistReservation(ReservationStatus.APPROVED).getRoom();

        boolean conflict = reservationRepository.existsOverlapping(room.getId(), OCCUPYING, START, END);

        assertThat(conflict).isTrue();
    }

    @Test
    @DisplayName("情境 11:同房有 processing 重疊 → 視為衝突(true)")
    void existsOverlapping_returnsTrue_whenProcessingOverlaps() {
        RoomEntity room = persistReservation(ReservationStatus.PROCESSING).getRoom();

        boolean conflict = reservationRepository.existsOverlapping(room.getId(), OCCUPYING, START, END);

        assertThat(conflict).isTrue();
    }

    @Test
    @DisplayName("情境 9:同房重疊但狀態是 rejected → 不擋(false)")
    void existsOverlapping_returnsFalse_whenOverlapIsRejected() {
        RoomEntity room = persistReservation(ReservationStatus.REJECTED).getRoom();

        boolean conflict = reservationRepository.existsOverlapping(room.getId(), OCCUPYING, START, END);

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("情境 10:同房重疊但狀態是 cancelled → 不擋(false)")
    void existsOverlapping_returnsFalse_whenOverlapIsCancelled() {
        RoomEntity room = persistReservation(ReservationStatus.CANCELLED).getRoom();

        boolean conflict = reservationRepository.existsOverlapping(room.getId(), OCCUPYING, START, END);

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("情境 8:重疊預約在不同會議室 → 不擋(false)")
    void existsOverlapping_returnsFalse_whenOverlapIsInDifferentRoom() {
        persistReservation(ReservationStatus.APPROVED);   // A 房有一筆 approved
        RoomEntity otherRoom = persistRoom();             // 另外開 B 房

        boolean conflict = reservationRepository.existsOverlapping(otherRoom.getId(), OCCUPYING, START, END);

        assertThat(conflict).isFalse();
    }

    // ---- helpers ----

    private ReservationEntity persistReservation(ReservationStatus status) {
        ReservationEntity res = new ReservationEntity(
                persistRoom(), persistUser(), START, END, "subject", "purpose", 5);
        ReflectionTestUtils.setField(res, "status", status);   // 直接設成要測的狀態
        return em.persistAndFlush(res);
    }

    private RoomEntity persistRoom() {
        return em.persistAndFlush(new RoomEntity("Room-" + System.nanoTime(), 10, "1F", "A"));
    }

    private UserEntity persistUser() {
        return em.persistAndFlush(new UserEntity("u", "u-" + System.nanoTime() + "@example.com", "dept", Role.USER));
    }
}
