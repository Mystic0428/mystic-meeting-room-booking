package com.mystic.booking.service;

import com.mystic.booking.dto.RoomRequest;
import com.mystic.booking.dto.RoomResponse;
import com.mystic.booking.entity.RoomEntity;
import com.mystic.booking.exception.ResourceNotFoundException;
import com.mystic.booking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    @Transactional
    public RoomResponse create(RoomRequest request) {
        RoomEntity room = new RoomEntity(
                request.name(), request.capacity(), request.floor(), request.location());
        return RoomResponse.from(roomRepository.save(room));
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> list() {
        return roomRepository.findAll().stream()
                .map(RoomResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse get(Long id) {
        return RoomResponse.from(findRoomOrThrow(id));
    }

    @Transactional
    public RoomResponse update(Long id, RoomRequest request) {
        RoomEntity room = findRoomOrThrow(id);
        room.update(request.name(), request.capacity(), request.floor(), request.location());
        // JPA dirty checking:room 是 managed 狀態,交易結束會自動 flush,不必再 save
        return RoomResponse.from(room);
    }

    @Transactional
    public void deactivate(Long id) {
        findRoomOrThrow(id).deactivate();
    }

    private RoomEntity findRoomOrThrow(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + id));
    }
}
