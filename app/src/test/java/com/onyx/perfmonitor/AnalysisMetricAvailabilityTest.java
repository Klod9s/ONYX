package com.onyx.perfmonitor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AnalysisMetricAvailabilityTest {

    @Test
    public void acceptsNegativeBatteryPowerReadings() {
        assertTrue(AnalysisMetricAvailability.hasAnySignedValues(
                Arrays.asList(-2.4, -0.8, -3.1)));
    }

    @Test
    public void acceptsPositiveChargingPowerReadings() {
        assertTrue(AnalysisMetricAvailability.hasAnySignedValues(
                Arrays.asList(1.5, 4.2)));
    }

    @Test
    public void ignoresUnavailableSentinelAndNonFiniteValues() {
        assertFalse(AnalysisMetricAvailability.hasAnySignedValues(
                Arrays.asList(-1.0, Double.NaN, Double.POSITIVE_INFINITY)));
    }

    @Test
    public void handlesEmptyAndNullLists() {
        assertFalse(AnalysisMetricAvailability.hasAnySignedValues(
                Collections.<Double>emptyList()));
        assertFalse(AnalysisMetricAvailability.hasAnySignedValues(null));
    }
}
