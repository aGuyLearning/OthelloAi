package othello.AdverserialLearning.Lisa;

import szte.mi.Move;

import java.util.ArrayList;
import java.util.Observable;

public class OthelloGameLogic extends Observable {

    private final int[][] directions = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    public boolean gameOver = false;

    public final int PLAYER_BLACK = 1;
    public final int PLAYER_WHITE = 2;

    public int currentPlayer = this.PLAYER_BLACK;

    public int winner = -1;

    private final int size = 8;
    private int[][] board;

    public OthelloGameLogic() {
        this.board = new int[this.size][this.size];
        this.board[3][3] = this.PLAYER_WHITE;
        this.board[4][4] = this.PLAYER_WHITE;
        this.board[3][4] = this.PLAYER_BLACK;
        this.board[4][3] = this.PLAYER_BLACK;
    }

    public int getSize() {
        return size;
    }

    public int[][] getBoard() {
        return board;
    }

    public Move draw(Move move) {
        if (move.x < size && move.y < size && move.x >= 0 && move.y >= 0) {
            if (this.move(move)) {
                //switch player
                this.currentPlayer = (this.currentPlayer == this.PLAYER_BLACK) ? this.PLAYER_WHITE : this.PLAYER_BLACK;
                int status = this.checkGameStatus();
                if (this.gameOver) {
                    this.winner = status;
                }
                this.setChanged();
                this.notifyObservers();
                return move;
            }
        }
        return null;
    }

    private boolean move(Move move) {
        ArrayList<Move> possibleMoves = this.getPossibleMovesOfPlayer(this.currentPlayer);
        if (isInPossibleMoves(possibleMoves, move)) {
            this.board[move.x][move.y] = currentPlayer;
            this.switchSigns(move);
            return true;
        } else {
            //System.out.println("Move not possible: "+move.x+" "+move.y + " by "+ this.currentPlayer);
            //this.printBoard();
            return false;
        }
    }

    private void switchSigns(Move move) {
        ArrayList<Move> toSwitch = this.getAllSwitches(this.currentPlayer, move);
        for (int i = 0; i < toSwitch.size(); i++) {
            this.board[toSwitch.get(i).x][toSwitch.get(i).y] = currentPlayer;
        }
    }

    private boolean isInPossibleMoves(ArrayList<Move> possibleMoves, Move move) {
        for (int i = 0; i < possibleMoves.size(); i++) {
            if (possibleMoves.get(i).x == move.x && possibleMoves.get(i).y == move.y) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<Move> getPossibleMovesOfPlayer(int player) {
        ArrayList<Move> possibleMoves = new ArrayList<>();
        if (!gameOver) {
            Move move;
            for (int i = 0; i < this.size; i++) {
                for (int j = 0; j < this.size; j++) {
                    move = new Move(i, j);
                    if (this.board[i][j] == 0 && (getAllSwitches(player, move)).size() > 0) {
                        possibleMoves.add(move);
                    }
                }
            }
        }
        return possibleMoves;
    }

    //return-values:
    //0: draw, 1: Player 1 wins, 2: Player 2 wins, -1: game continues
    public int checkGameStatus() {
        int otherPlayer = this.currentPlayer == this.PLAYER_BLACK ? this.PLAYER_WHITE : this.PLAYER_BLACK;
        if (!isMovePossible(currentPlayer)) {
            //System.out.println("es musste gepasst werden: "+this.currentPlayer);
            if (!isMovePossible(otherPlayer)) {
                this.gameOver = true;
                return this.isWinner();
            } else {
                this.currentPlayer = otherPlayer;
                return -1;
            }
        } else {
            return -1;
        }
    }

    private int isWinner() {
        if (this.gameOver) {
            int signsBlack = this.getResultForPlayer(this.PLAYER_BLACK);
            int signsWhite = this.getResultForPlayer(this.PLAYER_WHITE);
            if (signsBlack > signsWhite) {
                return 1;
            } else if (signsWhite > signsBlack) {
                return 2;
            } else {
                return 0;
            }
        }
        //should not happen!
        return -1;
    }

    public int getResultForPlayer(int player) {
        int counter = 0;
        for (int i = 0; i < this.size; i++) {
            for (int j = 0; j < this.size; j++) {
                if (this.board[i][j] == player) {
                    counter++;
                }
            }
        }
        return counter;
    }

    private boolean isMovePossible(int player) {
        return getPossibleMovesOfPlayer(player).size() > 0;
    }

    public ArrayList<Move> getAllSwitches(int player, Move move) {
        ArrayList<Move> switches = new ArrayList<>();
        for (int i = 0; i < this.directions.length; i++) {
            switches.addAll(getSwitchesForDirection(player, move, directions[i][0], directions[i][1]));
        }
        return switches;
    }


    private ArrayList<Move> getSwitchesForDirection(int player, Move move, int xDirection, int yDirection) {
        ArrayList<Move> switches = new ArrayList<>();
        ArrayList<Move> otherPlayerSigns = new ArrayList<>();
        int otherPlayer = player == this.PLAYER_BLACK ? this.PLAYER_WHITE : this.PLAYER_BLACK;

        //loop stops as soon as it would leave the board
        for (int i = 1;
             (move.y + i * yDirection < size) && //top
                     (move.y + i * yDirection >= 0) &&  //bottom
                     (move.x + i * xDirection < size) && //right
                     (move.x + i * xDirection >= 0) //left
                ; i++) {
            int currentSign = board[move.x + i * xDirection][move.y + i * yDirection];

            if (currentSign == player) {
                switches.addAll(otherPlayerSigns);
                //next player sign reached
                return switches;
            } else if (currentSign == 0) {
                return new ArrayList<>();
            } else if (currentSign == otherPlayer) {
                otherPlayerSigns.add(new Move(move.x + i * xDirection, move.y + i * yDirection));
            }
        }
        return new ArrayList<>();
    }

    public void printBoard() {
        for (int i = 0; i < this.size; i++) {
            for (int j = 0; j < this.size; j++) {
                System.out.print(board[i][j] + " ");
            }
            System.out.println();
        }
        System.out.println();
        System.out.println();
    }

    public OthelloGameLogic clone(){
        OthelloGameLogic clone = new OthelloGameLogic();
        clone.gameOver = this.gameOver;
        clone.currentPlayer = this.currentPlayer;
        clone.winner = this.winner;
        for (int i = 0; i < clone.board.length; i++) {
            clone.board[i] = this.board[i].clone();
        }
        return clone;
    }
}
