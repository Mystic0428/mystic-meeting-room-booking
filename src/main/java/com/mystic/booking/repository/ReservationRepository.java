package com.mystic.booking.repository;

import com.mystic.booking.entity.ReservationEntity;
import com.mystic.booking.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<ReservationEntity, Long>,
        JpaSpecificationExecutor<ReservationEntity> {

    @Query("""
           SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
           FROM ReservationEntity r
           WHERE r.room.id = :roomId
             AND r.status IN :statuses
             AND r.startTime < :newEnd
             AND r.endTime   > :newStart
           """)
    boolean existsOverlapping(@Param("roomId") Long roomId,
                              @Param("statuses") Collection<ReservationStatus> statuses,
                              @Param("newStart") LocalDateTime newStart,
                              @Param("newEnd") LocalDateTime newEnd);

    // 測試用:某房某起始時間的預約筆數(驗證併發後 DB 只有一筆)
    @Query("""
           SELECT COUNT(r) FROM ReservationEntity r
           WHERE r.room.id = :roomId AND r.startTime = :startTime
           """)
    long countByRoomAndStart(@Param("roomId") Long roomId, @Param("startTime") LocalDateTime startTime);

    // JOIN FETCH 一次把 room、user 一起載入,避免之後組 DTO 時的 N+1
    @Query("""
           SELECT r FROM ReservationEntity r
           JOIN FETCH r.room
           JOIN FETCH r.user
           WHERE r.room.id = :roomId
           ORDER BY r.startTime
           """)
    List<ReservationEntity> findByRoomIdWithDetails(@Param("roomId") Long roomId);

    // 查某使用者的所有預約(status 為 null 時不篩);JOIN FETCH room+user,依 startTime 排序
    @Query("""
           SELECT r FROM ReservationEntity r
           JOIN FETCH r.room
           JOIN FETCH r.user
           WHERE r.user.id = :userId
             AND (:status IS NULL OR r.status = :status)
           ORDER BY r.startTime
           """)
    List<ReservationEntity> findByUserIdWithDetails(@Param("userId") Long userId,
                                                    @Param("status") ReservationStatus status);

    // timeline 用:某天的指定狀態預約,JOIN FETCH user(取 username),依 startTime 排序
    @Query("""
           SELECT r FROM ReservationEntity r
           JOIN FETCH r.user
           WHERE r.status = :status
             AND r.startTime >= :dayStart
             AND r.startTime <  :dayEnd
           ORDER BY r.startTime
           """)
    List<ReservationEntity> findApprovedByDateWithUser(@Param("status") ReservationStatus status,
                                                       @Param("dayStart") LocalDateTime dayStart,
                                                       @Param("dayEnd") LocalDateTime dayEnd);

    // monthly-summary 用:某月各狀態數量(統計交給 DB 的 GROUP BY,不在 Java 數)
    @Query("""
           SELECT r.status, COUNT(r)
           FROM ReservationEntity r
           WHERE r.startTime >= :monthStart AND r.startTime < :monthEnd
           GROUP BY r.status
           """)
    List<Object[]> countByStatusForMonth(@Param("monthStart") LocalDateTime monthStart,
                                         @Param("monthEnd") LocalDateTime monthEnd);

    // monthly-summary 用:當月明細,JOIN FETCH room+user,依 startTime 排序
    @Query("""
           SELECT r FROM ReservationEntity r
           JOIN FETCH r.room
           JOIN FETCH r.user
           WHERE r.startTime >= :monthStart AND r.startTime < :monthEnd
           ORDER BY r.startTime
           """)
    List<ReservationEntity> findByMonthWithDetails(@Param("monthStart") LocalDateTime monthStart,
                                                   @Param("monthEnd") LocalDateTime monthEnd);

    // top-used 用:native query 算每間房的次數與總分鐘數(時間差只能在 native SQL 算)
    @Query(value = """
            SELECT r.room_id                                                            AS "roomId",
                   rm.name                                                              AS "roomName",
                   COUNT(*)                                                             AS "reservationCount",
                   CAST(SUM(EXTRACT(EPOCH FROM (r.end_time - r.start_time)) / 60) AS bigint) AS "totalMinutes"
            FROM reservations r
            JOIN rooms rm ON rm.id = r.room_id
            WHERE r.status = 'APPROVED'
              AND r.start_time >= :monthStart
              AND r.start_time <  :monthEnd
            GROUP BY r.room_id, rm.name
            ORDER BY "totalMinutes" DESC, "reservationCount" DESC, r.room_id
            LIMIT 3
            """, nativeQuery = true)
    List<TopUsedRoomProjection> findTopUsedRooms(@Param("monthStart") LocalDateTime monthStart,
                                                 @Param("monthEnd") LocalDateTime monthEnd);
}
