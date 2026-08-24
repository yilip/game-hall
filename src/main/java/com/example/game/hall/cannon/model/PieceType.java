package com.example.game.hall.cannon.model;

public enum PieceType {
    CANNON("🎯"),    // 大炮（坦克/炮台）
    SOLDIER("🪖"),   // 小兵
    EMPTY("·");      // 空位
    
    private final String symbol;
    
    PieceType(String symbol) {
        this.symbol = symbol;
    }
    
    public String getSymbol() {
        return symbol;
    }
}
