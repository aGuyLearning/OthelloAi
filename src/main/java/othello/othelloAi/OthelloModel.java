package othello.othelloAi;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;

import java.util.*;

public class OthelloModel {
    public static final int PLAYER_BLACK = 1;
    public static final int PLAYER_WHITE = -1;
    /**
     * Number of squares in the game
     */
    public static final int NUM_SQUARES = 64;
    /**
     * Unicode character for a black disc.
     */
    private static final char BLACK_STONE = '\u25C9';
    /**
     * Unicode character for a white disc.
     */
    private static final char WHITE_STONE = '\u25CE';
    /**
     * Bitboard of the black stones at the start of the game.
     */
    private static final long INIT_BLACK_BB = 34628173824L;
    /**
     * Bitboard of the white stones at the start of the game.
     */
    private static final long INIT_WHITE_BB = 68853694464L;
    /**
     * Bitboard of the legal moves at the start of the game. (For the black player)
     */
    private static final Long INIT_LEGAL_BB = 17729692631040L;
    public static final int BOARD_SIZE = 8;
    // X  X  X  X  X  X  X  -
    // X  X  X  X  X  X  X  -
    // X  X  X  X  X  X  X  -
    // X  X  X  X  X  X  X  -
    // X  X  X  X  X  X  X  -
    // X  X  X  X  X  X  X  -
    // X  X  X  X  X  X  X  -
    // X  X  X  X  X  X  X  -
    public static final long RIGHT_MASK = 9187201950435737471L;

    // -  X  X  X  X  X  X  X
    // -  X  X  X  X  X  X  X
    // -  X  X  X  X  X  X  X
    // -  X  X  X  X  X  X  X
    // -  X  X  X  X  X  X  X
    // -  X  X  X  X  X  X  X
    // -  X  X  X  X  X  X  X
    // -  X  X  X  X  X  X  X
    public static final long LEFT_MASK = -72340172838076674L;

    // -  -  -  -  -  -  -  -
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    public static final long UP_MASK = -256L;

    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // -  -  -  -  -  -  -  -
    public static final long DOWN_MASK = 72057594037927935L;

    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    // X  X  X  X  X  X  X  X
    public static final long PASS = -1L;
    public static final INDArray ZEROS_PLAYGROUND_IMAGE = Nd4j.zeros(1, BOARD_SIZE, BOARD_SIZE);
    public static final INDArray ONES_PLAYGROUND_IMAGE = Nd4j.ones(1, BOARD_SIZE, BOARD_SIZE);
    public static final INDArray MINUS_ONES_PLAYGROUND_IMAGE = ZEROS_PLAYGROUND_IMAGE.sub(1);
    /**
     * Bitboard for the black discs.
     */
    private long blackBB;

    /**
     * Bitboard of the white discs.
     */
    private long whiteBB;

    /**
     * Bitboard for the currentPlayer.
     */
    private long legal;
    private boolean movesArrayUpdated = false;
    private long[] movesArray = null;
    private final long[] allCellsArray = new long[NUM_SQUARES];
    private final long[] tmpCellsArray = new long[NUM_SQUARES];
    private int tmpCellsCount = 0;
    private boolean gameOver;
    private int currentPlayer;
    private int round;
    long lastCellChanged;

    public OthelloModel() {
        this.reset();
    }


