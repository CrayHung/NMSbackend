package com.example.demo.service;

import com.example.demo.dto.DeviceConfigDto;
import com.example.demo.model.DeviceEntity;
import com.example.demo.model.DeviceStatusLog;
import com.example.demo.repository.DeviceRepository;
import com.example.demo.repository.DeviceStatusLogRepository;
import com.google.protobuf.ByteString;
import io.chirpstack.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.example.demo.dto.DeviceDetailResponseDto;
import com.example.demo.service.CobsCodec;
import java.util.concurrent.ConcurrentHashMap;

import com.google.protobuf.Timestamp;
import org.springframework.beans.factory.annotation.Value;
import io.chirpstack.api.FlushDeviceQueueRequest;
import io.chirpstack.api.GetDeviceQueueItemsRequest;
import io.chirpstack.api.GetDeviceQueueItemsResponse;

@Service
public class DeviceService {

    @Autowired
    private DeviceServiceGrpc.DeviceServiceBlockingStub deviceStub;

    @Autowired
    private DeviceStatusLogRepository statusLogRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    private final DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_INSTANT;

    // 紀錄 DevEUI -> 上次請求同步的 Timestamp
    private final ConcurrentHashMap<String, Long> syncCooldownMap = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 1 * 60 * 1000; // 1 分鐘冷卻時間

    // 讀取 application.yml 中的過渡期開關 預設為 false 比較安全
    @Value("${iot.workaround.enable-flush-queue:false}")
    private boolean enableFlushQueueWorkaround;

    public DeviceDetailResponseDto getDeviceAggregatedDetail(String devEui) {
        DeviceDetailResponseDto response = new DeviceDetailResponseDto();
        response.setDevEui(devEui);

        // 用來精準判斷缺什麼資料
        boolean missingBasicInfo = false;
        boolean missingSettings = false;

        Optional<DeviceEntity> deviceOpt = deviceRepository.findById(devEui);
        if (deviceOpt.isPresent()) {
            DeviceEntity device = deviceOpt.get();
            response.setName(device.getName() != null ? device.getName() : "未命名");
            response.setLastSeenAt(device.getLastSeenAt());

            // 檢查是否缺少基本資訊
            if (device.getPartName() != null) {
                response.getBasicInfo().setPartName(device.getPartName());
                response.getBasicInfo().setPartNumber(device.getPartNumber());
                response.getBasicInfo().setSerialNumber(device.getSerialNumber());
                response.getBasicInfo().setFwVersion(device.getFwVersion());
            } else {
                missingBasicInfo = true;
            }

            // 檢查是否缺少設定參數
            if (device.getTempHighAlarm() != null) {
                response.getSettings().getAlarms().setTempHigh(device.getTempHighAlarm());
                response.getSettings().getAlarms().setTempLow(device.getTempLowAlarm());
                response.getSettings().getAlarms().setVoltHigh(device.getVoltHighAlarm());
                response.getSettings().getAlarms().setVoltLow(device.getVoltLowAlarm());
                response.getSettings().getAlarms().setRfOutputHigh(device.getRfOutputHighAlarm());
                response.getSettings().getSystem().setLogIntervalMin(device.getLogIntervalMin());
            } else {
                missingSettings = true;
            }
        } else {
            // 資料庫沒有這台設備全部都缺
            missingBasicInfo = true;
            missingSettings = true;
        }

        // 撈取 device_status_logs 表的最新一筆狀態
        statusLogRepository.findFirstByDevEuiOrderByCreatedAtDesc(devEui).ifPresent(log -> {
            DeviceDetailResponseDto.LatestStatus latest = response.getLatestStatus();
            latest.setUpdatedAt(log.getCreatedAt());
            latest.setUnitStatus(log.getUnitStatus() != null && log.getUnitStatus() == 1 ? "Normal" : "Alarm");

            latest.getMeasurements().setTemperature(log.getTemperature());
            latest.getMeasurements().setVoltage(log.getVoltage());
            latest.getMeasurements().setRipple(log.getRipple());
            latest.getMeasurements().setRfOutputPower(log.getRfOutputPower());
            latest.getMeasurements().setPilotLowPwr(log.getPilotLowPwr());
            latest.getMeasurements().setPilotHighPwr(log.getPilotHighPwr());
        });

        // 傳送下一個指令
        if (missingBasicInfo || missingSettings) {
            response.setSyncStatus("SYNCING");
            // 傳入缺少的狀態 讓方法決定要發送哪個指令
            triggerBackgroundSync(devEui, missingBasicInfo, missingSettings);
        }

        return response;
    }

