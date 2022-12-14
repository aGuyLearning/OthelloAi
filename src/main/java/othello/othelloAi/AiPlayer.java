package othello.othelloAi;

import cc.mallet.types.Dirichlet;
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import othello.AdverserialLearning.AdversaryLearningConfiguration;

import othello.othelloAi.mcts.MonteCarloTreeSearch;
import szte.mi.Move;
import szte.mi.Player;

import java.io.IOException;
import java.util.Random;
import java.util.stream.IntStream;

public class AiPlayer implements Player {
    private OthelloModel game = new OthelloModel();
    private int order;
    private Random rnd;
    private MonteCarloTreeSearch mcts;
    private ComputationGraph model;

    @Override
    public void init(int order, long t, Random rnd) {
        this.game = new OthelloModel();
        this.order = order == 0 ? OthelloModel.PLAYER_BLACK : OthelloModel.PLAYER_WHITE;
        this.rnd = rnd;
        try {
            this.loadComputationGraphs();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.mcts = new MonteCarloTreeSearch(this.rnd, this.model);


    }

    @Override
    public Move nextMove(Move prevMove, long tOpponent, long t) {
        System.out.println(prevMove);
        if (prevMove != null) {
            int cellIndex = prevMove.y * OthelloModel.BOARD_SIZE + prevMove.x;
            int[] m = game.getValidMoveIndices();
            for (int i = 0; i < m.length; i++) {
                if (m[i] == cellIndex) {
                    game.makeMove(i);
                    break;
                }
            }
        } else if (prevMove == null && game.getRound() != 0 && game.isRunning()) {
            game.makeMove(0);
        }


        if (game.toPlay() == this.order && !game.isPass() && this.game.isRunning()) {
            if (game.getRound() <= 10) {
                int numMoves = this.game.getNumMoves();
                int ind = numMoves > 1 ? rnd.nextInt(numMoves - 1) : 0;
                game.makeMove(ind);
                long theMove = game.getLastCellChanged();
                if (theMove != OthelloModel.PASS) {
                    long row = theMove / OthelloModel.BOARD_SIZE;
                    long col = theMove - OthelloModel.BOARD_SIZE * row;
                    Move move = new Move((int) col, (int) row);
                    return move;
                }

            } else {
                if (game.getRound() > 34) {
                    mcts.setLevel(2);
                }
                if (game.getRound() > 60) {
                    mcts.setLevel(1);
                }
                int[] emptyFields = game.getValidMoveIndices();
                System.out.println(game);
                INDArray actionProbabilities = mcts.findNextMove(game, 0);

                INDArray validMask = game.validMovesMask();
                int[] validMoveIndices = game.getValidMoveIndices();
                INDArray validActionProbabilities = actionProbabilities.mul(validMask);
                INDArray normalizedActionProbabilities = validActionProbabilities.div(Nd4j.sum(actionProbabilities));
                int moveAction = chooseNewMoveAction(game.getValidMoveIndices(), normalizedActionProbabilities);

                int finalMoveAction = moveAction;
                boolean contains = !IntStream.of(emptyFields).anyMatch(x -> x == finalMoveAction);
                if (!contains) {
                    moveAction = AdversaryLearningConfiguration.randomGenerator.nextInt(emptyFields.length);
                    game.makeMove(moveAction);
                } else {
                    if (moveAction == -1) {
                        game.makeMove(0);
                    } else {
                        for (int i = 0; i < validMoveIndices.length; i++) {
                            if (moveAction == validMoveIndices[i]) {
                                game.makeMove(i);
                                break;
                            }
                        }
                    }
                }
                long theMove = game.getLastCellChanged();
                if (theMove != OthelloModel.PASS) {
                    long row = theMove / OthelloModel.BOARD_SIZE;
                    long col = theMove - OthelloModel.BOARD_SIZE * row;
                    return new Move((int) col, (int) row);
                }

                if (this.game.isPass() && game.isRunning()) {
                    this.game.makeMove(0);
                }
            }
        }
        return null;
    }


    public void loadComputationGraphs() throws IOException {
        String absoluteBestModelPath =
                AdversaryLearningConfiguration.getAbsoluteModelPathFrom(AdversaryLearningConfiguration.bestModelFileName);
        this.model = ModelSerializer.restoreComputationGraph(absoluteBestModelPath, true);
        this.model.setLearningRate(AdversaryLearningConfiguration.learningRate);
    }

    int chooseNewMoveAction(int[] validMoveIndices, INDArray normalizedActionProbabilities) {

        int moveAction;
        if (validMoveIndices.length == 0) {
            moveAction = -1;
        } else if (validMoveIndices.length == 1) {
            moveAction = validMoveIndices[0];
        } else {

            double alpha = AdversaryLearningConfiguration.dirichletAlpha;
            Dirichlet dirichlet = new Dirichlet(validMoveIndices.length, alpha);

            INDArray nextDistribution = Nd4j.createFromArray(dirichlet.nextDistribution());
            INDArray slices = Nd4j.createFromArray(validMoveIndices);
            INDArray reducedValidActionProbabilities = normalizedActionProbabilities.getColumns(slices.toIntVector());
            INDArray noiseActionDistribution = reducedValidActionProbabilities
                    .mul(1 - AdversaryLearningConfiguration.dirichletWeight)
                    .add(nextDistribution.mul(AdversaryLearningConfiguration.dirichletWeight));

            nextDistribution.close();

            EnumeratedIntegerDistribution distribution = new EnumeratedIntegerDistribution(validMoveIndices,
                    noiseActionDistribution.toDoubleVector());

            moveAction = distribution.sample();
        }
        return moveAction;
    }


}
