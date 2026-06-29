package com.mystic.booking.service;

import com.mystic.booking.dto.UserRequest;
import com.mystic.booking.dto.UserResponse;
import com.mystic.booking.entity.UserEntity;
import com.mystic.booking.enums.Role;
import com.mystic.booking.exception.DuplicateResourceException;
import com.mystic.booking.exception.ResourceNotFoundException;
import com.mystic.booking.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UserService 單元測試")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    UserService userService;

    @Test
    @DisplayName("建立使用者:email 不重複時成功建立")
    void create_shouldSucceed_whenEmailIsUnique() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.create(
                new UserRequest("小明", "a@example.com", "IT", Role.USER, null));

        assertThat(response.username()).isEqualTo("小明");
        assertThat(response.role()).isEqualTo("USER");
        verify(userRepository).save(any());
    }

    @Test
    @DisplayName("建立使用者:email 已存在 → 409,且不存檔")
    void create_shouldThrowDuplicate_whenEmailExists() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                new UserRequest("小明", "a@example.com", "IT", Role.USER, null)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("列出使用者:把每個 entity 轉成 DTO")
    void list_shouldMapAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(
                user(1L, "a@example.com"), user(2L, "b@example.com")));

        List<UserResponse> result = userService.list();

        assertThat(result).extracting(UserResponse::id).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("查單一使用者:存在時回傳 DTO")
    void get_shouldReturnUser_whenExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "a@example.com")));

        assertThat(userService.get(1L).email()).isEqualTo("a@example.com");
    }

    @Test
    @DisplayName("查單一使用者:不存在 → 404")
    void get_shouldThrowNotFound_whenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private UserEntity user(Long id, String email) {
        UserEntity u = new UserEntity("小明", email, "IT", Role.USER);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }
}
