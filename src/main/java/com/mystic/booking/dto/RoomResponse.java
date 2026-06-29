package com.mystic.booking.dto;

import com.mystic.booking.entity.RoomEntity;

public record RoomResponse(
        Long id,
        String name,
        Integer capacity,
        String floor,
        String location,
        boolean isActive
) {
    public static RoomResponse from(RoomEntity room) {
        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.getCapacity(),
                room.getFloor(),
                room.getLocation(),
                room.isActive());
    }
}
