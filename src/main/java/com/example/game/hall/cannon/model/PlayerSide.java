package com.example.game.hall.cannon.model;

public enum PlayerSide {
    CANNON("三大炮"),
    SOLDIER("十五兵");
    
    private final String displayName;
    
    PlayerSide(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}

