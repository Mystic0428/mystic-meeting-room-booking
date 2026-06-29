package com.mystic.booking.enums;

public enum ReviewAction {

    APPROVED,   // 同意退回 → 預約變 CANCELLED

    REJECTED    // 駁回退回 → 預約維持原狀
}
