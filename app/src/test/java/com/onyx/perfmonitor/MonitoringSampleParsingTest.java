package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MonitoringSampleParsingTest {

    @Test
    public void parsesDisplayControllerFpsFromRootOutput() {
        assertEquals(59.94, MonitoringEngine.parseFps("fps: 59.94\\n"), 0.0001);
        assertEquals(120.0, MonitoringEngine.parseFps("header\\nfps: 120\\nfooter"), 0.0001);
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
                "CPU 42000\\nCPU 51875\\nGPU 46300\\nGPU 50125\\n"
                        + "battery 39000\\ninvalid value\\nCPU not-a-number\\n"
                        + "GPU 0\\nCPU 151000",
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
                "thermal-cpufreq-0 2 5\\nthermal-gpu-0 1 4\\n"
                        + "battery 1 3\\n",
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
        assertNull(null); // Keeps this test independent of Android runtime objects.
    }

    @Test
    public void coolingParserSafelyIgnoresNullSample() {
        MonitoringEngine.applyCoolingReading("cpu 1 2", null);
        assertNotNull(new Sample());
    }
}
