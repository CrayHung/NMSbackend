package com.example.demo.controller;

import com.example.demo.dto.DeviceConfigDto;
import com.example.demo.dto.DeviceDetailResponseDto;
import com.example.demo.dto.GatewayMetricsResponseDTO;
import com.example.demo.model.ChirpStackApp;
import com.example.demo.model.DeviceEntity;
import com.example.demo.model.DeviceStatusLog;
import com.example.demo.repository.DeviceRepository;
import com.example.demo.service.ApplicationService;
import com.example.demo.service.DeviceService;
import com.example.demo.service.CobsCodec;
import com.example.demo.service.GatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/iot")
public class IotController {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private GatewayService gatewayService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceRepository deviceRepository; 


    // ==========================================
    // 全域地圖拓撲資料 (Map Topology)
    // 在GatewayService.java 實作
    // ==========================================
    @GetMapping("/dashboard/map-data")
    public ResponseEntity<?> getGlobalMapTopology() {
        try {
            Map<String, Object> mapData = gatewayService.getGlobalMapData();
            return ResponseEntity.ok(mapData);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "無法取得地圖資料: " + e.getMessage()));
        }
    }


    // ==========================================
    // === Alarms ===
    // ==========================================
    @GetMapping("/alarms/active")
    public ResponseEntity<List<DeviceEntity>> getActiveAlarms() {

        // 回傳unitStatus 不是 1 (非正常)
        List<DeviceEntity> activeAlarms = deviceRepository.findByUnitStatusNot(1);
        return ResponseEntity.ok(activeAlarms);
    }

    @PutMapping("/alarms/{devEui}/ack")
    public ResponseEntity<?> ackAlarm(@PathVariable String devEui) {
        deviceService.ackAlarm(devEui);
        return ResponseEntity.ok(Map.of("message", "警報已確認", "devEui", devEui));
    }

    @PutMapping("/alarms/ack-all")
    public ResponseEntity<?> ackAllAlarms() {
        deviceService.ackAllAlarms();
        return ResponseEntity.ok(Map.of("message", "所有警報已確認"));
    }

    // ==========================================
    // === Applications ===
    // ==========================================
    @GetMapping("/applications")
    public ResponseEntity<List<ChirpStackApp>> getApplications() {

        List<ChirpStackApp> apps = applicationService.getAllFromLocal();
        // 如果資料庫是空的就自動同步
        if (apps.isEmpty()) {
            apps = applicationService.syncFromChirpStack();
        }
        return ResponseEntity.ok(apps);

    }


    @PostMapping("/applications/sync")
    public ResponseEntity<List<ChirpStackApp>> syncApplications() {
        return ResponseEntity.ok(applicationService.syncFromChirpStack());
    }

    // ==========================================
    // === Gateways ===
    // ==========================================
    @GetMapping("/gateways")
    public ResponseEntity<List<Map<String, Object>>> getAllGateways() {
        return ResponseEntity.ok(gatewayService.listGatewaysWithMapFormat());
    }

    @GetMapping("/gateways/{gatewayId}/metrics")
    public ResponseEntity<GatewayMetricsResponseDTO> getGatewayMetrics(
            @PathVariable String gatewayId,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam String aggregation) {
        return ResponseEntity.ok(gatewayService.getGatewayMetrics(gatewayId, start, end, aggregation));
    }

    @GetMapping("/gateways/{gatewayId}/devices")
    public ResponseEntity<List<DeviceEntity>> getDevicesUnderGateway(@PathVariable String gatewayId) {
        return ResponseEntity.ok(gatewayService.getDevicesUnderGateway(gatewayId));
    }

    // ==========================================
    // 更新 Gateway 座標
    // 前端發送 JSON: { "latitude": 24.81, "longitude": 121.03 }
    // ==========================================
    @PutMapping("/gateways/{gatewayId}/location")
    public ResponseEntity<?> updateGatewayLocation(
            @PathVariable String gatewayId,
            @RequestBody Map<String, Double> payload) {
        try {
            Double latitude = payload.get("latitude");
            Double longitude = payload.get("longitude");

            if (latitude == null || longitude == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "錯誤：缺少 latitude 或 longitude 參數"
                ));
            }

            // 更新 ChirpStack
            gatewayService.updateGatewayLocation(gatewayId, latitude, longitude);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Gateway [" + gatewayId + "] 座標更新成功",
                    "latitude", latitude,
                    "longitude", longitude
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "更新 Gateway 座標失敗: " + e.getMessage()
            ));
        }
    }


    // ==========================================
    // === Devices ===
    // ==========================================


    // ==========================================
    // 手動觸發同步設備資料 (01 或 02 指令)
    // ==========================================
    @PostMapping("/devices/{devEui}/sync0102")
    public ResponseEntity<?> syncDeviceData(
            @PathVariable String devEui,
            @RequestBody Map<String, String> payload) {
        try {
            String target = payload.get("target"); // "INFO" 或 "SETTINGS"
            deviceService.forceSyncCommand(devEui, target);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "同步指令已成功加入列隊，等待設備回傳..."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "下發同步指令失敗: " + e.getMessage()));
        }
    }

    /** ==========================================
     * 01+02
     * 取得單一設備的全部詳細資訊 
     ==========================================*/
    @GetMapping("/devices/{devEui}/detail")
    public ResponseEntity<DeviceDetailResponseDto> getDeviceAggregatedDetail(@PathVariable String devEui) {
        DeviceDetailResponseDto detail = deviceService.getDeviceAggregatedDetail(devEui);

        return ResponseEntity.ok(detail);
    }

    @GetMapping("/devices")
    public ResponseEntity<List<Map<String, Object>>> getDevices(@RequestParam String applicationId) {
        return ResponseEntity.ok(deviceService.getDevicesByApplication(applicationId));
    }

    @PutMapping("/devices/{devEui}")
    public ResponseEntity<?> updateDevice(@PathVariable String devEui, @RequestBody DeviceConfigDto dto) {
        deviceService.updateDevice(devEui, dto);
        return ResponseEntity.ok(Map.of("message", "Device updated successfully"));
    }

    @GetMapping("/devices/{devEui}/history")
    public ResponseEntity<List<DeviceStatusLog>> getDeviceHistory(
            @PathVariable String devEui,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        return ResponseEntity.ok(deviceService.getDeviceHistory(devEui, start, end));
    }

    // ==========================================
    // 下發03即時數據指令
    // ==========================================
    @PostMapping("/devices/{devEui}/start-monitor")
    public ResponseEntity<?> startDeviceMonitor(@PathVariable String devEui) {
        try {
            deviceService.startRealTimeMonitor(devEui);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "監控指令已成功下發至設備 " + devEui));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "下發指令失敗: " + e.getMessage()));
        }
    }

    /**
     * ==========================================
     * 下發downlink 02設定指令(讀取)：設定高/低 溫警報門檻 (Set Temperature High/Low Alarm)
     * 指令格式: 0xB0 0x10 0x00 0x90 0x00 + 2 bytes
     * 指令格式: 0xB0 0x10 0x00 0x90 0x02 + 2 bytes
     * ==========================================
     */
    @PostMapping("/devices/{devEui}/settings/alarms")
    public ResponseEntity<?> setDeviceAlarms(@PathVariable String devEui, @RequestBody Map<String, Double> payload) {
        try {
            Double tempHigh = payload.get("tempHigh");
            Double tempLow = payload.get("tempLow");

            if (tempHigh == null && tempLow == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "錯誤：請至少提供 tempHigh 或 tempLow 參數"));
            }

            deviceService.updateDeviceTempAlarms(devEui, tempHigh, tempLow);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "設定指令已成功下發至設備 " + devEui + "，正在等待設備回傳最新狀態..."));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "下發設定指令失敗: " + e.getMessage()));
        }
    }

    


    /**==========================================
     * 設定device實體座標
     * JSON: { "latitude": 24.8123, "longitude": 121.0123 }
     ==========================================*/
     @PostMapping("/devices/{devEui}/location")
     public ResponseEntity<?> setDeviceLocation(
             @PathVariable String devEui,
             @RequestBody Map<String, Double> payload) {
         try {
             Double lat = payload.get("latitude");
             Double lon = payload.get("longitude");
 
             if (lat == null || lon == null) {
                 return ResponseEntity.badRequest().body(Map.of("message", "缺少經緯度參數"));
             }
 
             deviceService.updateDeviceLocation(devEui, lat, lon);
 
             return ResponseEntity.ok(Map.of(
                     "success", true,
                     "message", "座標設定指令已送入佇列，等待設備更新..."
             ));
         } catch (Exception e) {
             return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
         }
     }

    // ==========================================
    // 2. 高電壓警報門檻 (Voltage High Alarm)
    // 指令格式: 0xB0 0x10 0x00 0x90 0x04 + 2 bytes
    // ==========================================
    @PostMapping("/{devEui}/settings/voltage-high-alarm")
    public ResponseEntity<?> setVoltageHighAlarm(@PathVariable String devEui,
            @RequestBody Map<String, Double> payload) {
        return processTwoByteSettingCommand(devEui, (byte) 0x04, payload.get("voltage"), "高電壓警報");
    }

    // ==========================================
    // 3. 低電壓警報門檻 (Voltage Low Alarm)
    // 指令格式: 0xB0 0x10 0x00 0x90 0x06 + 2 bytes
    // ==========================================
    @PostMapping("/{devEui}/settings/voltage-low-alarm")
    public ResponseEntity<?> setVoltageLowAlarm(@PathVariable String devEui, @RequestBody Map<String, Double> payload) {
        return processTwoByteSettingCommand(devEui, (byte) 0x06, payload.get("voltage"), "低電壓警報");
    }


    // ==========================================
    // 設定設備實體地址"文字" 
    // 指令: 0xB0 0x10 0x00 0x90 0x33 + 96 bytes UTF-16
    // JSON: { "address": "66TH Avenue South Kent, WA 98032 U.S.A. " }
     // ==========================================
    @PostMapping("/devices/{devEui}/address")
    public ResponseEntity<?> setDeviceAddress(
            @PathVariable String devEui,
            @RequestBody Map<String, String> payload) {
        try {
            String address = payload.get("address");

            if (address == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "缺少 address 參數"));
            }

            deviceService.updateDeviceAddress(devEui, address);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "地址設定指令已送入佇列，等待設備更新..."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }




    // ==========================================
    //  處理 2 Bytes 設定值的通用方法
    // ==========================================
    private ResponseEntity<?> processTwoByteSettingCommand(String devEui, byte settingIndex, Double targetValue,
            String settingName) {
        try {
            if (targetValue == null) {
                return ResponseEntity.badRequest().body("錯誤：缺少必要的數值參數");
            }

            // 數值轉換：溫度與電壓皆須乘以 10 轉為整數
            short rawValue = (short) (targetValue * 10);

            // 組合原始未加密指令陣列 (固定 7 Bytes)
            byte[] rawCmd = new byte[] {
                    (byte) 0xB0, (byte) 0x10, (byte) 0x00, (byte) 0x90,
                    settingIndex, // 帶入該設定項目的專屬 Index
                    (byte) (rawValue & 0xFF), // 低位元 Little-Endian
                    (byte) ((rawValue >> 8) & 0xFF) // 高位元
            };

            // 進行 COBS 編碼 
            byte[] encodedCmd = CobsCodec.encode(rawCmd);

            // 轉為 Hex 字串
            String hexPayload = bytesToHex(encodedCmd);

            System.out.printf("準備下發 [%s] 給設備 %s, 目標值: %.1f, Hex: %s%n",
                    settingName, devEui, targetValue, hexPayload);

            // 送進 ChirpStack Queue
            String messageId = deviceService.enqueueDownlink(devEui, 10, hexPayload, 60);// 60秒過期

            return ResponseEntity.ok(Map.of(
                    "message", " 設定 [" + settingName + "] 指令已成功加入QUEUE",
                    "hexPayload", hexPayload,
                    "messageId", messageId));

        } catch (Exception e) {
            System.err.println("下發指令失敗: " + e.getMessage());
            return ResponseEntity.internalServerError().body("下發指令失敗: " + e.getMessage());
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    // ==========================================
    // 頻譜掃描 (Spectrum Scan) 指令 04~09
    // ==========================================
    @PostMapping("/{devEui}/spectrum/scan")
    public ResponseEntity<?> scanSpectrum(
            @PathVariable String devEui,
            @RequestBody Map<String, Integer> payload) {

        Integer type = payload.get("type"); // 0: Input, 1: Output
        Integer part = payload.get("part"); // 1: Part I, 2: Part II, 3: Part III

        if (type == null || part == null || part < 1 || part > 3 || (type != 0 && type != 1)) {
            return ResponseEntity.badRequest().body("錯誤：參數不正確 (type: 0/1, part: 1/2/3)");
        }

        // ==========================================
        // (4~9 指令)
        // Input Part 1~3 => 4, 5, 6
        // Output Part 1~3 => 7, 8, 9
        // ==========================================
        int commandId = 4 + (type * 3) + (part - 1);

        // 直接組合出 40010104 ~ 40010109 的 HEX 字串 
        String hexPayload = String.format("4001010%d", commandId);

        System.out.printf(" 準備下發 [頻譜掃描 Type:%d Part:%d] 給設備 %s, 指令代號: %d, Hex: %s%n",
                type, part, devEui, commandId, hexPayload);

        try {
            // 改用 enqueueSpectrumDownlink 並且把 TTL 設為 60 秒
            String messageId = deviceService.enqueueSpectrumDownlink(devEui, 10, hexPayload, 60);
            
            return ResponseEntity.ok(Map.of(
                    "message", "頻譜掃描指令已加入佇列",
                    "hexPayload", hexPayload,
                    "messageId", messageId));
        } catch (Exception e) {
            System.err.println("下發頻譜指令失敗: " + e.getMessage());
            return ResponseEntity.internalServerError().body("下發指令失敗: " + e.getMessage());
        }
    }

    // ==========================================
    // 強制清空設備指令 Queue 
    // ==========================================
    @DeleteMapping("/devices/{devEui}/queue")
    public ResponseEntity<?> clearDeviceQueue(@PathVariable String devEui) {
        try {
            // 直接呼叫 Service 層執行清空
            deviceService.flushQueue(devEui);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "已強制清空設備 " + devEui + " 的指令列隊"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "清空列隊失敗: " + e.getMessage()));
        }
    }

}