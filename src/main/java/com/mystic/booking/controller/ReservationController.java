package com.mystic.booking.controller;

import com.mystic.booking.dto.CancelRequestRequest;
import com.mystic.booking.dto.CreateReservationRequest;
import com.mystic.booking.dto.ReviewRequest;
import com.mystic.booking.dto.ReservationResponse;
import com.mystic.booking.service.ReservationService;
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

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest request) {
        ReservationResponse response = reservationService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/cancel-request")
    public ReservationResponse cancelRequest(@PathVariable Long id,
                                             @AuthenticationPrincipal Long currentUserId,
                                             @Valid @RequestBody CancelRequestRequest request) {
        return reservationService.requestCancellation(id, currentUserId, request);
    }

    @PostMapping("/{id}/review")
    public ReservationResponse review(@PathVariable Long id,
                                      @AuthenticationPrincipal Long reviewerId,
                                      @Valid @RequestBody ReviewRequest request) {
        return reservationService.review(id, reviewerId, request);
    }
}
