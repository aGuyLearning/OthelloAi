package othello.othelloAi;

import szte.mi.Move;
import szte.mi.Player;

import java.util.Random;

public class AiPlayer implements Player {
    private OthelloModel game = new OthelloModel();
    private int order;
    private Random rnd;
    private MonteCarloTreeSearch mcts;

    @Override
    public void init(int order, long t, Random rnd) {
        this.game = new OthelloModel();
        this.order = order;
        this.rnd = rnd;
        this.mcts = new MonteCarloTreeSearch(rnd);
    }

    @Override
    public Move nextMove(Move prevMove, long tOpponent, long t) {
        // update model with opponents move
        if (prevMove != null) {
            int cellIndex = prevMove.y * OthelloModel.BOARD_SIZE + prevMove.x;
            int[] m = game.getIndexMoves();
            for (int i = 0; i < m.length; i++) {
                if (m[i] == cellIndex) {
                    game.makeMove(i);
                    break;
                }
            }
        }
        else if(prevMove == null && game.getRound() != 0 && game.isRunning()){
            game.makeMove(0);
        }

        if (game.getCurrent() == this.order && !game.isPass() && this.game.isRunning()) {
            if (game.getRound() <= 12) {
                int numMoves = this.game.getNumMoves();
                int ind = numMoves > 1 ? rnd.nextInt(numMoves - 1) : 0;
                game.makeMove(ind);
                long theMove = game.getLastCellChanged();
                if (theMove != OthelloModel.PASS) {
                    long row = theMove / OthelloModel.BOARD_SIZE;
                    long col = theMove - OthelloModel.BOARD_SIZE * row;
                    return new Move((int) col, (int) row);
                }

            } else {
                int level = game.getRound() > 40 && game.getRound() < 55? 2 : 1;
                mcts.setLevel(level);
                // this.game = mcts.findNextMove(game, order);
                game.makeMove(MiniMaxAlpha.getBestMove(game));

                long theMove = game.getLastCellChanged();
                if (theMove != OthelloModel.PASS) {
                    long row = theMove / OthelloModel.BOARD_SIZE;
                    long col = theMove - OthelloModel.BOARD_SIZE * row;
                    return new Move((int) col, (int) row);
                }

            }

        }
        if (this.game.isPass() && game.isRunning()){
            this.game.makeMove(0);
        }
        return null;
    }
}
