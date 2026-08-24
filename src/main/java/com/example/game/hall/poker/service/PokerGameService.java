package com.example.game.hall.poker.service;

import com.example.game.hall.poker.model.*;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 扑克游戏服务
 * 管理游戏状态和逻辑
 */
@Service
public class PokerGameService {
    
    // 游戏配置
    private static final int MAX_PLAYERS = 8;
    private static final int SMALL_BLIND = 10;
    private static final int BIG_BLIND = 20;
    private static final int MIN_PLAYERS = 2;
    
    // 游戏状态
    private GamePhase phase = GamePhase.WAITING;
    private final List<PokerPlayer> players = new CopyOnWriteArrayList<>();
    private final Deck deck = new Deck();
    private final List<Card> communityCards = new ArrayList<>();
    private int pot = 0;
    private int currentBet = 0;
    private int dealerIndex = 0;
    private int currentPlayerIndex = 0;
    private int minRaise = BIG_BLIND;
    
    // 会话管理
    private final Map<String, WebSocketSession> playerSessions = new HashMap<>();
    private final AtomicInteger playerIdCounter = new AtomicInteger(1);
    
    // AI 服务
    private final AIPlayerService aiService = new AIPlayerService();
    
    // AI 玩家风格列表（用于填充空位）
    private static final AIStyle[] AI_STYLES = {
        AIStyle.AGGRESSIVE,   // 激进型
        AIStyle.CONSERVATIVE, // 保守型
        AIStyle.RANDOM,       // 随机型
        AIStyle.CALCULATOR,   // 计算型
        AIStyle.CALLER        // 跟随型
    };
    
    // 游戏锁
    private final Object gameLock = new Object();
    
    /**
     * 玩家加入游戏
     */
    public synchronized PokerPlayer joinGame(String username, String avatar, WebSocketSession session) {
        if (players.size() >= MAX_PLAYERS) {
            return null; // 游戏已满
        }
        
        // 检查是否已有该用户
        for (PokerPlayer player : players) {
            if (player.getUsername().equals(username)) {
                playerSessions.put(player.getId(), session);
                return player;
            }
        }
        
        // 创建新玩家
        String playerId = "player_" + playerIdCounter.incrementAndGet();
        int seatIndex = findEmptySeat();
        
        PokerPlayer player = new PokerPlayer(playerId, username, avatar != null ? avatar : getDefaultAvatar(), 
                                            seatIndex, false);
        players.add(player);
        playerSessions.put(playerId, session);
        
        // 广播玩家加入
        broadcastGameState();
        
        // 如果游戏未开始且玩家足够，可以开始游戏
        if (phase == GamePhase.WAITING && players.size() >= MIN_PLAYERS) {
            // 等待更多玩家或手动开始
        }
        
        return player;
    }
    
    /**
     * 玩家离开游戏
     */
    public synchronized void leaveGame(String playerId) {
        PokerPlayer playerToRemove = null;
        for (PokerPlayer player : players) {
            if (player.getId().equals(playerId)) {
                playerToRemove = player;
                break;
            }
        }
        
        if (playerToRemove != null) {
            players.remove(playerToRemove);
            playerSessions.remove(playerId);
            
            // 如果玩家在游戏中，自动弃牌
            if (!phase.equals(GamePhase.WAITING)) {
                playerToRemove.setFolded(true);
            }
            
            broadcastGameState();
        }
    }
    
    /**
     * 开始游戏
     */
    public synchronized boolean startGame() {
        if (players.size() < MIN_PLAYERS || phase != GamePhase.WAITING) {
            return false;
        }
        
        // 填充 AI 玩家到空座位（最多 8 人）
        fillAIPlayers();
        
        // 重置游戏
        resetGame();
        
        // 设置盲注
        setBlinds();
        
        // 发手牌
        dealHoleCards();
        
        // 进入翻牌前阶段
        phase = GamePhase.PREFLOP;
        
        // 设置当前玩家（大盲注左边）
        currentPlayerIndex = (dealerIndex + 2) % players.size();
        
        broadcastGameState();
        
        return true;
    }
    
    /**
     * 玩家操作
     */
    public synchronized void playerAction(String playerId, PokerPlayer.Action action, int raiseAmount) {
        if (currentPlayerIndex >= players.size()) {
            return;
        }
        
        PokerPlayer currentPlayer = players.get(currentPlayerIndex);
        if (!currentPlayer.getId().equals(playerId)) {
            return; // 不是当前玩家的回合
        }
        
        int toCall = currentBet - currentPlayer.getCurrentBet();
        
        switch (action) {
            case FOLD:
                currentPlayer.setFolded(true);
                break;
                
            case CHECK:
                if (toCall > 0) {
                    return; // 不能过牌，需要跟注
                }
                break;
                
            case CALL:
                currentPlayer.bet(toCall);
                break;
                
            case RAISE:
                int actualRaise = Math.max(raiseAmount, toCall + minRaise);
                currentPlayer.bet(actualRaise);
                currentBet = currentPlayer.getCurrentBet();
                minRaise = actualRaise - toCall;
                break;
                
            case ALL_IN:
                int allInAmount = currentPlayer.getChips();
                currentPlayer.bet(allInAmount);
                if (currentPlayer.getCurrentBet() > currentBet) {
                    currentBet = currentPlayer.getCurrentBet();
                }
                break;
        }
        
        // 移动到下一个玩家
        moveToNextPlayer();
    }
    
