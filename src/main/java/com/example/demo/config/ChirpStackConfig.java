package com.example.demo.config;

import io.chirpstack.api.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChirpStackConfig {

    @Value("${chirpstack.host:localhost}")
    private String host;

    @Value("${chirpstack.port:8080}")
    private int port;

    @Value("${chirpstack.api-token:}")
    private String apiToken;

    @Bean
    public ManagedChannel managedChannel() {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    private Metadata getAuthHeader() {
        Metadata header = new Metadata();
        Metadata.Key<String> key = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
        header.put(key, "Bearer " + apiToken);
        return header;
    }

    @Bean
    public GatewayServiceGrpc.GatewayServiceBlockingStub gatewayStub(ManagedChannel channel) {
        // 修正後的注入方式
        return GatewayServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(getAuthHeader()));
    }

    @Bean
    public DeviceServiceGrpc.DeviceServiceBlockingStub deviceStub(ManagedChannel channel) {
        return DeviceServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(getAuthHeader()));
    }

    @Bean
    public ApplicationServiceGrpc.ApplicationServiceBlockingStub applicationStub(ManagedChannel channel) {
        return ApplicationServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(getAuthHeader()));
    }
}