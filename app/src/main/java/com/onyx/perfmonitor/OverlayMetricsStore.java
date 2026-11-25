package com.onyx.perfmonitor;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.Locale;

/**
 * Publishes the latest sampled metrics for the floating overlay.
 *
 * This class deliberately contains no sampling or service lifecycle logic.
 */
public final class OverlayMetricsStore {

    private final SharedPreferences livePrefs;
    private final String[] lastPublished = new String[6];

    public OverlayMetricsStore(Context context) {
        livePrefs = context.getApplicationContext()
                .getSharedPreferences("overlay_live", Context.MODE_PRIVATE);
    }

    public void publish(
            double fps,
            double temperatureC,
            double averageFrameTimeMs,
            double[] cpu,
            double gpuLoadPercent,
            double ramUsagePercent
    ) {
        double cpuTotal = 0;
        int cpuCount = 0;

        if (cpu != null) {
            for (double value : cpu) {
                if (value >= 0) {
                    cpuTotal += value;
                    cpuCount++;
                }
            }
        }

        double averageCpu = cpuCount > 0 ? cpuTotal / cpuCount : -1;

        String[] values = {
                fps < 0 ? "--" : String.valueOf(Math.round(fps)),
                temperatureC < 0 ? "--°C"
                        : String.format(Locale.US, "%.1f°C", temperatureC),
                averageFrameTimeMs < 0 ? "--.- ms"
                        : String.format(Locale.US, "%.1f ms", averageFrameTimeMs),
                averageCpu < 0 ? "--%"
                        : String.format(Locale.US, "%.0f%%", averageCpu),
                gpuLoadPercent < 0 ? "--%"
                        : String.format(Locale.US, "%.0f%%", gpuLoadPercent),
                ramUsagePercent < 0 ? "--%"
                        : String.format(Locale.US, "%.0f%%", ramUsagePercent)
        };

        if (Arrays.equals(values, lastPublished)) return;
        System.arraycopy(values, 0, lastPublished, 0, values.length);

        livePrefs.edit()
                .putString("fps", values[0])
                .putString("temperature", values[1])
                .putString("frame_time", values[2])
                .putString("cpu_load", values[3])
                .putString("gpu_load", values[4])
                .putString("ram_usage", values[5])
                .apply();
    }
}
