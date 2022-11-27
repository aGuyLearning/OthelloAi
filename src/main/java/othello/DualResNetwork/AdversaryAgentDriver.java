package othello.DualResNetwork;

import cc.mallet.types.Dirichlet;
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import othello.othelloAi.AiPlayer;
import othello.othelloAi.OthelloModel;
import othello.othelloAi.mcts.MonteCarloTreeSearch;
import szte.mi.Move;

import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static othello.DualResNetwork.AdversaryLearning.*;

/**
 * {@link AdversaryAgentDriver} is only relevant if {@link AdversaryLearningConfiguration} has alwaysUpdateNeuralNetwork = false.
 * In that case, a configured number of games and win rate decide if the alpha zero network gets updated with newest version of
 * the neural net, a {@link ComputationGraph} here.
 * 
 * @author evolutionsoft
 */
public class AdversaryAgentDriver {

  ComputationGraph player1Policy;
  ComputationGraph player2Policy;
  
  public AdversaryAgentDriver(ComputationGraph player1, ComputationGraph player2) {
    
    this.player1Policy = player1;
    this.player2Policy = player2;
  }

  public int[] playGames(OthelloModel game) {
    
    int numberOfEpisodesPlayer1Starts = AdversaryLearningConfiguration.numberOfGamesToDecideUpdate / 2;
    int numberOfEpisodesPlayer2Starts = AdversaryLearningConfiguration.numberOfGamesToDecideUpdate - numberOfEpisodesPlayer1Starts;
    
    int player1Wins = 0;
    int player2Wins = 0;
    int draws = 0;
    
    for (int gameNumber = 1; gameNumber <= numberOfEpisodesPlayer1Starts; gameNumber++) {
      double gameResult = this.playGame(game.copy());
      
      if (gameResult == OthelloModel.PLAYER_BLACK) {
        
        player1Wins++;
      
      } else if (gameResult == OthelloModel.PLAYER_WHITE) {
        
        player2Wins++;
      
      } else {
        
        draws++;
      }
    }
    
    ComputationGraph tempPlayerPolicy = player1Policy;
    player1Policy = player2Policy;
    player2Policy = tempPlayerPolicy;

    for (int gameNumber = 1; gameNumber <= numberOfEpisodesPlayer2Starts; gameNumber++) {
      double gameResult = this.playGame(game.copy());
      
      if (gameResult == OthelloModel.PLAYER_WHITE) {
        
        player1Wins++;
      
      } else if (gameResult == OthelloModel.PLAYER_BLACK) {
        
        player2Wins++;
      
      } else {
        
        draws++;
      }
    }
    
    return new int[] {player1Wins, player2Wins, draws};
  }
  
  public double playGame(OthelloModel game) {
    
    MonteCarloTreeSearch player1 = new MonteCarloTreeSearch(new Random(), player1Policy);
    MonteCarloTreeSearch player2 = new MonteCarloTreeSearch(new Random(), player2Policy);

    int currentPlayer = OthelloModel.PLAYER_BLACK;

    int[] emptyFields = game.getValidMoveIndices();

    while (game.isRunning()) {
      System.out.println(game);
      INDArray actionProbabilities = Nd4j.zeros(game.getNumberOfAllAvailableMoves());
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
        moveAction = AdversaryLearningConstants.randomGenerator.nextInt(emptyFields.length);
        game.makeMove(moveAction);
      }
      else{
        if (moveAction == -1){
          game.makeMove(0);
        }
        else {
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

      return OthelloModel.PLAYER_BLACK;

    } else if (endResult < 0.5) {
      
      return OthelloModel.PLAYER_WHITE;
    }
    
    return DRAW_VALUE;
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

}
