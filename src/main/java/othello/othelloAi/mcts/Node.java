package othello.othelloAi.mcts;

import org.nd4j.linalg.api.ndarray.INDArray;
import othello.othelloAi.OthelloModel;

import java.util.*;


import static othello.AdverserialLearning.AdversaryLearning.DRAW_VALUE;


public class Node {
    static double UctConstant = 1.5;
    int lastMove;

    int depth;

    int lastMoveColor;

    int timesVisited = 0;

    double qValue = DRAW_VALUE;

    double uValue = 0;

    double moveProbability;
    private Node parent;
    private final List<Node> childArray = new ArrayList<>();

    public Node(
            int lastMove,
            int lastMoveColor,
            int depth,
            double moveProbability,
            double initialQ,
            Node parent){

        this.parent = parent;
        this.qValue = initialQ;
        this.depth = depth;
        this.lastMove = lastMove;
        this.moveProbability = moveProbability;
        this.lastMoveColor = lastMoveColor;
    }


    public Node getParent() {
        return parent;
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    public List<Node> getChildArray() {
        return childArray;
    }

    public Node getRandomChildNode() {
        int noOfPossibleMoves = this.childArray.size();
        int selectRandom = (int) (Math.random() * noOfPossibleMoves);
        return this.childArray.get(selectRandom);
    }

    void expand(OthelloModel game, INDArray previousActionProbabilities) {

        for (int i = 0; i < game.getNumMoves(); i++) {
            this.childArray.add(
                    i,
                    new Node(
                            i,
                            game.getOpponent(),
                            this.depth + 1,
                            previousActionProbabilities.getDouble(i),
                            1 - this.qValue,
                            this));
        }


    }

    public boolean isExpanded() {

        return !this.childArray.isEmpty();
    }

    public double getValue(double cpUct) {

        this.uValue =
                cpUct * this.moveProbability *
                        Math.sqrt(this.parent.timesVisited) / (1 + this.timesVisited);

        return this.qValue + this.uValue;
    }

    public Node getChildWithMaxScore() {
        return Collections.max(this.childArray, Comparator.comparing(c -> c.getValue(UctConstant)));
    }
    public void update(double newValue) {

        this.timesVisited++;

        this.qValue += (newValue - this.qValue) / (this.timesVisited);

    }

    public void backpropagation (double newValue) {


        if (null != this.parent) {

            this.parent.backpropagation(1 - newValue);
        }

        this.update(newValue);

    }

    public Node getChildWithMoveIndex(int moveIndex) {

        return this.childArray.get(moveIndex);
    }

    public boolean containsChildMoveIndex(int moveIndex) {

        return this.childArray.size() > moveIndex;
    }
}


