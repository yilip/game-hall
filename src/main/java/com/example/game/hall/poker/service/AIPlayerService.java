package com.example.game.hall.poker.service;

import com.example.game.hall.poker.model.AIStyle;
import com.example.game.hall.poker.model.Card;
import com.example.game.hall.poker.model.PokerHand;
import com.example.game.hall.poker.model.PokerPlayer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

/**
 * AI 玩家服务
 * 根据 AI 风格实现不同的决策逻辑
 */
@Service
public class AIPlayerService {
    
    private final Random random = new Random();
    
    /**
     * AI 思考并做出决策
     * @param aiPlayer AI 玩家
     * @param currentBet 当前下注额
     * @param toCall 需要跟注的金额
     * @param holeCards 手牌
     * @param communityCards 公共牌
     * @param pot 底池
     * @return 决策动作
     */
    public PokerPlayer.Action think(PokerPlayer aiPlayer, int currentBet, int toCall,
                                    List<Card> holeCards, List<Card> communityCards, int pot) {
        AIStyle style = aiPlayer.getAiStyle();
        if (style == null) {
            return PokerPlayer.Action.FOLD;
        }
        
        // 评估手牌强度 (0.0 - 1.0)
        double handStrength = evaluateHandStrength(holeCards, communityCards);
        
        // 计算底池赔率
        double potOdds = toCall > 0 ? (double) toCall / (pot + toCall) : 0;
        
        return decideAction(style, handStrength, potOdds, toCall, aiPlayer.getChips());
    }
    
    /**
     * 获取加注金额
     * @param aiPlayer AI 玩家
     * @param currentBet 当前下注额
     * @param minRaise 最小加注额
     * @return 加注金额
     */
    public int getRaiseAmount(PokerPlayer aiPlayer, int currentBet, int minRaise) {
        AIStyle style = aiPlayer.getAiStyle();
        int chips = aiPlayer.getChips();
        
        switch (style) {
            case AGGRESSIVE:
                // 激进型：加注较大
                return Math.min(chips, currentBet + minRaise * 3);
            case CONSERVATIVE:
                // 保守型：加注适中
                return Math.min(chips, currentBet + minRaise * 2);
            case RANDOM:
                // 随机型：随机加注
                return Math.min(chips, currentBet + minRaise * (1 + random.nextInt(3)));
            case CALCULATOR:
                // 计算型：根据底池比例加注
                return Math.min(chips, currentBet + (pot / 4));
            case CALLER:
                // 跟随型：最小加注
                return Math.min(chips, currentBet + minRaise);
            default:
                return minRaise;
        }
    }
    
    /**
     * 根据 AI 风格做出决策
     */
    private PokerPlayer.Action decideAction(AIStyle style, double handStrength, double potOdds, 
                                           int toCall, int chips) {
        switch (style) {
            case AGGRESSIVE:
                return aggressiveDecision(handStrength, potOdds, toCall, chips, style);
            case CONSERVATIVE:
                return conservativeDecision(handStrength, potOdds, toCall, chips, style);
            case RANDOM:
                return randomDecision(handStrength, toCall, chips);
            case CALCULATOR:
                return calculatorDecision(handStrength, potOdds, toCall, chips);
            case CALLER:
                return callerDecision(handStrength, toCall, chips);
            default:
                return PokerPlayer.Action.FOLD;
        }
    }
    
    /**
     * 激进型决策
     * 特点：频繁加注、诈唬，高风险偏好
     */
    private PokerPlayer.Action aggressiveDecision(double handStrength, double potOdds, 
                                                  int toCall, int chips, AIStyle style) {
        double bluffChance = random.nextDouble();
        
        // 即使牌不好也可能诈唬
        if (bluffChance < style.getBluffFrequency() && toCall == 0) {
            return PokerPlayer.Action.RAISE;
        }
        
        // 手牌强度好时激进下注
        if (handStrength > 0.6) {
            return PokerPlayer.Action.RAISE;
        } else if (handStrength > 0.4) {
            return toCall > 0 ? PokerPlayer.Action.CALL : PokerPlayer.Action.CHECK;
        } else if (handStrength > 0.2 || bluffChance < style.getBluffFrequency()) {
            return toCall > 0 ? PokerPlayer.Action.CALL : PokerPlayer.Action.CHECK;
        } else {
            return toCall > chips * 0.5 ? PokerPlayer.Action.FOLD : PokerPlayer.Action.CALL;
        }
    }
    
    /**
     * 保守型决策
     * 特点：只在牌好时下注，很少诈唬
     */
    private PokerPlayer.Action conservativeDecision(double handStrength, double potOdds, 
                                                    int toCall, int chips, AIStyle style) {
        // 手牌很好时才加注
        if (handStrength > 0.75) {
            return PokerPlayer.Action.RAISE;
        } else if (handStrength > 0.5) {
            return toCall > 0 ? PokerPlayer.Action.CALL : PokerPlayer.Action.CHECK;
        } else if (handStrength > 0.3 && potOdds < 0.2) {
            // 底池赔率好时跟注
            return PokerPlayer.Action.CALL;
        } else {
            // 牌不好时容易弃牌
            return toCall > chips * 0.2 ? PokerPlayer.Action.FOLD : PokerPlayer.Action.CALL;
        }
    }
    
