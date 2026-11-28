package com.onyx.perfmonitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Determines which metrics this device actually exposes, using the same
 * sampling path the Monitor page uses.
 *
 * A metric counts as available when at least one of the supplied readings
 * produced a value, so a single transient miss does not mark it unsupported.
 */
final class DeviceCapabilityCheck {

    static final int AVAILABLE = 0;
    static final int UNTESTED = 1;
    static final int UNAVAILABLE = 2;

    static final class Row {
        final String label;
        final int state;

        Row(String label, int state) {
            this.label = label;
            this.state = state;
        }
    }

    /** Returns a short detail string, or null when the metric is unavailable. */
    private interface Probe {
        boolean available(Sample sample);
    }

    private DeviceCapabilityCheck() {
    }

    static List<Row> evaluate(List<Sample> samples) {
        List<Row> rows = new ArrayList<>();

        // FPS is only readable when a SurfaceFlinger latency layer or frame
        // counter exists for the app in front. Outside a game there may be
        // nothing to read, which is not the same as the phone being unable to.
        boolean sawFps = false;
        if (samples != null) {
            for (Sample sample : samples) {
                if (sample != null && sample.fps >= 0) sawFps = true;
            }
        }
        rows.add(new Row("FPS", sawFps ? AVAILABLE : UNTESTED));
        rows.add(row(samples, "CPU usage", sample -> anyCore(sample.cpu)));
        rows.add(row(samples, "CPU frequency", sample -> anyCore(sample.cpuFreqMHz)));
        rows.add(row(samples, "CPU temperature", sample -> sample.cpuTemperatureC >= 0));
        rows.add(row(samples, "GPU load", sample -> sample.gpuLoadPercent >= 0));
        rows.add(row(samples, "GPU frequency", sample -> sample.gpuFreqMHz >= 0));
        rows.add(row(samples, "GPU temperature", sample -> sample.gpuTemperatureC >= 0));
        rows.add(row(samples, "RAM usage", sample -> sample.ramUsagePercent >= 0));
        rows.add(row(samples, "Battery level", sample -> sample.battery >= 0));
        rows.add(row(samples, "Battery current", sample -> sample.currentMa != -1));
        rows.add(row(samples, "Battery voltage", sample -> sample.voltage > 0));
        rows.add(row(samples, "Battery power", sample -> sample.power != -1));
        rows.add(row(samples, "Battery temperature", sample -> sample.temperatureC >= 0));
        rows.add(row(samples, "Thermal status", sample ->
                sample.thermalStatus != ThermalStatusReader.UNAVAILABLE));
        rows.add(row(samples, "Cooling devices", sample ->
                sample.coolingDeviceActive != ThermalCoolingReader.UNAVAILABLE));

        // Frame pacing needs a live game surface, so it cannot be judged from
        // inside the app. Reporting "unsupported" here would be wrong.
        rows.add(new Row("Frame pacing", UNTESTED));

        return rows;
    }

    private static Row row(List<Sample> samples, String label, Probe probe) {
        if (samples != null) {
            for (Sample sample : samples) {
                if (sample != null && probe.available(sample)) {
                    return new Row(label, AVAILABLE);
                }
            }
        }
        return new Row(label, UNAVAILABLE);
    }


    private static boolean anyCore(double[] values) {
        if (values == null) return false;
        for (double value : values) {
            if (value >= 0) return true;
        }
        return false;
    }

}