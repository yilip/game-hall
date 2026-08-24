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
        System.out.println("检查吃子：from=[" + fromRow + "," + fromCol + "] to=[" + toRow + "," + toCol + "] piece=" + piece);
        
        if (!isValidPosition(fromRow, fromCol) || !isValidPosition(toRow, toCol)) {
            System.out.println("失败：位置无效");
            return false;
        }
        
        // 只有大炮能吃子
        if (piece != PieceType.CANNON) {
            System.out.println("失败：不是大炮");
            return false;
        }
        
        // 目标位置必须有小兵
        PieceType targetPiece = board[toRow][toCol];
        System.out.println("目标位置棋子：" + targetPiece);
        if (targetPiece != PieceType.SOLDIER) {
            System.out.println("失败：目标位置没有小兵");
            return false;
        }
        
        // 检查是否在同一直线上
        int rowDiff = toRow - fromRow;
        int colDiff = toCol - fromCol;
        System.out.println("rowDiff=" + rowDiff + " colDiff=" + colDiff);
        
        // 必须是直线移动（横向或纵向）
        if (rowDiff != 0 && colDiff != 0) {
            System.out.println("失败：不是直线移动");
            return false;
        }
        
        // 检查是否隔一个格子
        if (rowDiff != 0) {
            // 纵向移动
            int absRowDiff = Math.abs(rowDiff);
            System.out.println("纵向移动，距离=" + absRowDiff);
            if (absRowDiff != 2) {
                System.out.println("失败：距离不是 2");
                return false;
            }
            // 检查中间是否为空（大炮隔一个空格吃小兵）
            int midRow = fromRow + rowDiff / 2;
            PieceType midPiece = board[midRow][fromCol];
            System.out.println("中间位置 [" + midRow + "," + fromCol + "] 棋子：" + midPiece);
            if (midPiece != PieceType.EMPTY) {
                System.out.println("失败：中间有障碍物，无法跳过");
                return false;
            }
            System.out.println("吃子验证成功！");
            return true;
        } else {
            // 横向移动
            int absColDiff = Math.abs(colDiff);
            System.out.println("横向移动，距离=" + absColDiff);
            if (absColDiff != 2) {
                System.out.println("失败：距离不是 2");
                return false;
            }
            // 检查中间是否为空（大炮隔一个空格吃小兵）
            int midCol = fromCol + colDiff / 2;
            PieceType midPiece = board[fromRow][midCol];
            System.out.println("中间位置 [" + fromRow + "," + midCol + "] 棋子：" + midPiece);
            if (midPiece != PieceType.EMPTY) {
                System.out.println("失败：中间有障碍物，无法跳过");
                return false;
            }
            System.out.println("吃子验证成功！");
            return true;
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
