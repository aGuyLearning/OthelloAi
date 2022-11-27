package othello.AdverserialLearning;

import cc.mallet.types.Dirichlet;
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import othello.othelloAi.OthelloModel;
import othello.othelloAi.mcts.MonteCarloTreeSearch;

import java.util.Random;
import java.util.stream.IntStream;

import static othello.AdverserialLearning.AdversaryLearning.DRAW_VALUE;

public class PlayoutThread extends Thread {
    private OthelloModel game;

    private ComputationGraph player1Policy;
    private ComputationGraph player2Policy;

    double result;

    public PlayoutThread(ComputationGraph player1Policy, ComputationGraph player2Policy) {
        this.game = new OthelloModel();
        this.player1Policy = player1Policy;
        this.player2Policy = player2Policy;
    }

    @Override
    public void run() {

        MonteCarloTreeSearch player1 = new MonteCarloTreeSearch(new Random(), player1Policy);
        MonteCarloTreeSearch player2 = new MonteCarloTreeSearch(new Random(), player2Policy);

        int currentPlayer = OthelloModel.PLAYER_BLACK;

        int[] emptyFields = game.getValidMoveIndices();

        while (game.isRunning()) {
            INDArray actionProbabilities = Nd4j.zeros(OthelloModel.NUM_SQUARES);
            if (currentPlayer == OthelloModel.PLAYER_BLACK) {
                actionProbabilities = player1.findNextMove(game, 0);

            } else if (currentPlayer == OthelloModel.PLAYER_WHITE) {

                actionProbabilities = player2.findNextMove(game, 0);
            }
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
            emptyFields = game.getValidMoveIndices();
            currentPlayer = game.toPlay();
        }

        double endResult = game.checkStatus();
        if (endResult > 0.5) {

            result = OthelloModel.PLAYER_BLACK;

        } else if (endResult < 0.5) {

            result = OthelloModel.PLAYER_WHITE;
        } else {
            result = DRAW_VALUE;
        }
    }

    public double getResult() {
        return result;
    }

    private static int chooseNewMoveAction(int[] validMoveIndices, INDArray normalizedActionProbabilities) {

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
