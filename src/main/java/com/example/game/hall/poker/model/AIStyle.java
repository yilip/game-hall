package com.example.game.hall.poker.model;

/**
 * AI 玩家风格枚举
 * 定义不同类型的 AI 游戏风格
 */
public enum AIStyle {
    
    /**
     * 激进型 - 频繁加注、虚张声势
     * 特点：高风险偏好，喜欢诈唬，下注激进
     */
    AGGRESSIVE("激进者", "🦈", 0.8, 0.6, 0.3, 500, 1500),
    
    /**
     * 保守型 - 只在牌好时下注
     * 特点：低风险偏好，耐心等待好牌，很少诈唬
     */
    CONSERVATIVE("保守派", "🦉", 0.3, 0.2, 0.7, 1000, 2000),
    
    /**
     * 随机型 - 行为难以预测
     * 特点：完全随机，无法捉摸
     */
    RANDOM("随机者", "🎭", 0.5, 0.5, 0.5, 300, 2500),
    
    /**
     * 计算型 - 根据概率决策
     * 特点：理性计算，基于数学期望
     */
    CALCULATOR("计算师", "🤖", 0.6, 0.4, 0.5, 800, 1800),
    
    /**
     * 跟随型 - 倾向跟注而非加注
     * 特点：被动，喜欢跟注看牌，很少主动下注
     */
    CALLER("跟注王", "🐑", 0.4, 0.1, 0.8, 600, 1200);
    
    // AI 风格名称
    private final String displayName;
    
    // 头像 emoji
    private final String avatar;
    
    // 风险偏好 (0.0-1.0) - 越高越愿意冒险
    private final double riskTolerance;
    
    // 诈唬频率 (0.0-1.0) - 越高越喜欢虚张声势
    private final double bluffFrequency;
    
    // 弃牌倾向 (0.0-1.0) - 越高越容易弃牌
    private final double foldTendency;
    
    // 思考时间最小值 (毫秒)
    private final int minThinkTime;
    
    // 思考时间最大值 (毫秒)
    private final int maxThinkTime;
    
    AIStyle(String displayName, String avatar, double riskTolerance, 
            double bluffFrequency, double foldTendency, 
            int minThinkTime, int maxThinkTime) {
        this.displayName = displayName;
        this.avatar = avatar;
        this.riskTolerance = riskTolerance;
        this.bluffFrequency = bluffFrequency;
        this.foldTendency = foldTendency;
        this.minThinkTime = minThinkTime;
        this.maxThinkTime = maxThinkTime;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getAvatar() {
        return avatar;
    }
    
    public double getRiskTolerance() {
        return riskTolerance;
    }
    
    public double getBluffFrequency() {
        return bluffFrequency;
    }
    
    public double getFoldTendency() {
        return foldTendency;
    }
    
    public int getMinThinkTime() {
        return minThinkTime;
    }
    
    public int getMaxThinkTime() {
        return maxThinkTime;
    }
    
    /**
     * 获取默认 AI 玩家名称
     */
    public String getDefaultUsername() {
        return displayName;
    }
}
