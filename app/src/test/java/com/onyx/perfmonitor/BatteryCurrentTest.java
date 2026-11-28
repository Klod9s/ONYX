
package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BatteryCurrentTest {

    @Test
    public void calibrationRequiresFourConsecutiveVotes() {
        BatteryCurrent.SignCalibration calibration =
                new BatteryCurrent.SignCalibration(false);

        for (int i = 0; i < 3; i++) {
            calibration.addSample(500, true);
            assertFalse(calibration.isInverted());
        }

        calibration.addSample(500, true);
        assertTrue(calibration.isInverted());
    }

    @Test
    public void sysfsAndBatteryManagerCalibrationsRemainIndependent() {
        BatteryCurrent.SignCalibration batteryManager =
                new BatteryCurrent.SignCalibration(false);
        BatteryCurrent.SignCalibration sysfs =
                new BatteryCurrent.SignCalibration(false);

        for (int i = 0; i < 4; i++) {
            sysfs.addSample(500, true);
        }

        assertTrue(sysfs.isInverted());
        assertFalse(batteryManager.isInverted());
    }

    @Test
    public void negativeDischargeReadingsKeepNormalSign() {
        BatteryCurrent.SignCalibration calibration =
                new BatteryCurrent.SignCalibration(false);

        for (int i = 0; i < 4; i++) {
            calibration.addSample(-500, true);
        }

        assertFalse(calibration.isInverted());
    }

    @Test
    public void pluggedInSamplesDoNotTeachCalibration() {
        BatteryCurrent.SignCalibration calibration =
                new BatteryCurrent.SignCalibration(false);

        for (int i = 0; i < 10; i++) {
            calibration.addSample(500, false);
        }

        assertFalse(calibration.isInverted());
    }

    @Test
    public void lowCurrentSamplesDoNotTeachCalibration() {
        BatteryCurrent.SignCalibration calibration =
                new BatteryCurrent.SignCalibration(false);

        for (int i = 0; i < 10; i++) {
            calibration.addSample(100, true);
        }

        assertFalse(calibration.isInverted());
    }

    @Test
    public void interruptedVoteSequenceRestartsCalibration() {
        BatteryCurrent.SignCalibration calibration =
                new BatteryCurrent.SignCalibration(false);

        calibration.addSample(500, true);
        calibration.addSample(500, true);
        calibration.addSample(500, true);
        calibration.addSample(0, true);
        calibration.addSample(500, true);
        calibration.addSample(500, true);
        calibration.addSample(500, true);

        assertFalse(calibration.isInverted());

        calibration.addSample(500, true);
        assertTrue(calibration.isInverted());
    }

    @Test
    public void existingCalibrationIsPreservedInitially() {
        BatteryCurrent.SignCalibration calibration =
                new BatteryCurrent.SignCalibration(true);

        assertTrue(calibration.isInverted());
    }

    @Test
    public void sysfsUnitConversionRemainsUnchanged() {
        assertEquals(1250.0,
                BatteryCurrent.sysfsMicroampsToMilliamps(1250000L), 0.0001);
        assertEquals(-850.0,
                BatteryCurrent.sysfsMicroampsToMilliamps(-850000L), 0.0001);
    }
}
