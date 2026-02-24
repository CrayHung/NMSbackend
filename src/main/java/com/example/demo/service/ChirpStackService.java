package com.example.demo.service;

import io.chirpstack.api.ApplicationListItem;
import io.chirpstack.api.ApplicationServiceGrpc;
import io.chirpstack.api.ListApplicationsRequest;
import io.chirpstack.api.ListApplicationsResponse;

import io.chirpstack.api.Device;
import io.chirpstack.api.GetDeviceRequest;
import io.chirpstack.api.GetDeviceResponse;
import io.chirpstack.api.UpdateDeviceRequest;
import com.example.demo.dto.DeviceConfigDto;
import io.chirpstack.api.DeviceListItem;
import io.chirpstack.api.DeviceServiceGrpc;
import io.chirpstack.api.*;
import io.chirpstack.api.ListDevicesRequest;
import io.chirpstack.api.ListDevicesResponse;


import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.model.ChirpStackApp;
import com.example.demo.repository.ChirpStackAppRepository;

import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Service
public class ChirpStackService {

    @Value("${chirpstack.host}")
    private String host;

    @Value("${chirpstack.port}")
    private int port;

    @Value("${chirpstack.api-key}")
    private String apiKey;

    @Value("${chirpstack.tenant-id}")
    private String tenantId;

    @Autowired
    private ChirpStackAppRepository appRepository;

    private ApplicationServiceGrpc.ApplicationServiceBlockingStub applicationStub;
    private DeviceServiceGrpc.DeviceServiceBlockingStub deviceStub;
    private GatewayServiceGrpc.GatewayServiceBlockingStub gatewayStub;

    // 當 Bean 初始化完成後，自動建立連線通道
    @PostConstruct
    public void init() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        // 設定Header (API KEY)
        Metadata header = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        header.put(key, "Bearer " + apiKey);

