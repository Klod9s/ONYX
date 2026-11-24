package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SessionDataTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadParsesSessionMetadataAndReadings() throws Exception {
        File file = writeSession(
                "Device,\"Phone, Pro\"",
                "Android,14",
                "Mode,ROOT",
                "00:00:01.500,ignored,60,1200,4000,4.8,85,600,38.5"
        );

        SessionData data = SessionData.load(file);

        assertNotNull(data);
        assertEquals(file.getName(), data.fileName);
        assertEquals("Phone, Pro", data.deviceName);
        assertEquals("14", data.androidVersion);
        assertEquals("ROOT", data.mode);
        assertEquals(1.5, data.times.get(0), 0.0001);
        assertEquals(60.0, data.fps.get(0), 0.0001);
        assertEquals(1200.0, data.current.get(0), 0.0001);
        assertEquals(4000.0, data.voltage.get(0), 0.0001);
        assertEquals(4.8, data.power.get(0), 0.0001);
        assertEquals(85.0, data.battery.get(0), 0.0001);
        assertEquals(600.0, data.secondsPerPercent.get(0), 0.0001);
        assertEquals(38.5, data.temperature.get(0), 0.0001);
    }

    @Test
    public void loadKeepsAllReadingSeriesAlignedAndFillsMissingValues() throws Exception {
        File file = writeSession(
                "Device,Test device",
                row("00:00:02", "60"),
                row("00:00:03", "invalid")
        );

        SessionData data = SessionData.load(file);

        assertNotNull(data);
        assertEquals(2, data.times.size());
        assertEquals(data.times.size(), data.fps.size());
        assertEquals(data.times.size(), data.current.size());
        assertEquals(data.times.size(), data.voltage.size());
        assertEquals(data.times.size(), data.power.size());
        assertEquals(data.times.size(), data.battery.size());
        assertEquals(data.times.size(), data.temperature.size());
        assertEquals(data.times.size(), data.gpuFreq.size());
        assertEquals(data.times.size(), data.averageFrameTime.size());

        assertEquals(60.0, data.fps.get(0), 0.0001);
        assertEquals(-1.0, data.fps.get(1), 0.0001);
        assertEquals(-1.0, data.current.get(0), 0.0001);
        assertEquals(-1.0, data.gpuFreq.get(0), 0.0001);
        for (int i = 0; i < 8; i++) {
            assertEquals(2, data.cpu.get(i).size());
            assertEquals(2, data.cpuFreq.get(i).size());
            assertEquals(-1.0, data.cpu.get(i).get(0), 0.0001);
            assertEquals(-1.0, data.cpuFreq.get(i).get(0), 0.0001);
        }
    }

    @Test
    public void invalidAndNonFiniteNumbersBecomeUnavailable() throws Exception {
        File file = writeSession(
                "Device,Test device",
                row("1", "NaN", "Infinity", "-Infinity")
        );

        SessionData data = SessionData.load(file);

        assertNotNull(data);
        assertEquals(-1.0, data.fps.get(0), 0.0001);
        assertEquals(-1.0, data.current.get(0), 0.0001);
        assertEquals(-1.0, data.voltage.get(0), 0.0001);
    }

    @Test
    public void invalidTimestampsFallBackToSequentialSeconds() throws Exception {
        File file = writeSession(
                "Device,Test device",
                row("00:00:05", "60"),
                row("not-a-time", "61"),
                row("00:00:08", "62"),
                row("NaN", "63")
        );

        SessionData data = SessionData.load(file);

        assertNotNull(data);
        assertEquals(Arrays.asList(5.0, 6.0, 8.0, 9.0), data.times);
    }

    @Test
    public void loadReturnsNullWhenReadingsMarkerOrRowsAreMissing() throws Exception {
        File noMarker = temporaryFolder.newFile("no_marker.csv");
        try (FileWriter writer = new FileWriter(noMarker)) {
            writer.write("Device,Test device\n");
        }

        File noRows = temporaryFolder.newFile("no_rows.csv");
        try (FileWriter writer = new FileWriter(noRows)) {
            writer.write("Device,Test device\nPERFORMANCE READINGS\nTime,Other,FPS\n");
        }

        assertNull(SessionData.load(noMarker));
        assertNull(SessionData.load(noRows));
    }

    @Test
    public void readMetaReadsMetadataWithoutLoadingReadings() throws Exception {
        File file = writeSession(
                "Device,Test device",
                "Android,15",
                "Mode,SHIZUKU",
                row("00:00:01", "60")
        );

        SessionData data = SessionData.readMeta(null, file);

        assertNotNull(data);
        assertEquals("Test device", data.deviceName);
        assertEquals("15", data.androidVersion);
        assertEquals("SHIZUKU", data.mode);
        assertTrue(data.times.isEmpty());
        assertTrue(data.fps.isEmpty());
    }

    private File writeSession(String... lines) throws Exception {
        File file = temporaryFolder.newFile("test_session.csv");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("ONYX SESSION\n");
            for (String line : lines) {
                writer.write(line);
                writer.write('\n');
            }
            writer.write("PERFORMANCE READINGS\n");
            writer.write("Time,Unused,FPS,Current,Voltage,Power,Battery,SecondsPerPercent,Temperature,CPU0,CPU1,CPU2,CPU3,CPU4,CPU5,CPU6,CPU7,CPUFreq0,CPUFreq1,CPUFreq2,CPUFreq3,CPUFreq4,CPUFreq5,CPUFreq6,CPUFreq7,GPUFreq,GPULoad,AverageFrameTime,FrameTimeStdDev,StutterCount\n");
            for (String line : lines) {
                if (line.startsWith("00:") || line.startsWith("not-")
                        || line.startsWith("NaN") || line.startsWith("1,")) {
                    writer.write(line);
                    writer.write('\n');
                }
            }
        }
        return file;
    }

    private String row(String time, String fps, String... extraValues) {
        String[] columns = new String[30];
        Arrays.fill(columns, "");
        columns[0] = time;
        columns[2] = fps;
        for (int i = 0; i < extraValues.length && i + 3 < columns.length; i++) {
            columns[i + 3] = extraValues[i];
        }
        return String.join(",", columns);
    }
}
