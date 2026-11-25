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
    public void calculatesSessionWideMeanDeviationAndStutters() {
        List<Double> intervals = Arrays.asList(10.0, 10.0, 20.0, 10.0);

        FramePacingSummaryCalculator.Result result =
                FramePacingSummaryCalculator.calculate(intervals);

        assertEquals(12.5, result.averageFrameTimeMs, 0.0001);
        assertEquals(Math.sqrt(18.75), result.standardDeviationMs, 0.0001);
        assertEquals(1, result.stutterCount);
        assertEquals(4, result.validIntervalCount);
        assertTrue(result.valid());
    }

    @Test
    public void ignoresInvalidIntervals() {
        List<Double> intervals = Arrays.asList(
                10.0, Double.NaN, Double.POSITIVE_INFINITY,
                0.0, -1.0, 20.0, 10_001.0);

        FramePacingSummaryCalculator.Result result =
                FramePacingSummaryCalculator.calculate(intervals);

        assertEquals(13.3333333333, result.averageFrameTimeMs, 0.0001);
        assertEquals(1, result.stutterCount);
        assertEquals(3, result.validIntervalCount);
    }

    @Test
    public void returnsUnavailableWhenNoUsableIntervalsExist() {
        FramePacingSummaryCalculator.Result result =
                FramePacingSummaryCalculator.calculate(
                        Collections.<Double>emptyList());

        assertFalse(result.valid());
        assertEquals(-1.0, result.averageFrameTimeMs, 0.0);
        assertEquals(-1.0, result.standardDeviationMs, 0.0);
        assertEquals(-1, result.stutterCount);
    }
}
