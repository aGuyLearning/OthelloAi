package othello.AdverserialLearning.Lisa;

import szte.mi.Move;
import szte.mi.Player;

import java.util.ArrayList;
import java.util.Random;

public class KiPlayerMiniMax implements Player {

    final int[][] heuristicsOfBoard = {
            {30, -15, 12, 12, 12, 12, -15, 30},
            {-15, -30, -7, -7, -7, -7, -30, -15},
            {12, -7, 7, 0, 0, 7, -7, 12},
            {12, -7, 0, 7, 7, 0, -7, 12},
            {12, -7, 12, 12, 12, 12, -7, 12},
            {12, -7, 7, 0, 0, 7, -7, 12},
            {-15, -30, -7, -7, -7, -7, -30, -15},
            {30, -15, 12, 12, 12, 12, -15, 30},

    };

    int searchDepth = 5;
    final int PLAYER_BLACK = 1;
    final int PLAYER_WHITE = 2;

    public OthelloGameLogic game;
    public int player;
    int opponent;

    Random random;

    @Override
    public void init(int order, long t, Random rnd) {
        this.player = order == 0 ? this.PLAYER_BLACK : this.PLAYER_WHITE;
        this.opponent = order == 1 ? this.PLAYER_BLACK : this.PLAYER_WHITE;
        this.random = rnd;
        this.game = new OthelloGameLogic();
    }

    @Override
    public Move nextMove(Move prevMove, long tOpponent, long t) {
        if (prevMove != null) {
            //Playing opponents move
            this.game.draw(new Move(prevMove.y, prevMove.x));
        }
        if (!this.game.gameOver) {
            if (this.game.currentPlayer == this.player) {
                Move re;
                //KiPlayer does not have to pass -> he has to return a move
                if(this.game.getResultForPlayer(this.PLAYER_BLACK)+this.game.getResultForPlayer(this.PLAYER_WHITE)<8){
                    re = this.calculateNextMoveRandom();
                } else {
                    if(this.game.getResultForPlayer(this.PLAYER_BLACK)+this.game.getResultForPlayer(this.PLAYER_WHITE)<60){
                        this.searchDepth = 3;
                    }
                    re = this.calculateNextMove();
                }
                return new Move(re.y, re.x);
            } else {
                //game is still running but KiPlayer has to pass
                return null;
            }
        } else {
            //game is over
            return null;
        }
    }

    private Move calculateNextMoveRandom() {
        ArrayList<Move> possibleMoves = this.game.getPossibleMovesOfPlayer(this.player);
        int index = possibleMoves.size() > 1 ? this.random.nextInt(possibleMoves.size() - 1) : 0;
        this.game.draw(possibleMoves.get(index));
        return possibleMoves.get(index);

    }

    private Move calculateNextMove() {
        //there has to be at least one possible move!
        ArrayList<Move> possibleMoves = this.game.getPossibleMovesOfPlayer(this.player);
        int[] scores = new int[possibleMoves.size()];
        int bestScore = -999999999;
        int alpha = -999999999;
        int beta = 999999999;
        Move bestMove = possibleMoves.get(0);
        OthelloGameLogic copyGame;
        for (int i = 0; i < possibleMoves.size(); i++) {
            copyGame = this.game.clone();
            copyGame.draw(possibleMoves.get(i));
            scores[i] = miniMaxValue(copyGame, this.player, this.opponent,1, alpha, beta);
            if(scores[i] > bestScore){
                bestMove = possibleMoves.get(i);
                bestScore = scores[i];
            }
            alpha = Math.max(alpha, bestScore);
            if(alpha >= beta){
                //alpha-beta pruning
                break;
            }
        }
        this.game.draw(bestMove);
        return bestMove;
    }

    private int miniMaxValue(OthelloGameLogic copyBoard,int originalPlayer, int currentPlayer, int currentDepth, int alpha, int beta){
        //TODO: use alpha and beta
        int oppo = currentPlayer == 2 ? this.PLAYER_BLACK : this.PLAYER_WHITE;
        if(this.searchDepth == currentDepth || copyBoard.gameOver){
            return score(copyBoard, originalPlayer);
        }
        if(copyBoard.currentPlayer != currentPlayer){
            //player had to skip
            return miniMaxValue(copyBoard, originalPlayer, oppo, currentDepth+1, alpha, beta);
        } else {
            ArrayList<Move> possibleMoves = copyBoard.getPossibleMovesOfPlayer(currentPlayer);
            int bestVal = originalPlayer==currentPlayer? -999999999 : 999999999;
            OthelloGameLogic gameCopy;
            for (int i = 0; i < possibleMoves.size(); i++) {
                gameCopy = copyBoard.clone();
                gameCopy.draw(possibleMoves.get(i));
                int value = miniMaxValue(gameCopy, originalPlayer, oppo, currentDepth+1, alpha, beta);
                if(originalPlayer == currentPlayer){
                    if (value>bestVal){
                        //max if originator's turn
                        bestVal = value;
                        alpha = Math.max(bestVal, alpha);
                        if(alpha >= beta){
                            //alpha-beta pruning
                            break;
                        }
                    }
                } else {
                    if (value<bestVal){
                        //min if opponent's turn
                        bestVal = value;
                        beta = Math.min(bestVal, beta);
                        if(alpha >= beta){
                            //alpha-beta pruning
                            break;
                        }
                    }
                }
            }
            return bestVal;
        }
    }

    private int score(OthelloGameLogic board, int originalPlayer){
        int oppo = originalPlayer == 2 ? this.PLAYER_BLACK : this.PLAYER_WHITE;
        int score = 0;
        score +=this.calculateBonus(board, originalPlayer, oppo);
        return score;
    }

    private int calculateBonus(OthelloGameLogic board, int originalPlayer, int oppo){
        int tempScore = 0;
        if(board.winner == originalPlayer){
            tempScore+=100;
        }
        tempScore += board.getResultForPlayer(originalPlayer);
        tempScore -= board.getResultForPlayer(oppo)*2;
        ArrayList<Move> possibleMoves = board.getPossibleMovesOfPlayer(oppo);
        if(possibleMoves.size() == 0){
            tempScore += 20;
        } else {
            tempScore -= possibleMoves.size() * 2;
        }
        tempScore -= getHighestSwitches(board, oppo, possibleMoves);

        tempScore += getHeuristicsFieldScore(board, originalPlayer, oppo);

        return tempScore;
    }

    private int getHighestSwitches(OthelloGameLogic board, int oppo, ArrayList<Move> possibleMoves){
        int maxSwitches = 0;
        int currentSwitches;
        for (int i = 0; i < possibleMoves.size(); i++) {
            currentSwitches = board.getAllSwitches(oppo, possibleMoves.get(i)).size();
            if(currentSwitches>maxSwitches){
                maxSwitches = currentSwitches;
            }
        }
        return maxSwitches;
    }

    private int getHeuristicsFieldScore(OthelloGameLogic board, int originalPlayer, int oppo){
        int score = 0;
        for (int i = 0; i < board.getBoard().length; i++) {
            for (int j = 0; j < board.getBoard().length; j++) {
                if(board.getBoard()[i][j]==originalPlayer){
                    score += this.heuristicsOfBoard[i][j];
                } else if(board.getBoard()[i][j]==oppo){
                    score -= this.heuristicsOfBoard[i][j];
                }
            }

        }
        return score;
    }

}
