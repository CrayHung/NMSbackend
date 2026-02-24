// 接收前端要修改的欄位資訊
package com.example.demo.dto;

import lombok.Data;

@Data
public class DeviceConfigDto {
    // 1. Name
    private String name; 
    
    // 2. Description
    private String description;
    
    // 3. Device Profile (下拉選單對應的 ID)
    private String deviceProfileId; 
    
    // 4. Join EUI (OTA 裝置用)
    private String joinEui;

    // 5. Device is disabled (開關)
    private Boolean isDisabled; 
    
    // 6. Disable frame-counter validation (開關)
    // 注意: 在 Protobuf 中這個欄位叫 skip_fcnt_check
    private Boolean skipFcntCheck; 
}