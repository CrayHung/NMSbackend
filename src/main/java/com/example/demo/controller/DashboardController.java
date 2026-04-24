package com.example.demo.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.DeviceStatusLog;
import com.example.demo.repository.DeviceStatusLogRepository;

@RestController
@RequestMapping("/api/iot/dashboard")
public class DashboardController {

    @Autowired
    private DeviceStatusLogRepository logRepository;

    @GetMapping("/alarms")
    public ResponseEntity<?> getAlarmDashboardData(@RequestParam(defaultValue = "1") int days) {
        try {
            // 計算時間範圍 (從今天往前推算 days 天)
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startDate = now.minusDays(days);

            // 撈取該時間區間內的所有 Log
            List<DeviceStatusLog> logs = logRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDate, now);

            // 計算 Normal 與 Warning 的數量 (假設 1 = Normal, 2 = Alarm/Warning)
            long normalCount = logs.stream().filter(log -> log.getUnitStatus() != null && log.getUnitStatus() == 1).count();
            long warningCount = logs.stream().filter(log -> log.getUnitStatus() != null && log.getUnitStatus() == 2).count();

            // 整理格式化回傳
            Map<String, Object> summary = new HashMap<>();
            summary.put("normal", normalCount);
            summary.put("warning", warningCount);

            Map<String, Object> result = new HashMap<>();
            result.put("summary", summary);
            result.put("logs", logs); // 前端下方的Alarm Logs列表所需資料

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "無法取得 Dashboard 資料: " + e.getMessage()));
        }
    }
}


