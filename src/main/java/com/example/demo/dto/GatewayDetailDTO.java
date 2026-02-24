package com.example.demo.dto;

import lombok.Data;
import java.util.List;

@Data
public class GatewayDetailDTO {
    // 網關唯一識別碼 (對應 ChirpStack 的 gateway_id)
    private String gatewayEui;    
    
    private String name;
    
    // 映射自 ChirpStack 的 description 欄位
    private String location;      
    
    private Double latitude;
    private Double longitude;
    private Double altitude;
    
    private Boolean onlineStatus;
    
    // 最後見到時間 (ISO 8601 格式: 2026-02-09T09:32:04.119Z)
    private String lastSeen;      
    
    // 供 Dashboard 圖表使用的數據列表
    private List<ChartDataDTO> metrics; 
}