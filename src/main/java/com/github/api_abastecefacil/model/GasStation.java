package com.github.api_abastecefacil.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gas_stations")
public class GasStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "fantasy_name")
    private String fantasyName;

    @Column(name = "cnpj", nullable = false, unique = true)
    private String cnpj;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "cep", nullable = false)
    private String cep;

    @Column(name = "district", nullable = false)
    private String district;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "phone")
    private String phone;

    @Column(name = "business_hours")
    private String businessHours;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public GasStation setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public GasStation setName(String name) {
        this.name = name;
        return this;
    }

    public String getFantasyName() {
        return fantasyName;
    }

    public GasStation setFantasyName(String fantasyName) {
        this.fantasyName = fantasyName;
        return this;
    }

    public String getCnpj() {
        return cnpj;
    }

    public GasStation setCnpj(String cnpj) {
        this.cnpj = cnpj;
        return this;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public GasStation setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
        return this;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public GasStation setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
        return this;
    }

    public String getCep() {
        return cep;
    }

    public GasStation setCep(String cep) {
        this.cep = cep;
        return this;
    }

    public String getDistrict() {
        return district;
    }

    public GasStation setDistrict(String district) {
        this.district = district;
        return this;
    }

    public String getAddress() {
        return address;
    }

    public GasStation setAddress(String address) {
        this.address = address;
        return this;
    }

    public String getState() {
        return state;
    }

    public GasStation setState(String state) {
        this.state = state;
        return this;
    }

    public String getCity() {
        return city;
    }

    public GasStation setCity(String city) {
        this.city = city;
        return this;
    }

    public String getPhone() {
        return phone;
    }

    public GasStation setPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public String getBusinessHours() {
        return businessHours;
    }

    public GasStation setBusinessHours(String businessHours) {
        this.businessHours = businessHours;
        return this;
    }

    public Boolean getActive() {
        return isActive;
    }

    public GasStation setActive(Boolean active) {
        isActive = active;
        return this;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public GasStation setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public GasStation setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.isActive = true;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
