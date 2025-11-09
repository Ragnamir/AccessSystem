package com.example.webui.config;

import com.example.webui.websocket.UserStateWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UserStateWebSocketHandler userStateWebSocketHandler;

    public WebSocketConfig(UserStateWebSocketHandler userStateWebSocketHandler) {
        this.userStateWebSocketHandler = userStateWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(userStateWebSocketHandler, "/ws/users")
            .setAllowedOriginPatterns("*");
    }
}


