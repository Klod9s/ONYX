package com.onyx.perfmonitor;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class SessionSummaryFormatterTest {

    @Test
    public void reportsUnavailableWhenBatteryWasNeverMeasured() {
        Sample sample = new Sample();
        sample.timeMs = 60_000;
        sample.battery = -1;

        String summary = SessionSummaryFormatter.build(Collections.singletonList(sample));

        assertTrue(summary.contains("Battery level: unavailable"));
        assertTrue(!summary.contains("-1%"));
    }

    @Test
    public void skipsUnavailableBatterySamplesWhenBuildingRange() {
        Sample first = new Sample();
        first.timeMs = 0;
        first.battery = -1;
        Sample measuredStart = new Sample();
        measuredStart.timeMs = 30_000;
        measuredStart.battery = 80;
        Sample measuredEnd = new Sample();
        measuredEnd.timeMs = 60_000;
        measuredEnd.battery = 75;
        Sample last = new Sample();
        last.timeMs = 90_000;
        last.battery = -1;

        String summary = SessionSummaryFormatter.build(
                Arrays.asList(first, measuredStart, measuredEnd, last));

        assertTrue(summary.contains("Battery level: 80% → 75% (-5% change)"));
        assertTrue(!summary.contains("-1%"));
    }
}
