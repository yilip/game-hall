package com.example.game.hall.cannon.model;

public class Board {
    private static final int SIZE = 5;
    private PieceType[][] board;
    
    public Board() {
        board = new PieceType[SIZE][SIZE];
        initializeBoard();
    }
    
    private void initializeBoard() {
        // 初始化棋盘为空
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                board[i][j] = PieceType.EMPTY;
            }
        }
        
        // 放置三大炮：第 1 排，位置 1, 3, 5 (索引 0, 2, 4)
        board[0][0] = PieceType.CANNON;
        board[0][2] = PieceType.CANNON;
        board[0][4] = PieceType.CANNON;
        
        // 放置十五兵：占满第 3、4、5 排 (索引 2, 3, 4)
        for (int row = 2; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                board[row][col] = PieceType.SOLDIER;
            }
        }
    }
    
    public PieceType getPiece(int row, int col) {
        if (isValidPosition(row, col)) {
            return board[row][col];
        }
        return PieceType.EMPTY;
    }
    
    public void setPiece(int row, int col, PieceType piece) {
        if (isValidPosition(row, col)) {
            board[row][col] = piece;
        }
    }
    
    public boolean isValidPosition(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }
    
    public boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol, PieceType piece) {
        if (!isValidPosition(fromRow, fromCol) || !isValidPosition(toRow, toCol)) {
            return false;
        }
        
        // 目标位置必须是空的
        if (board[toRow][toCol] != PieceType.EMPTY) {
            return false;
        }
        
        // 检查是否是相邻移动（上下左右）
        int rowDiff = Math.abs(toRow - fromRow);
        int colDiff = Math.abs(toCol - fromCol);
        
        // 只能走一步
        return (rowDiff + colDiff) == 1;
    }
    
    /**
     * 检查大炮是否被围住（上下左右都无法移动）
     */
    public boolean isCannonTrapped(int row, int col) {
        if (board[row][col] != PieceType.CANNON) {
            return false;
        }
        
        // 检查四个方向是否都能移动
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; // 上、下、左、右
        
        for (int[] dir : directions) {
            int newRow = row + dir[0];
            int newCol = col + dir[1];
            
            // 如果有任何一个方向可以移动（位置有效且为空），则没有被围住
            if (isValidPosition(newRow, newCol) && board[newRow][newCol] == PieceType.EMPTY) {
                return false;
            }
        }
        
        // 所有方向都被堵住
        return true;
    }
    
    /**
     * 检查所有大炮，返回被围住的大炮位置列表
     */
    public int[][] getTrappedCannons() {
        int[][] trapped = new int[3][2]; // 最多 3 个大炮
        int count = 0;
        
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == PieceType.CANNON && isCannonTrapped(i, j)) {
                    trapped[count][0] = i;
                    trapped[count][1] = j;
                    count++;
                }
            }
        }
        
        // 返回实际数量的数组
        int[][] result = new int[count][2];
        System.arraycopy(trapped, 0, result, 0, count);
        return result;
    }
    
    public boolean isValidCapture(int fromRow, int fromCol, int toRow, int toCol, PieceType piece) {
        if (!isValidPosition(fromRow, fromCol) || !isValidPosition(toRow, toCol)) {
            return false;
        }
        
        if (piece != PieceType.CANNON) {
            return false;
        }
        
        PieceType targetPiece = board[toRow][toCol];
        if (targetPiece != PieceType.SOLDIER) {
            return false;
        }
        
        int rowDiff = toRow - fromRow;
        int colDiff = toCol - fromCol;
        
        if (rowDiff != 0 && colDiff != 0) {
            return false;
        }
        
        if (rowDiff != 0) {
            int absRowDiff = Math.abs(rowDiff);
            if (absRowDiff != 2) {
                return false;
            }
            int midRow = fromRow + rowDiff / 2;
            PieceType midPiece = board[midRow][fromCol];
            return midPiece == PieceType.EMPTY;
        } else {
            int absColDiff = Math.abs(colDiff);
            if (absColDiff != 2) {
                return false;
            }
            int midCol = fromCol + colDiff / 2;
            PieceType midPiece = board[fromRow][midCol];
            return midPiece == PieceType.EMPTY;
        }
    }
    
    public void movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        if (isValidPosition(fromRow, fromCol) && isValidPosition(toRow, toCol)) {
            board[toRow][toCol] = board[fromRow][fromCol];
            board[fromRow][fromCol] = PieceType.EMPTY;
        }
    }
    
    public void capturePiece(int fromRow, int fromCol, int toRow, int toCol) {
        movePiece(fromRow, fromCol, toRow, toCol);
    }
    
    public int countPieces(PieceType type) {
        int count = 0;
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                if (board[i][j] == type) {
                    count++;
                }
            }
        }
        return count;
    }
    
    public PieceType[][] getBoard() {
        return board;
    }
    
    public String printBoard() {
        StringBuilder sb = new StringBuilder();
        sb.append("   1   2   3   4   5\n");
        sb.append("  ┌───┬───┬───┬───┬───┐\n");
        
        for (int i = 0; i < SIZE; i++) {
            sb.append(i + 1).append(" │");
            for (int j = 0; j < SIZE; j++) {
                PieceType piece = board[i][j];
                sb.append(" ").append(piece.getSymbol()).append(" │");
            }
            sb.append("\n");
            if (i < SIZE - 1) {
                sb.append("  ├───┼───┼───┼───┼───┤\n");
            }
        }
        
        sb.append("  └───┴───┴───┴───┴───┘\n");
        return sb.toString();
    }
}