    public void makeMove(int move) {
        this.round++;
        int nMoves = getNumMoves();
        long[] movesArray = getBitMovesArray();

        if (move < 0 || move >= nMoves)
            throw new IllegalArgumentException("Wrong move: " + move);

        long theMove = movesArray[move];

        if (theMove != PASS) {
            this.lastCellChanged = getValidMoveIndices()[move];
            long next; // potential moves
            long lastCell;
            long oppBoard = opponentBoard();
            long curBoard = currentBoard();
            setCurrentBoard(currentBoard() | theMove); // place the new stone on the board
            int allCellsCount = 0;

            // UP
            lastCell = 0L;
            tmpCellsCount = 0;
            next = (theMove >> BOARD_SIZE) & DOWN_MASK & oppBoard;

            while (next != 0L) {
                tmpCellsArray[tmpCellsCount++] = next;
                long tmp = (next >> BOARD_SIZE) & DOWN_MASK;
                lastCell = tmp & curBoard;
                next = tmp & oppBoard;
            }

            if (lastCell != 0L)
                for (int i = 0; i < tmpCellsCount; i++)
                    allCellsArray[allCellsCount++] = tmpCellsArray[i];

            // DOWN
            lastCell = 0L;
            tmpCellsCount = 0;
            next = (theMove << BOARD_SIZE) & UP_MASK & oppBoard;

            while (next != 0L) {
                tmpCellsArray[tmpCellsCount++] = next;
                long tmp = (next << BOARD_SIZE) & UP_MASK;
                lastCell = tmp & curBoard;
                next = tmp & oppBoard;
            }

            if (lastCell != 0L) {
                for (int i = 0; i < tmpCellsCount; i++) {
                    allCellsArray[allCellsCount++] = tmpCellsArray[i];
                }
            }

            // LEFT
            lastCell = 0L;
            tmpCellsCount = 0;
            next = (theMove >> 1L) & RIGHT_MASK & oppBoard;

            while (next != 0L) {
                tmpCellsArray[tmpCellsCount++] = next;
                long tmp = (next >> 1L) & RIGHT_MASK;
                lastCell = tmp & curBoard;
                next = tmp & oppBoard;
            }

            if (lastCell != 0L) {
                for (int i = 0; i < tmpCellsCount; i++) {
                    allCellsArray[allCellsCount++] = tmpCellsArray[i];
                }
            }

            // RIGHT
            lastCell = 0L;
            tmpCellsCount = 0;
            next = (theMove << 1L) & LEFT_MASK & oppBoard;

            while (next != 0L) {
                tmpCellsArray[tmpCellsCount++] = next;
                long tmp = (next << 1L) & LEFT_MASK;
                lastCell = tmp & curBoard;
                next = tmp & oppBoard;
            }

            if (lastCell != 0L)
                for (int i = 0; i < tmpCellsCount; i++)
                    allCellsArray[allCellsCount++] = tmpCellsArray[i];

            // TOP LEFT
            lastCell = 0L;
            tmpCellsCount = 0;
            next = (theMove >> (BOARD_SIZE + 1L)) & RIGHT_MASK & DOWN_MASK & oppBoard;

            while (next != 0L) {
                tmpCellsArray[tmpCellsCount++] = next;
                long tmp = (next >> (BOARD_SIZE + 1L)) & RIGHT_MASK & DOWN_MASK;
                lastCell = tmp & curBoard;
                next = tmp & oppBoard;
            }

            if (lastCell != 0L)
                for (int i = 0; i < tmpCellsCount; i++)
                    allCellsArray[allCellsCount++] = tmpCellsArray[i];

            // TOP RIGHT
            lastCell = 0L;
            tmpCellsCount = 0;
            next = (theMove >> (BOARD_SIZE - 1L)) & LEFT_MASK & DOWN_MASK & oppBoard;

            while (next != 0L) {
                tmpCellsArray[tmpCellsCount++] = next;
                long tmp = (next >> (BOARD_SIZE - 1L)) & LEFT_MASK & DOWN_MASK;
                lastCell = tmp & curBoard;
                next = tmp & oppBoard;
            }

            if (lastCell != 0L)
                for (int i = 0; i < tmpCellsCount; i++)
                    allCellsArray[allCellsCount++] = tmpCellsArray[i];

            // DOWN LEFT
            lastCell = 0L;
            tmpCellsCount = 0;
            next = (theMove << (BOARD_SIZE - 1L)) & RIGHT_MASK & UP_MASK & oppBoard;

            while (next != 0L) {
                tmpCellsArray[tmpCellsCount++] = next;
                long tmp = (next << (BOARD_SIZE - 1L)) & RIGHT_MASK & UP_MASK;
                lastCell = tmp & curBoard;
                next = tmp & oppBoard;
            }

            if (lastCell != 0L)
                for (int i = 0; i < tmpCellsCount; i++)
                    allCellsArray[allCellsCount++] = tmpCellsArray[i];

            // DOWN RIGHT
            lastCell = 0L;
            tmpCellsCount = 0;
            next = (theMove << (BOARD_SIZE + 1L)) & LEFT_MASK & UP_MASK & oppBoard;

            while (next != 0L) {
                tmpCellsArray[tmpCellsCount++] = next;
                long tmp = (next << (BOARD_SIZE + 1L)) & LEFT_MASK & UP_MASK;
                lastCell = tmp & curBoard;
                next = tmp & oppBoard;
            }

            if (lastCell != 0L)
                for (int i = 0; i < tmpCellsCount; i++)
                    allCellsArray[allCellsCount++] = tmpCellsArray[i];

            // flip the stones
            for (int i = 0; i < allCellsCount; i++) {
                setCurrentBoard(currentBoard() | allCellsArray[i]);
                setOpponentBoard(opponentBoard() & ~allCellsArray[i]);
            }
        }

        currentPlayer = this.getOpponent();
        calculateMoves();

        if (Long.bitCount(legal) == 0) {
            currentPlayer = this.getOpponent();
            calculateMoves();

            if (Long.bitCount(legal) == 0)
                gameOver = true;
            else
                legal = PASS;

            currentPlayer = this.getOpponent();
        }
    }

