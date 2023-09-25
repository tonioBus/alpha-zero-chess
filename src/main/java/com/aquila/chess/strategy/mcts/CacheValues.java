package com.aquila.chess.strategy.mcts;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;

import java.util.Collection;
import java.util.Map;

@Slf4j
public class CacheValues {

    private final Map<Long, CacheValue> lruMap;

    @Getter
    private final CacheValue winCacheValue = new CacheValue(1, "WIN", new double[0]);

    @Getter
    private final CacheValue lostCacheValue = new CacheValue(-1, "LOST", new double[0]);

    @Getter
    private final CacheValue drawnCacheValue = new CacheValue(0, "DRAWN", new double[0]);

    public Collection<CacheValue> getValues() {
        return lruMap.values();
    }

    public CacheValues(final int size) {
        lruMap = new LRUMap<>(size);
        clearCache();
    }

    public synchronized void clearCache() {
        if (log.isDebugEnabled()) log.debug("EMPTY cacheNNValues: {}", this.lruMap.size());
        this.lruMap.clear();
        lostCacheValue.getNodes().clear();
        drawnCacheValue.getNodes().clear();
        winCacheValue.getNodes().clear();
        this.lruMap.put(-1L, lostCacheValue);
        this.lruMap.put(0L, drawnCacheValue);
        this.lruMap.put(1L, winCacheValue);
    }

    public synchronized CacheValue get(final long key) {
        CacheValue ret = lruMap.get(key);
        return ret;
    }

    public synchronized boolean containsKey(final long key) {
        return this.lruMap.containsKey(key);
    }

    public synchronized int size() {
        return this.lruMap.size();
    }

    public static CacheValue createDummy(String label) {
        return CacheValue.getNotInitialized(label);
    }

    public synchronized CacheValue create(long key, final String label) {
        if (containsKey(key)) throw new RuntimeException("node already created for key:" + key);
        CacheValue ret = CacheValue.getNotInitialized(String.format("[%d] %s", key, label));
        this.lruMap.put(key, ret);
        return ret;
    }

    /**
     * update value and policies on 1 node. The node is define by the key
     * @param key
     * @param value
     * @param notNormalisedPolicies
     * @return
     */
    synchronized CacheValue updateValueAndPolicies(long key, double value, double[] notNormalisedPolicies) {
        CacheValue cacheValue = this.lruMap.get(key);
        if (cacheValue == null) {
            throw new RuntimeException("node for key:" + key + " not found");
        }
        cacheValue.setInferenceValuesAndPolicies(value, notNormalisedPolicies);
        return cacheValue;
    }

}
