package com.example.demo.repository;

import com.example.demo.model.DeviceStatusLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceStatusLogRepository extends JpaRepository<DeviceStatusLog, Long> {

    // 根據 DevEUI 查詢歷史紀錄並按時間降序排列
    List<DeviceStatusLog> findByDevEuiOrderByCreatedAtDesc(String devEui);

    // 支援時間區間查詢
    List<DeviceStatusLog> findByDevEuiAndCreatedAtBetweenOrderByCreatedAtDesc(
        String devEui, LocalDateTime start, LocalDateTime end);


    // 取得單一設備最新的一筆歷史紀錄
    Optional<DeviceStatusLog> findFirstByDevEuiOrderByCreatedAtDesc(String devEui);

     // 支援時間區間查詢
     List<DeviceStatusLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
        LocalDateTime start, LocalDateTime end);
    
}