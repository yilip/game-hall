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
        System.out.println("=== 移动请求 ===");
        System.out.println("from: [" + fromRow + "," + fromCol + "]");
        System.out.println("to: [" + toRow + "," + toCol + "]");
        System.out.println("currentPlayer: " + (currentPlayer != null ? currentPlayer.getSide() : "null"));
        System.out.println("gameOver: " + gameOver);
        
        if (gameOver) {
            System.out.println("失败：游戏已结束");
            return false;
        }
        
        PieceType piece = board.getPiece(fromRow, fromCol);
        System.out.println("棋子类型：" + piece);
        
        // 检查是否是自己回合
        if (currentPlayer.getSide() == PlayerSide.CANNON && piece != PieceType.CANNON) {
            System.out.println("失败：大炮回合但选中的不是大炮");
            return false;
        }
        if (currentPlayer.getSide() == PlayerSide.SOLDIER && piece != PieceType.SOLDIER) {
            System.out.println("失败：小兵回合但选中的不是小兵");
            return false;
        }
        
        // 尝试吃子（只有大炮可以吃子）
        if (board.isValidCapture(fromRow, fromCol, toRow, toCol, piece)) {
            System.out.println("执行吃子");
            board.capturePiece(fromRow, fromCol, toRow, toCol);
            switchTurn();
            removeTrappedCannons(); // 检查是否有大炮被围住
            checkGameOver();
            return true;
        }
        
        // 尝试普通移动
        boolean validMove = board.isValidMove(fromRow, fromCol, toRow, toCol, piece);
        System.out.println("isValidMove: " + validMove);
        
        if (validMove) {
            System.out.println("执行普通移动");
            board.movePiece(fromRow, fromCol, toRow, toCol);
            switchTurn();
            removeTrappedCannons(); // 检查是否有大炮被围住
            checkGameOver();
            return true;
        }
        
        System.out.println("失败：无效的移动");
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
        System.out.println("切换回合：当前回合 -> " + currentPlayer.getSide());
    }
    
    /**
     * AI 计算并执行移动
     */
    public int[] makeAIMove() {
        if (!isSinglePlayer || gameOver) {
            System.out.println("AI 无法移动：isSinglePlayer=" + isSinglePlayer + ", gameOver=" + gameOver);
            return null;
        }
        
        PlayerSide aiSide = (humanSide == PlayerSide.CANNON) ? PlayerSide.SOLDIER : PlayerSide.CANNON;
        
        // 检查是否是 AI 的回合
        if (currentPlayer.getSide() != aiSide) {
            System.out.println("AI 无法移动：不是 AI 的回合，currentPlayer=" + currentPlayer.getSide() + ", aiSide=" + aiSide);
            return null;
        }
        
        // 获取所有可能的移动
        int[][] moves = getAllPossibleMoves(aiSide);
        
        System.out.println("AI 找到 " + moves.length + " 个可能的移动");
        
        if (moves.length == 0) {
            return null;
        }
        
        // 评估每个移动，选择最好的
        int[] bestMove = selectBestMove(moves, aiSide);
        
        if (bestMove != null) {
            System.out.println("AI 选择移动：[" + bestMove[0] + "," + bestMove[1] + "] -> [" + bestMove[2] + "," + bestMove[3] + "]");
            makeMove(bestMove[0], bestMove[1], bestMove[2], bestMove[3]);
        }
        
        return bestMove;
    }
    
    /**
     * 获取所有可能的移动
     */
    private int[][] getAllPossibleMoves(PlayerSide side) {
        java.util.List<int[]> moves = new java.util.ArrayList<>();
        PieceType pieceType = (side == PlayerSide.CANNON) ? PieceType.CANNON : PieceType.SOLDIER;
        
        for (int fromRow = 0; fromRow < 5; fromRow++) {
            for (int fromCol = 0; fromCol < 5; fromCol++) {
                if (board.getPiece(fromRow, fromCol) == pieceType) {
                    // 检查吃子移动
                    for (int toRow = 0; toRow < 5; toRow++) {
                        for (int toCol = 0; toCol < 5; toCol++) {
                            if (board.isValidCapture(fromRow, fromCol, toRow, toCol, pieceType)) {
                                moves.add(new int[]{fromRow, fromCol, toRow, toCol});
                            }
                        }
                    }
                    
                    // 检查普通移动
                    int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                    for (int[] dir : directions) {
                        int toRow = fromRow + dir[0];
                        int toCol = fromCol + dir[1];
                        if (board.isValidMove(fromRow, fromCol, toRow, toCol, pieceType)) {
                            moves.add(new int[]{fromRow, fromCol, toRow, toCol});
                        }
                    }
                }
            }
        }
        
        return moves.toArray(new int[moves.size()][]);
    }
    
    /**
     * 选择最好的移动（简单的贪心算法）
     */
    private int[] selectBestMove(int[][] moves, PlayerSide side) {
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;
        
        for (int[] move : moves) {
            int score = evaluateMove(move, side);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }
        
        return bestMove;
    }
    
    /**
     * 评估移动的价值
     */
    private int evaluateMove(int[] move, PlayerSide side) {
        int score = 0;
        int fromRow = move[0], fromCol = move[1], toRow = move[2], toCol = move[3];
        PieceType piece = board.getPiece(fromRow, fromCol);
        
        // 如果是吃子，优先选择
        if (board.isValidCapture(fromRow, fromCol, toRow, toCol, piece)) {
            score += 100; // 吃子优先级最高
        }
        
        // 大炮策略
        if (side == PlayerSide.CANNON) {
            // 大炮应该保持活动空间
            if (!board.isCannonTrapped(toRow, toCol)) {
                score += 20;
            }
            
            // 大炮靠近小兵更容易吃子
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    if (board.getPiece(r, c) == PieceType.SOLDIER) {
                        int dist = Math.abs(toRow - r) + Math.abs(toCol - c);
                        if (dist <= 3) {
                            score += 10;
                        }
                    }
                }
            }
        }
        
        // 小兵策略
        if (side == PlayerSide.SOLDIER) {
            // 小兵应该向大炮方向移动
            if (side == PlayerSide.SOLDIER) {
                // 如果小兵初始在下方（行索引大），向上移动（行索引减小）
                if (fromRow > 2 && toRow < fromRow) {
                    score += 5;
                }
            }
            
            // 围困大炮
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    if (board.getPiece(r, c) == PieceType.CANNON) {
                        // 检查移动后是否有助于围困
                        int dist = Math.abs(toRow - r) + Math.abs(toCol - c);
                        if (dist == 1) {
                            score += 15; // 相邻大炮，准备围困
                        }
                        
                        // 如果移动后能困住大炮，额外加分
                        Board tempBoard = cloneBoard();
                        tempBoard.movePiece(fromRow, fromCol, toRow, toCol);
                        if (tempBoard.isCannonTrapped(r, c)) {
                            score += 50;
                        }
                    }
                }
            }
            
            // 避免被大炮吃掉
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    if (board.getPiece(r, c) == PieceType.CANNON) {
                        // 检查是否在炮的攻击线上
                        if (r == toRow && Math.abs(c - toCol) == 2) {
                            score -= 30; // 危险位置
                        }
                        if (c == toCol && Math.abs(r - toRow) == 2) {
                            score -= 30;
                        }
                    }
                }
            }
        }
        
        // 随机性，避免 AI 太 predictable
        score += (int)(Math.random() * 10);
        
        return score;
    }
    
    /**
     * 克隆棋盘用于模拟
     */
    private Board cloneBoard() {
        Board clone = new Board();
        PieceType[][] board = this.board.getBoard();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                clone.setPiece(i, j, board[i][j]);
            }
        }
        return clone;
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
