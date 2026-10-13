package com.example.recircle;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

enum Role { USER, ADMIN }
enum CollectionStatus { PENDING, APPROVED, SCHEDULED, COLLECTED, RECYCLED, CANCELLED }

@Entity @Table(name="users")
class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public String name;
    @Column(nullable=false, unique=true) public String email;
    @Column(nullable=false) public String phone;
    @Column(nullable=false) public String password;
    @Column(nullable=false) public String address;
    @Column(nullable=false) public String city;
    @Column(nullable=false) public String state;
    @Column(nullable=false) public String pincode;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public Role role=Role.USER;
    @Column(nullable=false, updatable=false) public LocalDateTime createdAt=LocalDateTime.now();
    protected AppUser() {}
}

@Entity @Table(name="ewaste")
class EWaste {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id") public AppUser user;
    @Column(nullable=false) public String category;
    @Column(nullable=false) public String itemName;
    public String brand;
    @Column(nullable=false) public Integer quantity;
    @Column(nullable=false) public Double weight;
    @Column(name="item_condition",nullable=false) public String condition;
    @Column(length=1200) public String description;
    @Column(nullable=false) public String pickupAddress;
    @Column(nullable=false) public String status="AVAILABLE";
    @Column(nullable=false, updatable=false) public LocalDateTime createdAt=LocalDateTime.now();
    protected EWaste() {}
}

@Entity @Table(name="collection_requests")
class CollectionRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false, unique=true) public String requestNumber;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id") public AppUser user;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="ewaste_id") public EWaste ewaste;
    @Column(nullable=false) public String pickupAddress;
    @Column(nullable=false) public LocalDate pickupDate;
    @Column(nullable=false) public String preferredTime;
    @Column(length=1000) public String notes;
    @Enumerated(EnumType.STRING) @Column(nullable=false) public CollectionStatus status=CollectionStatus.PENDING;
    @Column(nullable=false, updatable=false) public LocalDateTime createdAt=LocalDateTime.now();
    @Column(nullable=false) public LocalDateTime updatedAt=LocalDateTime.now();
    protected CollectionRequest() {}
}
