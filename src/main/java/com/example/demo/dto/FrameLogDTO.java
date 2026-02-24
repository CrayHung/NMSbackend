package com.example.demo.dto;

import lombok.Data;
import java.util.Map;

@Data
public class FrameLogDTO {
    private String time;      // 格式化後的時間
    private String type;      // JoinRequest 或 UnconfirmedDataUp
    private String devAddr;   // 設備地址或 EUI
    private Map<String, Object> details; // JSON 詳情資料
}