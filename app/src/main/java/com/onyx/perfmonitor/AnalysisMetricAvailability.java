package com.onyx.perfmonitor;

import java.util.List;

/** Shared availability checks for metrics whose valid values may be negative. */
final class AnalysisMetricAvailability {

    private AnalysisMetricAvailability() {
    }

    /**
     * Returns true when a list contains at least one finite signed reading.
     * The existing -1 value remains the unavailable-reading sentinel.
     */
    static boolean hasAnySignedValues(List<Double> values) {
        if (values == null) return false;
        for (Double value : values) {
            if (value != null && Double.isFinite(value) && value != -1) {
                return true;
            }
        }
        return false;
    }
}
