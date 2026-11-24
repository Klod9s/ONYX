package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class CpuUsageTrackerTest {
    @Test public void firstSnapshotEstablishesBaseline() {
        double[] loads = new CpuUsageTracker().parse("cpu0 100 0 50 0 50\ncpu1 200 0 100 0 100");
        assertEquals(8, loads.length);
        assertEquals(-1.0, loads[0], 0.0001);
        assertEquals(-1.0, loads[1], 0.0001);
        assertEquals(-1.0, loads[7], 0.0001);
    }

    @Test public void calculatesUsageFromTwoSnapshots() {
        CpuUsageTracker tracker = new CpuUsageTracker();
        tracker.parse("cpu0 100 0 50 0 50");
        double[] loads = tracker.parse("cpu0 150 0 70 0 80");
        assertEquals(70.0, loads[0], 0.0001);
        assertEquals(-1.0, loads[1], 0.0001);
    }

    @Test public void malformedOrMissingCoreDoesNotCorruptOtherResults() {
        CpuUsageTracker tracker = new CpuUsageTracker();
        tracker.parse("cpu0 100 0 50 0 50\ncpu1 100 0 50 0 50");
        double[] loads = tracker.parse("cpu0 130 0 60 0 70\ncpu1 invalid counters\ncpu2 200 0 100 0 100");
        assertEquals(60.0, loads[0], 0.0001);
        assertEquals(-1.0, loads[1], 0.0001);
        assertEquals(-1.0, loads[2], 0.0001);
    }

    @Test public void counterResetReturnsUnavailableAndEstablishesNewBaseline() {
        CpuUsageTracker tracker = new CpuUsageTracker();
        tracker.parse("cpu0 500 0 200 0 300");
        assertEquals(-1.0, tracker.parse("cpu0 100 0 40 0 60")[0], 0.0001);
        assertEquals(70.0, tracker.parse("cpu0 150 0 60 0 90")[0], 0.0001);
    }

    @Test public void resetRequiresANewBaseline() {
        CpuUsageTracker tracker = new CpuUsageTracker();
        tracker.parse("cpu0 100 0 50 0 50");
        tracker.parse("cpu0 150 0 70 0 80");
        tracker.reset();
        assertEquals(-1.0, tracker.parse("cpu0 200 0 90 0 110")[0], 0.0001);
    }

    @Test public void clampsUsageToPercentageRange() {
        CpuUsageTracker tracker = new CpuUsageTracker();
        tracker.parse("cpu0 100 0 50 0 50");
        assertEquals(100.0, tracker.parse("cpu0 150 0 40 0 60")[0], 0.0001);
    }
}
