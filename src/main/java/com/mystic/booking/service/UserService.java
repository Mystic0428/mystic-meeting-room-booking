package com.mystic.booking.service;

import com.mystic.booking.dto.UserRequest;
import com.mystic.booking.dto.UserResponse;
import com.mystic.booking.entity.UserEntity;
import com.mystic.booking.exception.DuplicateResourceException;
import com.mystic.booking.exception.ResourceNotFoundException;
import com.mystic.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse create(UserRequest request) {
        // email 不可重複:先友善檢查(DB 的 unique 約束是最後防線)
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("email already exists: " + request.email());
        }
        UserEntity user = new UserEntity(
                request.username(), request.email(), request.department(), request.role());
        // 有給密碼就雜湊後存(entity 只收已編碼字串,不碰明文)
        if (request.password() != null && !request.password().isBlank()) {
            user.changePassword(passwordEncoder.encode(request.password()));
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return UserResponse.from(user);
    }
}
