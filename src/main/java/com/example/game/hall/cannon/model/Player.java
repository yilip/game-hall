package com.example.game.hall.cannon.model;

public class Player {
    private PlayerSide side;
    
    public Player(PlayerSide side) {
        this.side = side;
    }
    
    public PlayerSide getSide() {
        return side;
    }
}
