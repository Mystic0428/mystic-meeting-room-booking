package com.mystic.booking.service;

import com.mystic.booking.dto.UserRequest;
import com.mystic.booking.dto.UserResponse;
import com.mystic.booking.entity.UserEntity;
import com.mystic.booking.exception.DuplicateResourceException;
import com.mystic.booking.exception.ResourceNotFoundException;
import com.mystic.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponse create(UserRequest request) {
        // email 不可重複:先友善檢查(DB 的 unique 約束是最後防線)
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("email already exists: " + request.email());
        }
        UserEntity user = new UserEntity(
                request.username(), request.email(), request.department(), request.role());
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
