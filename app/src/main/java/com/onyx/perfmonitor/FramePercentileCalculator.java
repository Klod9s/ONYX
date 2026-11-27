package com.onyx.perfmonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Calculates benchmark lows from individual frame presentation intervals. */
final class FramePercentileCalculator {

    private static final int MIN_PERCENTILE_FRAME_COUNT = 10;

    private FramePercentileCalculator() {
    }

    /**
     * Returns the arithmetic mean FPS of the slowest requested fraction of
     * frames. Invalid intervals are ignored; -1 means insufficient usable data.
     *
     * @param frameIntervalsMs individual presentation-to-presentation intervals
     * @param percentile fraction in the range (0, 1], e.g. 0.01 for 1%
     */
    static double averageLowFps(List<Double> frameIntervalsMs, double percentile) {
        if (frameIntervalsMs == null || frameIntervalsMs.isEmpty()
                || !Double.isFinite(percentile)
                || percentile <= 0 || percentile > 1) {
            return -1;
        }

        List<Double> frameFps = new ArrayList<>();
        for (Double interval : frameIntervalsMs) {
            if (interval == null || !Double.isFinite(interval)
                    || interval <= 0
                    || interval > FramePacingSummaryCalculator.RECORDING_GAP_THRESHOLD_MS) {
                continue;
            }
            frameFps.add(1000.0 / interval);
        }

        if (frameFps.isEmpty()) return -1;

        Collections.sort(frameFps);

        int count = (int) Math.ceil(frameFps.size() * percentile);

        // Require at least 10 frames in the percentile sample.
        if (count < 10) return -1;

        double total = 0;
        for (int i = 0; i < count; i++) {
            total += frameFps.get(i);
        }

        return total / count;
    }
}