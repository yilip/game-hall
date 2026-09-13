package com.example.game.hall.cannon.controller;

import com.example.game.hall.cannon.model.*;
import com.example.game.hall.cannon.service.CannonGameService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cannon")
@CrossOrigin(origins = "*")
public class GameController {

    private final CannonGameService gameService;

    public GameController(CannonGameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/start")
    public Map<String, Object> startGame(@RequestBody Map<String, String> request) {
        CannonGameRoom room = gameService.createRoom();
        String player1Side = request.getOrDefault("player1Side", "CANNON");
        String player2Side = request.getOrDefault("player2Side", "SOLDIER");

        room.initializeGame(
            PlayerSide.valueOf(player1Side),
            PlayerSide.valueOf(player2Side)
        );

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "游戏开始！");
        response.put("roomId", room.getRoomId());
        response.put("board", gameService.getBoardState(room));
        response.put("gameState", room.getGame().getGameState());

        return response;
    }

    @PostMapping("/start-single")
    public Map<String, Object> startSinglePlayer(@RequestBody Map<String, String> request) {
        CannonGameRoom room = gameService.createRoom();
        String humanSide = request.getOrDefault("humanSide", "CANNON");

        room.initializeSinglePlayer(PlayerSide.valueOf(humanSide));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "单机模式开始！你选择：" + (humanSide.equals("CANNON") ? "三大炮" : "十五兵"));
        response.put("roomId", room.getRoomId());
        response.put("board", gameService.getBoardState(room));
        response.put("gameState", room.getGame().getGameState());
        response.put("isSinglePlayer", true);
        response.put("humanSide", humanSide);

        return response;
    }

    @PostMapping("/move")
    public Map<String, Object> makeMove(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();

        String roomId = (String) request.get("roomId");
        CannonGameRoom room = gameService.getRoom(roomId);

        if (room == null) {
            response.put("success", false);
            response.put("message", "游戏房间不存在！");
            return response;
        }

        int fromRow = (Integer) request.get("fromRow");
        int fromCol = (Integer) request.get("fromCol");
        int toRow = (Integer) request.get("toRow");
        int toCol = (Integer) request.get("toCol");

        boolean success = room.makeMove(fromRow, fromCol, toRow, toCol);

        if (success) {
            response.put("success", true);
            response.put("message", "移动成功！");
            response.put("board", gameService.getBoardState(room));
            response.put("gameState", room.getGame().getGameState());
            response.put("gameOver", room.getGame().isGameOver());
            response.put("winner", room.getGame().getWinner());
            response.put("isSinglePlayer", room.isSinglePlayer());
        } else {
            response.put("success", false);
            response.put("message", "无效的移动！");
        }

        return response;
    }

    @PostMapping("/ai-move")
    public Map<String, Object> makeAIMove(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        String roomId = request.get("roomId");
        CannonGameRoom room = gameService.getRoom(roomId);

        if (room == null) {
            response.put("success", false);
            response.put("message", "游戏房间不存在！");
            return response;
        }

        int[] aiMove = room.makeAIMove();

        if (aiMove != null) {
            response.put("success", true);
            response.put("message", "AI 移动成功！");
            response.put("board", gameService.getBoardState(room));
            response.put("gameState", room.getGame().getGameState());
            response.put("gameOver", room.getGame().isGameOver());
            response.put("winner", room.getGame().getWinner());
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

    @GetMapping("/state/{roomId}")
    public Map<String, Object> getGameState(@PathVariable String roomId) {
        CannonGameRoom room = gameService.getRoom(roomId);

        if (room == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "游戏房间不存在！");
            return response;
        }

        return gameService.getGameState(room);
    }

    @GetMapping("/reset/{roomId}")
    public Map<String, Object> resetGame(@PathVariable String roomId) {
        CannonGameRoom room = gameService.getRoom(roomId);

        if (room == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "游戏房间不存在！");
            return response;
        }

        room.initializeGame(PlayerSide.CANNON, PlayerSide.SOLDIER);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "游戏已重置！");
        response.put("board", gameService.getBoardState(room));
        response.put("gameState", room.getGame().getGameState());

        return response;
    }

    @GetMapping("/trapped/{roomId}")
    public Map<String, Object> getTrappedCannons(@PathVariable String roomId) {
        CannonGameRoom room = gameService.getRoom(roomId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);

        if (room == null) {
            response.put("trappedCount", 0);
            response.put("trappedPositions", new int[0][2]);
            return response;
        }

        int[][] trapped = room.getGame().getBoard().getTrappedCannons();
        response.put("trappedCount", trapped.length);
        response.put("trappedPositions", trapped);

        return response;
    }
}
