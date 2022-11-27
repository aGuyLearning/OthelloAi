package othello.othelloAi;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.INDArrayIndex;
import org.nd4j.linalg.indexing.NDArrayIndex;
import othello.AdverserialLearning.AdversaryTrainingExample;

import java.util.ArrayList;
import java.util.List;

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

    static synchronized INDArray rotateBoard90(INDArray playgroundRotation) {

        INDArray boardEmptyRotation = playgroundRotation.slice(0);
        INDArray maxPlayerRotation = rotate90(playgroundRotation.slice(1));
        INDArray minPlayerRotation = rotate90(playgroundRotation.slice(2));

        return createNewBoard(boardEmptyRotation, maxPlayerRotation, minPlayerRotation);
    }

    static synchronized INDArray createNewBoard(INDArray newEmptyBoardPart, INDArray newMaxPlayerBoardPart, INDArray newMinPlayerBoardPart) {

        INDArray newPlaygroundRotation = Nd4j.create(3, BOARD_SIZE, BOARD_SIZE);
        newPlaygroundRotation.put(new INDArrayIndex[]{NDArrayIndex.point(0)}, newEmptyBoardPart);
        newPlaygroundRotation.put(new INDArrayIndex[]{NDArrayIndex.point(1)}, newMaxPlayerBoardPart);
        newPlaygroundRotation.put(new INDArrayIndex[]{NDArrayIndex.point(2)}, newMinPlayerBoardPart);

        return newPlaygroundRotation;
    }

    static synchronized INDArray rotate90(INDArray toRotate) {

        INDArray rotated90 = Nd4j.ones(toRotate.shape());

        for (int col = 0; col < toRotate.shape()[1]; col++) {

            INDArray slice = toRotate.getColumn(col).dup();
            rotated90.put(new INDArrayIndex[]{NDArrayIndex.point(col)}, Nd4j.reverse(slice));
        }
        return rotated90;
    }

    public static synchronized List<AdversaryTrainingExample> getSymmetries(INDArray board, INDArray actionProbabilities, int currentPlayer, int iteration) {

        List<AdversaryTrainingExample> symmetries = new ArrayList<>();

        INDArray twoDimensionalActionProbabilities = actionProbabilities.reshape(BOARD_SIZE, BOARD_SIZE);
        INDArray playgroundRotation = Nd4j.create(3, BOARD_SIZE, BOARD_SIZE);
        Nd4j.copy(board, playgroundRotation);

        INDArray actionMirrorHorizontal = mirrorBoardPartHorizontally(twoDimensionalActionProbabilities);
        actionMirrorHorizontal = actionMirrorHorizontal.reshape(OthelloModel.NUM_SQUARES);
        INDArray newPlaygroundMirrorHorizontal = mirrorBoardHorizontally(playgroundRotation);
        symmetries.add(
                new AdversaryTrainingExample(
                        newPlaygroundMirrorHorizontal.dup(),
                        currentPlayer,
                        actionMirrorHorizontal,
                        iteration)
        );

        for (int rotation = 1; rotation < 4; rotation++) {

            INDArray newActionRotation = rotate90(twoDimensionalActionProbabilities.dup());
            INDArray newPlaygroundRotation = rotateBoard90(playgroundRotation.dup());
            symmetries.add(
                    new AdversaryTrainingExample(
                            newPlaygroundRotation.dup(),
                            currentPlayer,
                            newActionRotation.reshape(OthelloModel.NUM_SQUARES).dup(),
                            iteration)
            );

            INDArray newActionMirrorHorizontal = mirrorBoardPartHorizontally(newActionRotation.dup());
            newActionMirrorHorizontal = newActionMirrorHorizontal.reshape(OthelloModel.NUM_SQUARES);
            newPlaygroundMirrorHorizontal = mirrorBoardHorizontally(newPlaygroundRotation.dup());
            symmetries.add(
                    new AdversaryTrainingExample(
                            newPlaygroundMirrorHorizontal.dup(),
                            currentPlayer,
                            newActionMirrorHorizontal.dup(),
                            iteration)
            );

            Nd4j.copy(newActionRotation, twoDimensionalActionProbabilities);
            Nd4j.copy(newPlaygroundRotation, playgroundRotation);
        }


        return symmetries;
    }

    static synchronized INDArray mirrorBoardHorizontally(INDArray playgroundRotation) {

        INDArray boardPlayerMirrorHorizontal = playgroundRotation.slice(0);
        INDArray maxPlayerMirrorHorizontal = mirrorBoardPartHorizontally(playgroundRotation.slice(1));
        INDArray minPlayerMirrorHorizontal = mirrorBoardPartHorizontally(playgroundRotation.slice(2));

        return createNewBoard(boardPlayerMirrorHorizontal, maxPlayerMirrorHorizontal,
                minPlayerMirrorHorizontal);
    }
    
    private synchronized static INDArray mirrorBoardPartHorizontally(INDArray toMirror) {

        INDArray mirrorHorizontal = Nd4j.ones(toMirror.shape()).neg();
        mirrorHorizontal.put(new INDArrayIndex[]{NDArrayIndex.point(0)}, toMirror.slice(2));
        mirrorHorizontal.put(new INDArrayIndex[]{NDArrayIndex.point(1)}, toMirror.slice(1));
        mirrorHorizontal.put(new INDArrayIndex[]{NDArrayIndex.point(2)}, toMirror.slice(0));

        return mirrorHorizontal;
    }

}
