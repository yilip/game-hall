package com.example.game.hall.poker.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家类
 * 表示游戏中的一个玩家（真人或 AI）
 */
public class PokerPlayer {
    
    /**
     * 玩家动作枚举
     */
    public enum Action {
        FOLD("弃牌"),
        CHECK("过牌"),
        CALL("跟注"),
        RAISE("加注"),
        ALL_IN("全下");
        
        private final String displayName;
        
        Action(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private final String id;
    private final String username;
    private final String avatar;
    private final boolean isAI;
    private final int seatIndex;
    private final AIStyle aiStyle; // AI 风格（仅 AI 玩家）
    
    private List<Card> handCards;
    private int chips;
    private int currentBet;
    private boolean folded;
    private boolean allIn;
    private boolean isDealer;
    private boolean isSmallBlind;
    private boolean isBigBlind;
    
    public PokerPlayer(String id, String username, String avatar, int seatIndex, boolean isAI) {
        this(id, username, avatar, seatIndex, isAI, null);
    }
    
    public PokerPlayer(String id, String username, String avatar, int seatIndex, boolean isAI, AIStyle aiStyle) {
        this.id = id;
        this.username = username;
        this.avatar = avatar;
        this.seatIndex = seatIndex;
        this.isAI = isAI;
        this.aiStyle = aiStyle;
        this.handCards = new ArrayList<>();
        this.chips = 10000; // 初始筹码 10000
        this.currentBet = 0;
        this.folded = false;
        this.allIn = false;
        this.isDealer = false;
        this.isSmallBlind = false;
        this.isBigBlind = false;
    }
    
    // Getters
    public String getId() {
        return id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getAvatar() {
        return avatar;
    }
    
    public int getSeatIndex() {
        return seatIndex;
    }
    
    public boolean isAI() {
        return isAI;
    }
    
    public AIStyle getAiStyle() {
        return aiStyle;
    }
    
    public List<Card> getHandCards() {
        return handCards;
    }
    
    public int getChips() {
        return chips;
    }
    
    public int getCurrentBet() {
        return currentBet;
    }
    
    public boolean isFolded() {
        return folded;
    }
    
    public boolean isAllIn() {
        return allIn;
    }
    
    public boolean isDealer() {
        return isDealer;
    }
    
    public boolean isSmallBlind() {
        return isSmallBlind;
    }
    
    public boolean isBigBlind() {
        return isBigBlind;
    }
    
    // Setters
    public void setHandCards(List<Card> handCards) {
        this.handCards = handCards;
    }
    
    public void setChips(int chips) {
        this.chips = chips;
    }
    
    public void setCurrentBet(int currentBet) {
        this.currentBet = currentBet;
    }
    
    public void setFolded(boolean folded) {
        this.folded = folded;
    }
    
    public void setAllIn(boolean allIn) {
        this.allIn = allIn;
    }
    
    public void setDealer(boolean dealer) {
        isDealer = dealer;
    }
    
    public void setSmallBlind(boolean smallBlind) {
        isSmallBlind = smallBlind;
    }
    
    public void setBigBlind(boolean bigBlind) {
        isBigBlind = bigBlind;
    }
    
    /**
     * 下注
     * @param amount 下注金额
     * @return 实际下注金额
     */
    public int bet(int amount) {
        if (amount >= chips) {
            // 全下
            int actualBet = chips;
            currentBet += actualBet;
            chips = 0;
            allIn = true;
            return actualBet;
        } else {
            currentBet += amount;
            chips -= amount;
            return amount;
        }
    }
    
    /**
     * 重置本轮状态
     */
    public void resetRound() {
        handCards = new ArrayList<>();
        currentBet = 0;
        folded = false;
        allIn = false;
        isSmallBlind = false;
        isBigBlind = false;
        isDealer = false;
    }
    
    /**
     * 是否可以操作
     * @return 是否可以操作
     */
    public boolean canAct() {
        return !folded && !allIn;
    }
    
    /**
     * 获取玩家状态 JSON（用于前端）
     * @return 玩家状态对象
     */
    public PlayerState toState() {
        return new PlayerState(
            id,
            username,
            avatar,
            seatIndex,
            isAI,
            handCards.stream().map(Card::getDisplayName).toArray(String[]::new),
            chips,
            currentBet,
            folded,
            allIn,
            isDealer,
            isSmallBlind,
            isBigBlind
        );
    }
    
    /**
     * 玩家状态内部类（用于 JSON 序列化）
     */
    public static class PlayerState {
        public String id;
        public String username;
        public String avatar;
        public int seatIndex;
        public boolean isAI;
        public String[] handCards;
        public int chips;
        public int currentBet;
        public boolean folded;
        public boolean allIn;
        public boolean isDealer;
        public boolean isSmallBlind;
        public boolean isBigBlind;
        
        public PlayerState(String id, String username, String avatar, int seatIndex, boolean isAI,
                          String[] handCards, int chips, int currentBet, boolean folded,
                          boolean allIn, boolean isDealer, boolean isSmallBlind, boolean isBigBlind) {
            this.id = id;
            this.username = username;
            this.avatar = avatar;
            this.seatIndex = seatIndex;
            this.isAI = isAI;
            this.handCards = handCards;
            this.chips = chips;
            this.currentBet = currentBet;
            this.folded = folded;
            this.allIn = allIn;
            this.isDealer = isDealer;
            this.isSmallBlind = isSmallBlind;
            this.isBigBlind = isBigBlind;
        }
    }
}
