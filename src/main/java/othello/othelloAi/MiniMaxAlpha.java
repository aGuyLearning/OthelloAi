package othello.othelloAi;

/**
 * The MiniMax algorithm optimised with Alpha-Beta pruning.
 *
 * @author DavidHurst
 */
public class MiniMaxAlpha {

    private static final int MAX_DEPTH = 9;

    private MiniMaxAlpha() {
    }
    public static double miniMax(OthelloModel board, int depth, double alpha, double beta,
                              boolean isMax) {
        double boardVal = board.evaluateBoard();
        int[] children = board.getIndexMoves();

        // Terminal node (win/lose/draw) or max depth reached.
        if (Math.abs(boardVal) == 10 || depth == 0 || children.length == 0) {
            return boardVal;
        }

        // Maximising player, find the maximum attainable value.
        if (isMax) {
            double highestVal = Double.MIN_VALUE;
            for (int i = 0; i < children.length; i++) {
                OthelloModel newBoard = board.copy();
                newBoard.makeMove(i);
                highestVal = Math.max(highestVal, miniMax(newBoard,
                        depth - 1, alpha, beta, false));
                alpha = Math.max(alpha, highestVal);
                if (alpha >= beta) {
                    return highestVal;
                }
            }
            return  highestVal;
            // Minimising player, find the minimum attainable value;
        } else {
            double lowestVal = Double.MAX_VALUE;
            int[] validMoves = board.getIndexMoves();
            for (int i = 0; i < validMoves.length; i++) {
                        OthelloModel newBoard = board.copy();
                        newBoard.makeMove(i);
                        lowestVal = Math.min(lowestVal, miniMax(newBoard,
                                depth - 1, alpha, beta, true));
                        beta = Math.min(beta, lowestVal);
                        if (beta <= alpha) {
                            return lowestVal;
                        }
                }
            return lowestVal;
            }

        }

    /**
     * Evaluate every legal move on the board and return the best one.
     * @param board Board to evaluate
     * @return Coordinates of best move
     */
    public static int getBestMove(OthelloModel board) {
        int bestMove = 0;
        double bestValue = Double.MIN_VALUE;
        int[] validMoves = board.getIndexMoves();
        for (int i = 0; i < validMoves.length; i++) {
            double moveValue = miniMax(board.copy(), MAX_DEPTH, Double.MIN_VALUE,
                    Double.MAX_VALUE, false);
            if (moveValue > bestValue) {
                bestMove = i;
                bestValue = moveValue;
            }
        }
        return bestMove;
    }
}