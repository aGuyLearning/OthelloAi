package othello.AdverserialLearning.Util;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import othello.AdverserialLearning.Model.DualResnetModel;

import java.util.Arrays;

/**
 * Train AlphaGo Zero model on dummy data. To run a full AGZ system with DL4J, check out
 *
 * https://github.com/maxpumperla/ScalphaGoZero
 *
 * for a complete example. The input to the network has a number of "planes" the size of the
 * board, i.e. in most cases 19x19. One such plane could be "the number of liberties each
 * black stone on the board has". In the AGZ paper a total of 11 planes are used, while in
 * the previous AlphaGo version there have been 48 (resp. 49).
 *
 * The output of the policy head of the network produces move probabilities and emits one
 * probability for each move on the board, including passing (i.e. 19x19 + 1 = 362 in total).
 * The value head produces winning probabilities for the current position.
 *
 * @author Max Pumperla
 */
public class EvalTrainer {

    private static final Logger log = LoggerFactory.getLogger(EvalTrainer.class);


    public static void main(String[] args) {

        int miniBatchSize = 5;
        int boardSize = 8;

        int numResidualBlocks = 10;
        int numFeaturePlanes = 3;

        log.info("Initializing AGZ model");
        ComputationGraph model = DualResnetModel.getModel(numResidualBlocks, numFeaturePlanes);

        log.info("Create dummy data");
        INDArray input = Nd4j.create(miniBatchSize,numFeaturePlanes, boardSize, boardSize);
        System.out.println(Arrays.toString(input.shape()));

        // move prediction has one value for each point on the board (19x19) plus one for passing.
        INDArray policyOutput = Nd4j.create(miniBatchSize, boardSize * boardSize);

        // the value network spits out a value between 0 and 1 to assess how good the current board situation is.
        INDArray valueOutput = Nd4j.create(miniBatchSize, 1);
        System.out.println(model.summary());

        log.info("Train AGZ model");
        model.fit(new INDArray[] {input}, new INDArray[] {policyOutput, valueOutput});
        System.out.println(Arrays.toString(model.output(input)));

    }
}
