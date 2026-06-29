package com.mystic.booking.entity;

import com.mystic.booking.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 50)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    // BCrypt 雜湊(非明文)。可為 null:沒設密碼的使用者無法登入,但不影響其他功能。
    @Column(length = 100)
    private String passwordHash;

    protected UserEntity() {
        // JPA 需要無參數建構子
    }

    public UserEntity(String username, String email, String department, Role role) {
        this.username = username;
        this.email = email;
        this.department = department;
        this.role = role;
    }

    /** 設定(已雜湊的)密碼。呼叫端負責先用 PasswordEncoder 編碼,entity 不碰明文。 */
    public void changePassword(String encodedPassword) {
        this.passwordHash = encodedPassword;
    }
}
