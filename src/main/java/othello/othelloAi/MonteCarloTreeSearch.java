package othello.othelloAi;

import java.util.List;
import java.util.Random;

/**
 * Implementation based on:
 * https://www.baeldung.com/java-monte-carlo-tree-search
 */
public class MonteCarloTreeSearch {

    private static final int WIN_SCORE = 10;
    private int level;
    private Random rnd;
    private int opponent;

    public MonteCarloTreeSearch(Random rnd) {
        this.level = 5;
        this.rnd = rnd;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    private int getMillisForCurrentLevel() {
        return 2 * (this.level - 1) + 1;
    }

    public OthelloModel findNextMove(OthelloModel board, int playerNo) {
        long start = System.currentTimeMillis();
        long end = start + 60 * getMillisForCurrentLevel();

        opponent = 3 - playerNo;
        Tree tree = new Tree();
        Node rootNode = tree.getRoot();
        rootNode.getState().setBoard(board);
        rootNode.getState().setPlayerNo(opponent);

        while (System.currentTimeMillis() < end) {
            // Phase 1 - Selection
            Node promisingNode = selectPromisingNode(rootNode);
            // Phase 2 - Expansion
            if (promisingNode.getState().getBoard().isRunning()) {
                expandNode(promisingNode);
            }
            // Phase 3 - Simulation
            Node nodeToExplore = promisingNode;
            if (promisingNode.getChildArray().size() > 0) {
                nodeToExplore = promisingNode.getRandomChildNode();
            }
            int playoutResult = simulateRandomPlayout(nodeToExplore);
            // Phase 4 - Update
            backPropogation(nodeToExplore, playoutResult);
        }

        Node winnerNode = rootNode.getChildWithMaxScore();
        tree.setRoot(winnerNode);
        return winnerNode.getState().getBoard();
    }

    private Node selectPromisingNode(Node rootNode) {
        Node node = rootNode;
        while (node.getChildArray().size() != 0) {
            node = UCT.findBestNodeWithUCT(node);
        }
        return node;
    }

    private void expandNode(Node node) {
        List<State> possibleStates = node.getState().getAllPossibleStates();
        possibleStates.forEach(state -> {
            Node newNode = new Node(state);
            newNode.setParent(node);
            newNode.getState().setPlayerNo(node.getState().getOpponent());
            node.getChildArray().add(newNode);
        });
    }

    private void backPropogation(Node nodeToExplore, int playerNo) {
        Node tmp = nodeToExplore;
        while (tmp != null) {
            tmp.getState().incrementVisit();
            if (tmp.getState().getPlayerNo() == playerNo)
                tmp.getState().addScore(WIN_SCORE);
            tmp = tmp.getParent();
        }
    }

    private int simulateRandomPlayout(Node node) {
        Node tmp = new Node(node);
        State tmpState = tmp.getState();
        int boardStatus = tmpState.getBoard().checkStatus();

        if (boardStatus == opponent) {
            tmp.getParent().getState().setWinScore(Integer.MIN_VALUE);
            return boardStatus;
        }
        while (tmp.getState().getBoard().isRunning()) {
            tmpState.randomPlay();
            boardStatus = tmpState.getBoard().checkStatus();
        }

        return boardStatus;
    }

}