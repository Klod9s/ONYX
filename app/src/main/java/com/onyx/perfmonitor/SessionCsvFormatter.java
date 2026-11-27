/*
 * Copyright (C) 2026 Klod9s
 *
 * This file is part of ONYX Performance Monitor.
 *
 * ONYX Performance Monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ONYX Performance Monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ONYX Performance Monitor. If not, see <https://www.gnu.org/licenses/>.
 */

package com.onyx.perfmonitor;

import android.os.Build;

import java.util.Locale;

/**
 * Builds the CSV representation of a recorded ONYX session.
 *
 * This class is deliberately independent of Activity and Android views so
 * session formatting can be tested and maintained separately.
 */
final class SessionCsvFormatter {

    private SessionCsvFormatter() {
    }

    static void appendHeader(StringBuilder csv) {
        csv.append(
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
                        "Thermal Status,Cooling Device Active\n"
        );
    }

    static void appendSample(StringBuilder csv, Sample sample, android.content.Context context) {
        appendSample(csv, SessionDataPreferences.forSession(sample, context));
    }

    static void appendSample(StringBuilder csv, Sample sample) {
        StringBuilder row = new StringBuilder();

        row.append(formatElapsed(sample.timeMs)).append(',')
                .append(quoteSafeText(sample.app)).append(',')
                .append(sample.fps < 0 ? "" :
                        String.format(Locale.US, "%.0f", sample.fps))
                .append(',')
                .append(String.format(Locale.US,
                        "%.0f,%.3f,%.2f,%d,",
                        sample.currentMa, sample.voltage, sample.power,
                        sample.battery));

        row.append(',')
                .append(String.format(Locale.US,
                        "%.1f", sample.temperatureC));

        for (double value : sample.cpu) {
            row.append(',');
            if (value >= 0) {
                row.append(String.format(Locale.US, "%.1f", value));
            }
        }

        for (double value : sample.cpuFreqMHz) {
            row.append(',');
            if (value >= 0) {
                row.append(String.format(Locale.US, "%.1f", value));
            }
        }

        row.append(',');
        if (sample.gpuFreqMHz >= 0) {
            row.append(String.format(Locale.US, "%.1f", sample.gpuFreqMHz));
        }

        row.append(',');
        if (sample.gpuLoadPercent >= 0) {
            row.append(String.format(Locale.US, "%.1f", sample.gpuLoadPercent));
        }

        row.append(',');
        if (sample.averageFrameTimeMs >= 0) {
            row.append(String.format(Locale.US, "%.2f", sample.averageFrameTimeMs));
        }

        row.append(',');
        if (sample.frameTimeStdDevMs >= 0) {
            row.append(String.format(Locale.US, "%.2f", sample.frameTimeStdDevMs));
        }

        row.append(',');
        if (sample.stutterCount >= 0) {
            row.append(sample.stutterCount);
        }

        row.append(',').append(quoteSafeText(sample.fpsSource));

        row.append(',').append('"');
        for (int i = 0; i < sample.frameIntervalsMs.size(); i++) {
            if (i > 0) row.append(';');
            row.append(String.format(Locale.US, "%.3f", sample.frameIntervalsMs.get(i)));
        }
        row.append('"').append(',');
        if (sample.ramUsagePercent >= 0) {
            row.append(String.format(Locale.US, "%.1f", sample.ramUsagePercent));
        }

        row.append(',');
        if (sample.cpuTemperatureC >= 0) {
            row.append(String.format(Locale.US, "%.1f", sample.cpuTemperatureC));
        }

        row.append(',');
        if (sample.gpuTemperatureC >= 0) {
            row.append(String.format(Locale.US, "%.1f", sample.gpuTemperatureC));
        }

        row.append(',').append(sample.thermalStatus >= 0
                ? Integer.toString(sample.thermalStatus) : "");
        row.append(',').append(sample.coolingDeviceActive >= 0
                ? Integer.toString(sample.coolingDeviceActive) : "");

        csv.append(row).append('\n');
    }

    static String buildSessionCsv(
            StringBuilder readings, String summary, String monitoringMode) {
        StringBuilder output = new StringBuilder();

        output.append("SESSION INFO\n");
        output.append("Device,").append(quoteSafeText(deviceName())).append('\n');
        output.append("Android,").append(Build.VERSION.RELEASE).append('\n');
        output.append("Mode,").append(monitoringMode).append('\n');
        output.append("Summary\n");
        output.append(quoteSafeText(summary)).append("\n\n");

        output.append("PERFORMANCE READINGS\n");
        output.append(readings);

        return output.toString();
    }


    /**
     * Quotes a CSV text cell and neutralizes spreadsheet formulas.
     * A leading apostrophe makes formula-like values plain text in common
     * spreadsheet applications; CSV quoting alone does not prevent execution.
     */
    private static String quoteSafeText(String value) {
        String safe = value == null ? "" : value;
        int first = 0;
        while (first < safe.length()
                && (Character.isWhitespace(safe.charAt(first))
                || Character.isISOControl(safe.charAt(first)))) {
            first++;
        }

        if (first < safe.length()) {
            char initial = safe.charAt(first);
            if (initial == '=' || initial == '+' || initial == '-'
                    || initial == '@'
                    || Character.isISOControl(safe.charAt(0))) {
                safe = "'" + safe;
            }
        } else if (!safe.isEmpty() && Character.isISOControl(safe.charAt(0))) {
            safe = "'" + safe;
        }

        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String formatElapsed(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private static String deviceName() {
        String soc = "";
        if (Build.VERSION.SDK_INT >= 31
                && !"unknown".equalsIgnoreCase(Build.SOC_MODEL)) {
            soc = " (" + Build.SOC_MODEL + ")";
        }
        return Build.MANUFACTURER + " " + Build.MODEL + soc;
    }
}
