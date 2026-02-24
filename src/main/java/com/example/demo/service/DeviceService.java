package com.example.demo.service;

import io.chirpstack.api.*;
import com.example.demo.dto.DeviceConfigDto;
import com.example.demo.dto.AmplifierHistoryDto;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DeviceService {

    @Autowired
    private DeviceServiceGrpc.DeviceServiceBlockingStub deviceStub;

    private final DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_INSTANT;

    /**
     * 取得應用程式下的裝置列表
     */
    public List<Map<String, Object>> getDevicesByApplication(String applicationId) {
        ListDevicesRequest request = ListDevicesRequest.newBuilder()
                .setApplicationId(applicationId)
                .setLimit(100)
                .build();

        ListDevicesResponse response = deviceStub.list(request);
        List<Map<String, Object>> result = new ArrayList<>();

        for (DeviceListItem item : response.getResultList()) {
            Map<String, Object> map = new HashMap<>();
            map.put("devEui", item.getDevEui());
            map.put("name", item.getName());
            map.put("description", item.getDescription());

            if (item.hasLastSeenAt()) {
                map.put("lastSeen", isoFormatter.format(Instant.ofEpochSecond(
                        item.getLastSeenAt().getSeconds(), item.getLastSeenAt().getNanos())));
            } else {
                map.put("lastSeen", null);
            }
            result.add(map);
        }
        return result;
    }

    /**
     * 更新裝置配置 (原本 ChirpStackService 的邏輯)
     */
    public void updateDevice(String devEui, DeviceConfigDto dto) {
        GetDeviceResponse getResp = deviceStub.get(GetDeviceRequest.newBuilder().setDevEui(devEui).build());
        Device current = getResp.getDevice();

        Device.Builder builder = current.toBuilder();
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

        deviceStub.update(UpdateDeviceRequest.newBuilder().setDevice(builder.build()).build());
    }


    
    /**
     * 取得 Amplifier 歷史紀錄
     * 參考 ChirpStack v4.13.0 GetDevice 邏輯
     */
    public List<AmplifierHistoryDto> getAmplifierHistory(String devEui, String start, String end) {
        // 1. 先從 ChirpStack 獲取裝置目前的靜態資訊 (如 HW/FW 版本)
        GetDeviceRequest getReq = GetDeviceRequest.newBuilder()
                .setDevEui(devEui)
                .build();
        GetDeviceResponse getResp = deviceStub.get(getReq);
        Device device = getResp.getDevice();

        // 2. 準備回傳清單 (實務上這裡會從資料庫 SELECT * WHERE dev_eui = ... AND created_at BETWEEN
        // ...)
        List<AmplifierHistoryDto> historyList = new ArrayList<>();

        // 模擬一筆從資料來源轉換而來的數據
        AmplifierHistoryDto record = new AmplifierHistoryDto();
        record.setDeviceEui(devEui);
        record.setCreatedAt(start != null ? start : "2026-02-23T06:58:51.335Z");

        // 帶入來自 ChirpStack Device 物件的資訊
        record.setPartName(device.getName());
        record.setSerialNumber(devEui); // 通常 DevEUI 即為 SN 或在 Tags 內

        // 模擬數據欄位填充
        record.setTemperature(45.2);
        record.setVoltage(24.0);
        record.setRfOutputPower(15.5);
        record.setUnitStatus(1);
        record.setStatusText("Operating");

        historyList.add(record);

        return historyList;
    }
}