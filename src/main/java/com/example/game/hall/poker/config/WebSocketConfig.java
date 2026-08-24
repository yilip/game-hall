package com.example.game.hall.poker.config;

import com.example.game.hall.poker.controller.PokerGameController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final PokerGameController pokerGameController;
    
    public WebSocketConfig(PokerGameController pokerGameController) {
        this.pokerGameController = pokerGameController;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pokerGameController, "/ws/poker")
                .setAllowedOrigins("*");
    }
}
