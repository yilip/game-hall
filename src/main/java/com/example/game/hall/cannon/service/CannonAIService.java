package com.example.game.hall.cannon.service;

import com.example.game.hall.cannon.model.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class CannonAIService {

    private final Random random = new Random();

    public int[] makeAIMove(Game game, PlayerSide aiSide) {
        if (game.isGameOver()) {
            return null;
        }

        if (game.getCurrentPlayer().getSide() != aiSide) {
            return null;
        }

        int[][] moves = getAllPossibleMoves(game.getBoard(), aiSide);

        if (moves.length == 0) {
            return null;
        }

        int[] bestMove = selectBestMove(moves, game.getBoard(), aiSide);

        if (bestMove != null) {
            game.makeMove(bestMove[0], bestMove[1], bestMove[2], bestMove[3]);
        }

        return bestMove;
    }

    private int[][] getAllPossibleMoves(Board board, PlayerSide side) {
        List<int[]> moves = new ArrayList<>();
        PieceType pieceType = (side == PlayerSide.CANNON) ? PieceType.CANNON : PieceType.SOLDIER;

        for (int fromRow = 0; fromRow < 5; fromRow++) {
            for (int fromCol = 0; fromCol < 5; fromCol++) {
                if (board.getPiece(fromRow, fromCol) == pieceType) {
                    for (int toRow = 0; toRow < 5; toRow++) {
                        for (int toCol = 0; toCol < 5; toCol++) {
                            if (board.isValidCapture(fromRow, fromCol, toRow, toCol, pieceType)) {
                                moves.add(new int[]{fromRow, fromCol, toRow, toCol});
                            }
                        }
                    }

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

    private int[] selectBestMove(int[][] moves, Board board, PlayerSide side) {
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;

        for (int[] move : moves) {
            int score = evaluateMove(move, board, side);
            if (score > bestScore) {
                bestScore = score;
                bestMove = move;
            }
        }

        return bestMove;
    }

    private int evaluateMove(int[] move, Board board, PlayerSide side) {
        int score = 0;
        int fromRow = move[0], fromCol = move[1], toRow = move[2], toCol = move[3];
        PieceType piece = board.getPiece(fromRow, fromCol);

        if (board.isValidCapture(fromRow, fromCol, toRow, toCol, piece)) {
            score += 100;
        }

        if (side == PlayerSide.CANNON) {
            if (!board.isCannonTrapped(toRow, toCol)) {
                score += 20;
            }

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

        if (side == PlayerSide.SOLDIER) {
            if (fromRow > 2 && toRow < fromRow) {
                score += 5;
            }

            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    if (board.getPiece(r, c) == PieceType.CANNON) {
                        int dist = Math.abs(toRow - r) + Math.abs(toCol - c);
                        if (dist == 1) {
                            score += 15;
                        }

                        Board tempBoard = cloneBoard(board);
                        tempBoard.movePiece(fromRow, fromCol, toRow, toCol);
                        if (tempBoard.isCannonTrapped(r, c)) {
                            score += 50;
                        }
                    }
                }
            }

            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    if (board.getPiece(r, c) == PieceType.CANNON) {
                        if (r == toRow && Math.abs(c - toCol) == 2) {
                            score -= 30;
                        }
                        if (c == toCol && Math.abs(r - toRow) == 2) {
                            score -= 30;
                        }
                    }
                }
            }
        }

        score += random.nextInt(10);

        return score;
    }

    private Board cloneBoard(Board original) {
        Board clone = new Board();
        PieceType[][] board = original.getBoard();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                clone.setPiece(i, j, board[i][j]);
            }
        }
        return clone;
    }
}
