package othello.game;

import othello.gui.Move;

import java.util.*;

public class OthelloModel extends Observable {
    private static final int PLAYER_BLACK = 0;
    private static final int PLAYER_WHITE = 1;
    private static final int NEUTRAL_FIELD = 3;
    private static final Map<Integer, String> figures = Map.of(NEUTRAL_FIELD, " ", PLAYER_BLACK, "B", PLAYER_WHITE, "W");
    private static final int[][] directions = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    public static final int BOARD_SIZE = 8;
    private final List[] playerMoves;
    protected int[][] board;
    private int round;
    private int state;
    private int currentPlayer;

    public OthelloModel() {
        this.playerMoves = new List<>[2];
        this.playerMoves[0] = new ArrayList<>();
        this.playerMoves[1] = new ArrayList<>();
        this.reset();
    }

    public boolean move(int row, int col) {
        ArrayList<Move> flips = getFlips(row, col);
        if (flips.isEmpty() || this.state != -1) {
            return false;
        }
        flips.add(new Move(row, col));
        // flip the fields
        for (Move flip : flips) {
            board[flip.x][flip.y] = currentPlayer;
        }
        this.setupNextTurn();
        this.setChanged();
        this.notifyObservers(flips);
        this.clearChanged();
        return true;
    }

    private void playerMoves() {
        ArrayList<Move> flips = new ArrayList<>();
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (!getFlips(i, j).isEmpty()) {
                    flips.add(new Move(i, j));
                }

            }
        }
        this.playerMoves[currentPlayer].clear();
        this.playerMoves[currentPlayer].addAll(flips);
    }

    private ArrayList<Move> flipsInDirection(int row, int col, int rowDir, int colDir) {
        ArrayList<Move> toBeFlipped = new ArrayList<>();
        int currentRow = row + rowDir;
        int currentCol = col + colDir;
        if (currentRow == 8 || currentRow < 0 || currentCol == 8 || currentCol < 0) {
            return toBeFlipped;
        }
        while (this.isSet(currentRow, currentCol)) {
            if (board[currentRow][currentCol] == this.currentPlayer) {
                while (!(row == currentRow && col == currentCol)) {
                    if (board[currentRow][currentCol] != this.currentPlayer) {
                        toBeFlipped.add(new Move(currentRow, currentCol));
                    }
                    currentRow = currentRow - rowDir;
                    currentCol = currentCol - colDir;
                }
                break;
            } else {
                currentRow = currentRow + rowDir;
                currentCol = currentCol + colDir;
            }

            if (currentRow < 0 || currentCol < 0 || currentRow == 8 || currentCol == 8) {
                break;
            }
        }
        return toBeFlipped;
    }


    private ArrayList<Move> getFlips(int row, int col) {
        ArrayList<Move> flips = new ArrayList<>();
        // is it on the board
        if (!this.isOnField(row, col)) {
            return flips;
        }
        // is the field set already
        if (this.board[row][col] != NEUTRAL_FIELD) {
            return flips;
        }


        for (int[] dir : directions) {
            flips.addAll(this.flipsInDirection(row, col, dir[0], dir[1]));
        }
        return flips;
    }

    private void setupNextTurn() {
        this.round++;
        this.nextPlayer();
        this.playerMoves();
        if (this.playerMoves[currentPlayer].isEmpty()){
            System.out.println("player had to pass");
            this.nextPlayer();
            this.playerMoves();
            if (this.playerMoves[currentPlayer].isEmpty()){
                this.selectWinner();
            }
        }
        if (round == 64) {
            this.selectWinner();
        }
    }

    private void selectWinner(){
        if (this.countFieldsOfColor(PLAYER_BLACK) > this.countFieldsOfColor(PLAYER_WHITE))
            this.state = PLAYER_BLACK;
        else if (this.countFieldsOfColor(PLAYER_BLACK) < this.countFieldsOfColor(PLAYER_WHITE)) {
            this.state = PLAYER_WHITE;
        } else {
            this.state = 2;
        }
    }
    public void reset() {
        this.board = new int[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                this.board[i][j] = 3;
            }
        }
        this.board[3][3] = PLAYER_WHITE;
        this.board[3][4] = PLAYER_BLACK;
        this.board[4][3] = PLAYER_BLACK;
        this.board[4][4] = PLAYER_WHITE;
        this.round = 0;
        this.state = -1;
        this.currentPlayer = PLAYER_BLACK;
        this.playerMoves[0].clear();
        this.playerMoves[1].clear();
        this.playerMoves();

    }

    public String gameStatus() {
        return switch (this.state) {
            case -1 -> "The game is still going!";
            case PLAYER_BLACK -> "Black won the game!";
            case PLAYER_WHITE -> "White won the game!";
            case 2 -> " The game ended in a draw!";
            default -> "Something went wrong";
        };
    }

    public void printBoard() {
        StringBuilder result = new StringBuilder();
        for (int[] row : this.board) {
            for (int value : row) {
                result.append(figures.get(value)).append(" | ");
            }
            result = new StringBuilder(result.substring(0, result.length() - 2));
            result.append("\n");
        }
        System.out.println(result);
    }

    public String getCurrentPlayerLabel() {
        if (this.currentPlayer == PLAYER_BLACK){
            return "BLACK";
        }
        else{
            return "WHITE";
        }
    }

    private boolean isOnField(int row, int col) {
        return row < BOARD_SIZE && row >= 0 && col < BOARD_SIZE && col >= 0;
    }

    private boolean isSet(int row, int col) {
        return this.isOnField(row, col) && this.board[row][col] != NEUTRAL_FIELD;

    }

    public String getFieldColor(int row, int col) {
        return switch (this.board[row][col]) {
            case PLAYER_BLACK -> "#000000";
            case PLAYER_WHITE -> "#ffffff";
            default -> "#808080";
        };

    }

    private void nextPlayer() {
            this.currentPlayer = this.currentPlayer == PLAYER_BLACK ? PLAYER_WHITE : PLAYER_BLACK;
    }

    public List<Move> getPossibleMovesCurrentPlayer () {
        return this.playerMoves[this.currentPlayer];
    }

    private int countFieldsOfColor(int player) {
        int counter = 0;
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == player){
                    counter ++;
                }
            }
        }
        return counter;
    }
}
