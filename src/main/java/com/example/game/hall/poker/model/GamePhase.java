package com.example.game.hall.poker.model;

/**
 * 游戏阶段枚举
 * 德州扑克的四个下注轮次
 */
public enum GamePhase {
    
    /**
     * 等待开始 - 游戏尚未开始或等待玩家加入
     */
    WAITING("等待开始"),
    
    /**
     * 翻牌前 - 玩家已收到手牌，尚未发公共牌
     */
    PREFLOP("翻牌前"),
    
    /**
     * 翻牌 - 已发出三张公共牌
     */
    FLOP("翻牌"),
    
    /**
     * 转牌 - 已发出第四张公共牌
     */
    TURN("转牌"),
    
    /**
     * 河牌 - 已发出第五张公共牌
     */
    RIVER("河牌"),
    
    /**
     * 摊牌 - 所有下注完成，展示手牌比牌
     */
    SHOWDOWN("摊牌"),
    
    /**
     * 游戏结束 - 本轮游戏结束
     */
    GAME_OVER("游戏结束");
    
    private final String displayName;
    
    GamePhase(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取下一个游戏阶段
     * @return 下一个阶段
     */
    public GamePhase next() {
        switch (this) {
            case WAITING:
                return PREFLOP;
            case PREFLOP:
                return FLOP;
            case FLOP:
                return TURN;
            case TURN:
                return RIVER;
            case RIVER:
                return SHOWDOWN;
            default:
                return GAME_OVER;
        }
    }
}
