package com.example.demo.controller;

import com.example.demo.model.DeviceStatusLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.demo.dto.DeviceConfigDto;
import com.example.demo.dto.AmplifierHistoryDto;
import com.example.demo.service.DeviceService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/amplifier")
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    /**
     * 獲取特定 Application 下的所有裝置列表
     */
    @GetMapping("/devices")
    public ResponseEntity<List<Map<String, Object>>> getDevices(@RequestParam String applicationId) {
        return ResponseEntity.ok(deviceService.getDevicesByApplication(applicationId));
    }

    @PutMapping("/{devEui}")
    public ResponseEntity<?> update(@PathVariable String devEui, @RequestBody DeviceConfigDto dto) {
        deviceService.updateDevice(devEui, dto);
        return ResponseEntity.ok(Map.of("message", "Update command sent"));
    }

    /**
     * 從 MySQL 取得裝置的歷史狀態紀錄
     * 範例: /api/amplifier/status/EUI123/history?start=2026-02-01T00:00:00&end=2026-02-02T23:59:59
     */
    @GetMapping("/status/{devEui}/history")
    public ResponseEntity<List<DeviceStatusLog>> getHistory(
            @PathVariable String devEui,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return ResponseEntity.ok(deviceService.getDeviceHistory(devEui, start, end));
    }
}