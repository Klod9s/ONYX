package com.onyx.perfmonitor;

/**
 * Parses CPU/GPU cooling-device state lines (type currentState maxState).
 * Battery and unrelated devices are deliberately excluded.
 */
final class ThermalCoolingReader {
    static final int UNAVAILABLE = -1;
    static final int IDLE = 0;
    static final int ACTIVE = 1;

    static final class Reading {
        final int cpuState;
        final int cpuMaxState;
        final int gpuState;
        final int gpuMaxState;

        Reading(int cpuState, int cpuMaxState, int gpuState, int gpuMaxState) {
            this.cpuState = cpuState;
            this.cpuMaxState = cpuMaxState;
            this.gpuState = gpuState;
            this.gpuMaxState = gpuMaxState;
        }

        int overallState() {
            if (cpuState < 0 && gpuState < 0) return UNAVAILABLE;
            return (cpuState > 0 || gpuState > 0) ? ACTIVE : IDLE;
        }
    }

    private ThermalCoolingReader() {
    }

    static Reading read(String raw) {
        int cpuState = UNAVAILABLE, cpuMax = UNAVAILABLE;
        int gpuState = UNAVAILABLE, gpuMax = UNAVAILABLE;
        double cpuRatio = -1, gpuRatio = -1;
        if (raw == null || raw.trim().isEmpty()) {
            return new Reading(cpuState, cpuMax, gpuState, gpuMax);
        }

        for (String line : raw.split("\\r?\\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 3) continue;
            String type = parts[0].toLowerCase(java.util.Locale.ROOT);
            boolean cpu = type.contains("cpu") || type.contains("cpufreq");
            boolean gpu = !cpu && (type.contains("gpu") || type.contains("devfreq"));
            if (!cpu && !gpu) continue;
            try {
                int current = Integer.parseInt(parts[1]);
                int maximum = Integer.parseInt(parts[2]);
                if (current < 0 || maximum <= 0 || current > maximum) continue;
                double ratio = current / (double) maximum;
                if (cpu && ratio > cpuRatio) {
                    cpuRatio = ratio;
                    cpuState = current;
                    cpuMax = maximum;
                } else if (gpu && ratio > gpuRatio) {
                    gpuRatio = ratio;
                    gpuState = current;
                    gpuMax = maximum;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed or unavailable device states.
            }
        }
        return new Reading(cpuState, cpuMax, gpuState, gpuMax);
    }

    static int parse(String raw) {
        return read(raw).overallState();
    }
}
