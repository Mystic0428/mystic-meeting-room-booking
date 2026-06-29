package com.mystic.booking.repository;

/**
 * top-used native query 的結果投影。Spring Data 依「欄位別名」對應到這些 getter。
 */
public interface TopUsedRoomProjection {
    Long getRoomId();

    String getRoomName();

    long getReservationCount();

    long getTotalMinutes();
}
