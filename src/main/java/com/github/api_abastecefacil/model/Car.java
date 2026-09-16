package com.github.api_abastecefacil.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "cars")
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_plate", nullable = false, unique = true)
    private String licensePlate;

    @Column(name = "model", nullable = false)
    private String model;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Car setId(Long id) {
        this.id = id;
        return this;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public Car setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
        return this;
    }

    public String getModel() {
        return model;
    }

    public Car setModel(String model) {
        this.model = model;
        return this;
    }

    public Boolean getActive() {
        return active;
    }

    public Car setActive(Boolean active) {
        this.active = active;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Car setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Car setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
