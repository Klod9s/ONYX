package com.onyx.perfmonitor;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

/**
 * Reads Android's system thermal severity without inferring throttling from
 * temperature alone. The raw status is retained so the UI can distinguish
 * elevated thermal pressure from more severe conditions.
 */
final class ThermalStatusReader {
    static final int UNAVAILABLE = -1;
    static final int NONE = 0;
    static final int LIGHT = 1;
    static final int MODERATE = 2;
    static final int SEVERE = 3;
    static final int CRITICAL = 4;
    static final int EMERGENCY = 5;
    static final int SHUTDOWN = 6;

    private ThermalStatusReader() {
    }

    static int read(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return UNAVAILABLE;
        }
        try {
            PowerManager manager =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return manager != null ? manager.getCurrentThermalStatus() : UNAVAILABLE;
        } catch (RuntimeException ignored) {
            return UNAVAILABLE;
        }
    }

    // Overlay levels: 0 = battery icon, 1 = light warning, 2 = moderate,
    // 3 = severe or higher. UNAVAILABLE remains distinct from normal, but
    // both use the battery icon because no thermal pressure is confirmed.
    static int overlayLevel(int status) {
        if (status == UNAVAILABLE || status == NONE) return 0;
        if (status == LIGHT) return 1;
        if (status == MODERATE) return 2;
        if (status >= SEVERE && status <= SHUTDOWN) return 3;
        return 0;
    }

    static boolean isElevated(int status) {
        return overlayLevel(status) > 0;
    }

    static boolean isSevere(int status) {
        return overlayLevel(status) >= 3;
    }

    static String label(int status) {
        switch (status) {
            case NONE: return "Normal";
            case LIGHT: return "Light";
            case MODERATE: return "Moderate";
            case SEVERE: return "Severe";
            case CRITICAL: return "Critical";
            case EMERGENCY: return "Emergency";
            case SHUTDOWN: return "Shutdown";
            default: return "Unavailable";
        }
    }
}
