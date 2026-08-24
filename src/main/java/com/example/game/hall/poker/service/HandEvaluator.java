package com.example.game.hall.poker.service;

import com.example.game.hall.poker.model.Card;
import com.example.game.hall.poker.model.PokerHand;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 牌型评估器
 * 评估 7 张牌（2 张手牌 + 5 张公共牌）中的最佳 5 张牌组合
 */
public class HandEvaluator {
    
    /**
     * 评估最佳牌型
     * @param holeCards 手牌（2 张）
     * @param communityCards 公共牌（0-5 张）
     * @return 最佳牌型
     */
    public static PokerHand evaluate(List<Card> holeCards, List<Card> communityCards) {
        List<Card> allCards = new ArrayList<>(holeCards);
        allCards.addAll(communityCards);
        
        if (allCards.size() < 5) {
            // 牌不够，无法评估
            return new PokerHand(PokerHand.HandType.HIGH_CARD, allCards, 0);
        }
        
        // 从 7 张牌中选择 5 张的最佳组合
        return findBestHand(allCards);
    }
    
    /**
     * 从 7 张牌中找出最佳 5 张牌组合
     */
    private static PokerHand findBestHand(List<Card> cards) {
        PokerHand bestHand = null;
        
        // 生成所有 5 张牌的组合
        List<List<Card>> combinations = getCombinations(cards, 5);
        
        for (List<Card> combination : combinations) {
            PokerHand hand = evaluateFiveCards(combination);
            if (bestHand == null || hand.getScore() > bestHand.getScore()) {
                bestHand = hand;
            }
        }
        
        return bestHand;
    }
    
    /**
     * 评估 5 张牌的牌型
     */
    private static PokerHand evaluateFiveCards(List<Card> cards) {
        // 按点数排序（从大到小）
        List<Card> sortedCards = cards.stream()
            .sorted((a, b) -> Integer.compare(b.getRank().getValue(), a.getRank().getValue()))
            .collect(Collectors.toList());
        
        boolean isFlush = isFlush(cards);
        boolean isStraight = isStraight(sortedCards);
        Map<Integer, Long> rankCounts = getRankCounts(cards);
        
        // 皇家同花顺
        if (isFlush && isStraight && sortedCards.get(0).getRank() == Card.Rank.ACE) {
            return new PokerHand(PokerHand.HandType.ROYAL_FLUSH, sortedCards, 90000);
        }
        
        // 同花顺
        if (isFlush && isStraight) {
            return new PokerHand(PokerHand.HandType.STRAIGHT_FLUSH, sortedCards, 80000 + sortedCards.get(0).getRank().getValue());
        }
        
        // 四条
        if (rankCounts.containsValue(4L)) {
            int quadRank = getRankByCount(rankCounts, 4);
            int kicker = getRankByCount(rankCounts, 1);
            return new PokerHand(PokerHand.HandType.FOUR_OF_A_KIND, sortedCards, 70000 + quadRank * 100 + kicker);
        }
        
        // 葫芦
        if (rankCounts.containsValue(3L) && rankCounts.containsValue(2L)) {
            int tripRank = getRankByCount(rankCounts, 3);
            int pairRank = getRankByCount(rankCounts, 2);
            return new PokerHand(PokerHand.HandType.FULL_HOUSE, sortedCards, 60000 + tripRank * 100 + pairRank);
        }
        
        // 同花
        if (isFlush) {
            return new PokerHand(PokerHand.HandType.FLUSH, sortedCards, 50000 + calculateHighCardScore(sortedCards));
        }
        
        // 顺子
        if (isStraight) {
            return new PokerHand(PokerHand.HandType.STRAIGHT, sortedCards, 40000 + sortedCards.get(0).getRank().getValue());
        }
        
        // 三条
        if (rankCounts.containsValue(3L)) {
            int tripRank = getRankByCount(rankCounts, 3);
            return new PokerHand(PokerHand.HandType.THREE_OF_A_KIND, sortedCards, 30000 + tripRank * 100);
        }
        
        // 两对
        long pairCount = rankCounts.values().stream().filter(c -> c == 2L).count();
        if (pairCount >= 2) {
            List<Integer> pairRanks = getRanksByCount(rankCounts, 2);
            Collections.sort(pairRanks, Collections.reverseOrder());
            int kicker = getRankByCount(rankCounts, 1);
            return new PokerHand(PokerHand.HandType.TWO_PAIR, sortedCards, 20000 + pairRanks.get(0) * 1000 + pairRanks.get(1) * 10 + kicker);
        }
        
        // 一对
        if (pairCount == 1) {
            int pairRank = getRankByCount(rankCounts, 2);
            return new PokerHand(PokerHand.HandType.ONE_PAIR, sortedCards, 10000 + pairRank * 100);
        }
        
        // 高牌
        return new PokerHand(PokerHand.HandType.HIGH_CARD, sortedCards, calculateHighCardScore(sortedCards));
    }
    
    private static boolean isFlush(List<Card> cards) {
        Card.Suit firstSuit = cards.get(0).getSuit();
        return cards.stream().allMatch(c -> c.getSuit() == firstSuit);
    }
    
    private static boolean isStraight(List<Card> sortedCards) {
        // 检查是否连续
        for (int i = 0; i < sortedCards.size() - 1; i++) {
            if (sortedCards.get(i).getRank().getValue() - sortedCards.get(i + 1).getRank().getValue() != 1) {
                // 特殊情况：A-2-3-4-5
                if (i == 0 && sortedCards.get(0).getRank() == Card.Rank.ACE &&
                    sortedCards.get(1).getRank() == Card.Rank.FIVE &&
                    sortedCards.get(2).getRank() == Card.Rank.FOUR &&
                    sortedCards.get(3).getRank() == Card.Rank.THREE &&
                    sortedCards.get(4).getRank() == Card.Rank.TWO) {
                    return true;
                }
                return false;
            }
        }
        return true;
    }
    
    private static Map<Integer, Long> getRankCounts(List<Card> cards) {
        return cards.stream()
            .collect(Collectors.groupingBy(
                c -> c.getRank().getValue(),
                Collectors.counting()
            ));
    }
    
    private static int getRankByCount(Map<Integer, Long> rankCounts, long count) {
        for (Map.Entry<Integer, Long> entry : rankCounts.entrySet()) {
            if (entry.getValue() == count) {
                return entry.getKey();
            }
        }
        return 0;
    }
    
    private static List<Integer> getRanksByCount(Map<Integer, Long> rankCounts, long count) {
        List<Integer> ranks = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : rankCounts.entrySet()) {
            if (entry.getValue() == count) {
                ranks.add(entry.getKey());
            }
        }
        return ranks;
    }
    
    private static int calculateHighCardScore(List<Card> sortedCards) {
        int score = 0;
        for (int i = 0; i < sortedCards.size(); i++) {
            score += sortedCards.get(i).getRank().getValue() * Math.pow(10, 4 - i);
        }
        return score;
    }
    
    private static List<List<Card>> getCombinations(List<Card> cards, int k) {
        List<List<Card>> combinations = new ArrayList<>();
        getCombinationsHelper(cards, k, 0, new ArrayList<>(), combinations);
        return combinations;
    }
    
    private static void getCombinationsHelper(List<Card> cards, int k, int start, List<Card> current, List<List<Card>> combinations) {
        if (current.size() == k) {
            combinations.add(new ArrayList<>(current));
            return;
        }
        
        for (int i = start; i < cards.size(); i++) {
            current.add(cards.get(i));
            getCombinationsHelper(cards, k, i + 1, current, combinations);
            current.remove(current.size() - 1);
        }
    }
}
