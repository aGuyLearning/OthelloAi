package othello.game;

public class OthelloUtil {
    private static final int BOARD_SIZE = 8;
    public static String cellToStr(int cellIndex){
        int row = cellIndex / 8;
        int col = cellIndex % 8;
        return String.format("(%d, %d)", row, col);
    }

    public static String cellToRowNum(int cellIndex) {
        return String.valueOf(cellIndex / BOARD_SIZE);
    }

    public static String colToChar(int col) {
        return String.valueOf(col);
    }
}
