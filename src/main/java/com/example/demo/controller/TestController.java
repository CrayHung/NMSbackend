package com.example.demo.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.MonitoringService;

@RestController
    @RequestMapping("/api/test")
    public class TestController {
        @Autowired
        private MonitoringService monitoringService;

        @PostMapping("/push/{devEui}")
        public String triggerPush(@PathVariable String devEui) {
            monitoringService.sendDeviceUpdate(devEui, Map.of(
                    "temperature", 42.5,
                    "voltage", 24.1,
                    "status", "Online"));
            return "Pushed to /topic/device/" + devEui;
        }
    }

