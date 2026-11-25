package com.onyx.perfmonitor;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class CpuCoreClassifierTest {

    @Test
    public void classifiesThreeCpuClusters() {
        int[] result = CpuCoreClassifier.classify(
                new long[]{512, 512, 768, 768, 1024, 1024, 1024, 1024});

        assertArrayEquals(new int[]{
                CpuCoreClassifier.EFFICIENCY,
                CpuCoreClassifier.EFFICIENCY,
                CpuCoreClassifier.NORMAL,
                CpuCoreClassifier.NORMAL,
                CpuCoreClassifier.PERFORMANCE,
                CpuCoreClassifier.PERFORMANCE,
                CpuCoreClassifier.PERFORMANCE,
                CpuCoreClassifier.PERFORMANCE
        }, result);
    }

    @Test
    public void classifiesTwoCpuClustersWithoutInventingNormalCores() {
        int[] result = CpuCoreClassifier.classify(
                new long[]{400, 400, 400, 400, 900, 900, 900, 900});

        assertArrayEquals(new int[]{
                CpuCoreClassifier.EFFICIENCY,
                CpuCoreClassifier.EFFICIENCY,
                CpuCoreClassifier.EFFICIENCY,
                CpuCoreClassifier.EFFICIENCY,
                CpuCoreClassifier.PERFORMANCE,
                CpuCoreClassifier.PERFORMANCE,
                CpuCoreClassifier.PERFORMANCE,
                CpuCoreClassifier.PERFORMANCE
        }, result);
    }

    @Test
    public void leavesUnknownCoresUnclassifiedWhenDataIsUnavailable() {
        int[] result = CpuCoreClassifier.classify(
                new long[]{-1, -1, -1, -1, -1, -1, -1, -1});

        assertArrayEquals(new int[]{
                CpuCoreClassifier.UNKNOWN,
                CpuCoreClassifier.UNKNOWN,
                CpuCoreClassifier.UNKNOWN,
                CpuCoreClassifier.UNKNOWN,
                CpuCoreClassifier.UNKNOWN,
                CpuCoreClassifier.UNKNOWN,
                CpuCoreClassifier.UNKNOWN,
                CpuCoreClassifier.UNKNOWN
        }, result);
    }
}
