package com.mystic.booking.enums;

/**
 * 使用者角色，決定可執行的操作權限。
 */
public enum Role {

    /** 一般使用者：可建立預約、查詢與申請退回自己的預約 */
    USER,

    /** 審核者：可審核退回申請、查看待審核預約 */
    REVIEWER,

    /** 管理員：可管理會議室、查看所有預約與報表 */
    ADMIN
}
