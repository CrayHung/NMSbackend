package com.example.demo.controller;

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

    @GetMapping("/devices")
    public ResponseEntity<List<Map<String, Object>>> getDevices(@RequestParam String applicationId) {
        return ResponseEntity.ok(deviceService.getDevicesByApplication(applicationId));
    }

    @PutMapping("/{devEui}")
    public ResponseEntity<?> update(@PathVariable String devEui, @RequestBody DeviceConfigDto dto) {
        deviceService.updateDevice(devEui, dto);
        return ResponseEntity.ok(Map.of("message", "Device updated successfully"));
    }

    /**
     * 取得 Amplifier 歷史狀態紀錄 API
     * 路徑: /api/amplifier/status/{deviceEui}/history
     * * @param devEui 裝置 EUI (路徑參數)
     * 
     * @param start 開始時間 (Query 參數, 選填)
     * @param end   結束時間 (Query 參數, 選填)
     */
    @GetMapping("/status/{devEui}/history")
    public ResponseEntity<List<AmplifierHistoryDto>> getHistory(
            @PathVariable String deviceEui,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        // 呼叫 Service 取得歷史資料
        List<AmplifierHistoryDto> result = deviceService.getAmplifierHistory(deviceEui, start, end);
        return ResponseEntity.ok(result);
    }
}