package com.mystic.booking.controller;

import com.mystic.booking.dto.LoginRequest;
import com.mystic.booking.dto.TokenResponse;
import com.mystic.booking.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "認證 Auth", description = "登入取得 JWT(其餘 API 皆需帶 Bearer token)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登入", description = "以 email + 密碼登入,成功回傳 JWT(token / userId / role)。密碼錯誤回 401。")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
