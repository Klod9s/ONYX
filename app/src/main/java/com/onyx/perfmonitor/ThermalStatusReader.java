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

    static boolean isElevated(int status) {
        return status >= LIGHT && status <= SHUTDOWN;
    }

    static boolean isSevere(int status) {
        return status >= SEVERE && status <= SHUTDOWN;
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
