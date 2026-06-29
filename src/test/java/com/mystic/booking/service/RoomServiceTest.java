package com.mystic.booking.service;

import com.mystic.booking.dto.RoomRequest;
import com.mystic.booking.dto.RoomResponse;
import com.mystic.booking.entity.RoomEntity;
import com.mystic.booking.exception.ResourceNotFoundException;
import com.mystic.booking.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RoomService 單元測試")
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    RoomRepository roomRepository;

    @InjectMocks
    RoomService roomService;

    @Test
    @DisplayName("建立會議室:存檔後回傳 DTO,且預設為啟用")
    void create_shouldSaveAndReturnResponse() {
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RoomResponse response = roomService.create(new RoomRequest("會議室 101", 10, "1F", "A棟"));

        assertThat(response.name()).isEqualTo("會議室 101");
        assertThat(response.capacity()).isEqualTo(10);
        assertThat(response.isActive()).isTrue();
        verify(roomRepository).save(any());
    }

    @Test
    @DisplayName("列出會議室:把每個 entity 轉成 DTO")
    void list_shouldMapAllRooms() {
        when(roomRepository.findAll()).thenReturn(List.of(room(1L, "A"), room(2L, "B")));

        List<RoomResponse> result = roomService.list();

        assertThat(result).extracting(RoomResponse::name).containsExactly("A", "B");
    }

    @Test
    @DisplayName("查單一會議室:存在時回傳 DTO")
    void get_shouldReturnRoom_whenExists() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, "A")));

        assertThat(roomService.get(1L).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("查單一會議室:不存在 → 404")
    void get_shouldThrowNotFound_whenMissing() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.get(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("更新會議室:改欄位後靠 dirty checking 寫回(不呼叫 save)")
    void update_shouldMutateManagedEntity() {
        RoomEntity room = room(1L, "舊名");
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        RoomResponse response = roomService.update(1L, new RoomRequest("新名", 20, "2F", "B棟"));

        assertThat(response.name()).isEqualTo("新名");
        assertThat(response.capacity()).isEqualTo(20);
    }

    @Test
    @DisplayName("更新會議室:不存在 → 404")
    void update_shouldThrowNotFound_whenMissing() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.update(99L, new RoomRequest("x", 1, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("停用會議室:把 isActive 設為 false")
    void deactivate_shouldSetInactive() {
        RoomEntity room = room(1L, "A");
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        roomService.deactivate(1L);

        assertThat(room.isActive()).isFalse();
    }

    @Test
    @DisplayName("停用會議室:不存在 → 404")
    void deactivate_shouldThrowNotFound_whenMissing() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.deactivate(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private RoomEntity room(Long id, String name) {
        RoomEntity r = new RoomEntity(name, 10, "1F", "A");
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }
}
