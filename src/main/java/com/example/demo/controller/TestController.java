package com.example.demo.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.*;
import com.example.demo.service.MonitoringService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {
    @Autowired
    private MonitoringService monitoringService;

    /**
     * 模擬 ChirpStack 推送數據給前端
     * 
     * @param devEui 裝置 EUI (測試用)
     */
    @PostMapping("/push/{devEui}")
    public String triggerPush(@PathVariable String devEui) {

        Map<String, Object> mockData = new HashMap<>();
        mockData.put("devEui", devEui);
        mockData.put("temperature", 36.5);
        mockData.put("voltage", 24.2);
        mockData.put("timestamp", System.currentTimeMillis());
        mockData.put("status", "Testing-Success");

        // 呼叫我們先前建立的推播服務
        monitoringService.sendDeviceUpdate(devEui, mockData);

        return "已向主題 /topic/device/" + devEui + " 發送測試數據";
    }
}