    /**
     * AI 玩家决策
     */
    public void aiThink(PokerPlayer aiPlayer) {
        new Thread(() -> {
            synchronized (gameLock) {
                int toCall = currentBet - aiPlayer.getCurrentBet();
                
                PokerPlayer.Action action = aiService.think(
                    aiPlayer,
                    currentBet,
                    toCall,
                    aiPlayer.getHandCards(),
                    communityCards,
                    pot
                );
                
                int raiseAmount = 0;
                if (action == PokerPlayer.Action.RAISE) {
                    raiseAmount = aiService.getRaiseAmount(aiPlayer, currentBet, minRaise);
                }
                
                playerAction(aiPlayer.getId(), action, raiseAmount);
            }
        }).start();
    }
    
    /**
     * 移动到下一个玩家
     */
    private void moveToNextPlayer() {
        int nextIndex = (currentPlayerIndex + 1) % players.size();
        int startIndex = nextIndex;
        
        // 找到下一个可以操作的玩家
        while (true) {
            PokerPlayer player = players.get(nextIndex);
            
            if (!player.isFolded() && !player.isAllIn()) {
                currentPlayerIndex = nextIndex;
                
                // 检查是否所有玩家都已行动
                if (isRoundComplete()) {
                    nextPhase();
                } else if (player.isAI()) {
                    aiThink(player);
                }
                break;
            }
            
            nextIndex = (nextIndex + 1) % players.size();
            
            if (nextIndex == startIndex) {
                // 所有玩家都已行动或弃牌/全下
                nextPhase();
                break;
            }
        }
        
        broadcastGameState();
    }
    
    /**
     * 检查本轮是否完成
     */
    private boolean isRoundComplete() {
        boolean allActed = true;
        int activePlayers = 0;
        
        for (PokerPlayer player : players) {
            if (!player.isFolded()) {
                activePlayers++;
                if (!player.isAllIn() && player.getCurrentBet() < currentBet) {
                    allActed = false;
                }
            }
        }
        
        return allActed && activePlayers > 1;
    }
    
    /**
     * 进入下一阶段
     */
    private void nextPhase() {
        // 收集底池
        for (PokerPlayer player : players) {
            pot += player.getCurrentBet();
            player.setCurrentBet(0);
        }
        
        currentBet = 0;
        minRaise = BIG_BLIND;
        
        phase = phase.next();
        
        switch (phase) {
            case FLOP:
                // 发翻牌（3 张）
                communityCards.addAll(deck.dealCards(3));
                currentPlayerIndex = dealerIndex; // 从庄家位置开始
                break;
                
            case TURN:
                // 发转牌（1 张）
                communityCards.add(deck.dealCard());
                currentPlayerIndex = dealerIndex;
                break;
                
            case RIVER:
                // 发河牌（1 张）
                communityCards.add(deck.dealCard());
                currentPlayerIndex = dealerIndex;
                break;
                
            case SHOWDOWN:
                // 摊牌
                showdown();
                return;
                
            default:
                // 游戏结束
                endGame();
                return;
        }
        
        // 找到第一个可以操作的玩家
        findFirstActivePlayer();
        broadcastGameState();
    }
    
    /**
     * 摊牌
     */
    private void showdown() {
        List<PokerPlayer> activePlayers = new ArrayList<>();
        for (PokerPlayer player : players) {
            if (!player.isFolded()) {
                activePlayers.add(player);
            }
        }
        
        if (activePlayers.size() == 1) {
            // 只有一人未弃牌，直接获胜
            distributePot(activePlayers.get(0));
        } else {
            // 比较手牌
            PokerHand bestHand = null;
            PokerPlayer winner = null;
            
            for (PokerPlayer player : activePlayers) {
                PokerHand hand = HandEvaluator.evaluate(player.getHandCards(), communityCards);
                if (bestHand == null || hand.getScore() > bestHand.getScore()) {
                    bestHand = hand;
                    winner = player;
                }
            }
            
            if (winner != null) {
                distributePot(winner);
            }
        }
        
        broadcastGameState();
    }
    
    /**
     * 分配底池
     */
    private void distributePot(PokerPlayer winner) {
        winner.setChips(winner.getChips() + pot);
        pot = 0;
        
        // 发送获胜消息
        sendMessage(winner.getId(), "恭喜你获胜！赢得底池：" + (pot + winner.getCurrentBet()));
        
        endGame();
    }
    
    /**
     * 填充 AI 玩家到空座位
     */
    private void fillAIPlayers() {
        int currentPlayers = players.size();
        int aiIndex = 0;
        
        while (players.size() < MAX_PLAYERS) {
            // 循环使用 5 种 AI 风格
            AIStyle style = AI_STYLES[aiIndex % AI_STYLES.length];
            
            String aiId = "ai_" + (players.size() + 1);
            String aiName = style.getDisplayName() + "_" + (aiIndex / AI_STYLES.length + 1);
            String aiAvatar = style.getAvatar();
            int seatIndex = findEmptySeat();
            
            PokerPlayer aiPlayer = new PokerPlayer(aiId, aiName, aiAvatar, seatIndex, true, style);
            players.add(aiPlayer);
            
            aiIndex++;
        }
    }
    
