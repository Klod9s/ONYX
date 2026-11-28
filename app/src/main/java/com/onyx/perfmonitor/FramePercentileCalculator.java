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
     * Worst percentile low inside any sliding window of the session, which is
     * what a player actually experiences. A 10-minute run's whole-session 1%
     * low can hide a five-second stutter completely.
     *
     * @param windowFrameCount frames per window; use ~1000 for roughly 10 s at 100 fps
     */
    static double worstWindowedLowFps(
            List<Double> frameIntervalsMs, double percentile, int windowFrameCount) {
        if (frameIntervalsMs == null || frameIntervalsMs.isEmpty()
                || windowFrameCount < MIN_PERCENTILE_FRAME_COUNT
                || !Double.isFinite(percentile)
                || percentile <= 0 || percentile > 1) {
            return -1;
        }

        List<Double> usable = new ArrayList<>();
        for (Double interval : frameIntervalsMs) {
            if (interval == null || !Double.isFinite(interval)
                    || interval <= 0
                    || interval > FramePacingSummaryCalculator.RECORDING_GAP_THRESHOLD_MS) {
                continue;
            }
            usable.add(interval);
        }

        // A window must be long enough for the percentile to be meaningful.
        int minimum = (int) Math.ceil(MIN_PERCENTILE_FRAME_COUNT / percentile);
        if (usable.size() < Math.max(windowFrameCount, minimum)) return -1;

        double worst = -1;
        for (int start = 0; start + windowFrameCount <= usable.size(); start += windowFrameCount / 2) {
            List<Double> window = usable.subList(start, start + windowFrameCount);
            double low = averageLowFps(window, percentile);
            if (low >= 0 && (worst < 0 || low < worst)) worst = low;
        }
        return worst;
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

        int minimumTotalFrameCount =
                (int) Math.ceil(MIN_PERCENTILE_FRAME_COUNT / percentile);

        if (frameFps.size() < minimumTotalFrameCount) return -1;

        int count = (int) Math.ceil(frameFps.size() * percentile);

        double total = 0;
        for (int i = 0; i < count; i++) {
            total += frameFps.get(i);
        }

        return total / count;
    }
}