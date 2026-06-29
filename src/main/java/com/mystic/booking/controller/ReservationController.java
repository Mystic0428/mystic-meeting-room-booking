package com.mystic.booking.controller;

import com.mystic.booking.dto.CancelRequestRequest;
import com.mystic.booking.dto.CreateReservationRequest;
import com.mystic.booking.dto.ReviewRequest;
import com.mystic.booking.dto.ReservationResponse;
import com.mystic.booking.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "預約 Reservation(寫入)", description = "建立預約、申請退回、審核")
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "建立預約",
            description = "驗證存在性/時間/容量後建立(status=processing)。時段衝突回 409、查無 room/user 回 404、輸入不合法回 400。")
    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse response = reservationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "申請退回預約",
            description = "本人對已核准的預約申請退回(→ cancel_requested)。操作者身分取自 JWT;非本人回 403。")
    @PostMapping("/{id}/cancel-request")
    public ReservationResponse cancelRequest(@PathVariable Long id,
                                             @AuthenticationPrincipal Long currentUserId,
                                             @Valid @RequestBody CancelRequestRequest request) {
        return reservationService.requestCancellation(id, currentUserId, request);
    }

    @Operation(summary = "審核預約",
            description = "REVIEWER / ADMIN 審核。依預約當下狀態分派:processing→核可/駁回新訂單;cancel_requested→核可/駁回退訂。角色不足回 403。")
    @PostMapping("/{id}/review")
    public ReservationResponse review(@PathVariable Long id,
                                      @AuthenticationPrincipal Long reviewerId,
                                      @Valid @RequestBody ReviewRequest request) {
        return reservationService.review(id, reviewerId, request);
    }
}