    /**
     * 结束游戏
     */
    private void endGame() {
        phase = GamePhase.GAME_OVER;
        
        // 移动庄家位置
        dealerIndex = (dealerIndex + 1) % players.size();
        
        // 重置玩家状态
        for (PokerPlayer player : players) {
            player.resetRound();
        }
        
        communityCards.clear();
        pot = 0;
        currentBet = 0;
        
        broadcastGameState();
        
        // 准备下一局
        phase = GamePhase.WAITING;
    }
    
    /**
     * 重置游戏
     */
    private void resetGame() {
        deck.shuffle();
        communityCards.clear();
        pot = 0;
        currentBet = 0;
        minRaise = BIG_BLIND;
        
        for (PokerPlayer player : players) {
            player.resetRound();
        }
    }
    
    /**
     * 设置盲注
     */
    private void setBlinds() {
        int sbIndex = (dealerIndex + 1) % players.size();
        int bbIndex = (dealerIndex + 2) % players.size();
        
        PokerPlayer sb = players.get(sbIndex);
        PokerPlayer bb = players.get(bbIndex);
        
        sb.setSmallBlind(true);
        bb.setBigBlind(true);
        
        sb.bet(SMALL_BLIND);
        bb.bet(BIG_BLIND);
        
        currentBet = BIG_BLIND;
        pot = SMALL_BLIND + BIG_BLIND;
    }
    
    /**
     * 发手牌
     */
    private void dealHoleCards() {
        for (PokerPlayer player : players) {
            List<Card> hand = deck.dealCards(2);
            player.setHandCards(hand);
        }
    }
    
    /**
     * 找到第一个可以操作的玩家
     */
    private void findFirstActivePlayer() {
        int startIndex = currentPlayerIndex;
        while (true) {
            PokerPlayer player = players.get(currentPlayerIndex);
            if (!player.isFolded() && !player.isAllIn()) {
                if (player.isAI()) {
                    aiThink(player);
                }
                break;
            }
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            if (currentPlayerIndex == startIndex) {
                break;
            }
        }
    }
    
    /**
     * 找到空座位
     */
    private int findEmptySeat() {
        Set<Integer> occupiedSeats = new HashSet<>();
        for (PokerPlayer player : players) {
            occupiedSeats.add(player.getSeatIndex());
        }
        
        for (int i = 0; i < MAX_PLAYERS; i++) {
            if (!occupiedSeats.contains(i)) {
                return i;
            }
        }
        
        return players.size(); // 应该不会到这里
    }
    
    /**
     * 获取默认头像
     */
    private String getDefaultAvatar() {
        String[] avatars = {
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼"
        };
        return avatars[playerIdCounter.get() % avatars.length];
    }
    
    /**
     * 广播游戏状态
     */
    private void broadcastGameState() {
        Map<String, Object> gameState = getGameState();
        String message = toJson(gameState);
        
        for (WebSocketSession session : playerSessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    /**
     * 发送消息给特定玩家
     */
    private void sendMessage(String playerId, String message) {
        WebSocketSession session = playerSessions.get(playerId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 获取游戏状态
     */
    public Map<String, Object> getGameState() {
        Map<String, Object> state = new HashMap<>();
        state.put("phase", phase.getDisplayName());
        state.put("pot", pot);
        state.put("currentBet", currentBet);
        state.put("currentPlayerIndex", currentPlayerIndex);
        state.put("dealerIndex", dealerIndex);
        state.put("communityCards", communityCards.stream()
            .map(Card::getDisplayName)
            .toArray(String[]::new));
        
        List<PokerPlayer.PlayerState> playerStates = new ArrayList<>();
        for (PokerPlayer player : players) {
            playerStates.add(player.toState());
        }
        state.put("players", playerStates);
        
        return state;
    }
    
    /**
     * 简单的 JSON 序列化（实际项目中应使用 Jackson）
     */
    private String toJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) json.append(",");
            first = false;
            
            json.append("\"").append(entry.getKey()).append("\":");
            json.append(serializeValue(entry.getValue()));
        }
        
        json.append("}");
        return json.toString();
    }
    
    private String serializeValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + value.toString().replace("\"", "\\\"") + "\"";
        } else if (value instanceof Integer || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof String[]) {
            String[] array = (String[]) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < array.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(array[i]).append("\"");
            }
            sb.append("]");
            return sb.toString();
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(serializeValue(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        } else if (value instanceof Map) {
            return toJson((Map<String, Object>) value);
        }
        return "\"" + value.toString() + "\"";
    }
    
    // Getters
    public GamePhase getPhase() {
        return phase;
    }
    
    public List<PokerPlayer> getPlayers() {
        return players;
    }
    
    public int getPot() {
        return pot;
    }
    
    public List<Card> getCommunityCards() {
        return communityCards;
    }
}
