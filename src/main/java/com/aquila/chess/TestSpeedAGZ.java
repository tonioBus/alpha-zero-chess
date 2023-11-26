package com.aquila.chess;

import com.aquila.chess.strategy.mcts.INN;
import com.aquila.chess.strategy.mcts.inputs.InputRecord;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsFullNN;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Board;
import com.chess.engine.classic.board.BoardUtils;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import java.util.ArrayList;

@Slf4j
public class TestSpeedAGZ {

    static private final String NN_TEST = "../AGZ_NN/AGZ.reference.test";

    public void run() {
        final Board board = Board.createStandardBoard();
        final Lc0InputsManagerImpl inputManager = new Lc0InputsManagerImpl();
        final Game game = Game.builder().inputsManager(inputManager).board(board).build();

        INN nnWhite = new NNDeep4j(NN_TEST, false, inputManager.getNbFeaturesPlanes(), 20);
        ComputationGraph computationGraph = (ComputationGraph) nnWhite.getNetwork();
        long start, end, delay;

        start = System.currentTimeMillis();
        int length = 150;
        final var nbIn = new double[length][inputManager.getNbFeaturesPlanes()][BoardUtils.NUM_TILES_PER_ROW][BoardUtils.NUM_TILES_PER_ROW];
        Lc0InputsFullNN inputsFullNN = inputManager.createInputs(
                new InputRecord(
                        game,
                        board,
                        null,
                        new ArrayList<>(),
                        Alliance.WHITE));
        for (int i = 0; i < length; i++) {
            System.arraycopy(inputsFullNN.inputs(), 0, nbIn[i], 0, inputManager.getNbFeaturesPlanes());
        }
        INDArray inputsArray = Nd4j.create(nbIn);
        end = System.currentTimeMillis();
        delay = end - start;
        log.info("delay:{} ms / {} s", delay, ((double) delay / 1000));

        start = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            // INDArray inputsArray = Nd4j.create(nbIn);
            INDArray[] ret = computationGraph.output(inputsArray);
        }
        end = System.currentTimeMillis();
        delay = end - start;
        log.info("delay:{} ms / {} s", delay, ((double) delay / 1000));
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public static void main(final String[] args) throws Exception {
        TestSpeedAGZ testSpeedAGZ = new TestSpeedAGZ();
        testSpeedAGZ.run();
    }
}
