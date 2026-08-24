package com.example.game.hall.poker.model;

import java.util.List;

/**
 * 手牌类
 * 表示玩家的最佳五张牌组合及其牌型
 */
public class PokerHand {
    
    /**
     * 牌型枚举（从大到小）
     */
    public enum HandType {
        ROYAL_FLUSH("皇家同花顺", 900),
        STRAIGHT_FLUSH("同花顺", 800),
        FOUR_OF_A_KIND("四条", 700),
        FULL_HOUSE("葫芦", 600),
        FLUSH("同花", 500),
        STRAIGHT("顺子", 400),
        THREE_OF_A_KIND("三条", 300),
        TWO_PAIR("两对", 200),
        ONE_PAIR("一对", 100),
        HIGH_CARD("高牌", 0);
        
        private final String displayName;
        private final int baseScore;
        
        HandType(String displayName, int baseScore) {
            this.displayName = displayName;
            this.baseScore = baseScore;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public int getBaseScore() {
            return baseScore;
        }
    }
    
    private final HandType handType;
    private final List<Card> bestCards;
    private final int score;
    
    public PokerHand(HandType handType, List<Card> bestCards, int score) {
        this.handType = handType;
        this.bestCards = bestCards;
        this.score = score;
    }
    
    public HandType getHandType() {
        return handType;
    }
    
    public List<Card> getBestCards() {
        return bestCards;
    }
    
    public int getScore() {
        return score;
    }
    
    public String getDisplayName() {
        return handType.getDisplayName();
    }
    
    /**
     * 比较两个手牌
     * @param other 另一个手牌
     * @return 正数表示当前手牌大，负数表示对方手牌大，0 表示平局
     */
    public int compareTo(PokerHand other) {
        if (this.score != other.score) {
            return Integer.compare(this.score, other.score);
        }
        // 分数相同，比较牌型
        return Integer.compare(this.handType.ordinal(), other.handType.ordinal());
    }
    
    @Override
    public String toString() {
        return handType.getDisplayName() + " - Score: " + score;
    }
}
