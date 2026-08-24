package com.example.game.hall.poker.controller;

import com.example.game.hall.poker.model.PokerPlayer;
import com.example.game.hall.poker.service.PokerGameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 扑克游戏 WebSocket 控制器
 * 处理客户端 WebSocket 连接和消息
 */
@Component
public class PokerGameController extends TextWebSocketHandler {
    
    @Autowired
    private PokerGameService gameService;
    
    /**
     * 连接建立
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("新玩家连接：" + session.getId());
    }
    
    /**
     * 接收消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("收到消息：" + payload);
        
        // 解析消息
        Map<String, Object> msg = parseJson(payload);
        if (msg == null) {
            return;
        }
        
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
    }
    
    /**
     * 处理玩家加入
     */
    private void handleJoin(WebSocketSession session, Map<String, Object> msg) {
        String username = (String) msg.get("username");
        String avatar = (String) msg.get("avatar");
        
        if (username == null || username.trim().isEmpty()) {
            username = "Player_" + System.currentTimeMillis() % 10000;
        }
        
        PokerPlayer player = gameService.joinGame(username, avatar, session);
        
        if (player != null) {
            // 发送欢迎消息
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
    
    /**
     * 处理玩家离开
     */
    private void handleLeave(WebSocketSession session) {
        // 从会话中获取玩家 ID（实际项目中应维护会话 - 玩家映射）
        // 这里简化处理
        System.out.println("玩家离开：" + session.getId());
    }
    
    /**
     * 处理开始游戏
     */
    private void handleStart(WebSocketSession session) {
        boolean success = gameService.startGame();
        if (!success) {
            sendMessage(session, Map.of("type", "error", "message", "无法开始游戏"));
        }
    }
    
    /**
     * 处理玩家操作
     */
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
    
    /**
     * 处理旁观
     */
    private void handleSpectate(WebSocketSession session) {
        // 发送当前游戏状态
        Map<String, Object> gameState = gameService.getGameState();
        gameState.put("type", "spectate");
        sendMessage(session, gameState);
    }
    
    /**
     * 连接关闭
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        System.out.println("连接关闭：" + session.getId() + ", 状态：" + status);
        handleLeave(session);
    }
    
    /**
     * 处理错误
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("WebSocket 错误：" + exception.getMessage());
    }
    
    /**
     * 发送消息
     */
    private void sendMessage(WebSocketSession session, Map<String, Object> message) {
        if (session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(toJson(message)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 简单的 JSON 解析（实际项目中应使用 Jackson）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        // 简化解析，实际应使用 Jackson 或 Gson
        Map<String, Object> result = new java.util.HashMap<>();
        
        // 移除花括号
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }
        
        // 分割键值对
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    /**
     * 对象转 JSON（简化版）
     */
    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;
            
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof Integer || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value.toString()).append("\"");
            }
        }
        
        json.append("}");
        return json.toString();
    }
}
