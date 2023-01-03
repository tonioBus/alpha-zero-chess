package com.aquila.chess.strategy.mcts;

import com.aquila.chess.MCTSStrategyConfig;
import com.aquila.chess.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

@Slf4j
public class CacheValues {

    private final Map<Long, CacheValue> lruMap;

    public Collection<CacheValue> getValues() {
        return lruMap.values();
    }

    public CacheValues(final int size) {
        lruMap = new LRUMap<>(size);
    }

    public synchronized void clearCache() {
        if (log.isDebugEnabled()) log.debug("EMPTY cacheNNValues: {}", this.lruMap.size());
        this.lruMap.clear();
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

    public synchronized CacheValue create(long key, final String label, boolean isDirichlet) {
        if (containsKey(key)) throw new RuntimeException("node already created for key:" + key);
        CacheValue ret = CacheValue.getNotInitialized(String.format("[%d] %s", key, label), isDirichlet);
        this.lruMap.put(key, ret);
        return ret;
    }

    public synchronized CacheValue updateValueAndPolicies(long key, double value, double[] notNormalisedPolicies) {
        CacheValue cacheValue = this.lruMap.get(key);
        if (cacheValue == null) {
            throw new RuntimeException("node for key:" + key + " not found");
        }
        cacheValue.setTrueValuesAndPolicies(value, notNormalisedPolicies);
        return cacheValue;
    }

    @Getter
    static public class CacheValue extends OutputNN implements Serializable {

        private static final double NOT_INITIALIZED_VALUE = 0;
        private boolean isDirichlet = true;

        private static final CacheValue getNotInitialized(final String label, boolean isDirichlet) {
            return new CacheValue(NOT_INITIALIZED_VALUE, label, new double[PolicyUtils.MAX_POLICY_INDEX], isDirichlet);
        }

        @Setter
        private boolean initialised = false;

        @Setter
        private boolean propagated = false;

        final private String label;

        private MCTSNode node = null;

        private CacheValueType type = CacheValueType.INTERMEDIATE;

        private int nbPropagate = 1;

        private CacheValue(double value, String label, double[] policies, boolean isDirichlet) {
            super(value, policies);
            this.label = label;
            this.isDirichlet = isDirichlet;
        }

        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
        }

        public void resetNbPropragate() {
            nbPropagate = 0;
        }

        public void setAsRoot() {
            this.type = CacheValueType.ROOT;
        }

        public synchronized void normalise(double[] policies) {
            int[] indexes = PolicyUtils.getIndexesFilteredPolicies(node.getChildMoves());
            if (log.isDebugEnabled())
                log.debug("NORMALIZED type:{} move.size:{} dirichlet:{}", this.type, node.getChildMoves().size(), node.isDirichlet());
            double[] normalisedPolicies = Utils.toDistribution(policies, indexes, MCTSStrategyConfig.isDirichlet(node.getMove()) && node.getState() == MCTSNode.State.ROOT);
            if (Arrays.stream(normalisedPolicies).filter(policy -> Double.isNaN(policy)).count() > 0) {
                throw new RuntimeException("ERROR, some policy with NaN value");
            }
            this.policies = normalisedPolicies;
        }

        public void setAsLeaf() {
            this.type = CacheValueType.LEAF;
        }

        public OutputNN setTrueValuesAndPolicies(final double value, final double[] policies) {
            this.value = value;
            this.policies = policies;
            log.debug("setTrueValuesAndPolicies({},{} {} {} ..)", value, policies[0], policies[1], policies[2]);
            this.setInitialised(true);
            if (node != null) {
                setTrueValuesAndPolicies();
            }
            return this;
        }

        public void setTrueValuesAndPolicies() {
            if (initialised && type != CacheValueType.LEAF) {
                node.syncSum();
                log.debug("normalise: {} {} {}", policies[0], policies[1], policies[2]);
                normalise(policies);
            }
        }

        public void setNode(MCTSNode node) {
            this.node = node;
            setTrueValuesAndPolicies();
        }

        public static enum CacheValueType {
            INTERMEDIATE, ROOT, LEAF
        }

        public void incPropagate() {
            this.nbPropagate++;
        }

    }
}
