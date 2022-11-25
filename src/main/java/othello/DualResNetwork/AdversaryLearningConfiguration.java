package othello.DualResNetwork;

import org.apache.commons.lang3.StringUtils;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.schedule.ISchedule;

import java.io.File;
import java.nio.file.Paths;

/**
 * {@link AdversaryLearningConfiguration} defines several configuration parameters
 * affecting the behavior of alpha zero learning.
 * 
 * @author evolutionsoft
 */
public class AdversaryLearningConfiguration {
  public static double learningRate = 1e-4;
  public static ISchedule learningRateSchedule;
  public static int batchSize = 8192;

  public static double dirichletAlpha = 1.1;
  public static double dirichletWeight = 0.45;
  public static boolean alwaysUpdateNeuralNetwork = true;
  public static int numberOfGamesToDecideUpdate = 36;
  public static double gamesWinRatioThresholdNewNetworkUpdate = 0.55;
  public static int numberOfIterationsBeforePotentialUpdate = 10;
  public static int iterationStart = 1;
  public static int numberOfIterations = 250;
  public static int checkPointIterationsFrequency = 50;
  public static int fromNumberOfIterationsTemperatureZero = -1;
  public static int fromNumberOfMovesTemperatureZero = 3;
  public static int maxTrainExamplesHistory = 5000;

  public static String bestModelFileName = "bestmodel.bin";
  public static String trainExamplesFileName = "trainExamples.obj";

  public static double uctConstantFactor = 0.8;
  public static int numberOfMonteCarloSimulations = 30;

  public static int populationSize = 10;

  public static String getAbsoluteModelPathFrom(String modelName) {

    String currentPath = String.valueOf(Paths.get(StringUtils.EMPTY).toAbsolutePath());

    return currentPath + File.separator + modelName;
  }

  public static double getCurrentTemperature(int iteration, int moveNumber) {

    if (getFromNumberOfIterationsTemperatureZero() >= 0 && iteration >= getFromNumberOfIterationsTemperatureZero() ||
            getFromNumberOfMovesTemperatureZero() >= 0 && moveNumber >= getFromNumberOfMovesTemperatureZero()) {
      return 0;
    }

    return AdversaryLearningConstants.ONE;
  }

  private static int getFromNumberOfIterationsTemperatureZero() {
    return fromNumberOfIterationsTemperatureZero;
  }

  private static int getFromNumberOfMovesTemperatureZero() {
    return fromNumberOfMovesTemperatureZero;
  }


}
