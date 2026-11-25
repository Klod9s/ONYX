package com.onyx.perfmonitor;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;

/** Shared recording preferences and per-session sample filtering. */
final class SessionDataPreferences {
    static final String PREFS = "data_collection";
    static final String FPS = "collect_fps";
    static final String BATTERY = "collect_battery";
    static final String CPU = "collect_cpu";
    static final String GPU = "collect_gpu";
    static final String RAM = "collect_ram";

    private SessionDataPreferences() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context, String key) {
        return get(context).getBoolean(key, true);
    }

    static boolean framePacingEnabled(Context context) {
        return enabled(context, FPS);
    }

    static Sample forSession(Sample source, Context context) {
        if (source == null) return null;
        Sample s = new Sample();
        s.timeMs = source.timeMs;
        s.app = source.app;
        s.charging = source.charging;

        if (enabled(context, FPS)) {
            s.fps = source.fps;
            s.fpsSource = source.fpsSource;
            s.averageFrameTimeMs = source.averageFrameTimeMs;
            s.frameTimeStdDevMs = source.frameTimeStdDevMs;
            s.stutterCount = source.stutterCount;
            s.frameIntervalsMs.addAll(source.frameIntervalsMs);
        }

        if (enabled(context, BATTERY)) {
            s.currentMa = source.currentMa;
            s.voltage = source.voltage;
            s.power = source.power;
            s.temperatureC = source.temperatureC;
            s.battery = source.battery;
            s.secondsPerPercent = source.secondsPerPercent;
        } else {
            s.currentMa = s.voltage = s.power = s.temperatureC = -1;
            s.battery = -1;
        }

        if (enabled(context, CPU)) {
            s.cpu = Arrays.copyOf(source.cpu, source.cpu.length);
            s.cpuFreqMHz = Arrays.copyOf(source.cpuFreqMHz, source.cpuFreqMHz.length);
            s.cpuTemperatureC = source.cpuTemperatureC;
        } else {
            Arrays.fill(s.cpu, -1);
            Arrays.fill(s.cpuFreqMHz, -1);
        }

        if (enabled(context, GPU)) {
            s.gpuFreqMHz = source.gpuFreqMHz;
            s.gpuLoadPercent = source.gpuLoadPercent;
            s.gpuTemperatureC = source.gpuTemperatureC;
        }

        if (enabled(context, RAM)) {
            s.ramUsagePercent = source.ramUsagePercent;
        }
        return s;
    }
}
