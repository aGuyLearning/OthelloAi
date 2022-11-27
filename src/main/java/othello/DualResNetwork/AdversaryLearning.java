package othello.DualResNetwork;

import cc.mallet.types.Dirichlet;
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
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
import othello.othelloAi.mcts.MonteCarloTreeSearch;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * {@link AdversaryLearning} is the main class to perform alpha zero learning.
 * It uses game results from 0 to 1 instead of -1 to 1 compared with other implementations.
 * 
 * This affects also the residual net, where a sigmoid activation instead of a tanh is
 * used for the expected value output head.
 * 
 * @author evolutionsoft
 */
public class AdversaryLearning {

  public static final double DRAW_VALUE = 0.5;
  public static final double DRAW_WEIGHT = 0.5;
  public static final double MAX_WIN = AdversaryLearningConstants.ONE;
  public static final double MIN_WIN = AdversaryLearningConstants.ZERO;

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
      for (int episode = 1; episode <= AdversaryLearningConfiguration.numberOfIterationsBeforePotentialUpdate; episode++) {
        List<AdversaryTrainingExample> newExamples = this.executeEpisode(iteration);
        replaceOldTrainingExamplesWithNewActionProbabilities(newExamples);

        log.info("Episode {}-{} ended, train examples {}", iteration, episode, this.trainExamplesHistory.size());
      }
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

  public List<AdversaryTrainingExample> executeEpisode(int iteration) {
    List<AdversaryTrainingExample> trainExamples = new ArrayList<>();

    OthelloModel currentGame = this.initialGame.copy();
    int currentPlayer = OthelloModel.PLAYER_BLACK;
    int moveNumber = 1;

    MonteCarloTreeSearch player = new MonteCarloTreeSearch(new Random(), this.computationGraph);

    while (currentGame.isRunning()) {
      System.out.println(currentGame);
      INDArray validMask = currentGame.validMovesMask();
      int[] validMoveIndices = currentGame.getValidMoveIndices();

      INDArray actionProbabilities = player.findNextMove(currentGame,
              AdversaryLearningConfiguration.getCurrentTemperature(iteration, moveNumber));
      INDArray validActionProbabilities = actionProbabilities.mul(validMask);
      INDArray normalizedActionProbabilities = validActionProbabilities.div(Nd4j.sum(actionProbabilities));

      List<AdversaryTrainingExample> newTrainingExamples =
              createNewTrainingExamplesWithSymmetries(iteration, currentGame.processedBoard(), currentPlayer,
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

    return trainExamples;
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

      int[] gameResults = adversaryAgentDriver.playGames(this.initialGame);

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

  List<AdversaryTrainingExample> createNewTrainingExamplesWithSymmetries(int iteration,
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
  int chooseNewMoveAction(int[] validMoveIndices, INDArray normalizedActionProbabilities) {

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

  void handleGameEnded(List<AdversaryTrainingExample> trainExamples, OthelloModel currentGame, int currentPlayer) {

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

  StringBuilder prependZeros(int iteration) {

    int prependingZeros = SEVEN_DIGITS - String.valueOf(iteration).length();

    StringBuilder prependedZeros = new StringBuilder();
    for (int n = 1; n <= prependingZeros; n++) {
      prependedZeros.append('0');
    }
    return prependedZeros;
  }

  ComputationGraph fitNeuralNet(ComputationGraph computationGraph, List<AdversaryTrainingExample> trainingExamples) {

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

  List<MultiDataSet> createMiniBatchList(List<AdversaryTrainingExample> trainingExamples) {

    int batchSize = AdversaryLearningConfiguration.batchSize;
    int trainingExamplesSize = trainingExamples.size();
    int batchNumber = 1 + trainingExamplesSize / batchSize;
    if (0 == trainingExamplesSize % batchSize) {
      batchNumber--;
    }

    long[] gameInputBoardStackShape = {3,8,8};

    List<MultiDataSet> batchedMultiDataSet = new LinkedList<>();

    for (int currentBatch = 0; currentBatch < batchNumber; currentBatch++) {

      INDArray inputBoards = Nd4j.zeros(batchSize, gameInputBoardStackShape[0], gameInputBoardStackShape[1],
              gameInputBoardStackShape[2]);
      INDArray probabilitiesLabels = Nd4j.zeros(batchSize, initialGame.getNumberOfAllAvailableMoves());
      INDArray valueLabels = Nd4j.zeros(batchSize, 1);

      if (currentBatch >= batchNumber - 1) {

        int lastBatchSize = trainingExamplesSize % batchSize;
        inputBoards = Nd4j.zeros(lastBatchSize, gameInputBoardStackShape[0], gameInputBoardStackShape[1],
                gameInputBoardStackShape[2]);
        probabilitiesLabels = Nd4j.zeros(lastBatchSize, initialGame.getNumberOfAllAvailableMoves());
        valueLabels = Nd4j.zeros(lastBatchSize, 1);
      }

      for (int batchExample = 0, exampleNumber = currentBatch * batchSize;
           exampleNumber < (currentBatch + 1) * batchSize && exampleNumber < trainingExamplesSize;
           exampleNumber++, batchExample++) {

        AdversaryTrainingExample currentTrainingExample = trainingExamples.get(exampleNumber);
        inputBoards.put(new INDArrayIndex[]{NDArrayIndex.point(batchExample)}, currentTrainingExample.getBoard());

        INDArray actionIndexProbabilities = Nd4j.zeros(initialGame.getNumberOfAllAvailableMoves());
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

      batchedMultiDataSet.add( new org.nd4j.linalg.dataset.MultiDataSet(new INDArray[] { inputBoards },
              new INDArray[] { probabilitiesLabels, valueLabels }));
    }
    return batchedMultiDataSet;
  }
}

