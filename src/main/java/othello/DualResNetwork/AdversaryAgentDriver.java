package othello.DualResNetwork;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import othello.othelloAi.OthelloModel;

import static othello.DualResNetwork.AdversaryLearning.MAX_WIN;
import static othello.DualResNetwork.AdversaryLearning.MIN_WIN;

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

  public int[] playGames(OthelloModel game, AdversaryLearningConfiguration configuration) {
    
    int numberOfEpisodesPlayer1Starts = AdversaryLearningConfiguration.numberOfGamesToDecideUpdate / 2;
    int numberOfEpisodesPlayer2Starts = AdversaryLearningConfiguration.numberOfGamesToDecideUpdate - numberOfEpisodesPlayer1Starts;
    
    int player1Wins = 0;
    int player2Wins = 0;
    int draws = 0;
    
    for (int gameNumber = 1; gameNumber <= numberOfEpisodesPlayer1Starts; gameNumber++) {
      
      double gameResult = this.playGame(game.copy(), configuration);
      
      if (gameResult >= MAX_WIN) {
        
        player1Wins++;
      
      } else if (gameResult <= MIN_WIN) {
        
        player2Wins++;
      
      } else {
        
        draws++;
      }
    }
    
    ComputationGraph tempPlayerPolicy = player1Policy;
    player1Policy = player2Policy;
    player2Policy = tempPlayerPolicy;

    for (int gameNumber = 1; gameNumber <= numberOfEpisodesPlayer2Starts; gameNumber++) {
      
      double gameResult = this.playGame(game.createNewInstance(), configuration);
      
      if (gameResult <= MIN_WIN) {
        
        player1Wins++;
      
      } else if (gameResult >= MAX_WIN) {
        
        player2Wins++;
      
      } else {
        
        draws++;
      }
    }
    
    return new int[] {player1Wins, player2Wins, draws};
  }
  
  public double playGame(OthelloModel game, AdversaryLearningConfiguration configuration) {
    
    MonteCarloTreeSearch player1 = new MonteCarloTreeSearch(this.player1Policy, configuration);
    MonteCarloTreeSearch player2 = new MonteCarloTreeSearch(this.player2Policy, configuration);
    
    int[] emptyFields = game.getValidMoveIndices();
    
    int currentPlayer = OthelloModel.PLAYER_BLACK;

    while (game.isRunning()) {
    
      INDArray moveActionValues = Nd4j.zeros(game.getNumMoves());
      if (currentPlayer == OthelloModel.PLAYER_BLACK) {
        
        moveActionValues = player1.getActionValues(game, 0);
        
      } else if (currentPlayer == OthelloModel.PLAYER_WHITE) {
        
        moveActionValues = player2.getActionValues(game, 0);
      }
      
      int moveAction = moveActionValues.argMax(0).getInt(0);

      if (!emptyFields.contains(moveAction)) {
        // Todo: select random move
      }
      
      game.makeMove(moveAction);
      emptyFields = game.getValidMoveIndices();
      currentPlayer = game.toPlay();
    }
    
    double endResult = game.getEndResult(currentPlayer);
    if (endResult > 0.5) {

      return MAX_WIN;    

    } else if (endResult < 0.5) {
      
      return MIN_WIN;
    }
    
    return DRAW_VALUE;
  }
}
