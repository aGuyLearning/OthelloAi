package othello.AdverserialLearning;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.util.ModelSerializer;
import org.deeplearning4j.util.NetworkUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import othello.othelloAi.OthelloModel;
import othello.othelloAi.OthelloUtil;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * {@link AdversaryLearning} is the main class to perform alpha zero learning.
 * It uses game results from 0 to 1 instead of -1 to 1 compared with other implementations.
 * <p>
 * This affects also the residual net, where a sigmoid activation instead of a tanh is
 * used for the expected value output head.
 *
 * @author evolutionsoft
 */
public class AdversaryLearning {

    public static final double DRAW_VALUE = 0.5;
    public static final double DRAW_WEIGHT = 0.5;

    public static final int SEVEN_DIGITS = 7;

    public static final String TEMPMODEL_NAME = "tempmodel.bin";

    public static final Logger log = LoggerFactory.getLogger(AdversaryLearning.class);

    Map<INDArray, AdversaryTrainingExample> trainExamplesHistory = new HashMap<>();

    OthelloModel initialGame;

    ComputationGraph computationGraph;
    ComputationGraph previousComputationGraph;

    boolean restoreTrainingExamples;

    boolean restoreTrainedNeuralNet;

    public AdversaryLearning(OthelloModel game, ComputationGraph computationGraph) {

        this.initialGame = game;
        this.computationGraph = computationGraph;
        this.restoreTrainingExamples = AdversaryLearningConfiguration.iterationStart > 1;
        this.restoreTrainedNeuralNet = AdversaryLearningConfiguration.iterationStart > 1;
    }

