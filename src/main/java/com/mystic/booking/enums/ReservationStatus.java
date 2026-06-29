package com.mystic.booking.enums;

public enum ReservationStatus {

    PROCESSING,         // 已建立、待審核(預設)

    APPROVED,           // 已核准

    REJECTED,           // 已拒絕

    CANCEL_REQUESTED,   // 已申請退回、待審核

    CANCELLED           // 已取消
}
