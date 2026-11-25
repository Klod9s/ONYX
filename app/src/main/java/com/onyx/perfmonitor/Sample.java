
package com.onyx.perfmonitor;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds one performance-monitoring sample.
 * Package-private so the monitoring and session classes
 * in this package can access it.
 */
final class Sample {

    long timeMs;
    String app;

    double fps = -1;
    double currentMa;
    double voltage;
    double power;
    double temperatureC;
    double ramUsagePercent = -1;
    double cpuTemperatureC = -1;
    double gpuTemperatureC = -1;
    int thermalStatus = ThermalStatusReader.UNAVAILABLE;
    int coolingDeviceActive = ThermalCoolingReader.UNAVAILABLE;
    int cpuCoolingState = ThermalCoolingReader.UNAVAILABLE;
    int cpuCoolingMaxState = ThermalCoolingReader.UNAVAILABLE;
    int gpuCoolingState = ThermalCoolingReader.UNAVAILABLE;
    int gpuCoolingMaxState = ThermalCoolingReader.UNAVAILABLE;

    int battery;
    double secondsPerPercent = -1;

    boolean charging;
    boolean batteryUnplugged;

    double[] cpu = new double[8];
    double[] cpuFreqMHz = new double[8];

    double gpuFreqMHz = -1;
    double gpuLoadPercent = -1;

    String fpsSource = "";

    // Frame pacing (-1 means unavailable)
    double averageFrameTimeMs = -1;
    double frameTimeStdDevMs = -1;
    int stutterCount = -1;
    final List<Double> frameIntervalsMs = new ArrayList<>();

    Sample() {
        Arrays.fill(cpuFreqMHz, -1);
    }
}
