package com.example.demo.dto;

import lombok.Data;
import java.util.Map;

@Data
public class AmplifierHistoryDto {
    private String deviceEui;
    private String createdAt;
    private String gatewayEui;
    private String partName;
    private String partNumber;
    private String serialNumber;
    private String hwVersion;
    private String fwVersion;
    private String location;
    private String gpsLocation;
    
    // Amplifier 狀態數值
    private Integer workingMode;
    private Integer dfuType;
    private Integer unitStatus;
    private String statusText;
    
    // 類比測量值
    private Double temperature;
    private Double voltage;
    private Double ripple;
    private Double current;
    private Double rfInputPower;
    private Double rfOutputPower;
    private Double pilotLowPower;
    private Double pilotHighPower;
    private Double outputSlope;
    
    // 警報狀態 (對應 Proto 中的變數類型)
    private Integer tempAlarmStatus;
    private Integer voltAlarmStatus;
    private Integer rippleAlarmStatus;
    private Integer tcpAlarmStatus;
    
    // 負載與頻率
    private Double fwdLoadingPowerLow;
    private Double fwdLoadingPowerHigh;
    private Integer fwdPilotLowFrequency;
    private Integer fwdPilotHighFrequency;
}