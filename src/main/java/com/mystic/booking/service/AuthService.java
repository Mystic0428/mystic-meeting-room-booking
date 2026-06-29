package com.mystic.booking.service;

import com.mystic.booking.dto.LoginRequest;
import com.mystic.booking.dto.TokenResponse;
import com.mystic.booking.entity.UserEntity;
import com.mystic.booking.repository.UserRepository;
import com.mystic.booking.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登入:驗 email + 密碼(BCrypt)→ 簽發 JWT。錯誤一律回 BadCredentials,不洩漏是帳號或密碼錯。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("invalid email or password"));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("invalid email or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getRole());
        return new TokenResponse(token, "Bearer", user.getId(), user.getRole().name());
    }
}
