package com.onyx.perfmonitor;

import android.widget.TextView;

import java.util.Locale;

/** Renders a monitoring sample into the Monitor page's metric views. */
final class MonitorSampleRenderer {
    private MonitorSampleRenderer() {
    }

    private static void setTextIfChanged(TextView view, String value) {
        if (view != null && !value.contentEquals(view.getText())) {
            view.setText(value);
        }
    }

    static void render(Sample s, TextView fps, TextView cpuTile,
            TextView gpuTile, TextView power, TextView powerState,
            TextView current, TextView voltage, TextView battery,
            TextView temperature) {

        setTextIfChanged(fps, s.fps < 0
                ? "—"
                : String.format(Locale.US, "%.0f", s.fps));

        double cpuSum = 0;
        int cpuCount = 0;
        for (double v : s.cpu) {
            if (v >= 0) { cpuSum += v; cpuCount++; }
        }
        setTextIfChanged(cpuTile, cpuCount > 0
                ? String.format(Locale.US, "%.0f%%", cpuSum / cpuCount)
                : "—");

        setTextIfChanged(gpuTile, s.gpuLoadPercent < 0
                ? "—"
                : String.format(Locale.US, "%.0f%%", s.gpuLoadPercent));

        setTextIfChanged(power,
                s.power == -1
                        ? "—"
                        : String.format(Locale.US, "%.2f W", s.power));

        setTextIfChanged(powerState,
                s.charging ? "charging" : "discharging");

        setTextIfChanged(current,
                s.currentMa == -1
                        ? "—"
                        : String.format(Locale.US, "%.0f mA", s.currentMa));

        setTextIfChanged(voltage,
                s.voltage < 0
                        ? "—"
                        : String.format(Locale.US, "%.3f V", s.voltage));

        setTextIfChanged(battery, s.battery + "%");

        setTextIfChanged(temperature,
                String.format(Locale.US, "%.1f°C", s.temperatureC));
    }
}
