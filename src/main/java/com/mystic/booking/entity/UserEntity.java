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

    protected UserEntity() {
        // JPA 需要無參數建構子
    }

    public UserEntity(String username, String email, String department, Role role) {
        this.username = username;
        this.email = email;
        this.department = department;
        this.role = role;
    }
}
