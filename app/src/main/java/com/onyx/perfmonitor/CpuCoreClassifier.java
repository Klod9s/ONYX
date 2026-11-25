package com.onyx.perfmonitor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;

/**
 * Detects CPU core classes from Linux CPU capacity data, falling back to
 * maximum-frequency data when capacity is unavailable.
 *
 * CPU numbering is deliberately not used as a performance assumption:
 * the detected values determine each core's class.
 */
final class CpuCoreClassifier {

    static final int UNKNOWN = 0;
    static final int EFFICIENCY = 1;
    static final int NORMAL = 2;
    static final int PERFORMANCE = 3;

    private static final int CORE_COUNT = 8;
    private static final int[] CORE_TYPES = detectCoreTypes();

    private CpuCoreClassifier() {
    }

    static int getCoreType(int coreIndex) {
        if (coreIndex < 0 || coreIndex >= CORE_TYPES.length) {
            return UNKNOWN;
        }
        return CORE_TYPES[coreIndex];
    }

    static String getDisplayName(int coreIndex) {
        String name = "CPU " + coreIndex;
        switch (getCoreType(coreIndex)) {
            case EFFICIENCY:
                return name + " · EFFICIENCY";
            case NORMAL:
                return name + " · NORMAL";
            case PERFORMANCE:
                return name + " · PERFORMANCE";
            default:
                return name;
        }
    }

    static int[] classify(long[] capacities) {
        int[] result = new int[capacities.length];
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        int validCount = 0;

        for (long value : capacities) {
            if (value <= 0) continue;
            min = Math.min(min, value);
            max = Math.max(max, value);
            validCount++;
        }

        if (validCount < 2 || max <= min) {
            return result;
        }

        long range = max - min;
        long lowBoundary = min + range / 3;
        long highBoundary = min + (range * 2) / 3;

        for (int i = 0; i < capacities.length; i++) {
            long value = capacities[i];
            if (value <= 0) continue;

            if (max == min) {
                result[i] = UNKNOWN;
            } else if (range < 3 || lowBoundary == highBoundary) {
                // Two-cluster processors have efficiency and performance
                // groups, but no artificial middle group.
                result[i] = value < max ? EFFICIENCY : PERFORMANCE;
            } else if (value <= lowBoundary) {
                result[i] = EFFICIENCY;
            } else if (value >= highBoundary) {
                result[i] = PERFORMANCE;
            } else {
                result[i] = NORMAL;
            }
        }

        return result;
    }

    private static int[] detectCoreTypes() {
        long[] values = readValues("cpu_capacity");
        boolean hasCapacity = hasMultipleValues(values);

        if (!hasCapacity) {
            values = readValues("cpufreq/cpuinfo_max_freq");
        }
        if (!hasMultipleValues(values)) {
            values = readValues("cpufreq/scaling_max_freq");
        }

        return classify(values);
    }

    private static boolean hasMultipleValues(long[] values) {
        long first = -1;
        for (long value : values) {
            if (value <= 0) continue;
            if (first < 0) {
                first = value;
            } else if (value != first) {
                return true;
            }
        }
        return false;
    }

    private static long[] readValues(String relativePath) {
        long[] values = new long[CORE_COUNT];
        Arrays.fill(values, -1);

        for (int i = 0; i < CORE_COUNT; i++) {
            String path = "/sys/devices/system/cpu/cpu" + i + "/" + relativePath;
            try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
                String line = reader.readLine();
                if (line != null) {
                    values[i] = Long.parseLong(line.trim());
                }
            } catch (Exception ignored) {
                // Some kernels restrict individual sysfs attributes.
            }
        }
        return values;
    }
}
