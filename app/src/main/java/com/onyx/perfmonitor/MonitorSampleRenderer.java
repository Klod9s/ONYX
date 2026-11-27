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

    /** The tile builder stores a GlassStyle.TileTag in the value view's tag. */
    private static void setLevel(TextView value, float level) {
        if (value == null || !(value.getTag() instanceof GlassStyle.TileTag)) return;
        GlassStyle.DotMatrixView m = ((GlassStyle.TileTag) value.getTag()).matrix;
        if (m != null) m.setLevel(level);
    }

    private static String compactSourceLabel(String source) {
        if (source == null || source.isEmpty() || "none".equalsIgnoreCase(source)) {
            return "";
        }
        if ("BLAST SF latency".equals(source)) return "BLAST";
        if ("SF latency".equals(source)) return "SF LAT";
        if ("SF Counter".equals(source)) return "SF COUNT";
        if ("TimeStats".equals(source)) return "TIMESTATS";
        if ("Display FPS".equals(source)) return "DISPLAY";
        return source.toUpperCase(Locale.US);
    }

    static void render(Sample s, TextView fps, TextView cpuTile,
            TextView gpuTile, TextView power, TextView powerState,
            TextView current, TextView voltage, TextView battery,
            TextView temperature, int appAccentColor) {

        setTextIfChanged(fps, s.fps < 0
                ? "—"
                : String.format(Locale.US, "%.0f", s.fps));

        if (fps != null && fps.getTag() instanceof GlassStyle.TileTag) {
            GlassStyle.TileTag tag = (GlassStyle.TileTag) fps.getTag();
            String sourceLabel = compactSourceLabel(s.fpsSource);
            // This tile's graph is a reciprocal-FPS estimate, not a measured
            // presentation interval. Keep that distinction visible.
            setTextIfChanged(tag.label, sourceLabel.isEmpty()
                    ? "FPS · EST"
                    : "FPS · " + sourceLabel + " · EST");

            GlassStyle.FrameGraphView g = tag.graph;
            if (g != null) {
                if (s.fps > 0 && !Double.isNaN(s.fps) && !Double.isInfinite(s.fps)) {
                    g.addFrameTime((float) (1000.0 / s.fps));
                } else {
                    g.noData();
                }
            }
        }

        double cpuSum = 0;
        int cpuCount = 0;
        for (double v : s.cpu) {
            if (v >= 0) { cpuSum += v; cpuCount++; }
        }
        setTextIfChanged(cpuTile, cpuCount > 0
                ? String.format(Locale.US, "%.0f%%", cpuSum / cpuCount)
                : "—");
        setLevel(cpuTile, cpuCount > 0 ? (float) (cpuSum / cpuCount / 100.0) : 0f);

        setTextIfChanged(gpuTile, s.gpuLoadPercent < 0
                ? "—"
                : String.format(Locale.US, "%.0f%%", s.gpuLoadPercent));
        setLevel(gpuTile, s.gpuLoadPercent < 0 ? 0f : (float) (s.gpuLoadPercent / 100.0));

        setTextIfChanged(power,
                s.power == -1
                        ? "—"
                        : String.format(Locale.US, "%.2f W", s.power));

        setTextIfChanged(powerState,
                s.charging ? "CHARGING" : "DISCHARGING");
        PowerStateBadgeStyle.apply(powerState, s.charging, appAccentColor);

        setTextIfChanged(current,
                s.currentMa == -1
                        ? "—"
                        : String.format(Locale.US, "%.0f mA", s.currentMa));
        setLevel(current, s.currentMa == -1 ? 0f : (float) (Math.abs(s.currentMa) / 2500.0));

        setTextIfChanged(voltage,
                s.voltage < 0
                        ? "—"
                        : String.format(Locale.US, "%.3f V", s.voltage));
        setLevel(voltage, s.voltage < 0 ? 0f : (float) ((s.voltage - 3.3) / 1.1));

        setTextIfChanged(battery, s.battery < 0 ? "—" : s.battery + "%");
        setLevel(battery, s.battery < 0 ? 0f : s.battery / 100f);

        setTextIfChanged(temperature, s.temperatureC < 0
                ? "—"
                : String.format(Locale.US, "%.1f°C", s.temperatureC));
        setLevel(temperature, s.temperatureC < 0
                ? 0f : (float) ((s.temperatureC - 20.0) / 30.0));
    }
}
