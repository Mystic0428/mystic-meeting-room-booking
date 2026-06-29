package com.mystic.booking.entity;

import jakarta.persistence.*;
import lombok.Getter;

@Getter
@Entity
@Table(name = "rooms")
public class RoomEntity extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,length =100,unique = true)
    private String name;

    @Column(nullable = false)
    private Integer capacity;

    @Column(length = 50)
    private String floor;

    @Column(length = 100)
    private String location;

    @Column(nullable = false)
    private boolean isActive;

    protected RoomEntity() {
        // JPA 需要無參數建構子
    }

    public RoomEntity(String name, Integer capacity, String floor, String location) {
        this.name = name;
        this.capacity = capacity;
        this.floor = floor;
        this.location = location;
        this.isActive = true;   // 新會議室預設啟用
    }

    public void update(String name, Integer capacity, String floor, String location) {
        this.name = name;
        this.capacity = capacity;
        this.floor = floor;
        this.location = location;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
