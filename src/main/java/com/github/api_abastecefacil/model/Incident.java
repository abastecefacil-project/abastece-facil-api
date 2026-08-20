package com.github.api_abastecefacil.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "car_plate", nullable = false)
    private String carPlate;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Column(nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Incident setId(Long id) {
        this.id = id;
        return this;
    }

    public String getCarPlate() {
        return carPlate;
    }

    public Incident setCarPlate(String carPlate) {
        this.carPlate = carPlate;
        return this;
    }

    public String getUserName() {
        return userName;
    }

    public Incident setUserName(String userName) {
        this.userName = userName;
        return this;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public Incident setOccurrenceDate(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public Incident setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Incident setDescription(String description) {
        this.description = description;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Incident setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
