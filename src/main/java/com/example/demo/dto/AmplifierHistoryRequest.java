package com.example.demo.dto;

import lombok.Data;

@Data
public class AmplifierHistoryRequest {
    private String deviceEui;
    private String start; // YYYY-MM-DDTHH:mm:ssZ
    private String end;   // YYYY-MM-DDTHH:mm:ssZ
}