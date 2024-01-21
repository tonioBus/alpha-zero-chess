package com.aquila.chess;

import com.aquila.chess.fit.AbstractFit;
import com.aquila.chess.fit.TrainFile;
import com.aquila.chess.strategy.mcts.*;
import com.aquila.chess.strategy.mcts.inputs.InputsManager;
import com.aquila.chess.strategy.mcts.inputs.lc0.Lc0InputsManagerImpl;
import com.aquila.chess.strategy.mcts.nnImpls.NNDeep4j;
import com.aquila.chess.strategy.mcts.nnImpls.NNSimul;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.conf.WorkspaceMode;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.jita.allocator.enums.AllocationStatus;
import org.nd4j.jita.conf.Configuration;
import org.nd4j.jita.conf.CudaEnvironment;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class MainFitNNLc0 {

    private static void settingsCuda() {
        CudaEnvironment.getInstance().getConfiguration()
                // key option enabled
                .allowMultiGPU(false) //
                .setFirstMemory(AllocationStatus.HOST)
                .setAllocationModel(Configuration.AllocationModel.CACHE_ALL)
                .setMaximumDeviceMemoryUsed(0.90) //
                .setMemoryModel(Configuration.MemoryModel.IMMEDIATE) //
                // cross-device access is used for faster model averaging over pcie
                .allowCrossDeviceAccess(false) //
                .setNumberOfGcThreads(4)
                // .setMaximumBlockSize(-1)
                .setMaximumGridSize(256)
                // .setMaximumDeviceCacheableLength(8L * 1024 * 1024 * 1024L)  // (6L * 1024 * 1024 * 1024L) //
                // .setMaximumDeviceCache(8L * 1024 * 1024 * 1024L) //
                .setMaximumHostCacheableLength(-1) // (6L * 1024 * 1024 * 1024L) //
                //.setMaximumHostCache(8L * 1024 * 1024 * 1024L)
                .setNoGcWindowMs(100)
                .enableDebug(false)
                .setVerbose(false);
    }

    public static void main(final String[] args) throws Exception {
        final AbstractFit abstractFit = new AbstractFit("config/configFit.xml");
        INN nnWhite;
        boolean simulation = abstractFit.getConfigFit().isSimulation();
        if (simulation) {
            nnWhite = new NNSimul(1);
        } else {
            nnWhite = new NNDeep4j(abstractFit.getConfigFit().getNnReference(), true, Lc0InputsManagerImpl.FEATURES_PLANES, 20);
            settingsCuda();
            ((ComputationGraph) nnWhite.getNetwork()).getConfiguration().setTrainingWorkspaceMode(WorkspaceMode.ENABLED);
        }
        log.info("SET UPDATE LR:{}", abstractFit.getConfigFit().getUpdateLr());
        UpdateLr updateLr = nbGames -> abstractFit.getConfigFit().getUpdateLr();
        nnWhite.setUpdateLr(updateLr, 1);
        final Map<String, StatisticsFit> statistics = new HashMap<>();
        abstractFit.getConfigFit().getConfigDirs().forEach(dir -> {
            statistics.put(dir.getDirectory(), new StatisticsFit(dir.getStartNumber(), dir.getEndNumber()));
        });
        InputsManager inputsManager = new Lc0InputsManagerImpl();
        final DeepLearningAGZ deepLearningWhite = DeepLearningAGZ
                .builder()
                .nn(nnWhite)
                .train(true)
                .batchSize(10)
                .inputsManager(inputsManager)
                .build();
        TrainFile trainFile = (file, statistics1) -> {
            log.info("train file:{}", file);
            String parent = file.toPath().getParent().toString();
            final TrainGame trainGame = TrainGame.load(file);
            try {
                StatisticsFit statisticsFit = statistics.get(parent);
                deepLearningWhite.train(trainGame, abstractFit.getConfigFit().getFitChunk(), statisticsFit);
            } catch (TrainException e) {
                log.error("TrainException: ", e);
                statistics1.get(parent).listErrorTrain.add(String.valueOf(trainGame.getNum()));
            } catch (IOException e) {
                log.error("Exception, we will close without saving", e);
                System.exit(-1);
            } catch (RuntimeException e) {
                log.error("RuntimeException, we will close without saving", e);
                System.exit(-1);
            }
        };
        abstractFit.run(trainFile, statistics);
        if (!simulation) {
            try {
                log.info("Saving NN: do not stop the JVM ...");
                ((ComputationGraph) nnWhite.getNetwork()).getConfiguration().setTrainingWorkspaceMode(WorkspaceMode.NONE);
                deepLearningWhite.save();
            } catch (IOException e) {
                log.error("Error when saving NN", e);
            }
        }
        log.info("----------------------------------------------------------------------------------------------------");
        log.info("Training config:{}", abstractFit.getFile());
        log.info("Training using UpdateLR:{}", abstractFit.getConfigFit().getUpdateLr());
        log.info("Train done in directories:\n{}",
                abstractFit.getConfigFit().getConfigDirs().stream().map(configDir -> configDir.getDirectory()).collect(Collectors.joining("\n- ", "- ", "")));
        statistics.entrySet().forEach(entry -> {
            String subDir = entry.getKey();
            log.info("SUBDIR:{}\n{}", subDir, entry.getValue());
        });
    }

}