    public void performLearning() throws IOException {
        loadComputationGraphs();
        loadEarlierTrainingExamples(AdversaryLearningConfiguration.trainExamplesFileName);

        for (int iteration = AdversaryLearningConfiguration.iterationStart;
             iteration < AdversaryLearningConfiguration.iterationStart +
                     AdversaryLearningConfiguration.numberOfIterations;
             iteration++) {

            // start episode playout in different Threads
            List<AdversaryTrainingExample>[] trainingExamples = new List[AdversaryLearningConfiguration.numberOfIterationsBeforePotentialUpdate];
            EpisodeThread[] threads = new EpisodeThread[AdversaryLearningConfiguration.numberOfIterationsBeforePotentialUpdate];

            for (int i = 0; i < threads.length; i++) {
                threads[i] = new EpisodeThread(i, trainingExamples, this.computationGraph, iteration);
                threads[i].start();
            }
            log.info("Running {} episodes of iteration {}", AdversaryLearningConfiguration.numberOfIterationsBeforePotentialUpdate, iteration);
            // wait for threads to compute intermediate results
            for (EpisodeThread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            for (List<AdversaryTrainingExample> episodeExample : trainingExamples) {
                replaceOldTrainingExamplesWithNewActionProbabilities(episodeExample);
            }
            log.info("Saving {} examples", this.trainExamplesHistory.size());
            saveTrainExamplesHistory();
            boolean updateAfterBetterPlayout = updateNeuralNet();

            if (AdversaryLearningConfiguration.alwaysUpdateNeuralNetwork ||
                    updateAfterBetterPlayout) {

                log.info("Accepting new model");
                String absoluteBestModelPath =
                        AdversaryLearningConfiguration.getAbsoluteModelPathFrom(AdversaryLearningConfiguration.bestModelFileName);
                ModelSerializer.writeModel(computationGraph,
                        absoluteBestModelPath,
                        true);

                log.info("Write new model {}", absoluteBestModelPath);
            }

            createCheckpoint(iteration);

            log.info("Iteration {} ended", iteration);
        }
    }

    public void loadComputationGraphs() throws IOException {
        if (restoreTrainedNeuralNet) {

            String absoluteBestModelPath =
                    AdversaryLearningConfiguration.getAbsoluteModelPathFrom(AdversaryLearningConfiguration.bestModelFileName);
            this.computationGraph = ModelSerializer.restoreComputationGraph(absoluteBestModelPath, true);
            this.computationGraph.setLearningRate(AdversaryLearningConfiguration.learningRate);
            log.info("restored model {}", absoluteBestModelPath);

            if (!AdversaryLearningConfiguration.alwaysUpdateNeuralNetwork) {

                this.previousComputationGraph = ModelSerializer.restoreComputationGraph(absoluteBestModelPath, true);
                this.previousComputationGraph.setLearningRate(AdversaryLearningConfiguration.learningRate);
                log.info("restored temp model from {}", absoluteBestModelPath);
            }
        }
    }

    public Map<INDArray, AdversaryTrainingExample> loadEarlierTrainingExamples(String trainExamplesFile) throws IOException {

        if (restoreTrainingExamples) {

            try (ObjectInputStream trainExamplesInput = new ObjectInputStream(new FileInputStream(trainExamplesFile))) {

                Object readObject = trainExamplesInput.readObject();
                if (readObject instanceof List<?>) {

                    List<AdversaryTrainingExample> storedExamples = (List<AdversaryTrainingExample>) readObject;

                    for (AdversaryTrainingExample currentItem : storedExamples) {

                        this.trainExamplesHistory.put(currentItem.getBoard(), currentItem);
                    }

                } else if (readObject instanceof Map<?, ?>) {

                    this.trainExamplesHistory = (Map<INDArray, AdversaryTrainingExample>) readObject;
                }

                log.info("Restored train examples from {} with {} train examples",
                        trainExamplesFile,
                        this.trainExamplesHistory.size());

            } catch (ClassNotFoundException e) {
                log.warn(
                        "Train examples from trainExamples.obj could not be restored. Continue with empty train examples history.",
                        e);
            }
        }

        return this.trainExamplesHistory;
    }

    void replaceOldTrainingExamplesWithNewActionProbabilities(List<AdversaryTrainingExample> newExamples) {

        for (AdversaryTrainingExample currentExample : newExamples) {

            this.trainExamplesHistory.put(currentExample.getBoard(), currentExample);
        }
    }

    boolean updateNeuralNet() throws IOException {

        List<AdversaryTrainingExample> trainExamples = new ArrayList<>(this.trainExamplesHistory.values());
        Collections.shuffle(trainExamples);

        boolean updateAfterBetterPlayout = false;
        if (!AdversaryLearningConfiguration.alwaysUpdateNeuralNetwork) {

            String absoluteTempModelPath = AdversaryLearningConfiguration.getAbsoluteModelPathFrom(TEMPMODEL_NAME);
            ModelSerializer.writeModel(computationGraph, absoluteTempModelPath, true);

            log.info("Write temp model {}", absoluteTempModelPath);

            this.previousComputationGraph = ModelSerializer.restoreComputationGraph(absoluteTempModelPath, true);

            this.computationGraph = this.fitNeuralNet(this.computationGraph, trainExamples);

            log.info("Challenge new model version with previous model in {} games", AdversaryLearningConfiguration.numberOfGamesToDecideUpdate);

            AdversaryAgentDriver adversaryAgentDriver = new AdversaryAgentDriver(
                    this.previousComputationGraph,
                    this.computationGraph);

            int[] gameResults = adversaryAgentDriver.playGames();

            log.info("New model wins {} / prev model wins {} / draws {}", gameResults[1], gameResults[0], gameResults[2]);

            double newModelWinDrawRatio = (gameResults[1] + DRAW_WEIGHT * gameResults[2])
                    / (gameResults[0] + gameResults[1] + DRAW_WEIGHT * gameResults[2]);
            updateAfterBetterPlayout = newModelWinDrawRatio > AdversaryLearningConfiguration.gamesWinRatioThresholdNewNetworkUpdate;

            log.info("New model win/draw ratio against previous model is {} vs configured threshold {}",
                    newModelWinDrawRatio,
                    AdversaryLearningConfiguration.gamesWinRatioThresholdNewNetworkUpdate);

            if (!updateAfterBetterPlayout) {

                log.info("Rejecting new model");
                this.computationGraph = ModelSerializer.restoreComputationGraph(absoluteTempModelPath, true);

                log.info("Restored best model from {}", absoluteTempModelPath);
            }

        } else {

            this.computationGraph = this.fitNeuralNet(this.computationGraph, trainExamples);
        }

        return updateAfterBetterPlayout;
    }

    void createCheckpoint(int iteration) throws IOException {

        StringBuilder prependedZeros = prependZeros(iteration);

        if (0 == iteration % AdversaryLearningConfiguration.checkPointIterationsFrequency) {

            String bestModelPath = AdversaryLearningConfiguration.getAbsoluteModelPathFrom(AdversaryLearningConfiguration.bestModelFileName);
            ModelSerializer.writeModel(computationGraph, bestModelPath.substring(0, bestModelPath.length() - ".bin".length()) + prependedZeros + iteration + ".bin", true);
            saveTrainExamplesHistory(iteration);
        }
    }

    void saveTrainExamplesHistory() throws IOException {

        this.resizeTrainExamplesHistory();

        String trainExamplesPath = AdversaryLearningConfiguration.getAbsoluteModelPathFrom(
                AdversaryLearningConfiguration.trainExamplesFileName);

        try (ObjectOutputStream trainExamplesOutput = new ObjectOutputStream(
                new FileOutputStream(trainExamplesPath))) {

            trainExamplesOutput.writeObject(trainExamplesHistory);

        }
    }

    void saveTrainExamplesHistory(int iteration) throws IOException {

        this.resizeTrainExamplesHistory();

        StringBuilder prependedZeros = prependZeros(iteration);
        String trainExamplesPath = AdversaryLearningConfiguration.getAbsoluteModelPathFrom(
                AdversaryLearningConfiguration.trainExamplesFileName);

        try (ObjectOutputStream trainExamplesOutput = new ObjectOutputStream(
                new FileOutputStream(trainExamplesPath.substring(0, trainExamplesPath.length() - ".obj".length()) + prependedZeros + iteration + ".obj"))) {

            trainExamplesOutput.writeObject(trainExamplesHistory);

        }
    }

    void resizeTrainExamplesHistory() {

        if (AdversaryLearningConfiguration.maxTrainExamplesHistory >=
                this.trainExamplesHistory.size()) {

            return;
        }

        Comparator<AdversaryTrainingExample> byIterationDescending =
                (AdversaryTrainingExample firstExample, AdversaryTrainingExample secondExample) ->
                        secondExample.getIteration() - firstExample.getIteration();
        this.trainExamplesHistory =
                this.trainExamplesHistory.entrySet().stream().
                        sorted(Entry.comparingByValue(byIterationDescending)).
                        limit(AdversaryLearningConfiguration.maxTrainExamplesHistory).
                        collect(Collectors.toMap(
                                Entry::getKey, Entry::getValue, (e1, e2) -> e1, HashMap::new));
    }

    static StringBuilder prependZeros(int iteration) {

        int prependingZeros = SEVEN_DIGITS - String.valueOf(iteration).length();

        StringBuilder prependedZeros = new StringBuilder();
        prependedZeros.append("0".repeat(Math.max(0, prependingZeros)));
        return prependedZeros;
    }

    static ComputationGraph fitNeuralNet(ComputationGraph computationGraph, List<AdversaryTrainingExample> trainingExamples) {

        int batchSize = AdversaryLearningConfiguration.batchSize;
        int trainingExamplesSize = trainingExamples.size();
        int batchNumber = 1 + trainingExamplesSize / batchSize;

        List<MultiDataSet> batchedMultiDataSet = createMiniBatchList(trainingExamples);

        for (int batchIteration = 0; batchIteration < batchNumber; batchIteration++) {

            computationGraph.fit(batchedMultiDataSet.get(batchIteration));

            if (0 == batchIteration && batchNumber > batchIteration + 1) {

                log.info("Batch size for {} batches from computation graph model {}",
                        batchNumber - 1,
                        computationGraph.batchSize());

            } else if (batchNumber == batchIteration + 1) {

                log.info("{}. batch size from computation graph model {}",
                        batchIteration + 1,
                        computationGraph.batchSize());
            }
        }

        log.info("Learning rate from computation graph model layer 'OutputLayer': {}",
                NetworkUtils.getLearningRate(computationGraph, "policy_head_output_"));

        return computationGraph;
    }

    static List<MultiDataSet> createMiniBatchList(List<AdversaryTrainingExample> trainingExamples) {

        int batchSize = AdversaryLearningConfiguration.batchSize;
        int trainingExamplesSize = trainingExamples.size();
        int batchNumber = 1 + trainingExamplesSize / batchSize;
        if (0 == trainingExamplesSize % batchSize) {
            batchNumber--;
        }

        long[] gameInputBoardStackShape = {3, 8, 8};

        List<MultiDataSet> batchedMultiDataSet = new LinkedList<>();

        for (int currentBatch = 0; currentBatch < batchNumber; currentBatch++) {

            INDArray inputBoards = Nd4j.zeros(batchSize, gameInputBoardStackShape[0], gameInputBoardStackShape[1],
                    gameInputBoardStackShape[2]);
            INDArray probabilitiesLabels = Nd4j.zeros(batchSize, OthelloModel.NUM_SQUARES);
            INDArray valueLabels = Nd4j.zeros(batchSize, 1);

            if (currentBatch >= batchNumber - 1) {

                int lastBatchSize = trainingExamplesSize % batchSize;
                inputBoards = Nd4j.zeros(lastBatchSize, gameInputBoardStackShape[0], gameInputBoardStackShape[1],
                        gameInputBoardStackShape[2]);
                probabilitiesLabels = Nd4j.zeros(lastBatchSize, OthelloModel.NUM_SQUARES);
                valueLabels = Nd4j.zeros(lastBatchSize, 1);
            }

            for (int batchExample = 0, exampleNumber = currentBatch * batchSize;
                 exampleNumber < (currentBatch + 1) * batchSize && exampleNumber < trainingExamplesSize;
                 exampleNumber++, batchExample++) {

                AdversaryTrainingExample currentTrainingExample = trainingExamples.get(exampleNumber);
                inputBoards.put(new INDArrayIndex[]{NDArrayIndex.point(batchExample)}, currentTrainingExample.getBoard());

                INDArray actionIndexProbabilities = Nd4j.zeros(OthelloModel.NUM_SQUARES);
                INDArray trainingExampleActionProbabilities = currentTrainingExample.getActionIndexProbabilities();

                // TODO review simplification by always having getNumberOfAllAvailableMoves
                if (actionIndexProbabilities.shape()[0] > trainingExampleActionProbabilities.shape()[0]) {

                    // Leave remaining moves at the end with 0, only pass at numberOfSquares in Go
                    for (int i = 0; i < trainingExampleActionProbabilities.shape()[0]; i++) {
                        actionIndexProbabilities.putScalar(i, trainingExampleActionProbabilities.getDouble(i));
                    }

                } else if (actionIndexProbabilities.shape()[0] < currentTrainingExample.getActionIndexProbabilities()
                        .shape()[0]) {

                    throw new IllegalArgumentException(
                            "Training example has more action than maximally specified by game.getNumberOfAllAvailableMoves()\n"
                                    + "Max specified shape is " + actionIndexProbabilities.shape()[0] + " versus training example "
                                    + currentTrainingExample.getActionIndexProbabilities());

                } else {

                    // Shapes do match
                    actionIndexProbabilities = trainingExampleActionProbabilities;
                }

                probabilitiesLabels.putRow(batchExample, actionIndexProbabilities);

                valueLabels.putRow(batchExample, Nd4j.zeros(1).putScalar(0, currentTrainingExample.getCurrentPlayerValue()));
            }

            batchedMultiDataSet.add(new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[]{inputBoards},
                    new INDArray[]{probabilitiesLabels, valueLabels}));
        }
        return batchedMultiDataSet;
    }
}


