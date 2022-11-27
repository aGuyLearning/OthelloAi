package othello.othelloAi.mcts;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import othello.AdverserialLearning.AdversaryLearningConfiguration;
import othello.othelloAi.OthelloModel;

import java.util.Random;

/**
 * Implementation based on:
 * https://www.baeldung.com/java-monte-carlo-tree-search
 */
public class MonteCarloTreeSearch {

    private int level;
    private Random rnd;
    private ComputationGraph model;

    Node rootNode;

    public MonteCarloTreeSearch(Random rnd, ComputationGraph model) {
        this.level = 2;
        this.rnd = rnd;
        this.model = model;

    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    private int getMillisForCurrentLevel() {
        return 2 * (this.level - 1) + 1;
    }

    public INDArray findNextMove(OthelloModel board, int temperature) {
        //long start = System.currentTimeMillis();
        //long end = start + 60 * getMillisForCurrentLevel();


        int playout = 0; // Todo: only for development and training
        int[] validMoves = board.getValidMoveIndices();
        if (validMoves.length == 0){
            return board.validMovesMask();
        }
        this.rootNode = new Node(-1, board.getOpponent(), 0, 1.0, 0.5, null);
        while (playout < AdversaryLearningConfiguration.numberOfMonteCarloSimulations) {
            OthelloModel copy = board.copy();
            simulateRandomPlayout(rootNode, copy);
            playout ++;

        }

        int[] visitedCounts = new int[validMoves.length];
        int maxVisitedCounts = 0;

        for (int index = 0; index < validMoves.length; index++) {

            if (this.rootNode.containsChildMoveIndex(index)) {

                visitedCounts[index] = this.rootNode.getChildWithMoveIndex(index).timesVisited;
                if (visitedCounts[index] > maxVisitedCounts) {

                    maxVisitedCounts = visitedCounts[index];
                }
            }
        }

        INDArray moveProbabilities = Nd4j.zeros(OthelloModel.NUM_SQUARES);

        // random play during exploartion phase
        if (0 == temperature) {

            INDArray visitedCountsArray = Nd4j.createFromArray(visitedCounts);

            INDArray visitedCountsMaximums = Nd4j.where(visitedCountsArray.gte(visitedCountsArray.amax(0).getNumber(0)), null, null)[0];

            visitedCountsArray.close();

            moveProbabilities.putScalar(
                    visitedCountsMaximums.getInt(
                            rnd.nextInt((int) visitedCountsMaximums.length())), 1);

            return moveProbabilities;
        }

        INDArray softmaxParameters = Nd4j.zeros(OthelloModel.NUM_SQUARES);
        for (int i = 0; i < validMoves.length; i++) {
            softmaxParameters.putScalar(validMoves[i], (1 / temperature) * Math.log(visitedCounts[i] + 1e-8));
        }

        double maxSoftmaxParameter = softmaxParameters.maxNumber().doubleValue();

        for (int i = 0; i < validMoves.length; i++) {

            double softmaxParameter = softmaxParameters.getDouble(i);

            moveProbabilities.putScalar(i, Math.exp(softmaxParameter - maxSoftmaxParameter));
        }

        moveProbabilities = moveProbabilities.div(moveProbabilities.sumNumber());
        return moveProbabilities;
    }

    private void simulateRandomPlayout(Node node, OthelloModel game) {
        // Selection
        if (node.isExpanded()) {
            node = node.getChildWithMaxScore();
            game.makeMove(node.lastMove);
        }
        // Simulation
        INDArray[] neuralNetOutput = this.model.output(game.processedBoard());

        INDArray actionProbabilities = neuralNetOutput[0];
        double leafValue = neuralNetOutput[1].getDouble(0);

        INDArray validActionProbabilities = actionProbabilities.mul(game.validMovesMask());
        validActionProbabilities = validActionProbabilities.div(validActionProbabilities.sumNumber());
        if (!game.isRunning()) {

            double endResult = game.checkStatus();

            leafValue = endResult;
            if (OthelloModel.PLAYER_BLACK == node.lastMoveColor) {

                leafValue = 1 - leafValue;
            }
        }
        // Update
        node.backpropagation(1 - leafValue);
        // Expansion
        if (game.isRunning()) {
            node.expand(game, validActionProbabilities);
        }
    }

}