    private void calculateMoves() {
        this.legal = 0L;
        long potentialMoves;
        long curBoard = currentBoard();
        long oppBoard = opponentBoard();
        long emptyBoard = emptyBoard();

        // UP
        potentialMoves = (curBoard >> BOARD_SIZE) & DOWN_MASK & oppBoard;

        while (potentialMoves != 0L) {
            long tmp = (potentialMoves >> BOARD_SIZE) & DOWN_MASK;
            legal |= tmp & emptyBoard;
            potentialMoves = tmp & oppBoard;
        }

        // DOWN
        potentialMoves = (curBoard << BOARD_SIZE) & UP_MASK & oppBoard;

        while (potentialMoves != 0L) {
            long tmp = (potentialMoves << BOARD_SIZE) & UP_MASK;
            legal |= tmp & emptyBoard;
            potentialMoves = tmp & oppBoard;
        }

        // LEFT
        potentialMoves = (curBoard >> 1L) & RIGHT_MASK & oppBoard;

        while (potentialMoves != 0L) {
            long tmp = (potentialMoves >> 1L) & RIGHT_MASK;
            legal |= tmp & emptyBoard;
            potentialMoves = tmp & oppBoard;
        }

        // RIGHT
        potentialMoves = (curBoard << 1L) & LEFT_MASK & oppBoard;

        while (potentialMoves != 0L) {
            long tmp = (potentialMoves << 1L) & LEFT_MASK;
            legal |= tmp & emptyBoard;
            potentialMoves = tmp & oppBoard;
        }

        // UP LEFT
        potentialMoves = (curBoard >> (BOARD_SIZE + 1L)) & RIGHT_MASK & DOWN_MASK & oppBoard;

        while (potentialMoves != 0L) {
            long tmp = (potentialMoves >> (BOARD_SIZE + 1L)) & RIGHT_MASK & DOWN_MASK;
            legal |= tmp & emptyBoard;
            potentialMoves = tmp & oppBoard;
        }

        // UP RIGHT
        potentialMoves = (curBoard >> (BOARD_SIZE - 1L)) & LEFT_MASK & DOWN_MASK & oppBoard;

        while (potentialMoves != 0L) {
            long tmp = (potentialMoves >> (BOARD_SIZE - 1L)) & LEFT_MASK & DOWN_MASK;
            legal |= tmp & emptyBoard;
            potentialMoves = tmp & oppBoard;
        }

        // DOWN LEFT
        potentialMoves = (curBoard << (BOARD_SIZE - 1L)) & RIGHT_MASK & UP_MASK & oppBoard;

        while (potentialMoves != 0L) {
            long tmp = (potentialMoves << (BOARD_SIZE - 1L)) & RIGHT_MASK & UP_MASK;
            legal |= tmp & emptyBoard;
            potentialMoves = tmp & oppBoard;
        }

        // DOWN RIGHT
        potentialMoves = (curBoard << (BOARD_SIZE + 1L)) & LEFT_MASK & UP_MASK & oppBoard;

        while (potentialMoves != 0L) {
            long tmp = (potentialMoves << (BOARD_SIZE + 1L)) & LEFT_MASK & UP_MASK;
            this.legal |= tmp & emptyBoard;
            potentialMoves = tmp & oppBoard;
        }

        movesArrayUpdated = false;
    }

