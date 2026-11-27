package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class FramePacingSummaryCalculatorTest {

    @Test
    public void calculatesMeanDeviationAndMedianBasedHitches() {
        List<Double> intervals = Arrays.asList(10.0, 10.0, 20.0, 10.0);

        FramePacingSummaryCalculator.Result result =
                FramePacingSummaryCalculator.calculate(intervals);

        assertEquals(12.5, result.averageFrameTimeMs, 0.0001);
        assertEquals(Math.sqrt(18.75), result.standardDeviationMs, 0.0001);
        assertEquals(1, result.hitchCount);
        assertEquals(-1, result.jankCount);
        assertEquals(-1, result.bigJankCount);
        assertEquals(4, result.validIntervalCount);
        assertTrue(result.valid());
    }

    @Test
    public void stableTwentyFpsOnSixtyHzIsJankButNotHitches() {
        List<Double> intervals = Arrays.asList(50.0, 50.0, 50.0, 50.0);
        List<Double> refreshPeriods = Arrays.asList(16.667, 16.667, 16.667, 16.667);

        FramePacingSummaryCalculator.Result result =
                FramePacingSummaryCalculator.calculate(intervals, refreshPeriods);

        assertEquals(0, result.hitchCount);
        assertEquals(4, result.jankCount);
        assertEquals(0, result.bigJankCount);
        assertEquals(4, result.refreshRateIntervalCount);
    }

    @Test
    public void usesRefreshPeriodCapturedForEachBatch() {
        List<Double> intervals = Arrays.asList(33.4, 100.1);
        List<Double> refreshPeriods = Arrays.asList(33.3, 16.667);

        FramePacingSummaryCalculator.Result result =
                FramePacingSummaryCalculator.calculate(intervals, refreshPeriods);

        assertEquals(1, result.jankCount);
        assertEquals(1, result.bigJankCount);
    }

    @Test
    public void preservesAndExcludesRecordingGapsFromFrameStatistics() {
        List<Double> intervals = Arrays.asList(16.667, 10_001.0, 33.333, 20.0);
        List<Double> refreshPeriods = Arrays.asList(16.667, 16.667, 16.667, 16.667);

        FramePacingSummaryCalculator.Result result =
                FramePacingSummaryCalculator.calculate(intervals, refreshPeriods);

        assertEquals((16.667 + 33.333 + 20.0) / 3.0,
                result.averageFrameTimeMs, 0.0001);
        assertEquals(3, result.validIntervalCount);
        assertEquals(1, result.recordingGapCount);
        assertEquals(10_001.0, result.recordingGapDurationMs, 0.0001);
        assertEquals(1, result.jankCount);
        assertEquals(3, result.refreshRateIntervalCount); // gap excluded
    }

    @Test
    public void ignoresInvalidIntervalsAndUnusableRefreshPeriods() {
        List<Double> intervals = Arrays.asList(
                10.0, Double.NaN, Double.POSITIVE_INFINITY,
                0.0, -1.0, 20.0, 10_001.0);
        List<Double> refreshPeriods = Arrays.asList(
                10.0, 10.0, 10.0, 10.0, 10.0, Double.NaN, 10.0);

        FramePacingSummaryCalculator.Result result =
                FramePacingSummaryCalculator.calculate(intervals, refreshPeriods);

        assertEquals(15.0, result.averageFrameTimeMs, 0.0001);
        assertEquals(2, result.validIntervalCount);
        assertEquals(1, result.recordingGapCount);
        assertEquals(1, result.refreshRateIntervalCount);
        assertEquals(0, result.jankCount);
        assertEquals(0, result.bigJankCount);
    }

    @Test
    public void returnsUnavailableWhenNoUsableIntervalsExist() {
        FramePacingSummaryCalculator.Result result =
                FramePacingSummaryCalculator.calculate(
                        Collections.<Double>emptyList());

        assertFalse(result.valid());
        assertEquals(-1.0, result.averageFrameTimeMs, 0.0);
        assertEquals(-1.0, result.standardDeviationMs, 0.0);
        assertEquals(-1, result.hitchCount);
    }
}
