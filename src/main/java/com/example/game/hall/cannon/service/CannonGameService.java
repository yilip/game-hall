package com.example.game.hall.cannon.service;

import com.example.game.hall.cannon.model.*;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CannonGameService {

    private final Map<String, CannonGameRoom> rooms = new ConcurrentHashMap<>();
    private final CannonAIService aiService;

    public CannonGameService(CannonAIService aiService) {
        this.aiService = aiService;
    }

    public synchronized CannonGameRoom createRoom() {
        String roomId = UUID.randomUUID().toString();
        CannonGameRoom room = new CannonGameRoom(roomId, aiService);
        rooms.put(roomId, room);
        return room;
    }

    public CannonGameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
    }

    public Map<String, Object> getBoardState(CannonGameRoom room) {
        Map<String, Object> boardState = new java.util.HashMap<>();
        Game game = room.getGame();
        PieceType[][] board = game.getBoard().getBoard();

        String[][] grid = new String[5][5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                grid[i][j] = board[i][j].getSymbol();
            }
        }

        boardState.put("grid", grid);
        boardState.put("cannons", game.getBoard().countPieces(PieceType.CANNON));
        boardState.put("soldiers", game.getBoard().countPieces(PieceType.SOLDIER));

        return boardState;
    }

    public Map<String, Object> getGameState(CannonGameRoom room) {
        Map<String, Object> response = new java.util.HashMap<>();
        Game game = room.getGame();

        response.put("success", true);
        response.put("board", getBoardState(room));
        response.put("gameState", game.getGameState());
        response.put("gameOver", game.isGameOver());
        response.put("currentPlayer", game.getCurrentPlayer().getSide().getDisplayName());

        return response;
    }
}
