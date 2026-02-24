package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class MonitoringService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 推播裝置狀態給前端
     * @param devEui 裝置識別碼
     * @param data 包含溫度、電壓等資訊的 Map
     */
    public void sendDeviceUpdate(String devEui, Map<String, Object> data) {
        // 前端將訂閱此路徑：/topic/device/{devEui}
        String destination = "/topic/device/" + devEui;
        messagingTemplate.convertAndSend(destination, (Object) data);
    }
}