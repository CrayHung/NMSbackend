package com.example.demo.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_spectrum_logs")
public class DeviceSpectrumLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dev_eui", length = 50)
    private String devEui;

    // 將 List<Double> 轉成 JSON 字串儲存 (例如: "[34.0, 35.1, ...]")
    @Column(name = "power_values", columnDefinition = "JSON")
    private String powerValuesJson; 

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDevEui() { return devEui; }
    public void setDevEui(String devEui) { this.devEui = devEui; }
    public String getPowerValuesJson() { return powerValuesJson; }
    public void setPowerValuesJson(String powerValuesJson) { this.powerValuesJson = powerValuesJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}