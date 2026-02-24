package com.example.demo.service;

import com.google.protobuf.Timestamp;
import io.chirpstack.api.*;
import com.example.demo.dto.GatewayDetailDTO;
import com.example.demo.dto.ChartDataDTO;
import com.example.demo.dto.FrameLogDTO;
import com.example.demo.dto.GatewayMetricsResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.stream.Collectors;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GatewayService {

    @Autowired
    private GatewayServiceGrpc.GatewayServiceBlockingStub gatewayStub;
    @Autowired
    private ApplicationServiceGrpc.ApplicationServiceBlockingStub applicationStub;
    @Autowired
    private DeviceServiceGrpc.DeviceServiceBlockingStub deviceStub;

    @Value("${chirpstack.tenant-id}")
    private String tenantId;

    // 使用 ISO 8601 格式對齊舊服務規格: yyyy-MM-ddTHH:mm:ss.SSSZ
    private final DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_INSTANT;

    /**
     * 獲取特定網關下的設備列表 (模擬舊有服務)
     * GET /api/gateways/{gatewayId}/devices
     */
    public List<Map<String, Object>> getDevicesByGatewayId(String gatewayId) {
        List<Map<String, Object>> resultList = new ArrayList<>();

        try {
            // 1. 取得 Gateway 詳情以獲取租戶資訊
            GetGatewayResponse gwResp = gatewayStub.get(GetGatewayRequest.newBuilder()
                    .setGatewayId(gatewayId).build());
            var gw = gwResp.getGateway();

            // 2. 獲取該租戶下的所有應用程式 (Application)
            ListApplicationsRequest appReq = ListApplicationsRequest.newBuilder()
                    .setTenantId(gw.getTenantId())
                    .setLimit(100)
                    .build();
            ListApplicationsResponse appResp = applicationStub.list(appReq);

            // 3. 遍歷應用程式，取得底下所有裝置
            for (var appItem : appResp.getResultList()) {
                ListDevicesRequest devReq = ListDevicesRequest.newBuilder()
                        .setApplicationId(appItem.getId())
                        .setLimit(100)
                        .build();
                ListDevicesResponse devResp = deviceStub.list(devReq);

                for (var dev : devResp.getResultList()) {
                    Map<String, Object> deviceMap = new HashMap<>();
                    // 模擬舊有格式欄位
                    deviceMap.put("deviceEui", dev.getDevEui());
                    deviceMap.put("partName", dev.getName()); // 假設名稱對應 partName
                    deviceMap.put("onlineStatus", isDeviceOnline(dev.getLastSeenAt()));
                    deviceMap.put("lastSeen", formatTimestamp(dev.getLastSeenAt()));

                    // 嵌套 Gateway 資訊 (與您提供的舊 JSON 格式一致)
                    Map<String, Object> gwInfo = new HashMap<>();
                    gwInfo.put("gatewayEui", gw.getGatewayId());
                    gwInfo.put("name", gw.getName());
                    gwInfo.put("location", gw.getDescription());
                    deviceMap.put("gateway", gwInfo);

                    // 其他舊服務特有欄位 (如溫度、電壓等) 通常存在 Tags 或需要另行解析
                    deviceMap.put("statusText", "Normal");

                    resultList.add(deviceMap);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("無法取得網關關聯設備: " + e.getMessage());
        }
        return resultList;
    }

    // 輔助方法：時間轉換
    private String formatTimestamp(com.google.protobuf.Timestamp ts) {
        if (ts == null)
            return null;
        return java.time.Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos()).toString();
    }

    // 輔助方法：在線判定
    private boolean isDeviceOnline(com.google.protobuf.Timestamp ts) {
        if (ts == null)
            return false;
        java.time.Instant lastSeen = java.time.Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
        return lastSeen.isAfter(java.time.Instant.now().minusSeconds(300));
    }

    /**
     * 獲取網關統計指標
     * 
     * @param gatewayId 網關 EUI
     * @param startStr  開始時間 (ISO 8601)
     * @param endStr    結束時間 (ISO 8601)
     * @param aggStr    聚合單位 (HOUR, DAY, MONTH)
     */
    public GatewayMetricsResponseDTO getGatewayMetrics(String gatewayId, String startStr, String endStr,
            String aggStr) {
        // 1. 將字串解析為 Instant
        Instant startInstant = Instant.parse(startStr);
        Instant endInstant = Instant.parse(endStr);

        // 2. 轉換為 Protobuf Timestamp (此處必須對應 .proto 定義的 start/end)
        Timestamp startTs = Timestamp.newBuilder()
                .setSeconds(startInstant.getEpochSecond())
                .setNanos(startInstant.getNano()).build();

        Timestamp endTs = Timestamp.newBuilder()
                .setSeconds(endInstant.getEpochSecond())
                .setNanos(endInstant.getNano()).build();

        // 3. 轉換 Aggregation 枚舉 (來自 common.proto)
        Aggregation aggEnum = Aggregation.valueOf(aggStr.toUpperCase());

        // 4. 構建請求 - 注意使用 .setStart() 和 .setEnd()
        GetGatewayMetricsRequest request = GetGatewayMetricsRequest.newBuilder()
                .setGatewayId(gatewayId)
                .setStart(startTs) // 對應 proto 欄位 2: start
                .setEnd(endTs) // 對應 proto 欄位 3: end
                .setAggregation(aggEnum)
                .build();

        // 5. 執行 gRPC 呼叫
        GetGatewayMetricsResponse response = gatewayStub.getMetrics(request);

        // 6. 轉換回舊服務要求的 DTO 格式
        return convertToDTO(response);
    }

    /**
     * 將 gRPC Response 轉換為前端巢狀 DTO
     */
    private GatewayMetricsResponseDTO convertToDTO(GetGatewayMetricsResponse response) {
        GatewayMetricsResponseDTO dto = new GatewayMetricsResponseDTO();

        // 依照 .proto 中的 7 個指標欄位進行映射 [cite: 590, 591, 592, 593]
        dto.setRxPackets(mapToMetricSet(response.getRxPackets(), "Received"));
        dto.setTxPackets(mapToMetricSet(response.getTxPackets(), "Transmitted"));
        dto.setTxPacketsPerFreq(mapToMetricSet(response.getTxPacketsPerFreq(), "Transmitted / frequency"));
        dto.setRxPacketsPerFreq(mapToMetricSet(response.getRxPacketsPerFreq(), "Received / frequency"));
        dto.setTxPacketsPerDr(mapToMetricSet(response.getTxPacketsPerDr(), "Transmitted / DR"));
        dto.setRxPacketsPerDr(mapToMetricSet(response.getRxPacketsPerDr(), "Received / DR"));
        dto.setTxPacketsPerStatus(mapToMetricSet(response.getTxPacketsPerStatus(), "TX packets / status"));

        return dto;
    }

    /**
     * 輔助方法：處理單一 Metric 轉換
     */
    private GatewayMetricsResponseDTO.MetricSet mapToMetricSet(Metric metric, String displayName) {
        GatewayMetricsResponseDTO.MetricSet set = new GatewayMetricsResponseDTO.MetricSet();
        set.setName(displayName);
        set.setKind("ABSOLUTE");

        // 若無資料則回傳空結構
        if (metric == null || metric.getTimestampsCount() == 0) {
            set.setTimestamps(new ArrayList<>());
            set.setDatasets(new ArrayList<>());
            return set;
        }

        // 時間戳記轉換: Timestamp -> ISO String
        List<String> timestamps = metric.getTimestampsList().stream()
                .map(ts -> isoFormatter.format(Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos())))
                .collect(Collectors.toList());
        set.setTimestamps(timestamps);

        // 數據集轉換: Float -> Integer (符合舊服務 Data 格式)
        List<GatewayMetricsResponseDTO.MetricDataset> datasets = metric.getDatasetsList().stream()
                .map(ds -> {
                    GatewayMetricsResponseDTO.MetricDataset datasetDTO = new GatewayMetricsResponseDTO.MetricDataset();
                    datasetDTO.setLabel(ds.getLabel());
                    List<Integer> intData = ds.getDataList().stream()
                            .map(Float::intValue)
                            .collect(Collectors.toList());
                    datasetDTO.setData(intData);
                    return datasetDTO;
                }).collect(Collectors.toList());
        set.setDatasets(datasets);

        return set;
    }

    /**
     * 獲取所有 Gateways (對齊舊服務指定的 JSON 結構)
     */
    public Map<String, Object> listGatewaysWithMapFormat() {
        ListGatewaysRequest request = ListGatewaysRequest.newBuilder()
                .setLimit(100)
                .setTenantId(tenantId)
                .build();

        ListGatewaysResponse response = gatewayStub.list(request);

        List<Map<String, Object>> gatewaysList = new ArrayList<>();
        int onlineCount = 0;

        for (GatewayListItem item : response.getResultList()) {
            Map<String, Object> gMap = new HashMap<>();

            // 1. 欄位映射: 使用 gatewayEui
            gMap.put("gatewayEui", item.getGatewayId());
            gMap.put("name", item.getName());
            gMap.put("location", item.getDescription()); // 描述映射到 location

            // 2. 座標映射
            if (item.hasLocation()) {
                gMap.put("latitude", item.getLocation().getLatitude());
                gMap.put("longitude", item.getLocation().getLongitude());
                gMap.put("altitude", item.getLocation().getAltitude());
            } else {
                gMap.put("latitude", 0.0);
                gMap.put("longitude", 0.0);
                gMap.put("altitude", 0.0);
            }

            // 3. 在線狀態與時間格式化
            boolean isOnline = item.hasLastSeenAt();
            gMap.put("onlineStatus", isOnline);

            if (isOnline) {
                onlineCount++;
                gMap.put("lastSeen", isoFormatter.format(Instant.ofEpochSecond(
                        item.getLastSeenAt().getSeconds(), item.getLastSeenAt().getNanos())));
            } else {
                gMap.put("lastSeen", null);
            }
            gatewaysList.add(gMap);
        }

        // 4. 封裝統計資訊
        Map<String, Object> result = new HashMap<>();
        result.put("gateways", gatewaysList);
        result.put("count", response.getTotalCount());
        result.put("online", onlineCount);
        return result;
    }

    /**
     * 獲取詳情 (Dashboard 專用)
     */
    public GatewayDetailDTO getGatewayDetail(String id) {
        GetGatewayResponse resp = gatewayStub.get(GetGatewayRequest.newBuilder().setGatewayId(id).build());
        Gateway g = resp.getGateway();

        GatewayDetailDTO dto = new GatewayDetailDTO();
        dto.setGatewayEui(g.getGatewayId());
        dto.setName(g.getName());
        dto.setLocation(g.getDescription());

        if (g.hasLocation()) {
            dto.setLatitude(g.getLocation().getLatitude());
            dto.setLongitude(g.getLocation().getLongitude());
            dto.setAltitude(g.getLocation().getAltitude());
        }

        dto.setOnlineStatus(resp.hasLastSeenAt());
        if (resp.hasLastSeenAt()) {
            dto.setLastSeen(isoFormatter.format(Instant.ofEpochSecond(
                    resp.getLastSeenAt().getSeconds(), resp.getLastSeenAt().getNanos())));
        }

        // Dashboard 圖表模擬數據 (可根據需求擴展 gRPC Metrics 呼叫)
        dto.setMetrics(Arrays.asList(
                new ChartDataDTO("Jan 8", 100L, 0L, 4, 923200000L),
                new ChartDataDTO("Jan 24", 800000L, 0L, 4, 903900000L),
                new ChartDataDTO("Jan 28", 1712000L, 0L, 4, 903900000L),
                new ChartDataDTO("Feb 5", 50000L, 0L, 6, 204100000L)));
        return dto;
    }

    /**
     * 建立網關 (對接舊服務 POST Body 格式)
     */
    public void createGateway(GatewayDetailDTO dto) {

        String eui = dto.getGatewayEui();

        // 檢查是否為空、長度是否為 16、是否為純十六進制
        if (eui == null || eui.length() != 16 || !eui.matches("^[0-9a-fA-F]+$")) {
            throw new IllegalArgumentException("Invalid Gateway EUI: Must be 16 hex characters.");
        }

        Gateway gateway = Gateway.newBuilder()
                .setGatewayId(dto.getGatewayEui())
                .setName(dto.getName())
                .setDescription(dto.getLocation() != null ? dto.getLocation() : "")
                .setTenantId(tenantId)
                .setLocation(Location.newBuilder()
                        .setLatitude(dto.getLatitude() != null ? dto.getLatitude() : 0.0)
                        .setLongitude(dto.getLongitude() != null ? dto.getLongitude() : 0.0)
                        .setAltitude(dto.getAltitude() != null ? dto.getAltitude() : 0.0)
                        .build())
                .build();

        gatewayStub.create(CreateGatewayRequest.newBuilder().setGateway(gateway).build());
    }

    /**
     * 更新網關
     */
    public void updateGateway(String id, GatewayDetailDTO dto) {
        GetGatewayResponse resp = gatewayStub.get(GetGatewayRequest.newBuilder().setGatewayId(id).build());
        Gateway current = resp.getGateway();

        Gateway updated = current.toBuilder()
                .setName(dto.getName())
                .setDescription(dto.getLocation() != null ? dto.getLocation() : "")
                .setLocation(current.getLocation().toBuilder()
                        .setLatitude(
                                dto.getLatitude() != null ? dto.getLatitude() : current.getLocation().getLatitude())
                        .setLongitude(
                                dto.getLongitude() != null ? dto.getLongitude() : current.getLocation().getLongitude())
                        .setAltitude(
                                dto.getAltitude() != null ? dto.getAltitude() : current.getLocation().getAltitude())
                        .build())
                .build();

        gatewayStub.update(UpdateGatewayRequest.newBuilder().setGateway(updated).build());
    }

    /**
     * 刪除網關
     */
    public void deleteGateway(String id) {
        gatewayStub.delete(DeleteGatewayRequest.newBuilder().setGatewayId(id).build());
    }

    /**
     * 獲取封包日誌 (模擬數據)
     */
    public List<FrameLogDTO> getFrameLogs(String id) {
        FrameLogDTO f = new FrameLogDTO();
        f.setTime(isoFormatter.format(Instant.now()));
        f.setType("UnconfirmedDataUp");
        f.setDevAddr("0008311f");
        f.setDetails(Map.of("rssi", -60, "snr", 9.0, "fPort", 1));
        return Collections.singletonList(f);
    }



    
    /**
     * 獲取整合網關詳情與設備清單的完整物件 (真實數據版本)
     */
    public Map<String, Object> getGatewayFullDetails(String gatewayId) {
        Map<String, Object> finalResponse = new HashMap<>();

        try {
            // 1. 取得網關詳情 (沿用現有邏輯)
            GatewayDetailDTO gatewayDetail = getGatewayDetail(gatewayId);
            Map<String, Object> gwMap = buildGatewayMap(gatewayDetail);

            // 2. 取得網關下的基本設備列表
            List<Map<String, Object>> rawDevices = getDevicesByGatewayId(gatewayId);

            // 3. 針對每個設備撈取真實 Metrics
            List<Map<String, Object>> enrichedDevices = rawDevices.stream().map(dev -> {
                Map<String, Object> d = new HashMap<>(dev);
                String devEui = dev.get("deviceEui").toString();

                // --- 關鍵：從 ChirpStack 撈取該設備的真實指標 ---
                Map<String, Double> realValues = getLatestDeviceMetrics(devEui);

                // 替換模擬值：如果 Metrics 裡有資料就用真實的，否則給 0.0
                d.put("temperature", realValues.getOrDefault("temperature", 0.0));
                d.put("voltage", realValues.getOrDefault("voltage", 0.0));
                d.put("ripple", realValues.getOrDefault("ripple", 0.0).intValue());
                d.put("rfInputAvgPower", realValues.getOrDefault("rfInputAvgPower", 0.0));
                d.put("rfOutputAvgPower", realValues.getOrDefault("rfOutputAvgPower", 0.0));

                // 補齊其他固定硬體資訊
                d.put("partNumber", "AFM8-TR41TC2RN80");
                d.put("hwVersion", "105");
                d.put("fwVersion", "160");
                d.put("unitStatus", ((boolean) dev.get("onlineStatus")) ? 1 : 0);
                d.put("statusText", ((Double) d.get("temperature") > 70.0) ? "Alarm" : "Normal"); // 簡單告警邏輯

                // 嵌套網關資訊
                d.put("gateway", gwMap);
                return d;
            }).collect(Collectors.toList());

            finalResponse.put("gateway", gwMap);
            finalResponse.put("devices", enrichedDevices);

        } catch (Exception e) {
            throw new RuntimeException("整合網關詳情失敗: " + e.getMessage());
        }
        return finalResponse;
    }

    /**
     * 輔助方法：呼叫 gRPC 取得設備最新的指標數據
     */
    private Map<String, Double> getLatestDeviceMetrics(String devEui) {
        Map<String, Double> metricsResult = new HashMap<>();
        try {
            // 設定查詢範圍：最近 24 小時 [cite: 1126]
            Instant now = Instant.now();
            Instant start = now.minus(24, java.time.temporal.ChronoUnit.HOURS);

            GetDeviceMetricsRequest req = GetDeviceMetricsRequest.newBuilder()
                    .setDevEui(devEui)
                    .setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).build())
                    .setEnd(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                    .setAggregation(Aggregation.HOUR)
                    .build();

            // 呼叫 DeviceService
            GetDeviceMetricsResponse resp = deviceStub.getMetrics(req);
            Map<String, Metric> metricsMap = resp.getMetricsMap();

            // 遍歷所有 Metrics (如 temperature, voltage 等)
            for (Map.Entry<String, Metric> entry : metricsMap.entrySet()) {
                Metric m = entry.getValue();
                // 如果該指標有數據，抓取最後一筆 (最新值)
                if (m.getDatasetsCount() > 0 && m.getDatasets(0).getDataCount() > 0) {
                    List<Float> dataList = m.getDatasets(0).getDataList();
                    float latestValue = dataList.get(dataList.size() - 1);
                    metricsResult.put(entry.getKey(), (double) latestValue);
                }
            }
        } catch (Exception e) {
            // 若該設備尚未有 Metrics (Codec 未設定或未上傳)，則返回空 Map
            System.err.println("無法取得設備 " + devEui + " 的指標: " + e.getMessage());
        }
        return metricsResult;
    }

    /**
     * 輔助方法：封裝 Gateway Map
     */
    private Map<String, Object> buildGatewayMap(GatewayDetailDTO detail) {
        Map<String, Object> gwMap = new HashMap<>();
        gwMap.put("gatewayEui", detail.getGatewayEui());
        gwMap.put("name", detail.getName());
        gwMap.put("location", detail.getLocation());
        gwMap.put("latitude", detail.getLatitude());
        gwMap.put("longitude", detail.getLongitude());
        gwMap.put("altitude", detail.getAltitude());
        gwMap.put("onlineStatus", detail.getOnlineStatus());
        gwMap.put("lastSeen", detail.getLastSeen());
        return gwMap;
    }
}