    /**
     * 前端手動觸發的強制01 03同步指令
     */
    public void forceSyncCommand(String devEui, String target) {
        if ("INFO".equalsIgnoreCase(target)) {
            System.out.println(" [手動同步] 請求設備 " + devEui + " 基本資訊 (40010101)");
            enqueueDownlink(devEui, 10, "40010101", 60); // 1分鐘過期
        } else if ("SETTINGS".equalsIgnoreCase(target)) {
            System.out.println(" [手動同步] 請求設備 " + devEui + " 設定參數 (40010102)");
            enqueueDownlink(devEui, 10, "40010102", 60); // 1分鐘過期
        } else {
            throw new IllegalArgumentException("未知的同步目標: " + target);
        }
    }


    /**
     * 背景同步01和02指令 
     */
    private void triggerBackgroundSync(String devEui, boolean missingBasicInfo, boolean missingSettings) {
        long now = System.currentTimeMillis();

        try {
            
            if (missingBasicInfo) {
                long lastSyncInfo = syncCooldownMap.getOrDefault(devEui + "_INFO", 0L);
                if (now - lastSyncInfo >= 30 * 1000) {
                    syncCooldownMap.put(devEui + "_INFO", now);
                    System.out.println(" [背景同步] 發現設備 " + devEui + " 缺基本資訊，下發 40010101...");
                    enqueueDownlink(devEui, 10, "40010101", 120); // 過期指令
                }
            }

            if (missingSettings) {
                long lastSyncSettings = syncCooldownMap.getOrDefault(devEui + "_SETTINGS", 0L);
                if (now - lastSyncSettings >= 30 * 1000) {
                    syncCooldownMap.put(devEui + "_SETTINGS", now);
                    System.out.println(" [背景同步] 發現設備 " + devEui + " 缺設定參數，下發 40010102...");
                    enqueueDownlink(devEui, 10, "40010102", 120); // 過期指令
                }
            }
        } catch (Exception e) {
            System.err.println(" 背景下發請求指令失敗: " + e.getMessage());
        }
    }

