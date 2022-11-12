package othello.game;

public class Runner {
    public static void main(String[] args) {
        OthelloModel game = new OthelloModel();
        long startTime = System.nanoTime();
        while(game.isRunning()) {
            game.makeMove(0);
        }
        long endTime = System.nanoTime();
        System.out.println("Time of method execution: " + (endTime - startTime));

        System.out.println(game);
    }
}
