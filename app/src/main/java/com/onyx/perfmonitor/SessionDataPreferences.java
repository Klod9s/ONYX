package com.onyx.perfmonitor;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;

/** Shared recording preferences and per-session sample filtering. */
final class SessionDataPreferences {
    static final String PREFS = "data_collection";

    // Legacy category keys are retained as migration fallbacks.
    static final String FPS = "collect_fps";
    static final String BATTERY = "collect_battery";
    static final String CPU = "collect_cpu";
    static final String GPU = "collect_gpu";
    static final String RAM = "collect_ram";

    static final String FPS_VALUE = "collect_fps_value";
    static final String FRAME_AVERAGE = "collect_frame_average";
    static final String FRAME_VARIATION = "collect_frame_variation";
    static final String FRAME_STUTTERS = "collect_frame_stutters";
    static final String FRAME_INTERVALS = "collect_frame_intervals";

    static final String BATTERY_LEVEL = "collect_battery_level";
    static final String BATTERY_CURRENT = "collect_battery_current";
    static final String BATTERY_VOLTAGE = "collect_battery_voltage";
    static final String BATTERY_POWER = "collect_battery_power";
    static final String BATTERY_DRAIN = "collect_battery_drain";
    static final String BATTERY_TEMP = "collect_battery_temp";

    static final String CPU_USAGE = "collect_cpu_usage";
    static final String CPU_TEMP = "collect_cpu_temp";
    static final String CPU_FREQUENCY = "collect_cpu_frequency";

    static final String GPU_USAGE = "collect_gpu_usage";
    static final String GPU_TEMP = "collect_gpu_temp";
    static final String GPU_FREQUENCY = "collect_gpu_frequency";

    static final String RAM_USAGE = "collect_ram_usage";

    private SessionDataPreferences() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context, String key) {
        return get(context).getBoolean(key, true);
    }

    private static boolean metricEnabled(Context context, String key, String legacyCategory) {
        SharedPreferences prefs = get(context);
        return prefs.getBoolean(key, prefs.getBoolean(legacyCategory, true));
    }

    static boolean metricEnabledForSettings(
            Context context, String key, String legacyCategory) {
        return metricEnabled(context, key, legacyCategory);
    }

    static boolean framePacingEnabled(Context context) {
        return metricEnabled(context, FRAME_AVERAGE, FPS)
                || metricEnabled(context, FRAME_VARIATION, FPS)
                || metricEnabled(context, FRAME_STUTTERS, FPS)
                || metricEnabled(context, FRAME_INTERVALS, FPS);
    }

    static Sample forSession(Sample source, Context context) {
        if (source == null) return null;
        Sample s = new Sample();
        s.timeMs = source.timeMs;
        s.app = source.app;
        s.charging = source.charging;

        if (metricEnabled(context, FPS_VALUE, FPS)) {
            s.fps = source.fps;
            s.fpsSource = source.fpsSource;
        }

        if (metricEnabled(context, FRAME_AVERAGE, FPS)) {
            s.averageFrameTimeMs = source.averageFrameTimeMs;
        }
        if (metricEnabled(context, FRAME_VARIATION, FPS)) {
            s.frameTimeStdDevMs = source.frameTimeStdDevMs;
        }
        if (metricEnabled(context, FRAME_STUTTERS, FPS)) {
            s.stutterCount = source.stutterCount;
        }
        if (metricEnabled(context, FRAME_INTERVALS, FPS)) {
            s.frameIntervalsMs.addAll(source.frameIntervalsMs);
        }

        if (metricEnabled(context, BATTERY_CURRENT, BATTERY)) {
            s.currentMa = source.currentMa;
        }
        if (metricEnabled(context, BATTERY_VOLTAGE, BATTERY)) {
            s.voltage = source.voltage;
        }
        if (metricEnabled(context, BATTERY_POWER, BATTERY)) {
            s.power = source.power;
        }
        if (metricEnabled(context, BATTERY_TEMP, BATTERY)) {
            s.temperatureC = source.temperatureC;
        }
        if (metricEnabled(context, BATTERY_LEVEL, BATTERY)) {
            s.battery = source.battery;
        } else {
            s.battery = -1;
        }
        if (metricEnabled(context, BATTERY_DRAIN, BATTERY)) {
            s.secondsPerPercent = source.secondsPerPercent;
        }

        if (metricEnabled(context, CPU_USAGE, CPU)) {
            s.cpu = Arrays.copyOf(source.cpu, source.cpu.length);
        } else {
            Arrays.fill(s.cpu, -1);
        }
        if (metricEnabled(context, CPU_FREQUENCY, CPU)) {
            s.cpuFreqMHz = Arrays.copyOf(source.cpuFreqMHz, source.cpuFreqMHz.length);
        } else {
            Arrays.fill(s.cpuFreqMHz, -1);
        }
        if (metricEnabled(context, CPU_TEMP, CPU)) {
            s.cpuTemperatureC = source.cpuTemperatureC;
        }

        if (metricEnabled(context, GPU_FREQUENCY, GPU)) {
            s.gpuFreqMHz = source.gpuFreqMHz;
        }
        if (metricEnabled(context, GPU_USAGE, GPU)) {
            s.gpuLoadPercent = source.gpuLoadPercent;
        }
        if (metricEnabled(context, GPU_TEMP, GPU)) {
            s.gpuTemperatureC = source.gpuTemperatureC;
        }

        if (metricEnabled(context, RAM_USAGE, RAM)) {
            s.ramUsagePercent = source.ramUsagePercent;
        }
        return s;
    }
}
