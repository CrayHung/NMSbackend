package com.example.demo.controller;

import com.example.demo.model.ChirpStackApp;
import com.example.demo.service.ChirpStackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.chirpstack.api.DeviceListItem;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.demo.dto.DeviceConfigDto;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChirpStackController {

    @Autowired
    private ChirpStackService chirpStackService;

    /************************************
     * Gateway
     ***********************************/
    /**
     * 獲取所有 Gateways
     * 對應前端: api.get('/gateways')
     */
    // @GetMapping("/gateways")
    // public ResponseEntity<Map<String, Object>> getAllGateways() {
    //     Map<String, Object> result = chirpStackService.getGateways();
    //     return ResponseEntity.ok(result);
    // }

    // /**
    //  * 獲取單一 Gateway
    //  * 對應前端: api.get('/gateways/{gatewayEui}')
    //  */
    // @GetMapping("/gateways/{gatewayEui}")
    // public ResponseEntity<?> getGateway(@PathVariable String gatewayEui) {
    //     Map<String, Object> gateway = chirpStackService.getGatewayDetail(gatewayEui);
    //     if (gateway == null) {
    //         return ResponseEntity.notFound().build();
    //     }
    //     return ResponseEntity.ok(gateway);
    // }

    /**
     * 新增單一 Gateway
     * POST http://localhost:8080/api/gateways
     */
    // @PostMapping("/gateways")
    // public ResponseEntity<?> createGateway(@RequestBody Map<String, Object> body) {
    //     try {
    //         // 檢查必要欄位 gatewayEui
    //         if (body.get("gatewayEui") == null || body.get("gatewayEui").toString().isEmpty()) {
    //             return ResponseEntity.badRequest().body("錯誤: 必須提供 gatewayEui");
    //         }

    //         Map<String, Object> result = chirpStackService.createGateway(body);
    //         return ResponseEntity.ok(result);
    //     } catch (Exception e) {
    //         return ResponseEntity.internalServerError().body("新增失敗: " + e.getMessage());
    //     }
    // }

    // /**
    //  * 更新單一 Gateway
    //  * PUT http://localhost:8080/api/gateways/{gatewayEui}
    //  */
    // @PutMapping("/gateways/{gatewayEui}")
    // public ResponseEntity<?> updateGateway(
    //         @PathVariable String gatewayEui,
    //         @RequestBody Map<String, Object> body) {
    //     try {
    //         Map<String, Object> result = chirpStackService.updateGateway(gatewayEui, body);
    //         return ResponseEntity.ok(result);
    //     } catch (Exception e) {
    //         return ResponseEntity.internalServerError().body("更新失敗: " + e.getMessage());
    //     }
    // }

    // /**
    //  * 刪除單一 Gateway
    //  * DELETE http://localhost:8080/api/gateways/{gatewayEui}
    //  */
    // @DeleteMapping("/gateways/{gatewayEui}")
    // public ResponseEntity<?> deleteGateway(@PathVariable String gatewayEui) {
    //     try {
    //         chirpStackService.deleteGateway(gatewayEui);
    //         Map<String, String> result = new HashMap<>();
    //         result.put("message", "Gateway " + gatewayEui + " 刪除成功");
    //         return ResponseEntity.ok(result);
    //     } catch (Exception e) {
    //         return ResponseEntity.internalServerError().body("刪除失敗: " + e.getMessage());
    //     }
    // }

    /************************************
     * Amplifier
     ***********************************/

    // /**
    //  * 獲取 Amplifier 裝置 (對齊舊後端路徑)
    //  * 對應前端: api.get('/amplifier/devices')
    //  */

    // // 取得特定 Application 底下的裝置devices
    // // 用法: GET /api/iot/devices?applicationId=xxxx-xxxx-xxxx
    // /**
    //  * 獲取所有裝置 (完全對齊舊後端 JSON 格式)
    //  * 格式: { "devices": [...], "count": 2, "online": 0, "alarmed": 0 }
    //  */
    // @GetMapping("/amplifier/devices")
    // public ResponseEntity<Map<String, Object>> getDevices(@RequestParam(required = false) String applicationId) {
        
    //     List<DeviceListItem> rawList;
        
    //     try {
    //         // 1. 決定資料來源：指定 ID 或 全部
    //         if (applicationId != null && !applicationId.trim().isEmpty()) {
    //             rawList = chirpStackService.getDevicesByApplicationId(applicationId);
    //         } else {
    //             // 若無 ID，則抓取所有 App 下的裝置
    //             rawList = chirpStackService.getAllDevices();
    //         }

    //         // 2. 準備回傳結構
    //         Map<String, Object> finalResponse = new HashMap<>();
    //         List<Map<String, Object>> devicesList = new ArrayList<>();
    //         int onlineCount = 0;
            
    //         DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX")
    //                 .withZone(ZoneId.of("UTC")); // 舊後端使用 ISO 8601 格式

    //         // 3. 逐筆轉換
    //         for (DeviceListItem device : rawList) {
    //             Map<String, Object> item = new HashMap<>();
                
    //             // --- 基本欄位對映 ---
    //             item.put("deviceEui", device.getDevEui());
    //             item.put("partName", device.getName()); // 對應 name
    //             item.put("partNumber", device.getName()); // 暫用 name
    //             item.put("description", device.getDescription());
    //             item.put("location", device.getDescription()); // 前端地圖依賴此欄位解析座標 (ex: Lat:24.8,Lon:121.0)
                
    //             // 嘗試解析 GPS (如果 description 裡有 "Lat:..,Lon:..")
    //             // 這裡先給 null，讓前端去 parse location 字串
    //             item.put("gpsLocation", null); 

    //             // --- 狀態判定 ---
    //             boolean isOnline = false;
    //             if (device.hasLastSeenAt()) {
    //                 Instant lastSeen = Instant.ofEpochSecond(
    //                     device.getLastSeenAt().getSeconds(), 
    //                     device.getLastSeenAt().getNanos()
    //                 );
    //                 item.put("lastSeen", formatter.format(lastSeen));
    //                 item.put("lastUpdated", formatter.format(lastSeen)); // 同步更新時間
                    
    //                 // 判斷 5 分鐘內為 Online
    //                 if (lastSeen.isAfter(Instant.now().minusSeconds(300))) {
    //                     isOnline = true;
    //                     onlineCount++;
    //                 }
    //             } else {
    //                 item.put("lastSeen", null);
    //                 item.put("lastUpdated", null);
    //             }
    //             item.put("onlineStatus", isOnline);

    //             // --- 模擬舊後端硬體欄位 (ChirpStack 列表無此資料，給予預設值避免前端報錯) ---
    //             item.put("serialNumber", "unknown");
    //             item.put("hwVersion", "1.0");
    //             item.put("fwVersion", "1.0");
    //             item.put("temperature", 0.0); // 無法從列表取得即時溫度
    //             item.put("voltage", 0.0);
    //             item.put("ripple", 0);
    //             item.put("unitStatus", isOnline ? 1 : 0); // 1: Normal, 0: Offline
    //             item.put("statusText", isOnline ? "Normal" : "Offline");
    //             item.put("batteryLevel", device.getDeviceStatus().getBatteryLevel()); // 如果有的話

    //             // --- Gateway 巢狀物件 (關鍵) ---
    //             // ChirpStack 列表不回傳 Gateway 資訊，這裡給予空結構以符合格式
    //             Map<String, Object> gwObj = new HashMap<>();
    //             gwObj.put("gatewayEui", null); // 前端會根據 null 不畫線，或者您可以填入預設值
    //             gwObj.put("name", null);
    //             gwObj.put("onlineStatus", false);
    //             item.put("gateway", gwObj);

    //             devicesList.add(item);
    //         }

    //         // 4. 封裝外層
    //         finalResponse.put("devices", devicesList);
    //         finalResponse.put("count", devicesList.size());
    //         finalResponse.put("online", onlineCount);
    //         finalResponse.put("alarmed", 0); // 暫無告警邏輯

    //         return ResponseEntity.ok(finalResponse);

    //     } catch (Exception e) {
    //         e.printStackTrace();
    //         return ResponseEntity.internalServerError().body(null);
    //     }
    // }

    
    // // 修改裝置設定
    // // PUT /api/iot/devices/{devEui}
    // /*
    //  * {
    //  * "name": "HFC-dev01-Updated",
    //  * "description": "這是透過 Spring Boot 修改的描述",
    //  * "isDisabled": true,
    //  * "skipFcntCheck": true,
    //  * "deviceProfileId": "你的_Device_Profile_ID"
    //  * }
    //  */
    // @PutMapping("/devices/{devEui}")
    // public ResponseEntity<?> updateDeviceConfig(
    //         @PathVariable String devEui,
    //         @RequestBody DeviceConfigDto deviceConfig) {

    //     try {
    //         chirpStackService.updateDevice(devEui, deviceConfig);

    //         Map<String, String> result = new HashMap<>();
    //         result.put("message", "裝置 " + devEui + " 更新成功");
    //         // 你也可以回傳更新後的設定，視前端需求而定
    //         return ResponseEntity.ok(result);

    //     } catch (Exception e) {
    //         return ResponseEntity.internalServerError().body("更新失敗: " + e.getMessage());
    //     }
    // }

    /************************************
     * Applications
     ***********************************/
    // 取得chirpstack應用程式列表 (讀取 MySQL速度快..但可能沒資料(尚未同步))
    // GET http://localhost:8080/api/iot/applications
    @GetMapping("/applications")
    public ResponseEntity<List<ChirpStackApp>> getApplications() {
        List<ChirpStackApp> list = chirpStackService.getAllFromLocal();
        return ResponseEntity.ok(list);
    }

    // 從 ChirpStack 同步更新資料到 MySQL
    // POST http://localhost:8080/api/iot/sync/applications
    @PostMapping("/sync/applications")
    public ResponseEntity<?> syncApplications() {
        try {
            List<ChirpStackApp> updatedList = chirpStackService.syncApplications();
            return ResponseEntity.ok("同步成功，共更新 " + updatedList.size() + " 筆資料");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("同步失敗: " + e.getMessage());
        }
    }

}