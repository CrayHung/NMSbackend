package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChartDataDTO {
    private String name;        // 時間標籤 (如 "Jan 8")
    private Long received;      // 接收封包數
    private Long transmitted;   // 發送封包數
    private Integer dr;         // Data Rate
    private Long freq;          // 頻率 (Hz)
}