    private long emptyBoard() {
        return ~(blackBB | whiteBB);
    }

    private long currentBoard() {
        return currentPlayer == PLAYER_BLACK ? blackBB : whiteBB;
    }

    private long opponentBoard() {
        return currentPlayer == PLAYER_BLACK ? whiteBB : blackBB;
    }

    private void setCurrentBoard(long bitboard) {
        if (currentPlayer == PLAYER_BLACK)
            blackBB = bitboard;
        else
            whiteBB = bitboard;
    }

    private void setOpponentBoard(long bitboard) {
        if (currentPlayer == PLAYER_BLACK)
            whiteBB = bitboard;
        else
            blackBB = bitboard;
    }

    public void reset() {
        currentPlayer = 1;
        gameOver = false;
        blackBB = INIT_BLACK_BB;
        whiteBB = INIT_WHITE_BB;
        legal = INIT_LEGAL_BB;
        this.round = 0;
        // caching
        movesArrayUpdated = false;
    }

    public String gameStatus() {

        if (!gameOver)
            return "The game is yet to be decided!";

        int blackStonesCount = Long.bitCount(blackBB);
        int whiteStonesCount = Long.bitCount(whiteBB);

        if (blackStonesCount > whiteStonesCount)
            return "Black Won!";

        if (whiteStonesCount > blackStonesCount)
            return "White Won!";

        return "The Game Ended In A Draw!";

    }

    public double checkStatus() {
        if (!gameOver)
            return -1;

        int blackStonesCount = Long.bitCount(blackBB);
        int whiteStonesCount = Long.bitCount(whiteBB);

        if (blackStonesCount > whiteStonesCount)
            return PLAYER_BLACK;

        if (whiteStonesCount > blackStonesCount)
            return PLAYER_WHITE;

        return 0.5;
    }

    public String getCurrentPlayer() {
        if (this.currentPlayer == PLAYER_BLACK) {
            return "BLACK";
        } else {
            return "WHITE";
        }
    }

    public int getOpponent() {
        if (this.currentPlayer == PLAYER_BLACK) {
            return PLAYER_WHITE;
        } else {
            return PLAYER_BLACK;
        }
    }

    public String getSquareColor(int squareIndex) {

        // blackBB stone in the cell
        if ((blackBB & (1L << squareIndex)) != 0L)
            return "#000000";

        // whiteBB stone in the cell
        if ((whiteBB & (1L << squareIndex)) != 0L)
            return "#ffffff";

        // no stones in the cell
        return "#808080";

    }

    private long[] getBitMovesArray() {
        if (movesArray == null)
            movesArray = new long[NUM_SQUARES];

        if (!movesArrayUpdated) {
            if (legal == PASS)
                movesArray[0] = PASS;
            else
                for (int i = 0, count = 0; i < NUM_SQUARES; i++)
                    if ((legal & (1L << i)) != 0L)
                        movesArray[count++] = 1L << i;

            movesArrayUpdated = true;
        }

        return movesArray;
    }

    public OthelloModel copy() {
        OthelloModel newOthello = new OthelloModel();
        newOthello.currentPlayer = currentPlayer;
        newOthello.gameOver = gameOver;
        newOthello.blackBB = blackBB;
        newOthello.whiteBB = whiteBB;
        newOthello.legal = legal;
        newOthello.round = round;

        return newOthello;
    }

    public int getNumMoves() {
        return legal == PASS ? 1 : Long.bitCount(legal);
    }

    @Override
    public String toString() {
        return getGameInfo() +
                getColumnHeaders() +
                getBoardStr() +
                getColumnHeaders();
    }

    private String getColumnHeaders() {
        StringBuilder builder = new StringBuilder("   ");

        for (int col = 0; col < BOARD_SIZE; col++)
            builder.append((" " + OthelloUtil.colToChar(col) + " "));

        builder.append("\n");

        return builder.toString();
    }

