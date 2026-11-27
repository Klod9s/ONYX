package com.onyx.perfmonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Calculates session-wide frame pacing metrics from unique presentation intervals. */
final class FramePacingSummaryCalculator {

    static final double RECORDING_GAP_THRESHOLD_MS = 10_000.0;
    private static final double HITCH_MULTIPLIER = 1.5;
    private static final double JANK_MULTIPLIER = 1.5;
    private static final double BIG_JANK_MULTIPLIER = 3.0;

    private FramePacingSummaryCalculator() {
    }

    static final class Result {
        final double averageFrameTimeMs;
        final double standardDeviationMs;
        final int hitchCount;
        final int jankCount;
        final int bigJankCount;
        final int recordingGapCount;
        final double recordingGapDurationMs;
        final int validIntervalCount;
        final int refreshRateIntervalCount;

        Result(double averageFrameTimeMs, double standardDeviationMs,
               int hitchCount, int jankCount, int bigJankCount,
               int recordingGapCount, double recordingGapDurationMs,
               int validIntervalCount, int refreshRateIntervalCount) {
            this.averageFrameTimeMs = averageFrameTimeMs;
            this.standardDeviationMs = standardDeviationMs;
            this.hitchCount = hitchCount;
            this.jankCount = jankCount;
            this.bigJankCount = bigJankCount;
            this.recordingGapCount = recordingGapCount;
            this.recordingGapDurationMs = recordingGapDurationMs;
            this.validIntervalCount = validIntervalCount;
            this.refreshRateIntervalCount = refreshRateIntervalCount;
        }

        boolean valid() {
            return validIntervalCount > 0;
        }

        boolean hasRefreshRateData() {
            return refreshRateIntervalCount > 0;
        }
    }

    /**
     * Calculates frame-time statistics and event counts. Refresh periods are
     * aligned with intervals; each period represents the SurfaceFlinger batch
     * in which its intervals were captured. Missing periods leave jank metrics
     * unavailable for those intervals, but do not affect FPS/frame-time metrics.
     */
    static Result calculate(List<Double> intervals, List<Double> refreshPeriodsMs) {
        if (intervals == null || intervals.isEmpty()) {
            return unavailable();
        }

        List<Double> usable = new ArrayList<>();
        int gaps = 0;
        double gapDuration = 0;
        for (Double value : intervals) {
            if (!isPositiveFinite(value)) continue;
            if (value > RECORDING_GAP_THRESHOLD_MS) {
                gaps++;
                gapDuration += value;
            } else {
                usable.add(value);
            }
        }

        if (usable.isEmpty()) {
            return new Result(-1, -1, -1, -1, -1,
                    gaps, gapDuration, 0, 0);
        }

        double total = 0;
        for (double value : usable) total += value;
        double average = total / usable.size();

        double squaredDifferenceTotal = 0;
        for (double value : usable) {
            double difference = value - average;
            squaredDifferenceTotal += difference * difference;
        }
        double standardDeviation = Math.sqrt(squaredDifferenceTotal / usable.size());

        List<Double> sorted = new ArrayList<>(usable);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        double median = sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0
                : sorted.get(middle);
        int hitches = 0;
        for (double value : usable) {
            if (value > median * HITCH_MULTIPLIER) hitches++;
        }

        int janks = 0;
        int bigJanks = 0;
        int refreshCount = 0;
        for (int i = 0; i < intervals.size(); i++) {
            Double interval = intervals.get(i);
            if (!isPositiveFinite(interval) || interval > RECORDING_GAP_THRESHOLD_MS) {
                continue;
            }
            Double refresh = refreshPeriodsMs != null && i < refreshPeriodsMs.size()
                    ? refreshPeriodsMs.get(i) : null;
            if (!isPositiveFinite(refresh)) continue;
            refreshCount++;
            if (interval > refresh * JANK_MULTIPLIER) janks++;
            if (interval > refresh * BIG_JANK_MULTIPLIER) bigJanks++;
        }

        return new Result(average, standardDeviation, hitches,
                refreshCount > 0 ? janks : -1,
                refreshCount > 0 ? bigJanks : -1,
                gaps, gapDuration, usable.size(), refreshCount);
    }

    /** Compatibility overload for callers/tests without refresh-rate metadata. */
    static Result calculate(List<Double> intervals) {
        return calculate(intervals, Collections.<Double>emptyList());
    }

    private static Result unavailable() {
        return new Result(-1, -1, -1, -1, -1, 0, 0, 0, 0);
    }

    private static boolean isPositiveFinite(Double value) {
        return value != null && Double.isFinite(value) && value > 0;
    }
}
