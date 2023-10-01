package com.aquila.chess.config;

import com.chess.engine.classic.Alliance;
import com.chess.engine.classic.board.Move;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class MCTSConfig {

    static public MCTSConfig mctsConfig = new MCTSConfig();

    private Properties properties = new Properties();

    @Getter
    private MCTSStrategyConfig mctsWhiteStrategyConfig;

    @Getter
    private MCTSStrategyConfig mctsBlackStrategyConfig;

    public MCTSConfig() {
        String dir = "config/mctsConfig.xml";
        try {
            InputStream in = new FileInputStream(dir);
            properties.loadFromXML(in);
            mctsWhiteStrategyConfig = new MCTSStrategyConfig("white", properties);
            mctsBlackStrategyConfig = new MCTSStrategyConfig("black", properties);
        } catch (IOException e) {
            log.error("Error when reading properties file using default values", e);
        }
    }

    public boolean isDirichlet(Alliance pieceAllegiance) {
        switch (pieceAllegiance) {
            case WHITE:
                return mctsWhiteStrategyConfig.isDirichlet();
            case BLACK:
                return mctsBlackStrategyConfig.isDirichlet();
        }
        throw new RuntimeException(String.format("allegiance not know: %s", pieceAllegiance));
    }

    public boolean isDirichlet(Move move) {
        if (move == null) return mctsWhiteStrategyConfig.isDirichlet();
        return isDirichlet(move.getAllegiance());
    }


}