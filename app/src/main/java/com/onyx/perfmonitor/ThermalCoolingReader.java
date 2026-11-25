package com.onyx.perfmonitor;

/**
 * Interprets CPU/GPU cooling-device state lines emitted by sysfs sampling.
 * Returns 1 when a relevant cooling device is active, 0 when relevant devices
 * are readable and all are idle, or -1 when no usable device data is present.
 */
final class ThermalCoolingReader {
    static final int UNAVAILABLE = -1;
    static final int IDLE = 0;
    static final int ACTIVE = 1;

    private ThermalCoolingReader() {
    }

    static int parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return UNAVAILABLE;
        boolean found = false;
        for (String line : raw.split("\\r?\\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 3 || !isRelevantType(parts[0])) continue;
            try {
                int current = Integer.parseInt(parts[1]);
                int maximum = Integer.parseInt(parts[2]);
                if (current < 0 || maximum <= 0 || current > maximum) continue;
                found = true;
                if (current > 0) return ACTIVE;
            } catch (NumberFormatException ignored) {
                // Ignore unavailable or malformed cooling-device state.
            }
        }
        return found ? IDLE : UNAVAILABLE;
    }

    private static boolean isRelevantType(String type) {
        String value = type.toLowerCase(java.util.Locale.ROOT);
        return value.contains("cpu") || value.contains("gpu")
                || value.contains("cpufreq") || value.contains("devfreq");
    }
}
