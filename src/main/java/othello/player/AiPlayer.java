package othello.player;

import othello.game.OthelloModel;
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
        if(prevMove != null) {
            int cellIndex = prevMove.y * OthelloModel.BOARD_SIZE + prevMove.x;
            int[] m = game.getIndexMoves();
            for (int i = 0; i < m.length; i++) {
                if(m[i] == cellIndex){
                    game.makeMove(i);
                    break;
                }
            }
        }else {
            if(this.game.isRunning()) {
                this.game.makeMove(0);
            }
        }
        if (game.getCurrent() == this.order && !game.isPass() && this.game.isRunning()) {
            this.game = mcts.findNextMove(game, order);
            long theMove = game.getLastCellChanged();
            if (theMove != OthelloModel.PASS) {
                long row = theMove / OthelloModel.BOARD_SIZE;
                long col = theMove - OthelloModel.BOARD_SIZE * row;
                return new Move((int) col, (int) row);
            }
        }
        return null;
    }
}