        // 初始化所有需要的 Stub
        gatewayStub = GatewayServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header));

        // 初始化application stub
        applicationStub = ApplicationServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header));

        // 初始化device stub
        deviceStub = DeviceServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header));

    }

    /*********************************************
     * 
     * gateway
     * 
     ********************************************/
    /**
     * 獲取所有 Gateway 列表 (對齊舊後端 Swagger 格式)
     */
    /**
     * 返回格式: { "gateways": [...], "count": X, "online": Y }
     */
    public Map<String, Object> getGateways() {
        Map<String, Object> finalResponse = new HashMap<>();
        List<Map<String, Object>> gatewaysList = new ArrayList<>();
        int onlineCount = 0;

        try {
            ListGatewaysRequest request = ListGatewaysRequest.newBuilder()
                    .setTenantId(tenantId)
                    .setLimit(100)
                    .build();

            ListGatewaysResponse response = gatewayStub.list(request);

            for (GatewayListItem gw : response.getResultList()) {
                Map<String, Object> item = new HashMap<>();
                // 轉譯：gRPC gatewayId -> 舊格式 gatewayEui
                item.put("gatewayEui", gw.getGatewayId());
                item.put("name", gw.getName().isEmpty() ? null : gw.getName());
                item.put("location", gw.getDescription().isEmpty() ? null : gw.getDescription());

                // 列表通常不含細節座標，預設 0.0 或依需求 call GetGateway
                item.put("latitude", 0.0);
                item.put("longitude", 0.0);
                item.put("altitude", 0);

                boolean isOnline = false;
                if (gw.hasLastSeenAt()) {
                    java.time.Instant instant = java.time.Instant.ofEpochSecond(
                            gw.getLastSeenAt().getSeconds(),
                            gw.getLastSeenAt().getNanos());
                    item.put("lastSeen", instant.toString());
                    // 判斷在線狀態 (5分鐘內)
                    isOnline = instant.isAfter(java.time.Instant.now().minusSeconds(300));
                } else {
                    item.put("lastSeen", null);
                }

                item.put("onlineStatus", isOnline);
                if (isOnline)
                    onlineCount++;
                gatewaysList.add(item);
            }

            finalResponse.put("gateways", gatewaysList);
            finalResponse.put("count", gatewaysList.size());
            finalResponse.put("online", onlineCount);

        } catch (Exception e) {
            throw new RuntimeException("獲取 Gateway 列表失敗: " + e.getMessage());
        }
        return finalResponse;
    }

    /**
     * 更新單一 Gateway 資訊
     */
    public Map<String, Object> updateGateway(String gatewayEui, Map<String, Object> body) {
        try {
            // A. 先抓取原始資料確保欄位完整
            GetGatewayResponse current = gatewayStub
                    .get(GetGatewayRequest.newBuilder().setGatewayId(gatewayEui).build());

            // B. 根據舊格式 body 更新原始物件
            Gateway.Builder builder = current.getGateway().toBuilder();
            if (body.containsKey("name"))
                builder.setName(body.get("name").toString());
            if (body.containsKey("location"))
                builder.setDescription(body.get("location").toString());

            io.chirpstack.api.Location.Builder locBuilder = builder.getLocation().toBuilder();
            if (body.containsKey("latitude"))
                locBuilder.setLatitude(Double.parseDouble(body.get("latitude").toString()));
            if (body.containsKey("longitude"))
                locBuilder.setLongitude(Double.parseDouble(body.get("longitude").toString()));
            builder.setLocation(locBuilder.build());

            UpdateGatewayRequest request = UpdateGatewayRequest.newBuilder().setGateway(builder.build()).build();
            gatewayStub.update(request);

            return body;
        } catch (Exception e) {
            throw new RuntimeException("ChirpStack 更新失敗: " + e.getMessage());
        }
    }
    /*
     * 取得單一 Gateway 詳情 (對齊舊後端 Swagger 格式)
     */

    public Map<String, Object> getGatewayDetail(String gatewayEui) {
        try {
            // 建立 gRPC 請求
            GetGatewayRequest request = GetGatewayRequest.newBuilder()
                    .setGatewayId(gatewayEui)
                    .build();

            // 呼叫 ChirpStack
            GetGatewayResponse response = gatewayStub.get(request);
            var gw = response.getGateway();

            // 封裝成舊後端格式
            Map<String, Object> result = new HashMap<>();
            result.put("gatewayEui", gw.getGatewayId());
            result.put("name", gw.getName());
            result.put("location", gw.getDescription());
            result.put("latitude", gw.getLocation().getLatitude());
            result.put("longitude", gw.getLocation().getLongitude());
            result.put("altitude", (int) gw.getLocation().getAltitude());

            // 處理在線狀態與時間格式 (2026-02-03T04:02:59.311Z)
            if (response.hasLastSeenAt()) {
                java.time.Instant instant = java.time.Instant.ofEpochSecond(
                        response.getLastSeenAt().getSeconds(),
                        response.getLastSeenAt().getNanos());
                result.put("lastSeen", instant.toString());
                // 判斷 5 分鐘內是否有訊號
                result.put("onlineStatus", instant.isAfter(java.time.Instant.now().minusSeconds(300)));
            } else {
                result.put("lastSeen", null);
                result.put("onlineStatus", false);
            }

            return result;
        } catch (Exception e) {
            System.err.println("取得單一 Gateway 失敗: " + e.getMessage());
            return null;
        }
    }

    /**
     * 新增單一 Gateway
     */
    public Map<String, Object> createGateway(Map<String, Object> body) {
        try {
            // 從舊格式 body 提取資料
            String gatewayEui = body.get("gatewayEui").toString();
            String name = (String) body.getOrDefault("name", "");
            String location = (String) body.getOrDefault("location", ""); // 映射至 description
            double lat = Double.parseDouble(body.getOrDefault("latitude", 0.0).toString());
            double lon = Double.parseDouble(body.getOrDefault("longitude", 0.0).toString());

            // 構建 gRPC 原始物件
            Gateway gw = Gateway.newBuilder()
                    .setGatewayId(gatewayEui) // 原始欄位: gateway_id
                    .setName(name)
                    .setDescription(location) // 舊格式 location 存入原始 description
                    .setTenantId(tenantId)
                    .setLocation(io.chirpstack.api.Location.newBuilder()
                            .setLatitude(lat)
                            .setLongitude(lon)
                            .build())
                    .build();

            CreateGatewayRequest request = CreateGatewayRequest.newBuilder().setGateway(gw).build();
            gatewayStub.create(request);

            return body; // 成功後回傳原格式
        } catch (Exception e) {
            throw new RuntimeException("ChirpStack 新增失敗: " + e.getMessage());
        }
    }

    /**
     * 刪除單一 Gateway
     */
    public void deleteGateway(String gatewayEui) {
        try {
            gatewayStub.delete(DeleteGatewayRequest.newBuilder().setGatewayId(gatewayEui).build());
        } catch (Exception e) {
            throw new RuntimeException("ChirpStack 刪除失敗: " + e.getMessage());
        }
    }

    /************************************
     * 
     * Devices
     * 
     *************************************/

    // 根據 Application ID 取得 Devices 列表的方法
    public List<DeviceListItem> getDevicesByApplicationId(String applicationId) {
        try {
            ListDevicesRequest request = ListDevicesRequest.newBuilder()
                    .setLimit(100) // 設定讀取上限
                    .setApplicationId(applicationId) // 指定 Application ID
                    .build();

            ListDevicesResponse response = deviceStub.list(request);
            return response.getResultList();

        } catch (Exception e) {
            System.err.println("ChirpStack Device Error: " + e.getMessage());
            throw new RuntimeException("無法取得裝置列表");
        }
    }

    // 修改Device欄位資訊
    public void updateDevice(String devEui, DeviceConfigDto dto) {
        try {
            // 1. 先讀取舊資料 (Get)
            // 這是必要的，因為我們只修改部分欄位，其他欄位要保持原樣
            GetDeviceRequest getRequest = GetDeviceRequest.newBuilder()
                    .setDevEui(devEui)
                    .build();

            GetDeviceResponse getResponse = deviceStub.get(getRequest);
            Device currentDevice = getResponse.getDevice();

            // 2. 建立 Builder 準備修改 (Modify)
            Device.Builder builder = currentDevice.toBuilder();

            // 3. 根據 DTO 內容更新欄位 (只更新有傳值的欄位)
            if (dto.getName() != null) {
                builder.setName(dto.getName());
            }
            if (dto.getDescription() != null) {
                builder.setDescription(dto.getDescription());
            }
            if (dto.getDeviceProfileId() != null) {
                builder.setDeviceProfileId(dto.getDeviceProfileId());
            }
            if (dto.getJoinEui() != null) {
                builder.setJoinEui(dto.getJoinEui());
            }
            if (dto.getIsDisabled() != null) {
                builder.setIsDisabled(dto.getIsDisabled());
            }
            if (dto.getSkipFcntCheck() != null) {
                builder.setSkipFcntCheck(dto.getSkipFcntCheck());
            }

            // 4. 送出更新 (Update)
            UpdateDeviceRequest updateRequest = UpdateDeviceRequest.newBuilder()
                    .setDevice(builder.build())
                    .build();

            deviceStub.update(updateRequest);
            System.out.println("裝置 " + devEui + " 設定更新成功！");

        } catch (Exception e) {
            System.err.println("更新裝置失敗: " + e.getMessage());
            throw new RuntimeException("無法更新裝置: " + devEui + ", 原因: " + e.getMessage());
        }
    }

    // 獲取所有 Application 下的所有裝置
    public List<DeviceListItem> getAllDevices() {
        List<DeviceListItem> allDevices = new ArrayList<>();

        // 1. 取得本地所有 App
        List<ChirpStackApp> apps = getAllFromLocal();
        if (apps.isEmpty()) {
            apps = syncApplications(); // 若無資料則嘗試同步
        }

        // 2. 遍歷每個 App 去抓裝置
        for (ChirpStackApp app : apps) {
            try {
                // 這裡重複利用已有的單一查詢方法
                allDevices.addAll(getDevicesByApplicationId(app.getId()));
            } catch (Exception e) {
                System.err.println("讀取 App (" + app.getName() + ") 裝置失敗: " + e.getMessage());
            }
        }
        return allDevices;
    }

    
    /***************************************
     * 
     * Applications
     * 
     ***************************************/
    // gRPC 抓取application方法
    public List<ApplicationListItem> fetchFromRemote() {
        try {
            ListApplicationsRequest request = ListApplicationsRequest.newBuilder()
                    .setLimit(100)
                    .setTenantId(tenantId)
                    .build();
            return applicationStub.list(request).getResultList();
        } catch (Exception e) {
            throw new RuntimeException("ChirpStack gRPC 連線失敗: " + e.getMessage());
        }
    }

    // 同步application方法：抓取 -> 轉換 -> 存入 MySQL
    @Transactional
    public List<ChirpStackApp> syncApplications() {
        // A. 從 gRPC 取得最新資料
        List<ApplicationListItem> remoteList = fetchFromRemote();
        List<ChirpStackApp> entities = new ArrayList<>();

        // B. 轉換資料格式
        LocalDateTime now = LocalDateTime.now();
        for (ApplicationListItem item : remoteList) {
            ChirpStackApp app = new ChirpStackApp();
            app.setId(item.getId());
            app.setName(item.getName());
            app.setDescription(item.getDescription());
            app.setLastSyncTime(now);
            entities.add(app);
        }

        // C. 存入資料庫 (saveAll 會自動判斷是新增還是更新)
        return appRepository.saveAll(entities);
    }

    // 從本地資料庫讀取 (給前端用，速度快)
    public List<ChirpStackApp> getAllFromLocal() {
        return appRepository.findAll();
    }

}