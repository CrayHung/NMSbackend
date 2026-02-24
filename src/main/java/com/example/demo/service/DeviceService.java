/*
 * (業務邏輯)：處理資料庫 (MySQL) 與 ChirpStack 資料的整合。
 * 例如：當 DeviceService 獲取裝置時，它會先查 DB 獲取自定義名稱，再調用 ChirpStackService 獲取即時狀態。
 */
package com.example.demo.service;

import io.chirpstack.api.Device;
import io.chirpstack.api.DeviceListItem;
import com.example.demo.repository.DeviceStatusLogRepository;

import com.example.demo.dto.AmplifierHistoryDto;
import com.example.demo.dto.DeviceConfigDto;
import com.example.demo.model.DeviceStatusLog;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DeviceService {

    @Autowired
    private ChirpStackService chirpStackService;

    @Autowired
    private MonitoringService monitoringService;

    @Autowired
    private DeviceStatusLogRepository statusLogRepository;

    /**
     * 獲取裝置列表並封裝為前端格式
     */
    public List<Map<String, Object>> getDevicesByApplication(String applicationId) {
        var response = chirpStackService.listDevices(applicationId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (DeviceListItem item : response.getResultList()) {
            Map<String, Object> map = new HashMap<>();
            map.put("devEui", item.getDevEui());
            map.put("name", item.getName());
            map.put("description", item.getDescription());
            map.put("status", item.hasLastSeenAt() ? "Online" : "Offline");
            result.add(map);
        }
        return result;
    }

    /**
     * 更新裝置設定並發送 WebSocket 通知
     */
    public void updateDevice(String devEui, DeviceConfigDto dto) {
        // 1. 取得現有資料
        Device current = chirpStackService.getDevice(devEui).getDevice();
        Device.Builder builder = current.toBuilder();

        // 2. 根據 DTO 更新
        if (dto.getName() != null)
            builder.setName(dto.getName());
        if (dto.getDescription() != null)
            builder.setDescription(dto.getDescription());
        if (dto.getDeviceProfileId() != null)
            builder.setDeviceProfileId(dto.getDeviceProfileId());
        if (dto.getIsDisabled() != null)
            builder.setIsDisabled(dto.getIsDisabled());
        if (dto.getSkipFcntCheck() != null)
            builder.setSkipFcntCheck(dto.getSkipFcntCheck());

        // 3. 呼叫底層更新
        chirpStackService.updateDevice(builder.build());

        // 4. 即時推播更新狀態給前端
        monitoringService.sendDeviceUpdate(devEui, Map.of(
                "event", "CONFIG_UPDATED",
                "timestamp", System.currentTimeMillis(),
                "status", "Pending Ack"));
    }

    /**
     * 從 MySQL 取得裝置的歷史狀態紀錄
     */
    // 真實歷史資料查詢 (含時間區間)
    public List<DeviceStatusLog> getDeviceHistory(String devEui, String startStr, String endStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

        // 若前端沒傳時間，預設查過去 24 小時
        LocalDateTime start = (startStr != null) ? LocalDateTime.parse(startStr, formatter)
                : LocalDateTime.now().minusDays(1);
        LocalDateTime end = (endStr != null) ? LocalDateTime.parse(endStr, formatter) : LocalDateTime.now();

        return statusLogRepository.findByDevEuiAndCreatedAtBetweenOrderByCreatedAtDesc(devEui, start, end);
    }

}