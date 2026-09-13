package com.example.game.hall.poker.controller;

import com.example.game.hall.poker.model.PokerPlayer;
import com.example.game.hall.poker.service.PokerGameService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PokerGameController extends TextWebSocketHandler {

    @Autowired
    private PokerGameService gameService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("新玩家连接：" + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(payload, Map.class);

            String action = (String) msg.get("action");

            switch (action) {
                case "join":
                    handleJoin(session, msg);
                    break;

                case "leave":
                    handleLeave(session);
                    break;

                case "start":
                    handleStart(session);
                    break;

                case "action":
                    handlePlayerAction(session, msg);
                    break;

                case "spectate":
                    handleSpectate(session);
                    break;

                default:
                    System.out.println("未知动作：" + action);
            }
        } catch (Exception e) {
            System.err.println("消息解析失败：" + e.getMessage());
        }
    }

    private void handleJoin(WebSocketSession session, Map<String, Object> msg) {
        String username = (String) msg.get("username");
        String avatar = (String) msg.get("avatar");

        if (username == null || username.trim().isEmpty()) {
            username = "Player_" + System.currentTimeMillis() % 10000;
        }

        PokerPlayer player = gameService.joinGame(username, avatar, session);

        if (player != null) {
            Map<String, Object> response = Map.of(
                "type", "welcome",
                "playerId", player.getId(),
                "username", player.getUsername(),
                "seatIndex", player.getSeatIndex()
            );
            sendMessage(session, response);
        } else {
            sendMessage(session, Map.of("type", "error", "message", "游戏已满"));
        }
    }

    private void handleLeave(WebSocketSession session) {
        System.out.println("玩家离开：" + session.getId());
    }

    private void handleStart(WebSocketSession session) {
        boolean success = gameService.startGame();
        if (!success) {
            sendMessage(session, Map.of("type", "error", "message", "无法开始游戏"));
        }
    }

    private void handlePlayerAction(WebSocketSession session, Map<String, Object> msg) {
        String playerId = (String) msg.get("playerId");
        String actionStr = (String) msg.get("actionType");
        Integer raiseAmount = (Integer) msg.get("raiseAmount");

        if (playerId == null || actionStr == null) {
            return;
        }

        PokerPlayer.Action action;
        try {
            action = PokerPlayer.Action.valueOf(actionStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        if (raiseAmount == null) {
            raiseAmount = 0;
        }

        gameService.playerAction(playerId, action, raiseAmount);
    }

    private void handleSpectate(WebSocketSession session) {
        Map<String, Object> gameState = gameService.getGameState();
        gameState.put("type", "spectate");
        sendMessage(session, gameState);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("连接关闭：" + session.getId() + ", 状态：" + status);
        handleLeave(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("WebSocket 错误：" + exception.getMessage());
    }

    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        if (session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(json));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
