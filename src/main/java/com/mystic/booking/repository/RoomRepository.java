package com.mystic.booking.repository;

import com.mystic.booking.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {

    // 所有「啟用中」的會議室,依名稱排序
    List<RoomEntity> findByIsActiveTrueOrderByNameAsc();
}
