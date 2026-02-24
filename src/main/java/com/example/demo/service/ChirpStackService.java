/*
 * 只負責與 ChirpStack 進行最原始的 gRPC 交互（例如：發送 Downlink、獲取原始 Device 列表）
 */
package com.example.demo.service;

import io.chirpstack.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ChirpStackService {

    @Value("${chirpstack.tenant-id}")
    private String tenantId;

    @Autowired
    private GatewayServiceGrpc.GatewayServiceBlockingStub gatewayStub;

    @Autowired
    private DeviceServiceGrpc.DeviceServiceBlockingStub deviceStub;

    @Autowired
    private ApplicationServiceGrpc.ApplicationServiceBlockingStub applicationStub;

    // --- Gateway 相關方法 ---
    public ListGatewaysResponse listGateways() {
        return gatewayStub.list(ListGatewaysRequest.newBuilder()
                .setTenantId(tenantId)
                .setLimit(100)
                .build());
    }

    public GetGatewayResponse getGateway(String gatewayId) {
        return gatewayStub.get(GetGatewayRequest.newBuilder().setGatewayId(gatewayId).build());
    }

    // --- Device 相關方法 ---
    public ListDevicesResponse listDevices(String applicationId) {
        return deviceStub.list(ListDevicesRequest.newBuilder()
                .setApplicationId(applicationId)
                .setLimit(100)
                .build());
    }

    public GetDeviceResponse getDevice(String devEui) {
        return deviceStub.get(GetDeviceRequest.newBuilder().setDevEui(devEui).build());
    }

    public void updateDevice(Device device) {
        deviceStub.update(UpdateDeviceRequest.newBuilder().setDevice(device).build());
    }

    // --- Application 相關方法 ---
    public ListApplicationsResponse listApplications() {
        return applicationStub.list(ListApplicationsRequest.newBuilder()
                .setTenantId(tenantId)
                .setLimit(100)
                .build());
    }
}