package com.example.game.hall.poker.model;

/**
 * 扑克牌类
 * 表示一张扑克牌，包含花色和点数
 */
public class Card {
    
    // 花色枚举
    public enum Suit {
        SPADES("♠", "黑桃"),
        HEARTS("♥", "红桃"),
        CLUBS("♣", "梅花"),
        DIAMONDS("♦", "方块");
        
        private final String symbol;
        private final String chineseName;
        
        Suit(String symbol, String chineseName) {
            this.symbol = symbol;
            this.chineseName = chineseName;
        }
        
        public String getSymbol() {
            return symbol;
        }
        
        public String getChineseName() {
            return chineseName;
        }
    }
    
    // 点数枚举
    public enum Rank {
        TWO(2, "2"),
        THREE(3, "3"),
        FOUR(4, "4"),
        FIVE(5, "5"),
        SIX(6, "6"),
        SEVEN(7, "7"),
        EIGHT(8, "8"),
        NINE(9, "9"),
        TEN(10, "10"),
        JACK(11, "J"),
        QUEEN(12, "Q"),
        KING(13, "K"),
        ACE(14, "A");
        
        private final int value;
        private final String displayName;
        
        Rank(int value, String displayName) {
            this.value = value;
            this.displayName = displayName;
        }
        
        public int getValue() {
            return value;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private final Suit suit;
    private final Rank rank;
    
    public Card(Suit suit, Rank rank) {
        this.suit = suit;
        this.rank = rank;
    }
    
    public Suit getSuit() {
        return suit;
    }
    
    public Rank getRank() {
        return rank;
    }
    
    public boolean isRed() {
        return suit == Suit.HEARTS || suit == Suit.DIAMONDS;
    }
    
    @Override
    public String toString() {
        return rank.getDisplayName() + suit.getSymbol();
    }
    
    public String getDisplayName() {
        return rank.getDisplayName() + suit.getSymbol();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Card card = (Card) obj;
        return suit == card.suit && rank == card.rank;
    }
    
    @Override
    public int hashCode() {
        return suit.hashCode() * 31 + rank.hashCode();
    }
}
