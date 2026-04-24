package com.example.demo.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeviceDetailResponseDto {
    private String devEui = "unknow";
    private String name = "unknow";
    private LocalDateTime lastSeenAt;
    private String syncStatus = "SYNCED"; // 狀態標記: SYNCED (已同步) 或 SYNCING (同步中)

    private BasicInfo basicInfo = new BasicInfo();
    private Settings settings = new Settings();
    private LatestStatus latestStatus = new LatestStatus();

    @Data
    public static class BasicInfo {
        // 如果資料庫是 NULL，前端就會收到 "資料同步中..."
        private String partName = "data sync...";
        private String partNumber = "data sync...";
        private String serialNumber = "data sync...";
        private String fwVersion = "data sync...";
    }

    @Data
    public static class Settings {
        private Alarms alarms = new Alarms();
        private SystemConfig system = new SystemConfig();
        
        @Data
        public static class Alarms {
            // 數字型態給予 -999.0 代表尚未取得資料
            private Double tempHigh = -999.0;
            private Double tempLow = -999.0;
            private Double voltHigh = -999.0;
            private Double voltLow = -999.0;
            private Double rfOutputHigh = -999.0;
        }
        
        @Data
        public static class SystemConfig {
            private Integer logIntervalMin = -999;
        }
    }

    @Data
    public static class LatestStatus {
        private LocalDateTime updatedAt;
        private String unitStatus = "waiting..."; 
        private Measurements measurements = new Measurements();
        private ActiveAlarms activeAlarms = new ActiveAlarms();

        @Data
        public static class Measurements {
            private Double temperature = -999.0;
            private Double voltage = -999.0;
            private Integer ripple = -999;
            private Double rfOutputPower = -999.0;
            private Double pilotLowPwr = -999.0;
            private Double pilotHighPwr = -999.0;
        }

        @Data
        public static class ActiveAlarms {
            private Boolean isTempAlarm = false;
            private Boolean isVoltageAlarm = false;
            private Boolean isRippleAlarm = false;
            private Boolean isRfPowerAlarm = false;
        }
    }
}