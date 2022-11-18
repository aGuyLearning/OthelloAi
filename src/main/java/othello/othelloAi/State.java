package othello.othelloAi;

import java.util.ArrayList;
import java.util.List;
public class State {
    private OthelloModel board;
    private int playerNo;
    private int visitCount;
    private double winScore;

    public State() {
        board = new OthelloModel();
    }

    public State(State state) {
        this.board = state.getBoard().copy();
        this.playerNo = state.getPlayerNo();
        this.visitCount = state.getVisitCount();
        this.winScore = state.getWinScore();
    }

    public State(OthelloModel board) {
        this.board = board.copy();
    }

    OthelloModel getBoard() {
        return board;
    }

    void setBoard(OthelloModel board) {
        this.board = board;
    }

    int getPlayerNo() {
        return playerNo;
    }

    void setPlayerNo(int playerNo) {
        this.playerNo = playerNo;
    }

    int getOpponent() {
        return board.getOpponent();
    }

    public int getVisitCount() {
        return visitCount;
    }

    public void setVisitCount(int visitCount) {
        this.visitCount = visitCount;
    }

    double getWinScore() {
        return winScore;
    }

    void setWinScore(double winScore) {
        this.winScore = winScore;
    }

    public List<State> getAllPossibleStates() {
        List<State> possibleStates = new ArrayList<>();
        int[] availablePositions = this.board.getIndexMoves();
        for (int i = 0; i < availablePositions.length; i++) {
            State newState = new State(this.board);
            newState.getBoard().makeMove(i);
            possibleStates.add(newState);
        }
        return possibleStates;
    }

    void incrementVisit() {
        this.visitCount++;
    }

    void addScore(double score) {
        if (this.winScore != Integer.MIN_VALUE) {
            this.winScore += score;
        }
    }

    void randomPlay() {
        int[] availablePositions = this.board.getIndexMoves();
        int totalPossibilities = availablePositions.length;
        int selectRandom = (int) (Math.random() * totalPossibilities);
        this.board.makeMove(selectRandom);
    }
}