package com.onyx.perfmonitor;

import java.util.Arrays;

/** Calculates per-core CPU utilization from consecutive /proc/stat snapshots. */
final class CpuUsageTracker {
    private boolean haveBaseline;
    private final long[] previousTotal = new long[8];
    private final long[] previousIdle = new long[8];

    void reset() {
        haveBaseline = false;
        Arrays.fill(previousTotal, 0);
        Arrays.fill(previousIdle, 0);
    }

    double[] parse(String raw) {
        double[] loads = new double[8];
        long[] currentTotal = new long[8];
        long[] currentIdle = new long[8];
        boolean[] found = new boolean[8];
        Arrays.fill(loads, -1);

        if (raw != null) {
            for (String line : raw.split("\\n")) {
                if (line.length() < 5 || !line.startsWith("cpu")) continue;
                char coreChar = line.charAt(3);
                if (coreChar < '0' || coreChar > '7') continue;
                int id = coreChar - '0';
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 5) continue;
                try {
                    long total = 0;
                    for (int j = 1; j < fields.length; j++) {
                        total += Long.parseLong(fields[j]);
                    }
                    long idle = Long.parseLong(fields[4]);
                    if (fields.length > 5) idle += Long.parseLong(fields[5]);
                    currentTotal[id] = total;
                    currentIdle[id] = idle;
                    found[id] = true;
                } catch (Exception ignored) {
                    // Ignore malformed core rows; other cores can still be used.
                }
            }
        }

        if (haveBaseline) {
            for (int i = 0; i < 8; i++) {
                if (found[i]) {
                    long totalDiff = currentTotal[i] - previousTotal[i];
                    long idleDiff = currentIdle[i] - previousIdle[i];
                    loads[i] = totalDiff > 0
                            ? Math.max(0, Math.min(100,
                            100.0 * (totalDiff - idleDiff) / totalDiff))
                            : -1;
                }
            }
        }

        for (int i = 0; i < 8; i++) {
            if (found[i]) {
                previousTotal[i] = currentTotal[i];
                previousIdle[i] = currentIdle[i];
            }
        }

        haveBaseline = true;
        return loads;
    }
}
