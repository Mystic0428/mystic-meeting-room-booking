package com.mystic.booking.config;

import com.mystic.booking.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT 安全設定:除登入 / Swagger 外皆需驗證,並依角色綁定端點層授權
 * (會議室管理 → ADMIN;審核與報表 → REVIEWER / ADMIN;其餘已登入即可)。
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)                    // 純 token、無 cookie session,不需 CSRF
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 登入 + Swagger 放行
                        .requestMatchers("/api/auth/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // 會議室管理(寫入)→ ADMIN;GET 讀取留給任何已登入者
                        .requestMatchers(HttpMethod.POST, "/api/rooms").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/rooms/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/rooms/*").hasRole("ADMIN")
                        // 註:建立使用者(POST /api/users)規格未指派角色,且 ADMIN 能力清單未含此項,
                        // 故不綁角色、留給已登入者(見 README Requirement Clarification 的權限提升註記)。
                        // 審核退回 → REVIEWER / ADMIN(角色來自 token)
                        .requestMatchers(HttpMethod.POST, "/api/reservations/*/review").hasAnyRole("REVIEWER", "ADMIN")
                        // 匯出報表 → ADMIN(規格角色表明文僅 ADMIN 可匯出報表)
                        .requestMatchers(HttpMethod.GET, "/api/reservations/export").hasRole("ADMIN")
                        // 月統計 / 使用率 → REVIEWER / ADMIN(規格未明指;假設審核者需全域統計輔助判斷)
                        .requestMatchers(HttpMethod.GET,
                                "/api/reservations/monthly-summary",
                                "/api/rooms/top-used").hasAnyRole("REVIEWER", "ADMIN")
                        // 其餘已登入即可:建立預約、申請退回、查列表 / timeline / 自己的預約
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        // 未驗證(無 / 無效 token)→ 401
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // 已驗證但角色不足 → 403。用 setStatus 而非 sendError:
                        // sendError 會觸發 error dispatch 重進 filter chain,而 OncePerRequestFilter 不重跑 → 變匿名 → 誤判 401
                        .accessDeniedHandler((req, res, ex) -> res.setStatus(HttpStatus.FORBIDDEN.value())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
