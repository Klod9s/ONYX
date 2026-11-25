package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ThermalCoolingReaderTest {

    @Test
    public void detectsActiveCpuCoolingDevice() {
        assertEquals(ThermalCoolingReader.ACTIVE,
                ThermalCoolingReader.parse("cpu0 1 4"));
    }

    @Test
    public void detectsActiveGpuCoolingDevice() {
        assertEquals(ThermalCoolingReader.ACTIVE,
                ThermalCoolingReader.parse("gpu 2 5"));
    }

    @Test
    public void reportsIdleWhenAllRelevantDevicesAreIdle() {
        assertEquals(ThermalCoolingReader.IDLE,
                ThermalCoolingReader.parse("cpu0 0 4\ngpu 0 3"));
    }

    @Test
    public void ignoresUnrelatedCoolingDevices() {
        assertEquals(ThermalCoolingReader.UNAVAILABLE,
                ThermalCoolingReader.parse("battery 1 4\nfan 2 5"));
    }

    @Test
    public void ignoresMalformedAndOutOfRangeValues() {
        assertEquals(ThermalCoolingReader.UNAVAILABLE,
                ThermalCoolingReader.parse("cpu0 unknown 4\ngpu 5 3"));
    }

    @Test
    public void emptyInputIsUnavailable() {
        assertEquals(ThermalCoolingReader.UNAVAILABLE,
                ThermalCoolingReader.parse(""));
    }
}
