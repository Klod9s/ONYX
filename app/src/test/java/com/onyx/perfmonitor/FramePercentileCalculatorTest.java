package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class FramePercentileCalculatorTest {

    @Test
    public void computesOnePercentLowFromSlowestIndividualFrames() {
        List<Double> intervals = new ArrayList<>();
        for (int i = 0; i < 990; i++) intervals.add(1000.0 / 60.0);
        for (int i = 0; i < 10; i++) intervals.add(20.0);

        assertEquals(50.0,
                FramePercentileCalculator.averageLowFps(intervals, 0.01),
                0.0001);
    }

    @Test
    public void computesPointOnePercentLowFromSlowestIndividualFrames() {
        List<Double> intervals = new ArrayList<>();
        for (int i = 0; i < 999; i++) intervals.add(1000.0 / 60.0);
        intervals.add(25.0);

        assertEquals(40.0,
                FramePercentileCalculator.averageLowFps(intervals, 0.001),
                0.0001);
    }

    @Test
    public void ignoresInvalidIntervals() {
        List<Double> intervals = new ArrayList<>();
        intervals.add(Double.NaN);
        intervals.add(0.0);
        intervals.add(-1.0);
        intervals.add(1000.0 / 50.0);

        assertEquals(50.0,
                FramePercentileCalculator.averageLowFps(intervals, 0.01),
                0.0001);
    }

    @Test
    public void returnsUnavailableWhenNoValidFrameIntervalsExist() {
        assertEquals(-1.0,
                FramePercentileCalculator.averageLowFps(
                        Collections.singletonList(-1.0), 0.01),
                0.0);
        assertTrue(FramePercentileCalculator.averageLowFps(
                Collections.<Double>emptyList(), 0.001) < 0);
    }
}
