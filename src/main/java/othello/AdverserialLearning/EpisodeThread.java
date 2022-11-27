package othello.AdverserialLearning;

import cc.mallet.types.Dirichlet;
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import othello.othelloAi.OthelloModel;
import othello.othelloAi.OthelloUtil;
import othello.othelloAi.mcts.MonteCarloTreeSearch;

import java.util.*;

import static othello.AdverserialLearning.AdversaryLearning.DRAW_VALUE;

public class EpisodeThread extends  Thread{
    private int id;
    private List<AdversaryTrainingExample>[] results;
    private ComputationGraph computationGraph;

    private int iteration;

    public EpisodeThread(int id, List<AdversaryTrainingExample>[] results, ComputationGraph computationGraph, int iteration) {
        this.id = id;
        this.results = results;
        this.computationGraph = computationGraph;
        this.iteration = iteration;
    }

    @Override
    public void run() {
        List<AdversaryTrainingExample> trainExamples = new ArrayList<>();

        OthelloModel currentGame = new OthelloModel();
        int currentPlayer = OthelloModel.PLAYER_BLACK;
        int moveNumber = 1;

        MonteCarloTreeSearch player = new MonteCarloTreeSearch(new Random(), this.computationGraph);

        while (currentGame.isRunning()) {
            INDArray validMask = currentGame.validMovesMask();
            int[] validMoveIndices = currentGame.getValidMoveIndices();

            INDArray actionProbabilities = player.findNextMove(currentGame,
                    AdversaryLearningConfiguration.getCurrentTemperature(this.iteration, moveNumber));
            INDArray validActionProbabilities = actionProbabilities.mul(validMask);
            INDArray normalizedActionProbabilities = validActionProbabilities.div(Nd4j.sum(actionProbabilities));

            List<AdversaryTrainingExample> newTrainingExamples =
                    createNewTrainingExamplesWithSymmetries(this.iteration, currentGame.processedBoard(), currentPlayer,
                            normalizedActionProbabilities);

            trainExamples.removeAll(newTrainingExamples);
            trainExamples.addAll(newTrainingExamples);

            // make move in internal representation
            int moveAction = chooseNewMoveAction(validMoveIndices, normalizedActionProbabilities);
            if (moveAction == -1){
                currentGame.makeMove(0);
            }
            else {
                for (int i = 0; i < validMoveIndices.length; i++) {
                    if (moveAction == validMoveIndices[i]) {
                        currentGame.makeMove(i);
                        break;
                    }
                }
            }
            moveNumber++;

            if (!currentGame.isRunning()) {
                handleGameEnded(trainExamples, currentGame, currentPlayer);
            }

            currentPlayer = currentGame.toPlay();
        }

        results[id] = trainExamples;
    }
    static int chooseNewMoveAction(int[] validMoveIndices, INDArray normalizedActionProbabilities) {

        int moveAction;
        if (validMoveIndices.length == 0) {
            moveAction = -1;
        } else if (validMoveIndices.length == 1) {
            moveAction = validMoveIndices[0                                                                                 ];
        } else {

            double alpha =AdversaryLearningConfiguration.dirichletAlpha;
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

    private static void handleGameEnded(List<AdversaryTrainingExample> trainExamples, OthelloModel currentGame, int currentPlayer) {

        double gameResult = currentGame.checkStatus();

        if (gameResult != DRAW_VALUE) {

            if (currentPlayer == OthelloModel.PLAYER_WHITE) {

                gameResult = 1 - gameResult;
            }

            for (AdversaryTrainingExample trainExample : trainExamples) {

                trainExample.setCurrentPlayerValue(
                        (float) (trainExample.getCurrentPlayer() == currentPlayer ? gameResult : 1 - gameResult));
            }
        } else {

            for (AdversaryTrainingExample trainExample : trainExamples) {

                trainExample.setCurrentPlayerValue((float) DRAW_VALUE);
            }
        }
    }

    private static synchronized List<AdversaryTrainingExample> createNewTrainingExamplesWithSymmetries(int iteration,
                                                                                               INDArray currentBoard, int currentPlayer, INDArray normalizedActionProbabilities) {

        List<AdversaryTrainingExample> newTrainingExamples = new ArrayList<>();

        AdversaryTrainingExample trainingExample = new AdversaryTrainingExample(currentBoard, currentPlayer,
                normalizedActionProbabilities, iteration);

        newTrainingExamples.add(trainingExample);

        List<AdversaryTrainingExample> symmetries = OthelloUtil.getSymmetries(currentBoard.dup(),
                normalizedActionProbabilities.dup(), currentPlayer, iteration);

        Set<AdversaryTrainingExample> addedSymmetries = new HashSet<>();
        addedSymmetries.add(trainingExample);
        for (AdversaryTrainingExample symmetryExample : symmetries) {

            if (!addedSymmetries.contains(symmetryExample)) {
                newTrainingExamples.add(symmetryExample);
                addedSymmetries.add(symmetryExample);
            }
        }

        return newTrainingExamples;
    }

}
