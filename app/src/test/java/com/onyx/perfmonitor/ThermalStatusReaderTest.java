package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThermalStatusReaderTest {

    @Test
    public void normalAndUnavailableUseBatteryIconLevel() {
        assertEquals(0, ThermalStatusReader.overlayLevel(ThermalStatusReader.NONE));
        assertEquals(0, ThermalStatusReader.overlayLevel(ThermalStatusReader.UNAVAILABLE));
        assertFalse(ThermalStatusReader.isElevated(ThermalStatusReader.NONE));
        assertFalse(ThermalStatusReader.isSevere(ThermalStatusReader.NONE));
        assertEquals("Normal", ThermalStatusReader.label(ThermalStatusReader.NONE));
        assertEquals("Unavailable", ThermalStatusReader.label(ThermalStatusReader.UNAVAILABLE));
    }

    @Test
    public void lightAndModerateStatusesAreElevatedButNotSevere() {
        assertTrue(ThermalStatusReader.isElevated(ThermalStatusReader.LIGHT));
        assertTrue(ThermalStatusReader.isElevated(ThermalStatusReader.MODERATE));
        assertEquals(1, ThermalStatusReader.overlayLevel(ThermalStatusReader.LIGHT));
        assertEquals(2, ThermalStatusReader.overlayLevel(ThermalStatusReader.MODERATE));
        assertFalse(ThermalStatusReader.isSevere(ThermalStatusReader.LIGHT));
        assertFalse(ThermalStatusReader.isSevere(ThermalStatusReader.MODERATE));
    }

    @Test
    public void severeAndHigherStatusesAreSevere() {
        assertTrue(ThermalStatusReader.isSevere(ThermalStatusReader.SEVERE));
        assertTrue(ThermalStatusReader.isSevere(ThermalStatusReader.CRITICAL));
        assertTrue(ThermalStatusReader.isSevere(ThermalStatusReader.EMERGENCY));
        assertTrue(ThermalStatusReader.isSevere(ThermalStatusReader.SHUTDOWN));
        assertEquals(3, ThermalStatusReader.overlayLevel(ThermalStatusReader.SEVERE));
        assertEquals(3, ThermalStatusReader.overlayLevel(ThermalStatusReader.CRITICAL));
        assertEquals(3, ThermalStatusReader.overlayLevel(ThermalStatusReader.EMERGENCY));
        assertEquals(3, ThermalStatusReader.overlayLevel(ThermalStatusReader.SHUTDOWN));
    }

    @Test
    public void unavailableStatusIsNotReportedAsThermalPressure() {
        assertFalse(ThermalStatusReader.isElevated(ThermalStatusReader.UNAVAILABLE));
        assertFalse(ThermalStatusReader.isSevere(ThermalStatusReader.UNAVAILABLE));
        assertEquals("Unavailable", ThermalStatusReader.label(ThermalStatusReader.UNAVAILABLE));
    }
}
