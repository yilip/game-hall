package com.example.game.hall.cannon.model;

public class Game {
    private Board board;
    private Player currentPlayer;
    private Player player1;
    private Player player2;
    private boolean gameOver;
    private String winner;
    private boolean isSinglePlayer; // 是否单机模式
    private PlayerSide humanSide;   // 人类玩家选择的阵营
    
    public Game() {
        this.board = new Board();
        this.gameOver = false;
        this.isSinglePlayer = false;
    }
    
    public void initializeGame(PlayerSide player1Side, PlayerSide player2Side) {
        this.board = new Board();
        this.player1 = new Player(player1Side);
        this.player2 = new Player(player2Side);
        this.currentPlayer = player1;
        this.gameOver = false;
        this.winner = null;
        this.isSinglePlayer = false;
        this.humanSide = null;
    }
    
    public void initializeSinglePlayer(PlayerSide humanSide) {
        this.board = new Board();
        this.humanSide = humanSide;
        this.player1 = new Player(PlayerSide.CANNON);
        this.player2 = new Player(PlayerSide.SOLDIER);
        // 大炮方永远先手
        this.currentPlayer = player1;
        this.gameOver = false;
        this.winner = null;
        this.isSinglePlayer = true;
    }
    
    public boolean isHumanTurn() {
        if (!isSinglePlayer) return true;
        return currentPlayer.getSide() == humanSide;
    }
    
    public boolean makeMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (gameOver) {
            return false;
        }
        
        PieceType piece = board.getPiece(fromRow, fromCol);
        
        if (currentPlayer.getSide() == PlayerSide.CANNON && piece != PieceType.CANNON) {
            return false;
        }
        if (currentPlayer.getSide() == PlayerSide.SOLDIER && piece != PieceType.SOLDIER) {
            return false;
        }
        
        if (board.isValidCapture(fromRow, fromCol, toRow, toCol, piece)) {
            board.capturePiece(fromRow, fromCol, toRow, toCol);
            switchTurn();
            removeTrappedCannons();
            checkGameOver();
            return true;
        }
        
        if (board.isValidMove(fromRow, fromCol, toRow, toCol, piece)) {
            board.movePiece(fromRow, fromCol, toRow, toCol);
            switchTurn();
            removeTrappedCannons();
            checkGameOver();
            return true;
        }
        
        return false;
    }
    
    /**
     * 移除所有被围住的大炮
     */
    private void removeTrappedCannons() {
        int[][] trappedCannons = board.getTrappedCannons();
        for (int[] pos : trappedCannons) {
            board.setPiece(pos[0], pos[1], PieceType.EMPTY);
        }
    }
    
    private void switchTurn() {
        if (currentPlayer.getSide() == PlayerSide.CANNON) {
            currentPlayer = player2;
        } else {
            currentPlayer = player1;
        }
    }
    
    private void checkGameOver() {
        int cannonCount = board.countPieces(PieceType.CANNON);
        int soldierCount = board.countPieces(PieceType.SOLDIER);
        
        if (cannonCount == 0) {
            gameOver = true;
            winner = "🎉 小兵方获胜！所有大炮被消灭！";
        } else if (soldierCount == 0) {
            gameOver = true;
            winner = "🎉 大炮方获胜！所有小兵被吃完！";
        }
    }
    
    public Board getBoard() {
        return board;
    }
    
    public Player getCurrentPlayer() {
        return currentPlayer;
    }
    
    public boolean isGameOver() {
        return gameOver;
    }
    
    public String getWinner() {
        return winner;
    }
    
    public String getGameState() {
        StringBuilder sb = new StringBuilder();
        sb.append(board.printBoard());
        sb.append("\n");
        
        int cannons = board.countPieces(PieceType.CANNON);
        int soldiers = board.countPieces(PieceType.SOLDIER);
        
        sb.append("🔫 大炮剩余：").append(cannons).append("\n");
        sb.append("💂 小兵剩余：").append(soldiers).append("\n");
        sb.append("当前回合：").append(currentPlayer.getSide() == PlayerSide.CANNON ? "🔫 大炮方" : "💂 小兵方").append("\n");
        
        // 检查并显示被围住的大炮
        int[][] trapped = board.getTrappedCannons();
        if (trapped.length > 0) {
            sb.append("⚠️ 被围住的大炮：").append(trapped.length).append(" 门\n");
        }
        
        if (gameOver) {
            sb.append("\n🎉 ").append(winner).append("\n");
        }
        
        return sb.toString();
    }
}
