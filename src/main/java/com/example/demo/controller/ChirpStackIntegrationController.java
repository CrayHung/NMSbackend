/**
 * 
 * 實作 Uplink 接收控制器 (ChirpStack Integration)
此控制器負責接收 ChirpStack 透過 HTTP Integration 發送的 JSON 數據。

 */
package com.example.demo.controller;

import com.example.demo.model.DeviceStatusLog;
import com.example.demo.repository.DeviceStatusLogRepository;
import com.example.demo.service.MonitoringService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/iot/integration")
public class ChirpStackIntegrationController {

    @Autowired
    private DeviceStatusLogRepository statusLogRepository;

    @Autowired
    private MonitoringService monitoringService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 接收 ChirpStack Uplink 數據的端點
     * 請在 ChirpStack Application 的 Integration 頁面設定此 URL
     */
    @PostMapping("/uplink")
    public void receiveUplink(@RequestBody String jsonPayload) {
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            String devEui = root.path("deviceInfo").path("devEui").asText();
            
            // 解析 Payload (假設 ChirpStack 已經透過 Codec 解析出 object 欄位)
            JsonNode objectNode = root.path("object");
            double temp = objectNode.path("temperature").asDouble();
            double volt = objectNode.path("voltage").asDouble();

            // 1. 儲存至 MySQL
            DeviceStatusLog log = new DeviceStatusLog();
            log.setDevEui(devEui);
            log.setTemperature(temp);
            log.setVoltage(volt);
            log.setRawData(root.path("data").asText());
            statusLogRepository.save(log);

            // 2. 透過 WebSocket 推播即時數據
            Map<String, Object> pushData = new HashMap<>();
            pushData.put("devEui", devEui);
            pushData.put("temperature", temp);
            pushData.put("voltage", volt);
            pushData.put("timestamp", System.currentTimeMillis());
            
            monitoringService.sendDeviceUpdate(devEui, pushData);

        } catch (Exception e) {
            System.err.println("解析 Uplink 失敗: " + e.getMessage());
        }
    }
}