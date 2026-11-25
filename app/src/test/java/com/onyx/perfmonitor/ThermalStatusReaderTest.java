package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThermalStatusReaderTest {

    @Test
    public void normalStatusIsNotElevated() {
        assertFalse(ThermalStatusReader.isElevated(ThermalStatusReader.NONE));
        assertFalse(ThermalStatusReader.isSevere(ThermalStatusReader.NONE));
        assertEquals("Normal", ThermalStatusReader.label(ThermalStatusReader.NONE));
    }

    @Test
    public void lightAndModerateStatusesAreElevatedButNotSevere() {
        assertTrue(ThermalStatusReader.isElevated(ThermalStatusReader.LIGHT));
        assertTrue(ThermalStatusReader.isElevated(ThermalStatusReader.MODERATE));
        assertFalse(ThermalStatusReader.isSevere(ThermalStatusReader.LIGHT));
        assertFalse(ThermalStatusReader.isSevere(ThermalStatusReader.MODERATE));
    }

    @Test
    public void severeAndHigherStatusesAreSevere() {
        assertTrue(ThermalStatusReader.isSevere(ThermalStatusReader.SEVERE));
        assertTrue(ThermalStatusReader.isSevere(ThermalStatusReader.CRITICAL));
        assertTrue(ThermalStatusReader.isSevere(ThermalStatusReader.EMERGENCY));
        assertTrue(ThermalStatusReader.isSevere(ThermalStatusReader.SHUTDOWN));
    }

    @Test
    public void unavailableStatusIsNotReportedAsThermalPressure() {
        assertFalse(ThermalStatusReader.isElevated(ThermalStatusReader.UNAVAILABLE));
        assertFalse(ThermalStatusReader.isSevere(ThermalStatusReader.UNAVAILABLE));
        assertEquals("Unavailable", ThermalStatusReader.label(ThermalStatusReader.UNAVAILABLE));
    }
}
