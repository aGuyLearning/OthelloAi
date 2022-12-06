package othello.AdverserialLearning;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import othello.AdverserialLearning.Model.DualResnetModel;
import othello.othelloAi.OthelloModel;

import java.io.IOException;
import java.util.Arrays;

public class Trainer {
    private static final Logger log = LoggerFactory.getLogger(Trainer.class);

    public static void main(String[] args) throws IOException {

        int numResidualBlocks = 10;
        int numFeaturePlanes = 3;

        ComputationGraph neuralNet = DualResnetModel.getModel(numResidualBlocks, numFeaturePlanes);

        AdversaryLearning adversaryLearning =
                new AdversaryLearning(
                        new OthelloModel(),
                        neuralNet);

        adversaryLearning.performLearning();
    }
}
