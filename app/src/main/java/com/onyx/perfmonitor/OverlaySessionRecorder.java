package com.onyx.perfmonitor;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Writes sessions started and stopped from the floating overlay. */
final class OverlaySessionRecorder {
    private static final String PREFS = "overlay_session_recorder";
    private static final String ROWS_FILE = "overlay_session_rows.tmp";
    private static final String HEADER =
            "Time,App,FPS,Current (mA),Voltage (V),Power (W)," +
            "Battery (%),Temperature (°C)," +
            "CPU 0 (%),CPU 1 (%),CPU 2 (%),CPU 3 (%)," +
            "CPU 4 (%),CPU 5 (%),CPU 6 (%),CPU 7 (%)," +
            "CPU 0 Frequency (MHz),CPU 1 Frequency (MHz)," +
            "CPU 2 Frequency (MHz),CPU 3 Frequency (MHz)," +
            "CPU 4 Frequency (MHz),CPU 5 Frequency (MHz)," +
            "CPU 6 Frequency (MHz),CPU 7 Frequency (MHz)," +
            "GPU Frequency (MHz),GPU Load (%)," +
            "Average Frame Time (ms),Frame Time Std Dev (ms)," +
            "Stutter Count,FPS Source,Frame Intervals (ms)," +
            "RAM Usage (%),CPU Temperature (°C),GPU Temperature (°C)," +
            "Thermal Status,Cooling Device Active\n";

    private final Context context;
    private final android.content.SharedPreferences prefs;
    private long previousSampleTime = -1;
    private int previousBattery = -1;

