package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SessionCsvRoundTripTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void formatterOutputRoundTripsAllCurrentColumns() throws Exception {
        Sample sample = new Sample();
        sample.timeMs = 125_000;
        sample.app = "Game, \"Pro\"";
        sample.fps = 59.8;
        sample.currentMa = 1200;
        sample.voltage = 4.012;
        sample.power = 4.81;
        sample.battery = 87;
        sample.secondsPerPercent = 540;
        sample.temperatureC = 39.4;
        Arrays.fill(sample.cpu, 12.5);
        Arrays.fill(sample.cpuFreqMHz, 1800);
        sample.gpuFreqMHz = 950;
        sample.gpuLoadPercent = 72.3;
        sample.averageFrameTimeMs = 16.72;
        sample.frameTimeStdDevMs = 1.25;
        sample.stutterCount = 3;
        sample.frameRefreshPeriodMs = 8.333333;
        sample.fpsSource = "SURFACE_FLINGER";
        sample.frameIntervalsMs.add(16.667);
        sample.frameIntervalsMs.add(33.333);
        sample.frameIntervalsMs.add(10_001.0);
        sample.ramUsagePercent = 64.2;
        sample.cpuTemperatureC = 52.1;
        sample.gpuTemperatureC = 46.7;
        sample.thermalStatus = 2;
        sample.coolingDeviceActive = 1;

        StringBuilder csv = new StringBuilder();
        SessionCsvFormatter.appendHeader(csv);
        SessionCsvFormatter.appendSample(csv, sample);

        String[] lines = csv.toString().split("\\n");
        assertEquals(2, lines.length);
        assertEquals(38, lines[0].split(",", -1).length);
        org.junit.Assert.assertTrue(lines[0].contains("Hitch Count (sampled window)"));

        File file = temporaryFolder.newFile("round_trip.csv");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("SESSION INFO\n");
            writer.write("Device,Test device\n");
            writer.write("PERFORMANCE READINGS\n");
            writer.write(csv.toString());
        }

        SessionData data = SessionData.load(file);

        assertNotNull(data);
        assertFalse(data.legacyStutterCountColumn);
        assertEquals(1, data.times.size());
        assertEquals(125.0, data.times.get(0), 0.0001);
        assertEquals(59.8, data.fps.get(0), 0.0001);
        assertEquals(1200.0, data.current.get(0), 0.0001);
        assertEquals(4.012, data.voltage.get(0), 0.0001);
        assertEquals(4.81, data.power.get(0), 0.0001);
        assertEquals(87.0, data.battery.get(0), 0.0001);
        assertEquals(-1.0, data.secondsPerPercent.get(0), 0.0001);
        assertEquals(39.4, data.temperature.get(0), 0.0001);
        assertEquals(12.5, data.cpu.get(0).get(0), 0.0001);
        assertEquals(1800.0, data.cpuFreq.get(0).get(0), 0.0001);
        assertEquals(950.0, data.gpuFreq.get(0), 0.0001);
        assertEquals(72.3, data.gpuLoad.get(0), 0.0001);
        assertEquals(16.72, data.averageFrameTime.get(0), 0.0001);
        assertEquals(1.25, data.frameTimeStdDev.get(0), 0.0001);
        assertEquals(3.0, data.stutterCount.get(0), 0.0001);
        assertEquals(Arrays.asList(16.667, 33.333, 10_001.0), data.frameIntervalsMs);
        assertEquals(Arrays.asList(8.333333, 8.333333, 8.333333),
                data.frameIntervalRefreshPeriodsMs);
        assertEquals(64.2, data.ramUsage.get(0), 0.0001);
        assertEquals(52.1, data.cpuTemperature.get(0), 0.0001);
        assertEquals(46.7, data.gpuTemperature.get(0), 0.0001);
        assertEquals(2.0, data.thermalStatus.get(0), 0.0001);
        assertEquals(1.0, data.coolingDeviceActive.get(0), 0.0001);
    }

    @Test
    public void unavailableThermalValuesAreWrittenAsEmptyCsvFields() throws Exception {
        Sample sample = new Sample();
        sample.app = "Test";
        sample.thermalStatus = ThermalStatusReader.UNAVAILABLE;
        sample.coolingDeviceActive = ThermalCoolingReader.UNAVAILABLE;

        StringBuilder csv = new StringBuilder();
        SessionCsvFormatter.appendHeader(csv);
        SessionCsvFormatter.appendSample(csv, sample);

        String[] lines = csv.toString().split("\\n");
        String[] columns = lines[1].split(",", -1);

        assertEquals(38, columns.length);
        assertEquals("", columns[35]);
        assertEquals("", columns[36]);
        assertEquals("", columns[37]);
    }

    @Test
    public void formulaLikeTextCellsAreExportedAsPlainText() {
        Sample sample = new Sample();
        sample.app = "=HYPERLINK(\"https://example.com\",\"open\")";
        sample.fpsSource = "+cmd|' /C calc'!A0";

        StringBuilder csv = new StringBuilder();
        SessionCsvFormatter.appendSample(csv, sample);

        org.junit.Assert.assertTrue(csv.toString().contains("\"'=HYPERLINK("));
        org.junit.Assert.assertTrue(csv.toString().contains("\"'+cmd|'"));
    }

    @Test
    public void formulaLikeSummaryIsExportedAsPlainText() {
        String csv = SessionCsvFormatter.buildSessionCsv(
                new StringBuilder(), "=1+1", "ROOT");
        org.junit.Assert.assertTrue(csv.contains("\"'=1+1\""));
    }
}
