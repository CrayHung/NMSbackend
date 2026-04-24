package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class TelegramAlertIntegrationService {

    // 指向未來要在 Telegram 服務端新寫的簡化版 API
    @Value("${telegram.alert.api.url:http://127.0.0.1:9081/api/alerts/simple-send}")
    private String alertApiUrl;

    // 讀取 API Key，預設給 alert-key
    @Value("${telegram.alert.api.key:alert-key}")
    private String apiKey;

    // 前端 NMS 的網址，預設帶入 twowayiot，方便 Telegram 點擊跳轉
    @Value("${nms.frontend.url:http://twowayiot.com:9080}")
    private String frontendUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public void sendSimpleAlarmToTelegram(String devEui, String deviceName, String alarmMessage) {
        try {
            Map<String, String> payload = new HashMap<>();

            // 處理空值避免報錯
            String safeDeviceName = (deviceName != null && !deviceName.isEmpty()) ? deviceName : "未知設備";

            payload.put("deviceName", safeDeviceName);
            payload.put("devEui", devEui);
            payload.put("statusMessage", alarmMessage);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            payload.put("timestamp", LocalDateTime.now().format(formatter));

            payload.put("link", frontendUrl);

            // ==========================================
            // 關鍵修改：建立 HttpHeaders 並塞入 API Key
            // ==========================================
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-API-KEY", apiKey); // 對應ApiKeyAuthFilter

            // 把 payload 和 headers 包裝成 HttpEntity
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(payload, headers);

            // 發送 POST 請求 (注意這裡傳入的是 requestEntity)

            ResponseEntity<String> response = restTemplate.postForEntity(alertApiUrl, requestEntity, String.class);
            System.out.println("成功發送 Telegram 告警至遠端服務, Response: " + response.getBody());

        } catch (Exception e) {
            System.err.println(" 發送 Telegram 告警失敗: " + e.getMessage());
        }
    }
}