    OverlaySessionRecorder(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized void start() {
        File rows = new File(context.getFilesDir(), ROWS_FILE);
        if (rows.exists()) rows.delete();
        try (FileOutputStream out = new FileOutputStream(rows, false)) {
            out.write(HEADER.getBytes(StandardCharsets.UTF_8));
            long now = System.currentTimeMillis();
            prefs.edit()
                    .putLong("start_ms", now)
                    .putInt("start_battery", -1)
                    .putInt("end_battery", -1)
                    .putString("mode", "ANDROID")
                    .putBoolean("recording", true)
                    .apply();
            previousSampleTime = -1;
            previousBattery = -1;
        } catch (Exception ignored) {
            prefs.edit().putBoolean("recording", false).apply();
        }
    }

    synchronized boolean isRecording() {
        return prefs.getBoolean("recording", false);
    }

    synchronized void append(Sample sample, String mode) {
        if (sample == null || !prefs.getBoolean("recording", false)) return;
        long start = prefs.getLong("start_ms", System.currentTimeMillis());
        long elapsed = Math.max(0, System.currentTimeMillis() - start);
        sample.timeMs = elapsed;

        if (previousSampleTime >= 0 && previousBattery >= 0
                && previousBattery <= 100 && sample.battery >= 0
                && sample.battery <= 100) {
            int drop = previousBattery - sample.battery;
            long interval = elapsed - previousSampleTime;
            if (drop > 0 && interval > 0) {
                sample.secondsPerPercent = (interval / 1000.0) / drop;
            }
        }
        previousSampleTime = elapsed;
        previousBattery = sample.battery;

        if (SessionDataPreferences.metricEnabledForSettings(
                context, SessionDataPreferences.BATTERY_LEVEL, SessionDataPreferences.BATTERY)
                && prefs.getInt("start_battery", -1) < 0
                && sample.battery >= 0 && sample.battery <= 100) {
            prefs.edit().putInt("start_battery", sample.battery).apply();
        }
        if (SessionDataPreferences.metricEnabledForSettings(
                context, SessionDataPreferences.BATTERY_LEVEL, SessionDataPreferences.BATTERY)
                && sample.battery >= 0 && sample.battery <= 100) {
            prefs.edit().putInt("end_battery", sample.battery).apply();
        }
        if (mode != null) prefs.edit().putString("mode", mode).apply();

        File rows = new File(context.getFilesDir(), ROWS_FILE);
        try (FileOutputStream out = new FileOutputStream(rows, true)) {
            Sample sessionSample = SessionDataPreferences.forSession(sample, context);
            out.write(makeRow(sessionSample).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    synchronized void finish() {
        prefs.edit().putBoolean("recording", false).apply();
        long start = prefs.getLong("start_ms", System.currentTimeMillis());
        long duration = Math.max(0, System.currentTimeMillis() - start);
        int first = prefs.getInt("start_battery", -1);
        int last = prefs.getInt("end_battery", -1);
        String summary;
        if (!SessionDataPreferences.metricEnabledForSettings(
                context, SessionDataPreferences.BATTERY_LEVEL, SessionDataPreferences.BATTERY)) {
            summary = String.format(Locale.US,
                    "Run time: %.1f minutes\nBattery level: not collected",
                    duration / 60000.0);
        } else if (first >= 0 && last >= 0) {
            summary = String.format(Locale.US,
                    "Run time: %.1f minutes\nBattery level: %d%% → %d%% (%d%% change)",
                    duration / 60000.0, first, last, last - first);
        } else {
            summary = String.format(Locale.US,
                    "Run time: %.1f minutes\nBattery level: unavailable",
                    duration / 60000.0);
        }

        File rows = new File(context.getFilesDir(), ROWS_FILE);
        if (!rows.exists() || rows.length() <= HEADER.length()) {
            rows.delete();
            return;
        }

        String stamp = new SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date(start));
        String fileName = "session_" + stamp + ".csv";
        File destination = new File(context.getFilesDir(), fileName);
        int suffix = 1;
        while (destination.exists()) {
            fileName = "session_" + stamp + "_" + suffix++ + ".csv";
            destination = new File(context.getFilesDir(), fileName);
        }

        String device = deviceName();
        try (FileOutputStream out = new FileOutputStream(destination, false)) {
            String meta = "SESSION INFO\nDevice,\"" +
                    device.replace("\"", "\"\"") + "\"\n" +
                    "Android," + Build.VERSION.RELEASE + "\n" +
                    "Mode," + prefs.getString("mode", "ANDROID") + "\n" +
                    "Summary\n\"" + summary.replace("\"", "\"\"") +
                    "\"\n\nPERFORMANCE READINGS\n";
            out.write(meta.getBytes(StandardCharsets.UTF_8));
            try (FileInputStream in = new FileInputStream(rows)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            }
        } catch (Exception ignored) {
            destination.delete();
        } finally {
            rows.delete();
        }
    }

    private String makeRow(Sample s) {
        StringBuilder row = new StringBuilder();
        row.append(String.format(Locale.US, "%02d:%02d",
                s.timeMs / 60000, (s.timeMs / 1000) % 60)).append(',')
                .append("\"").append((s.app == null ? "Unknown" : s.app)
                        .replace("\"", "\"\"")).append("\",")
                .append(s.fps < 0 ? "" : String.format(Locale.US, "%.0f", s.fps))
                .append(',')
                .append(String.format(Locale.US, "%.0f,%.3f,%.2f,%d",
                        s.currentMa, s.voltage, s.power, s.battery));
        row.append(',').append(String.format(Locale.US, "%.1f", s.temperatureC));
        for (double value : s.cpu) {
            row.append(',');
            if (value >= 0) row.append(String.format(Locale.US, "%.1f", value));
        }
        for (double value : s.cpuFreqMHz) {
            row.append(',');
            if (value >= 0) row.append(String.format(Locale.US, "%.1f", value));
        }
        row.append(',');
        if (s.gpuFreqMHz >= 0) row.append(String.format(Locale.US, "%.1f", s.gpuFreqMHz));
        row.append(',');
        if (s.gpuLoadPercent >= 0) row.append(String.format(Locale.US, "%.1f", s.gpuLoadPercent));
        row.append(',');
        if (s.averageFrameTimeMs >= 0) row.append(String.format(Locale.US, "%.2f", s.averageFrameTimeMs));
        row.append(',');
        if (s.frameTimeStdDevMs >= 0) row.append(String.format(Locale.US, "%.2f", s.frameTimeStdDevMs));
        row.append(',');
        if (s.stutterCount >= 0) row.append(s.stutterCount);
        row.append(',');
        if (s.fpsSource != null) row.append(s.fpsSource);
        row.append(',').append('"');
        for (int i = 0; i < s.frameIntervalsMs.size(); i++) {
            if (i > 0) row.append(';');
            row.append(String.format(Locale.US, "%.3f", s.frameIntervalsMs.get(i)));
        }
        row.append('"').append(',');
        if (s.ramUsagePercent >= 0) {
            row.append(String.format(Locale.US, "%.1f", s.ramUsagePercent));
        }
        row.append(',');
        if (s.cpuTemperatureC >= 0) {
            row.append(String.format(Locale.US, "%.1f", s.cpuTemperatureC));
        }
        row.append(',');
        if (s.gpuTemperatureC >= 0) {
            row.append(String.format(Locale.US, "%.1f", s.gpuTemperatureC));
        }
        row.append(',').append(s.thermalStatus >= 0
                ? Integer.toString(s.thermalStatus) : "");
        row.append(',').append(s.coolingDeviceActive >= 0
                ? Integer.toString(s.coolingDeviceActive) : "");
        return row.append('\n').toString();
    }

    private String deviceName() {
        String soc = "";
        if (Build.VERSION.SDK_INT >= 31
                && !"unknown".equalsIgnoreCase(Build.SOC_MODEL)) {
            soc = " (" + Build.SOC_MODEL + ")";
        }
        return Build.MANUFACTURER + " " + Build.MODEL + soc;
    }
}
