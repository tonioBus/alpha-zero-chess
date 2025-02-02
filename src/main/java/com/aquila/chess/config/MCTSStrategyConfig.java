package com.aquila.chess.config;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

@Getter
@ToString
@Slf4j
public class MCTSStrategyConfig {

    @ToString.Exclude
    private final Properties properties;

    private String nnReference = null;
    private boolean dirichlet = true;
    private int threads = -1;
    private int steps = 800;
    private long millisPerStep = -1;
    private int batch = 256;
    private int cpuAlgoNumberOfMoves = -1;
    private double maxCpuct = 2.5;

    int seed = 1;

    public MCTSStrategyConfig(String color, Properties properties) {
        this.properties = properties;
        this.nnReference = get(color + ".nnReference", String.class, null);
        this.dirichlet = get(color + ".dirichlet", Boolean.class, dirichlet);
        this.steps = get(color + ".steps", Integer.class, steps);
        this.threads = get(color + ".threads", Integer.class, threads);
        if (this.threads < 1) threads = Runtime.getRuntime().availableProcessors() - 4;
        this.batch = get(color + ".batch", Integer.class, batch);
        this.millisPerStep = get(color + ".millisPerStep", Long.class, millisPerStep);
        this.cpuAlgoNumberOfMoves = get(color + ".cpuAlgoNumberOfMoves", Integer.class, -1);
        this.maxCpuct = get(color + ".maxCpuct", Double.class, maxCpuct);
    }

    private <T> T get(String property, Class<T> clazz, T defaultValue) {
        try {
            return get(property, clazz);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            log.error("Error getting property:" + property, e);
            return defaultValue;
        }
    }

    private <T> T get(String property, Class<T> clazz) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String value = properties.getProperty(property);
        if (clazz == String.class) return (T) value;
        Method method = clazz.getDeclaredMethod("valueOf", String.class);
        return clazz.cast(method.invoke(null, value));
    }

}
