package com.example.game.hall.cannon.controller;

import com.example.game.hall.cannon.model.*;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cannon")
@CrossOrigin(origins = "*")
public class GameController {
    
    private Game game;
    
    public GameController() {
        this.game = new Game();
    }
    
    @PostMapping("/start")
    public Map<String, Object> startGame(@RequestBody Map<String, String> request) {
        String player1Side = request.getOrDefault("player1Side", "CANNON");
        String player2Side = request.getOrDefault("player2Side", "SOLDIER");
        
        game.initializeGame(
            PlayerSide.valueOf(player1Side),
            PlayerSide.valueOf(player2Side)
        );
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "游戏开始！");
        response.put("board", getBoardState());
        response.put("gameState", game.getGameState());
        
        return response;
    }
    
    @PostMapping("/start-single")
    public Map<String, Object> startSinglePlayer(@RequestBody Map<String, String> request) {
        String humanSide = request.getOrDefault("humanSide", "CANNON");
        
        game.initializeSinglePlayer(PlayerSide.valueOf(humanSide));
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "单机模式开始！你选择：" + (humanSide.equals("CANNON") ? "三大炮" : "十五兵"));
        response.put("board", getBoardState());
        response.put("gameState", game.getGameState());
        response.put("isSinglePlayer", true);
        response.put("humanSide", humanSide);
        
        return response;
    }
    
    @PostMapping("/move")
    public Map<String, Object> makeMove(@RequestBody Map<String, Integer> move) {
        Map<String, Object> response = new HashMap<>();
        
        int fromRow = move.get("fromRow");
        int fromCol = move.get("fromCol");
        int toRow = move.get("toRow");
        int toCol = move.get("toCol");
        
        boolean success = game.makeMove(fromRow, fromCol, toRow, toCol);
        
        if (success) {
            response.put("success", true);
            response.put("message", "移动成功！");
            response.put("board", getBoardState());
            response.put("gameState", game.getGameState());
            response.put("gameOver", game.isGameOver());
            response.put("winner", game.getWinner());
            response.put("isSinglePlayer", true);
        } else {
            response.put("success", false);
            response.put("message", "无效的移动！");
        }
        
        return response;
    }
    
    @PostMapping("/ai-move")
    public Map<String, Object> makeAIMove() {
        Map<String, Object> response = new HashMap<>();
        
        int[] aiMove = game.makeAIMove();
        
        if (aiMove != null) {
            response.put("success", true);
            response.put("message", "AI 移动成功！");
            response.put("board", getBoardState());
            response.put("gameState", game.getGameState());
            response.put("gameOver", game.isGameOver());
            response.put("winner", game.getWinner());
            response.put("aiMove", Map.of(
                "fromRow", aiMove[0],
                "fromCol", aiMove[1],
                "toRow", aiMove[2],
                "toCol", aiMove[3]
            ));
        } else {
            response.put("success", false);
            response.put("message", "AI 无法移动！");
        }
        
        return response;
    }
    
    @GetMapping("/state")
    public Map<String, Object> getGameState() {
        Map<String, Object> response = new HashMap<>();
        
        // 检查游戏是否已初始化
        try {
            game.getGameState();
        } catch (NullPointerException e) {
            response.put("success", false);
            response.put("message", "游戏未开始，请先选择阵营！");
            response.put("gameOver", false);
            return response;
        }
        
        response.put("success", true);
        response.put("board", getBoardState());
        response.put("gameState", game.getGameState());
        response.put("gameOver", game.isGameOver());
        response.put("currentPlayer", game.getCurrentPlayer().getSide().getDisplayName());
        
        return response;
    }
    
    @GetMapping("/reset")
    public Map<String, Object> resetGame() {
        game = new Game();
        game.initializeGame(PlayerSide.CANNON, PlayerSide.SOLDIER);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "游戏已重置！");
        response.put("board", getBoardState());
        response.put("gameState", game.getGameState());
        
        return response;
    }
    
    @GetMapping("/trapped")
    public Map<String, Object> getTrappedCannons() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        
        int[][] trapped = game.getBoard().getTrappedCannons();
        response.put("trappedCount", trapped.length);
        response.put("trappedPositions", trapped);
        
        return response;
    }
    
    private Map<String, Object> getBoardState() {
        Map<String, Object> boardState = new HashMap<>();
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
}
