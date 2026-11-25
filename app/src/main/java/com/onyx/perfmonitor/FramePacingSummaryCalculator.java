package com.onyx.perfmonitor;

import java.util.List;

/** Calculates session-wide frame pacing metrics from unique presentation intervals. */
final class FramePacingSummaryCalculator {

    private static final double STUTTER_MULTIPLIER = 1.5;
    private static final double MAX_INTERVAL_MS = 10_000.0;

    private FramePacingSummaryCalculator() {
    }

    static final class Result {
        final double averageFrameTimeMs;
        final double standardDeviationMs;
        final int stutterCount;
        final int validIntervalCount;

        Result(double averageFrameTimeMs, double standardDeviationMs,
               int stutterCount, int validIntervalCount) {
            this.averageFrameTimeMs = averageFrameTimeMs;
            this.standardDeviationMs = standardDeviationMs;
            this.stutterCount = stutterCount;
            this.validIntervalCount = validIntervalCount;
        }

        boolean valid() {
            return validIntervalCount > 0;
        }
    }

    /**
     * Calculates the arithmetic mean, population standard deviation, and
     * number of intervals exceeding 1.5 times the session's mean frametime.
     * Invalid or implausible intervals are ignored.
     */
    static Result calculate(List<Double> frameIntervalsMs) {
        if (frameIntervalsMs == null || frameIntervalsMs.isEmpty()) {
            return new Result(-1, -1, -1, 0);
        }

        double total = 0;
        int count = 0;

        for (Double interval : frameIntervalsMs) {
            if (isValid(interval)) {
                total += interval;
                count++;
            }
        }

        if (count == 0) {
            return new Result(-1, -1, -1, 0);
        }

        double average = total / count;
        double squaredDifferenceTotal = 0;
        int stutters = 0;
        double stutterThreshold = average * STUTTER_MULTIPLIER;

        for (Double interval : frameIntervalsMs) {
            if (!isValid(interval)) continue;

            double difference = interval - average;
            squaredDifferenceTotal += difference * difference;

            if (interval > stutterThreshold) {
                stutters++;
            }
        }

        double variance = squaredDifferenceTotal / count;
        return new Result(average, Math.sqrt(variance), stutters, count);
    }

    private static boolean isValid(Double interval) {
        return interval != null
                && Double.isFinite(interval)
                && interval > 0
                && interval <= MAX_INTERVAL_MS;
    }
}
