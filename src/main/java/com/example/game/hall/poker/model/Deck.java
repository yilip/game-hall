package com.example.game.hall.poker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 牌堆类
 * 管理一副或多副扑克牌
 */
public class Deck {
    
    private final List<Card> cards;
    private int currentIndex;
    
    /**
     * 创建一副新牌（52 张）
     */
    public Deck() {
        this(1);
    }
    
    /**
     * 创建多副牌
     * @param numDecks 牌堆数量
     */
    public Deck(int numDecks) {
        cards = new ArrayList<>();
        for (int d = 0; d < numDecks; d++) {
            for (Card.Suit suit : Card.Suit.values()) {
                for (Card.Rank rank : Card.Rank.values()) {
                    cards.add(new Card(suit, rank));
                }
            }
        }
        currentIndex = 0;
    }
    
    /**
     * 洗牌
     */
    public void shuffle() {
        Collections.shuffle(cards);
        currentIndex = 0;
    }
    
    /**
     * 发一张牌
     * @return 发出的牌，如果牌堆已空则返回 null
     */
    public Card dealCard() {
        if (currentIndex >= cards.size()) {
            return null;
        }
        return cards.get(currentIndex++);
    }
    
    /**
     * 发多张牌
     * @param count 发牌数量
     * @return 发出的牌列表
     */
    public List<Card> dealCards(int count) {
        List<Card> dealtCards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Card card = dealCard();
            if (card != null) {
                dealtCards.add(card);
            }
        }
        return dealtCards;
    }
    
    /**
     * 获取剩余牌数
     * @return 剩余牌数
     */
    public int cardsRemaining() {
        return cards.size() - currentIndex;
    }
    
    /**
     * 重置牌堆
     */
    public void reset() {
        currentIndex = 0;
    }
    
    /**
     * 获取所有牌（用于调试）
     * @return 所有牌的列表
     */
    public List<Card> getAllCards() {
        return new ArrayList<>(cards);
    }
}
