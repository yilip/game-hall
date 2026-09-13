package com.example.game.hall.cannon.model;

import com.example.game.hall.cannon.service.CannonAIService;

public class CannonGameRoom {

    private final String roomId;
    private final Game game;
    private final CannonAIService aiService;
    private boolean isSinglePlayer;
    private PlayerSide humanSide;

    public CannonGameRoom(String roomId, CannonAIService aiService) {
        this.roomId = roomId;
        this.game = new Game();
        this.aiService = aiService;
        this.isSinglePlayer = false;
    }

    public String getRoomId() {
        return roomId;
    }

    public Game getGame() {
        return game;
    }

    public void initializeGame(PlayerSide player1Side, PlayerSide player2Side) {
        game.initializeGame(player1Side, player2Side);
        this.isSinglePlayer = false;
    }

    public void initializeSinglePlayer(PlayerSide humanSide) {
        game.initializeSinglePlayer(humanSide);
        this.humanSide = humanSide;
        this.isSinglePlayer = true;
    }

    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        return game.makeMove(fromRow, fromCol, toRow, toCol);
    }

    public int[] makeAIMove() {
        if (!isSinglePlayer || game.isGameOver()) {
            return null;
        }

        PlayerSide aiSide = (humanSide == PlayerSide.CANNON) ? PlayerSide.SOLDIER : PlayerSide.CANNON;
        return aiService.makeAIMove(game, aiSide);
    }

    public boolean isSinglePlayer() {
        return isSinglePlayer;
    }

    public PlayerSide getHumanSide() {
        return humanSide;
    }
}