    /**
     * 啟動即時監控 (單發觸發 Middleware 輪詢)
     * 發送 40010103 指令給設備
     */
    public void startRealTimeMonitor(String devEui) {
        try {
           
            String messageId = enqueueDownlink(devEui, 10, "40010103", 60); // 1分鐘過期

        } catch (Exception e) {
            System.err.println(" 啟動即時監控失敗: " + e.getMessage());
            throw new RuntimeException("無法下發監控指令", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public List<Map<String, Object>> getDevicesByApplication(String applicationId) {
        ListDevicesRequest request = ListDevicesRequest.newBuilder()
                .setApplicationId(applicationId).setLimit(1000).build();
        ListDevicesResponse response = deviceStub.list(request);

        List<Map<String, Object>> result = new ArrayList<>();
        for (DeviceListItem item : response.getResultList()) {
            Map<String, Object> map = new HashMap<>();

            String devEui = item.getDevEui();
            map.put("devEui", devEui);
            map.put("name", item.getName());
            map.put("description", item.getDescription());

            LocalDateTime lastSeenLdt = null;
            if (item.hasLastSeenAt()) {

                // 將 Protobuf 的 Timestamp 轉為字串供前端使用
                map.put("lastSeen", isoFormatter.format(
                        Instant.ofEpochSecond(item.getLastSeenAt().getSeconds(), item.getLastSeenAt().getNanos())));

                // 轉成 LocalDateTime 供資料庫使用 依據系統預設時區
                lastSeenLdt = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(item.getLastSeenAt().getSeconds(), item.getLastSeenAt().getNanos()),
                        ZoneId.systemDefault());
            } else {
                map.put("lastSeen", null);
            }
            result.add(map);

            // ==========================================
            // 同步資料到本地 MySQL 的 devices 表
            // ==========================================
            try {
        
                DeviceEntity deviceEntity = deviceRepository.findById(devEui).orElse(new DeviceEntity());

                deviceEntity.setDevEui(devEui);
                deviceEntity.setName(item.getName()); 

                if (lastSeenLdt != null) {
                    deviceEntity.setLastSeenAt(lastSeenLdt);
                }

                deviceRepository.save(deviceEntity);

            } catch (Exception e) {
                System.err.println("同步設備 " + devEui + " 到本地資料庫失敗: " + e.getMessage());
            }

        }
        return result;
    }

    public void updateDevice(String devEui, DeviceConfigDto dto) {
        Device current = deviceStub.get(GetDeviceRequest.newBuilder().setDevEui(devEui).build()).getDevice();
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
     * 清空 Queue 過渡期
     * 避免誤殺正在排隊的頻譜掃描等 需要長時間執行的指令
     */
    private void safeFlushQueue(String devEui) {
    
        if (!enableFlushQueueWorkaround) {
            return;
        }

        try {
            GetDeviceQueueItemsResponse queueResp = deviceStub.getQueue(
                    GetDeviceQueueItemsRequest.newBuilder().setDevEui(devEui).build());

            boolean hasImportantTask = false;

            // 檢查是否有不能被中斷的指令 (例如頻譜 40010104 ~ 40010109)
            for (DeviceQueueItem item : queueResp.getResultList()) {
                String hexCmd = bytesToHex(item.getData().toByteArray()).toUpperCase();

                if (hexCmd.startsWith("40010104") || hexCmd.startsWith("40010105") ||
                        hexCmd.startsWith("40010106") || hexCmd.startsWith("40010107") ||
                        hexCmd.startsWith("40010108") || hexCmd.startsWith("40010109")) {
                    hasImportantTask = true;
                    break;
                }
            }

            // 只有在沒有重要任務時  才執行 Flush
            if (!hasImportantTask) {
                deviceStub.flushQueue(FlushDeviceQueueRequest.newBuilder().setDevEui(devEui).build());
                // System.out.println(" [過渡期機制] 已安全清空設備 " + devEui + " 的積壓 Queue");
            } else {
                System.out.println(" [過渡期保護] 設備 " + devEui + " Queue 內包含頻譜掃描指令 跳過 Flush 動作");
            }

        } catch (Exception e) {
            System.err.println("檢查/清空 Queue 時發生錯誤: " + e.getMessage());
        }
    }

    /**
     * 下發方法升級版 (Expires At 與動態 TTL)
     */
    public String enqueueDownlink(String devEui, int fPort, String hexPayload, long ttlSeconds) {

        // 執行過渡期的智慧清空
        safeFlushQueue(devEui);

        // 執行計算 Expires At (當前時間 + TTL)
        Instant expireInstant = Instant.now().plusSeconds(ttlSeconds);
        Timestamp expiresAt = Timestamp.newBuilder()
                .setSeconds(expireInstant.getEpochSecond())
                .setNanos(expireInstant.getNano())
                .build();

        DeviceQueueItem item = DeviceQueueItem.newBuilder()
                .setDevEui(devEui)
                .setFPort(fPort)
                .setConfirmed(false)
                .setData(ByteString.copyFrom(hexStringToByteArray(hexPayload)))
                .setExpiresAt(expiresAt) // 寫入過期時間
                .build();

        return deviceStub.enqueue(EnqueueDeviceQueueItemRequest.newBuilder().setQueueItem(item).build()).getId();
    }

    // public String enqueueDownlink(String devEui, int fPort, String hexPayload) {
    // DeviceQueueItem item = DeviceQueueItem.newBuilder()
    // .setDevEui(devEui)
    // .setFPort(fPort)
    // .setConfirmed(false)
    // .setData(ByteString.copyFrom(hexStringToByteArray(hexPayload)))
    // .build();
    // return
    // deviceStub.enqueue(EnqueueDeviceQueueItemRequest.newBuilder().setQueueItem(item).build()).getId();
    // }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public List<DeviceStatusLog> getDeviceHistory(String devEui, String startStr, String endStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        LocalDateTime start = (startStr != null) ? LocalDateTime.parse(startStr, formatter)
                : LocalDateTime.now().minusDays(1);
        LocalDateTime end = (endStr != null) ? LocalDateTime.parse(endStr, formatter) : LocalDateTime.now();
        return statusLogRepository.findByDevEuiAndCreatedAtBetweenOrderByCreatedAtDesc(devEui, start, end);
    }

    // ==========================================
    // 警報確認 
    // ==========================================

    /**
     * 取得目前所有異常 且未確認的設備清單
     */
    public List<DeviceEntity> getActiveUnackedAlarms() {

        return deviceRepository.findByUnitStatusNotAndIsAlarmAckedFalse(1);
    }

    /**
     * 單一警報確認 (ACK)
     */
    public void ackAlarm(String devEui) {
        deviceRepository.findById(devEui).ifPresent(device -> {
            device.setIsAlarmAcked(true);
            deviceRepository.save(device);
        });
    }

    /**
     * 全部警報確認 (Clear All)
     */
    public void ackAllAlarms() {
        List<DeviceEntity> unackedDevices = deviceRepository.findByUnitStatusNotAndIsAlarmAckedFalse(1);
        for (DeviceEntity device : unackedDevices) {
            device.setIsAlarmAcked(true);
        }
        deviceRepository.saveAll(unackedDevices);
    }

    /*********************************************************
     * 更新高低溫警報Temperature High Alarm
     ********************************************************/
    public void updateDeviceTempAlarms(String devEui, Double tempHigh, Double tempLow) {
        try {
            // 設定高溫警報 (Hex Index: 0x00)
            if (tempHigh != null) {
                sendSettingCommand(devEui, 0x00, tempHigh);
                Thread.sleep(2000); // 停頓2秒 避免碰撞
            }

            // 設定低溫警報 (Hex Index: 0x02)
            if (tempLow != null) {
                sendSettingCommand(devEui, 0x02, tempLow);
                Thread.sleep(2000);
            }

            // 硬體不給Response 我們主動去要
            System.out.println(" 設定指令已全數發送，下發 40010102 請求最新設定以更新資料庫...");

            // 稍微繞過防洪機制  確保讀取指令能發送
            syncCooldownMap.put(devEui + "_SETTINGS", System.currentTimeMillis());
            enqueueDownlink(devEui, 10, "40010102", 60); // 60秒過期

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(" 下發設定指令時發生中斷");
        }
    }

    /*******************************************************
     * 下行設定 指令
     * 組合 RAW 指令 -> COBS 編碼 -> 加表頭 -> 下發
     *******************************************************/
    private void sendSettingCommand(String devEui, int hexIndex, Double tempValue) {
        // 數值轉換 (乘 10 轉為 short，Java 會自動處理負數的二補數)
        short tempShort = (short) Math.round(tempValue * 10.0);
        byte lowByte = (byte) (tempShort & 0xFF); // 取低位元組 (Little-Endian)
        byte highByte = (byte) ((tempShort >> 8) & 0xFF); // 取高位元組

        // 組合原始指令 (如: B0 10 00 90 02 A0 FE)
        byte[] baseCmd = new byte[] {
                (byte) 0xB0, 0x10, 0x00, (byte) 0x90, (byte) hexIndex, lowByte, highByte
        };

        // COBS 編碼
        byte[] cobsEncoded = CobsCodec.encode(baseCmd);

        // 拼裝系統表頭與結尾
        // 表頭 40 01 01 0A 02
        byte[] header = new byte[] { 0x40, 0x01, 0x01, 0x0A, 0x02 };

        // CobsCodec.java 中結尾的 0x00 被註解掉了 我們在此處手動補上
        byte[] finalPayload = new byte[header.length + cobsEncoded.length + 1];

        System.arraycopy(header, 0, finalPayload, 0, header.length);
        System.arraycopy(cobsEncoded, 0, finalPayload, header.length, cobsEncoded.length);
        finalPayload[finalPayload.length - 1] = 0x00; // 結尾補 0x00

        // 轉成 HEX 字串並丟進 ChirpStack Queue 
        String hexString = bytesToHex(finalPayload);
        System.out.printf("準備下發設定指令 [Index 0x%02X] 數值: %.1f ℃ | Payload: %s%n", hexIndex, tempValue, hexString);
        enqueueDownlink(devEui, 10, hexString, 60); 

    }

    /*******************************************************
     * 下行指令 下發device設備座標設定指令 (0xB0 0x10 0x00 0x80 0x45)
     * 格式: "Lat, Lon" 補足 39 bytes ASCII
     * RAW 指令 -> COBS 編碼 -> 加表頭 -> 下發
     ******************************************************/
    public void updateDeviceLocation(String devEui, Double lat, Double lon) {
        try {
            // 格式化字串並補齊至39Bytes 補空白 0x20
            String coordStr = String.format("%.10f, %.10f", lat, lon);
            // 確保長度精確為 39 bytes
            StringBuilder sb = new StringBuilder(coordStr);
            while (sb.length() < 39) {
                sb.append(" ");
            }
            String finalStr = sb.substring(0, 39);
            byte[] asciiBytes = finalStr.getBytes(java.nio.charset.StandardCharsets.US_ASCII);

            // 組合原始指令: B0 10 00 80 45 + 39 bytes
            byte[] baseCmd = new byte[5 + 39];
            baseCmd[0] = (byte) 0xB0;
            baseCmd[1] = 0x10;
            baseCmd[2] = 0x00;
            baseCmd[3] = (byte) 0x80;
            baseCmd[4] = 0x45;
            System.arraycopy(asciiBytes, 0, baseCmd, 5, 39);

            // COBS 編碼
            byte[] cobsEncoded = CobsCodec.encode(baseCmd);

            // 加上系統表頭 40 01 01 0A 02 與結尾 0x00
            byte[] header = new byte[] { 0x40, 0x01, 0x01, 0x0A, 0x02 };
            byte[] finalPayload = new byte[header.length + cobsEncoded.length + 1];
            System.arraycopy(header, 0, finalPayload, 0, header.length);
            System.arraycopy(cobsEncoded, 0, finalPayload, header.length, cobsEncoded.length);
            finalPayload[finalPayload.length - 1] = 0x00;

            String hexString = bytesToHex(finalPayload);
            System.out.println(" 準備下發設備座標: " + finalStr + " | Hex: " + hexString);

            // 下發指令
            enqueueDownlink(devEui, 10, hexString, 120);


            // 同步寫入MySQL 這樣地圖才抓得到座標
            deviceRepository.findById(devEui).ifPresent(device -> {
                device.setLatitude(String.valueOf(lat));
                device.setLongitude(String.valueOf(lon));
                deviceRepository.save(device);
                System.out.println("  設備座標已同步寫入 MySQL: [" + lat + ", " + lon + "]");
            });

        } catch (Exception e) {
            System.err.println("下發設備座標失敗: " + e.getMessage());
            throw new RuntimeException("設備座標下發失敗", e);
        }
    }

    /*******************************************************
     * 下發設備地址設定指令 (0xB0 0x10 0x00 0x90 0x33)
     * 格式: UTF-16LE 編碼 補足 96 bytes
     *******************************************************/
    public void updateDeviceAddress(String devEui, String address) {
        try {
            // 轉換為 UTF-16LE 0x32 0x00 ('2')確認為 LittleEndian
            byte[] utf16Bytes = address.getBytes(java.nio.charset.StandardCharsets.UTF_16LE);

            // 準備 96 bytes 容器並預填空白   UTF-16LE 的空白是 0x20 0x00
            byte[] paddedData = new byte[96];
            for (int i = 0; i < 96; i += 2) {
                paddedData[i] = 0x20;
                paddedData[i + 1] = 0x00;
            }

            int lengthToCopy = Math.min(utf16Bytes.length, 96);
            System.arraycopy(utf16Bytes, 0, paddedData, 0, lengthToCopy);

            // 組合原始指令: B0 10 00 90 33 + 96 bytes
            byte[] baseCmd = new byte[5 + 96];
            baseCmd[0] = (byte) 0xB0;
            baseCmd[1] = 0x10;
            baseCmd[2] = 0x00;
            baseCmd[3] = (byte) 0x90; 
            baseCmd[4] = 0x33; 
            System.arraycopy(paddedData, 0, baseCmd, 5, 96);

            // 進行 COBS 編碼
            byte[] cobsEncoded = CobsCodec.encode(baseCmd);

            // 加上系統表頭 40 01 01 0A 02 與結尾 0x00
            byte[] header = new byte[] { 0x40, 0x01, 0x01, 0x0A, 0x02 };
            byte[] finalPayload = new byte[header.length + cobsEncoded.length + 1];
            System.arraycopy(header, 0, finalPayload, 0, header.length);
            System.arraycopy(cobsEncoded, 0, finalPayload, header.length, cobsEncoded.length);
            finalPayload[finalPayload.length - 1] = 0x00;

            String hexString = bytesToHex(finalPayload);
            System.out.println(" 準備下發設備地址: " + address + " | Hex 長度: " + hexString.length() / 2);


            enqueueDownlink(devEui, 10, hexString, 80); //文字長度大,過期時間可能要延長

        } catch (Exception e) {
            System.err.println("下發設備地址失敗: " + e.getMessage());
            throw new RuntimeException("設備地址下發失敗", e);
        }
    }

    /********************************************************
     * 手動強制清空 Queue
     *******************************************************/
    public void flushQueue(String devEui) {
        try {
            deviceStub.flushQueue(FlushDeviceQueueRequest.newBuilder().setDevEui(devEui).build());
            System.out.println(" [手動介入] 已強制清空設備 " + devEui + " 的所有列隊指令");
        } catch (Exception e) {
            System.err.println("強制清空 Queue 發生錯誤: " + e.getMessage());
            throw new RuntimeException("ChirpStack API 呼叫失敗", e);
        }
    }

    /********************************************************
     * 頻譜掃描專用的下發方法  強制清空 Queue
     * 這裡只要收到新請求 就代表舊的已經超時或完成了 可以直接清空舊 Queue
    *******************************************************/
    public String enqueueSpectrumDownlink(String devEui, int fPort, String hexPayload, long ttlSeconds) {

        // 無視 safeFlushQueue 直接強制清空
        try {
            deviceStub.flushQueue(FlushDeviceQueueRequest.newBuilder().setDevEui(devEui).build());
            System.out.println(" [頻譜掃描通道] 已強制清空設備 " + devEui + " 的 Queue");
        } catch (Exception e) {
            System.err.println("清空 Queue 發生錯誤: " + e.getMessage());
        }

        // 設定過期時間 TTL
        Instant expireInstant = Instant.now().plusSeconds(ttlSeconds);
        Timestamp expiresAt = Timestamp.newBuilder()
                .setSeconds(expireInstant.getEpochSecond())
                .setNanos(expireInstant.getNano())
                .build();

        //組合並下發指令
        DeviceQueueItem item = DeviceQueueItem.newBuilder()
                .setDevEui(devEui)
                .setFPort(fPort)
                .setConfirmed(false)
                .setData(ByteString.copyFrom(hexStringToByteArray(hexPayload)))
                .setExpiresAt(expiresAt)
                .build();

        return deviceStub.enqueue(EnqueueDeviceQueueItemRequest.newBuilder().setQueueItem(item).build()).getId();
    }

}