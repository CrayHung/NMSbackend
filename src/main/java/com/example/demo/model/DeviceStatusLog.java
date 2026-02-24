package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_status_logs")
@Data
public class DeviceStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String devEui;

    private Double temperature; // 溫度
    private Double voltage;     // 電壓
    
    @Column(columnDefinition = "TEXT")
    private String rawData;     // 原始 Payload (Base64)

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}