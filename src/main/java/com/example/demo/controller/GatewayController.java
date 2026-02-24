package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.service.GatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gateways")
public class GatewayController {

    @Autowired
    private GatewayService gatewayService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAll() {
        return ResponseEntity.ok(gatewayService.listGatewaysWithMapFormat());
    }

    @GetMapping("/{id}")
    public ResponseEntity<GatewayDetailDTO> getOne(@PathVariable String id) {
        return ResponseEntity.ok(gatewayService.getGatewayDetail(id));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody GatewayDetailDTO dto) {
        gatewayService.createGateway(dto);
        return ResponseEntity.ok(Map.of("message", "Gateway created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody GatewayDetailDTO dto) {
        gatewayService.updateGateway(id, dto);
        return ResponseEntity.ok(Map.of("message", "Gateway updated successfully"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        gatewayService.deleteGateway(id);
        return ResponseEntity.ok(Map.of("message", "Gateway deleted successfully"));
    }

    @GetMapping("/{id}/frames")
    public ResponseEntity<List<FrameLogDTO>> getFrames(@PathVariable String id) {
        return ResponseEntity.ok(gatewayService.getFrameLogs(id));
    }

    /**
     * 獲取網關統計指標 (Metrics)
     * 路徑: GET /api/gateways/{gatewayId}/metrics?start=...&end=...&aggregation=...
     * 格式完全適配舊服務 (ChirpStack 原生格式)
     */
    @GetMapping("/{gatewayId}/metrics")
    public ResponseEntity<GatewayMetricsResponseDTO> getGatewayMetrics(
            @PathVariable String gatewayId,
            @RequestParam(value = "start") String start,
            @RequestParam(value = "end") String end,
            @RequestParam(value = "aggregation") String aggregation) {

        // 將接收到的三個字串參數正確傳遞給 Service 處理
        GatewayMetricsResponseDTO metrics = gatewayService.getGatewayMetrics(
                gatewayId,
                start,
                end,
                aggregation);

        return ResponseEntity.ok(metrics);
    }

    /**
     * 獲取特定網關下的設備列表 (模擬舊有服務)
     * GET /api/gateways/{gatewayId}/devices
     */
    @GetMapping("/{gatewayId}/devices")
    public ResponseEntity<List<Map<String, Object>>> getDevicesByGateway(@PathVariable String gatewayId) {
        // 透過 Service 進行資料組裝與轉換
        List<Map<String, Object>> devices = gatewayService.getDevicesByGatewayId(gatewayId);
        return ResponseEntity.ok(devices);
    }

    /**
     * 獲取網關詳情及其關聯設備列表 (對齊舊服務規格)
     * GET /api/gateways/{gatewayId}/details
     */
    @GetMapping("/{gatewayId}/details")
    public ResponseEntity<Map<String, Object>> getGatewayFullDetails(@PathVariable String gatewayId) {
        // 呼叫 Service 取得整合後的數據結構
        Map<String, Object> details = gatewayService.getGatewayFullDetails(gatewayId);
        return ResponseEntity.ok(details);
    }

}