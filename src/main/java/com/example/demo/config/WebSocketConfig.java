package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 設定前端訂閱的前綴，例如 /topic/device/xxx
        config.enableSimpleBroker("/topic");
        // 設定前端發送訊息給後端的前綴 (本專案主要由後端推送，較少用到)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // React 前端連接的端點
        registry.addEndpoint("/ws-monitoring")
                .setAllowedOriginPatterns("*") // 允許所有來源跨域
                .withSockJS();
    }
}