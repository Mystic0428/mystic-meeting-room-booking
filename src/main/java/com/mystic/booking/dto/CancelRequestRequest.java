package com.mystic.booking.dto;

// 申請退回:身分(誰申請)改由 JWT 取得,不再從 body 帶 userId。
public record CancelRequestRequest(

        String reason
) {
}
