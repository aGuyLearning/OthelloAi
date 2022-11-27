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

import static othello.AdverserialLearning.AdversaryLearning.*;

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

    public int[] playGames() {

        int numberOfEpisodesPlayer1Starts = AdversaryLearningConfiguration.numberOfGamesToDecideUpdate / 2;
        int numberOfEpisodesPlayer2Starts = AdversaryLearningConfiguration.numberOfGamesToDecideUpdate - numberOfEpisodesPlayer1Starts;

        int player1Wins = 0;
        int player2Wins = 0;
        int draws = 0;

        PlayoutThread[] threads = new PlayoutThread[numberOfEpisodesPlayer1Starts];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new PlayoutThread(player1Policy, player2Policy);
            threads[i].start();
        }
        for (PlayoutThread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        for (PlayoutThread thread : threads) {
            double gameResult = thread.getResult();
            if (gameResult == OthelloModel.PLAYER_WHITE) {

                player1Wins++;

            } else if (gameResult == OthelloModel.PLAYER_BLACK) {

                player2Wins++;

            } else {

                draws++;
            }
        }

        // swap
        ComputationGraph tempPlayerPolicy = player1Policy;
        player1Policy = player2Policy;
        player2Policy = tempPlayerPolicy;


        threads = new PlayoutThread[numberOfEpisodesPlayer2Starts];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new PlayoutThread(player1Policy, player2Policy);
            threads[i].start();
        }
        for (PlayoutThread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        for (PlayoutThread thread : threads) {
            double gameResult = thread.getResult();
            if (gameResult == OthelloModel.PLAYER_WHITE) {

                player1Wins++;

            } else if (gameResult == OthelloModel.PLAYER_BLACK) {

                player2Wins++;

            } else {

                draws++;
            }
        }
        return new int[]{player1Wins, player2Wins, draws};
    }
}
