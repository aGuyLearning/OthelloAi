package othello.DualResNetwork;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.util.ModelSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import othello.othelloAi.OthelloModel;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Trainer {
    private static final Logger log = LoggerFactory.getLogger(Trainer.class);

    public static void main(String[] args) throws IOException {

        int numResidualBlocks = 10;
        int numFeaturePlanes = 3;

        ComputationGraph neuralNet = DualResnetModel.getModel(numResidualBlocks, numFeaturePlanes);

        if (log.isInfoEnabled()) {
            log.info(neuralNet.summary());
        }

        AdversaryLearning adversaryLearning =
                new AdversaryLearning(
                        new OthelloModel(),
                        neuralNet);

        adversaryLearning.performLearning();
    }
}