    private char cellToChar(int cellIndex) {
        if ((blackBB & (1L << cellIndex)) != 0)
            return BLACK_STONE;
        else if ((whiteBB & (1L << cellIndex)) != 0)
            return WHITE_STONE;
        else if ((legal & (1L << cellIndex)) != 0)
            return 'x';

        return '-';
    }

    private String getBoardStr() {
        StringBuilder builder = new StringBuilder();

        for (int cellIndex = 0; cellIndex < NUM_SQUARES; cellIndex++) {
            if (cellIndex % BOARD_SIZE == 0)
                builder.append((" " + OthelloUtil.cellToRowNum(cellIndex) + " "));

            builder.append((" " + cellToChar(cellIndex) + " "));

            if (cellIndex % BOARD_SIZE == BOARD_SIZE - 1)
                builder.append((" " + OthelloUtil.cellToRowNum(cellIndex) + " \n"));
        }

        return builder.toString();
    }

    private String getGameInfo() {
        return String.format("Turn: %s\n", curPlayerStr()) +
                String.format("Black count: %d\n", Long.bitCount(blackBB)) +
                String.format("White count: %d\n", Long.bitCount(whiteBB)) +
                ("Legal moves: " + Arrays.toString(getMoves()) + "\n\n");
    }

    public String[] getMoves() {
        List<String> othelloMoves = new ArrayList<>();
        long[] bitMovesArray = getBitMovesArray();
        int nMoves = getNumMoves();

        if (nMoves == 1 && bitMovesArray[0] == PASS)
            othelloMoves.add("PASS");
        else
            for (int i = 0; i < nMoves; i++) {
                int cellIndex = Long.numberOfTrailingZeros(bitMovesArray[i]);
                othelloMoves.add(OthelloUtil.cellToStr(cellIndex));
            }

        return othelloMoves.toArray(new String[0]);
    }

    public int[] getValidMoveIndices() {
        List<Integer> othelloMoves = new ArrayList<>();
        long[] bitMovesArray = getBitMovesArray();
        int nMoves = getNumMoves();

        if (nMoves == 1 && bitMovesArray[0] == PASS)
            return new int[0];
        else
            for (int i = 0; i < nMoves; i++) {
                int cellIndex = Long.numberOfTrailingZeros(bitMovesArray[i]);
                othelloMoves.add(cellIndex);
            }

        return othelloMoves.stream().mapToInt(i -> i).toArray();
    }

    private String curPlayerStr() {
        return currentPlayer == PLAYER_BLACK ? "Black" : "White";
    }

    public boolean isRunning() {
        return !gameOver;
    }

    public long getLastCellChanged() {
        return lastCellChanged;
    }

    public int toPlay() {
        return this.currentPlayer;
    }

    public boolean isPass() {
        String[] moves = getMoves();
        if (moves.length == 0) {
            return true;
        } else {
            return Objects.equals(getMoves()[0], "PASS");
        }
    }

    public int getRound() {
        return round;
    }

    public INDArray processedBoard(){
        int[][][][] processed = new int[1][3][BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < NUM_SQUARES; i++) {
            int row = i / BOARD_SIZE;
            int col = i - row * BOARD_SIZE;

            processed[0][1][row][col] = (blackBB & (1L << i)) != 0L ? 1:0;
            processed[0][2][row][col] = (whiteBB & (1L << i)) != 0L ? 1:0;

        }
        INDArray newBoard = Nd4j.createFromArray(processed);
        if (PLAYER_WHITE == currentPlayer) {

            newBoard.get(NDArrayIndex.point(0)).put(new INDArrayIndex[]{NDArrayIndex.point(0)}, ONES_PLAYGROUND_IMAGE);
        } else {

            newBoard.get(NDArrayIndex.point(0)).put(new INDArrayIndex[]{NDArrayIndex.point(0)}, MINUS_ONES_PLAYGROUND_IMAGE);
        }
        return newBoard;
    }

    public INDArray validMovesMask() {
        int[] valid = this.getValidMoveIndices();
        INDArray mask = Nd4j.zeros(1, NUM_SQUARES);
        for (int index: valid) {
            mask.putScalar(index, 1);
        }
        return mask;
    }
}

