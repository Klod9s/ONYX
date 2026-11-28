package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ThermalCoolingReaderTest {

    @Test
    public void separatesCpuAndGpuCoolingStates() {
        ThermalCoolingReader.Reading reading =
                ThermalCoolingReader.read("cpufreq-cpu2 1 4\ndevfreq-gpu 3 5");
        assertEquals(1, reading.cpuState);
        assertEquals(4, reading.cpuMaxState);
        assertEquals(3, reading.gpuState);
        assertEquals(5, reading.gpuMaxState);
        assertEquals(ThermalCoolingReader.ACTIVE, reading.overallState());
    }

    @Test
    public void selectsMostActiveDeviceByNormalizedState() {
        ThermalCoolingReader.Reading reading =
                ThermalCoolingReader.read("cpufreq-cpu0 1 8\ncpufreq-cpu1 2 4");
        assertEquals(2, reading.cpuState);
        assertEquals(4, reading.cpuMaxState);
    }

    @Test
    public void reportsIdleWhenRelevantDevicesAreIdle() {
        ThermalCoolingReader.Reading reading =
                ThermalCoolingReader.read("cpu0 0 4\ngpu 0 3");
        assertEquals(ThermalCoolingReader.IDLE, reading.overallState());
        assertEquals(0, reading.cpuState);
        assertEquals(0, reading.gpuState);
    }

    @Test
    public void excludesBatteryAndUnrelatedDevices() {
        ThermalCoolingReader.Reading reading =
                ThermalCoolingReader.read("battery 10 10\nfan 2 5");
        assertEquals(ThermalCoolingReader.UNAVAILABLE, reading.overallState());
        assertEquals(ThermalCoolingReader.UNAVAILABLE, reading.cpuState);
        assertEquals(ThermalCoolingReader.UNAVAILABLE, reading.gpuState);
    }

    @Test
    public void ignoresMalformedAndOutOfRangeValues() {
        ThermalCoolingReader.Reading reading =
                ThermalCoolingReader.read("cpu0 unknown 4\ngpu 5 3");
        assertEquals(ThermalCoolingReader.UNAVAILABLE, reading.overallState());
    }

    @Test
    public void emptyInputIsUnavailable() {
        assertEquals(ThermalCoolingReader.UNAVAILABLE,
                ThermalCoolingReader.read("").overallState());
    }

    @Test
    public void ignoresGenericDevfreqCoolingDevices() {
        ThermalCoolingReader.Reading reading =
                ThermalCoolingReader.read("thermal-devfreq-0 2 5");

        assertEquals(ThermalCoolingReader.UNAVAILABLE, reading.gpuState);
        assertEquals(ThermalCoolingReader.UNAVAILABLE, reading.overallState());
    }

    @Test
    public void keepsCpuAndGpuAvailabilityIndependent() {
        ThermalCoolingReader.Reading reading =
                ThermalCoolingReader.read("gpu 3 5");
        assertEquals(ThermalCoolingReader.UNAVAILABLE, reading.cpuState);
        assertEquals(3, reading.gpuState);
        assertEquals(5, reading.gpuMaxState);
    }
}
