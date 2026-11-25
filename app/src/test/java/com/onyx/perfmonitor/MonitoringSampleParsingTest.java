package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MonitoringSampleParsingTest {

    @Test
    public void parsesDisplayControllerFpsFromRootOutput() {
        assertEquals(59.94, MonitoringEngine.parseFps("fps: 59.94\n"), 0.0001);
        assertEquals(120.0, MonitoringEngine.parseFps("header\nfps: 120\nfooter"), 0.0001);
    }

    @Test
    public void rejectsMissingOrMalformedFps() {
        assertEquals(-1.0, MonitoringEngine.parseFps(null), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseFps("fps: unknown"), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseFps("refresh rate: 60"), 0.0001);
    }

    @Test
    public void parsesMaximumCpuAndGpuThermalZoneTemperatures() {
        Sample sample = new Sample();

        MonitoringEngine.parseThermalTemperatures(
                "CPU 42000\nCPU 51875\nGPU 46300\nGPU 50125\n"
                        + "battery 39000\ninvalid value\nCPU not-a-number\n"
                        + "GPU 0\nCPU 151000",
                sample);

        assertEquals(51.875, sample.cpuTemperatureC, 0.0001);
        assertEquals(50.125, sample.gpuTemperatureC, 0.0001);
    }

    @Test
    public void missingThermalBlocksSetTemperaturesUnavailable() {
        Sample sample = new Sample();
        MonitoringEngine.parseThermalTemperatures("", sample);
        assertEquals(-1.0, sample.cpuTemperatureC, 0.0001);
        assertEquals(-1.0, sample.gpuTemperatureC, 0.0001);

        MonitoringEngine.parseThermalTemperatures(null, sample);
        assertEquals(-1.0, sample.cpuTemperatureC, 0.0001);
        assertEquals(-1.0, sample.gpuTemperatureC, 0.0001);
    }

    @Test
    public void coolingBlockPopulatesOverallCpuAndGpuStates() {
        Sample sample = new Sample();

        MonitoringEngine.applyCoolingReading(
                "thermal-cpufreq-0 2 5\nthermal-gpu-0 1 4\n"
                        + "battery 1 3\n",
                sample);

        assertEquals(ThermalCoolingReader.ACTIVE, sample.coolingDeviceActive);
        assertEquals(2, sample.cpuCoolingState);
        assertEquals(5, sample.cpuCoolingMaxState);
        assertEquals(1, sample.gpuCoolingState);
        assertEquals(4, sample.gpuCoolingMaxState);
    }

    @Test
    public void unavailableCoolingBlockRemainsUnavailable() {
        Sample sample = new Sample();
        MonitoringEngine.applyCoolingReading("", sample);

        assertEquals(ThermalCoolingReader.UNAVAILABLE, sample.coolingDeviceActive);
        assertEquals(ThermalCoolingReader.UNAVAILABLE, sample.cpuCoolingState);
        assertEquals(ThermalCoolingReader.UNAVAILABLE, sample.gpuCoolingState);
    }

    @Test
    public void coolingParserSafelyIgnoresNullSample() {
        MonitoringEngine.applyCoolingReading("cpu 1 2", null);
        // No exception is expected for a null destination sample.
    }

    @Test
    public void convertsGpuFrequencyAcrossCommonDriverUnits() {
        assertEquals(0.587, MonitoringEngine.parseGpuFrequencyMHz("587"), 0.0001);
        assertEquals(0.587, MonitoringEngine.parseGpuFrequencyMHz("587000"), 0.0001);
        assertEquals(587.0, MonitoringEngine.parseGpuFrequencyMHz("587000000"), 0.0001);
        assertEquals(0.5, MonitoringEngine.parseGpuFrequencyMHz("0.5"), 0.0001);
    }

    @Test
    public void rejectsUnavailableOrMalformedGpuFrequencies() {
        assertEquals(-1.0, MonitoringEngine.parseGpuFrequencyMHz(null), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseGpuFrequencyMHz(""), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseGpuFrequencyMHz("-1"), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseGpuFrequencyMHz("NaN"), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseGpuFrequencyMHz("Infinity"), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseGpuFrequencyMHz("unknown"), 0.0001);
    }

    @Test
    public void parsesGpuBusyCountersAndDirectUtilizationFormats() {
        assertEquals(25.0, MonitoringEngine.parseGpuLoadPercent("250 1000 /sys/class/kgsl/gpubusy"), 0.0001);
        assertEquals(42.5, MonitoringEngine.parseGpuLoadPercent("42.5 -1 /sys/devices/gpu/load"), 0.0001);
        assertEquals(73.0, MonitoringEngine.parseGpuLoadPercent("73 /sys/devices/gpu/utilisation"), 0.0001);
        assertEquals(0.0, MonitoringEngine.parseGpuLoadPercent("0 -1 /sys/devices/gpu/load"), 0.0001);
    }

    @Test
    public void clampsGpuBusyCounterRatiosToValidPercentRange() {
        assertEquals(100.0, MonitoringEngine.parseGpuLoadPercent("1200 1000 /sys/class/kgsl/gpubusy"), 0.0001);
    }

    @Test
    public void rejectsInvalidGpuLoadValues() {
        assertEquals(-1.0, MonitoringEngine.parseGpuLoadPercent(null), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseGpuLoadPercent(""), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseGpuLoadPercent("-1 -1 /sys/devices/gpu/load"), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseGpuLoadPercent("101 /sys/devices/gpu/load"), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseGpuLoadPercent("20 0 /sys/class/kgsl/gpubusy"), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseGpuLoadPercent("NaN /sys/devices/gpu/load"), 0.0001);
        assertEquals(-1.0, MonitoringEngine.parseGpuLoadPercent("broken output"), 0.0001);
    }

    @Test
    public void convertsPerCoreCpuFrequenciesFromKHzToMHzAcrossAllEightCores() {
        double[] frequencies = new double[8];
        MonitoringEngine.parseCpuFrequencies(
                "300000 300000 768000 768000 1800000 1800000 2016000 2016000",
                frequencies);

        assertEquals(300.0, frequencies[0], 0.0001);
        assertEquals(300.0, frequencies[1], 0.0001);
        assertEquals(768.0, frequencies[2], 0.0001);
        assertEquals(768.0, frequencies[3], 0.0001);
        assertEquals(1800.0, frequencies[4], 0.0001);
        assertEquals(1800.0, frequencies[5], 0.0001);
        assertEquals(2016.0, frequencies[6], 0.0001);
        assertEquals(2016.0, frequencies[7], 0.0001);
    }

    @Test
    public void keepsMalformedCpuFrequencyReadingsUnavailableWithoutShiftingCores() {
        double[] frequencies = new double[8];
        MonitoringEngine.parseCpuFrequencies(
                "300000 broken 0 -1 NaN Infinity 1800000 2016000",
                frequencies);

        assertEquals(300.0, frequencies[0], 0.0001);
        assertEquals(-1.0, frequencies[1], 0.0001);
        assertEquals(-1.0, frequencies[2], 0.0001);
        assertEquals(-1.0, frequencies[3], 0.0001);
        assertEquals(-1.0, frequencies[4], 0.0001);
        assertEquals(-1.0, frequencies[5], 0.0001);
        assertEquals(1800.0, frequencies[6], 0.0001);
        assertEquals(2016.0, frequencies[7], 0.0001);
    }

    @Test
    public void missingCpuFrequencyCoresRemainUnavailableAndAreReset() {
        double[] frequencies = new double[8];
        java.util.Arrays.fill(frequencies, 999.0);
        MonitoringEngine.parseCpuFrequencies("600000 1200000", frequencies);

        assertEquals(600.0, frequencies[0], 0.0001);
        assertEquals(1200.0, frequencies[1], 0.0001);
        for (int i = 2; i < frequencies.length; i++) {
            assertEquals(-1.0, frequencies[i], 0.0001);
        }

        MonitoringEngine.parseCpuFrequencies(null, frequencies);
        for (double frequency : frequencies) {
            assertEquals(-1.0, frequency, 0.0001);
        }
    }

    @Test
    public void cpuFrequencyParserHandlesNullDestination() {
        MonitoringEngine.parseCpuFrequencies("600000 1200000", null);
    }
    @Test
    public void convertsSysfsBatteryCurrentFromMicroampsToMilliamps() {
        assertEquals(1250.0, BatteryCurrent.sysfsMicroampsToMilliamps(1250000L), 0.0001);
        assertEquals(-850.0, BatteryCurrent.sysfsMicroampsToMilliamps(-850000L), 0.0001);
        assertEquals(0.0, BatteryCurrent.sysfsMicroampsToMilliamps(0L), 0.0001);
    }
}
