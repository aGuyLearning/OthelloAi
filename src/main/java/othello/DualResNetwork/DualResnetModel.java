package othello.DualResNetwork;


import org.deeplearning4j.nn.graph.ComputationGraph;

/**
 * Define and load an AlphaGo Zero dual ResNet architecture
 * into DL4J.
 *
 * The dual residual architecture is the strongest
 * of the architectures tested by DeepMind for AlphaGo
 * Zero. It consists of an initial convolution layer block,
 * followed by a number (40 for the strongest, 20 as
 * baseline) of residual blocks. The network is topped
 * off by two "heads", one to predict policies and one
 * for value functions.
 *
 * @author Max Pumperla
 */
public class DualResnetModel {

    public static ComputationGraph getModel(int blocks, int numPlanes) {

       AlphaGoZeroBuilder builder = new AlphaGoZeroBuilder();
        String input = "in";

        builder.addInputs(input);
        String initBlock = "init";
        String convOut = builder.addConvBatchNormBlock(initBlock, input, numPlanes, true);
        String towerOut = builder.addResidualTower(blocks, convOut);
        String policyOut = builder.addPolicyHead(towerOut, true);
        String valueOut = builder.addValueHead(towerOut, true);
        builder.addOutputs(policyOut, valueOut);

        ComputationGraph model = new ComputationGraph(builder.buildAndReturn());
        model.init();

        return model;
    }
}