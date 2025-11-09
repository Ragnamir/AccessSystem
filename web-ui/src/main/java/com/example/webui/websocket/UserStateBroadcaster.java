package com.example.webui.websocket;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UserStateBroadcaster {

    private final UserStateWebSocketHandler handler;

    public UserStateBroadcaster(UserStateWebSocketHandler handler) {
        this.handler = handler;
    }

    @Scheduled(fixedRate = 1000)
    public void publishUserStates() {
        handler.broadcastSnapshot();
    }
}


