package othello.game;

import othello.othelloAi.OthelloModel;
import szte.mi.Move;
import othello.othelloAi.AiPlayer;

import java.util.Random;

public class Runner {
    public static void main(String[] args) {
        int white = 0;
        int black = 0;
        int rounds = 15;
        Random rnd = new Random();
        OthelloModel game = new OthelloModel();
        AiPlayer p1 = new AiPlayer();
        AiPlayer p2 = new AiPlayer();
        long startTime = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            p1.init(1, 8, rnd);
            p2.init(0, 3, rnd);
            Move last = null;
            while (game.isRunning()) {
                long start = System.currentTimeMillis();
                last = p2.nextMove(last, 0, 0);
                System.out.println("Time of Lisa method execution: " + (((double)System.currentTimeMillis() - (double)start))/1_000_000_000);
                System.out.println(last);
                updateGame(game, last);
                start = System.currentTimeMillis();
                last = p1.nextMove(last, 0, 0);
                System.out.println("Time of Carl method execution: " + (((double)System.currentTimeMillis() - (double)start))/1_000_000_000);
                updateGame(game ,last);
                System.out.println(last);
                System.out.println(game);
            }

            int winner = game.checkStatus();
            if (winner == OthelloModel.PLAYER_BLACK){
                black++;
            } else if (winner == OthelloModel.PLAYER_WHITE) {
                white++;
            }
            game.reset();
        }
        long endTime = System.nanoTime();
        System.out.println("Time of method execution: " + (((double)endTime - (double)startTime))/1_000_000_000);
        System.out.printf("The result is: \nBlack: %d\nWhite: %d%n", black, white);

    }

    private static void updateGame(OthelloModel game, Move move) {
        if (move != null && game.isRunning()) {
            int cellIndex = move.y * OthelloModel.BOARD_SIZE + move.x;
            int[] m = game.getValidMovesIndexes();
            for (int i = 0; i < m.length; i++) {
                if (m[i] == cellIndex) {
                    game.makeMove(i);
                    break;
                }
            }
        } else if (move == null && game.isRunning()) {
            game.makeMove(0);
        }
    }
}