    /**
     * 随机型决策
     * 特点：行为难以预测
     */
    private PokerPlayer.Action randomDecision(double handStrength, int toCall, int chips) {
        double rand = random.nextDouble();
        
        if (rand < 0.2) {
            // 20% 概率弃牌
            return PokerPlayer.Action.FOLD;
        } else if (rand < 0.4) {
            // 20% 概率加注
            return PokerPlayer.Action.RAISE;
        } else if (rand < 0.7) {
            // 30% 概率跟注
            return toCall > 0 ? PokerPlayer.Action.CALL : PokerPlayer.Action.CHECK;
        } else {
            // 30% 概率根据手牌决定
            if (handStrength > 0.5) {
                return PokerPlayer.Action.RAISE;
            } else if (toCall > chips * 0.5) {
                return PokerPlayer.Action.FOLD;
            } else {
                return toCall > 0 ? PokerPlayer.Action.CALL : PokerPlayer.Action.CHECK;
            }
        }
    }
    
    /**
     * 计算型决策
     * 特点：基于概率和数学期望理性决策
     */
    private PokerPlayer.Action calculatorDecision(double handStrength, double potOdds, 
                                                  int toCall, int chips) {
        // 计算期望值
        double expectedValue = handStrength - potOdds;
        
        if (expectedValue > 0.2) {
            // 期望值高，加注
            return PokerPlayer.Action.RAISE;
        } else if (expectedValue > 0) {
            // 期望值为正，跟注
            return toCall > 0 ? PokerPlayer.Action.CALL : PokerPlayer.Action.CHECK;
        } else if (expectedValue > -0.1) {
            // 期望值略负，根据情况决定
            if (toCall < chips * 0.1) {
                return PokerPlayer.Action.CALL;
            } else {
                return PokerPlayer.Action.FOLD;
            }
        } else {
            // 期望值很负，弃牌
            return PokerPlayer.Action.FOLD;
        }
    }
    
    /**
     * 跟随型决策
     * 特点：被动，喜欢跟注，很少主动下注
     */
    private PokerPlayer.Action callerDecision(double handStrength, int toCall, int chips) {
        // 很少加注，除非牌非常好
        if (handStrength > 0.85) {
            return PokerPlayer.Action.RAISE;
        } else if (handStrength > 0.4) {
            // 中等牌力就跟注
            return toCall > 0 ? PokerPlayer.Action.CALL : PokerPlayer.Action.CHECK;
        } else if (toCall < chips * 0.15) {
            // 跟注金额小就跟注看牌
            return PokerPlayer.Action.CALL;
        } else {
            // 否则弃牌
            return PokerPlayer.Action.FOLD;
        }
    }
    
    /**
     * 评估手牌强度
     * @param holeCards 手牌
     * @param communityCards 公共牌
     * @return 手牌强度 (0.0 - 1.0)
     */
    private double evaluateHandStrength(List<Card> holeCards, List<Card> communityCards) {
        if (holeCards == null || holeCards.isEmpty()) {
            return 0.0;
        }
        
        double baseStrength = 0.0;
        
        // 评估手牌
        Card card1 = holeCards.get(0);
        Card card2 = holeCards.get(1);
        
        // 对子
        if (card1.getRank() == card2.getRank()) {
            baseStrength += 0.6;
        }
        
        // 高牌
        int highCard = Math.max(card1.getRank().getValue(), card2.getRank().getValue());
        baseStrength += highCard / 28.0; // ACE=14, max possible = 28
        
        // 同花潜力
        if (card1.getSuit() == card2.getSuit()) {
            baseStrength += 0.1;
        }
        
        // 连牌潜力
        int rankDiff = Math.abs(card1.getRank().getValue() - card2.getRank().getValue());
        if (rankDiff == 1) {
            baseStrength += 0.15;
        } else if (rankDiff == 2) {
            baseStrength += 0.1;
        }
        
        // 有公共牌时评估完整牌力
        if (communityCards != null && !communityCards.isEmpty()) {
            PokerHand hand = HandEvaluator.evaluate(holeCards, communityCards);
            double handScore = hand.getScore();
            
            // 归一化到 0-1 范围
            if (handScore >= 90000) { // 皇家同花顺
                baseStrength = Math.max(baseStrength, 1.0);
            } else if (handScore >= 80000) { // 同花顺
                baseStrength = Math.max(baseStrength, 0.95);
            } else if (handScore >= 70000) { // 四条
                baseStrength = Math.max(baseStrength, 0.9);
            } else if (handScore >= 60000) { // 葫芦
                baseStrength = Math.max(baseStrength, 0.8);
            } else if (handScore >= 50000) { // 同花
                baseStrength = Math.max(baseStrength, 0.7);
            } else if (handScore >= 40000) { // 顺子
                baseStrength = Math.max(baseStrength, 0.65);
            } else if (handScore >= 30000) { // 三条
                baseStrength = Math.max(baseStrength, 0.5);
            } else if (handScore >= 20000) { // 两对
                baseStrength = Math.max(baseStrength, 0.35);
            } else if (handScore >= 10000) { // 一对
                baseStrength = Math.max(baseStrength, 0.2);
            }
        }
        
        // 限制在 0-1 范围
        return Math.min(1.0, Math.max(0.0, baseStrength));
    }
    
    private int pot = 0;
    
    public void setPot(int pot) {
        this.pot = pot;
    }
}
