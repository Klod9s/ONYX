package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OverlaySessionRecorderInstrumentedTest {

    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private Set<String> sessionFiles() {
        Set<String> names = new HashSet<>();
        File[] files = context().getFilesDir().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().startsWith("session_")
                        && file.getName().endsWith(".csv")) {
                    names.add(file.getName());
                }
            }
        }
        return names;
    }

    @Test
    public void startAppendAndFinishSaveReadableSessionWithThermalColumns() {
        Set<String> before = sessionFiles();
        OverlaySessionRecorder recorder = new OverlaySessionRecorder(context());

        recorder.start();
        assertTrue(recorder.isRecording());

        Sample sample = new Sample();
        sample.app = "Game, \"Pro\"";
        sample.fps = 60;
        sample.battery = 81;
        sample.thermalStatus = ThermalStatusReader.MODERATE;
        sample.coolingDeviceActive = ThermalCoolingReader.ACTIVE;
        recorder.append(sample, "ROOT");
        recorder.finish();

        assertFalse(recorder.isRecording());
        Set<String> after = sessionFiles();
        after.removeAll(before);
        assertEquals(1, after.size());

        File saved = new File(context().getFilesDir(), after.iterator().next());
        try {
            SessionData data = SessionData.load(saved);
            assertNotNull(data);
            assertEquals(1, data.times.size());
            assertEquals("ROOT", data.mode);
            assertEquals(60.0, data.fps.get(0), 0.0001);
            assertEquals((double) ThermalStatusReader.MODERATE,
                    data.thermalStatus.get(0), 0.0001);
            assertEquals((double) ThermalCoolingReader.ACTIVE,
                    data.coolingDeviceActive.get(0), 0.0001);
            assertTrue(data.fileName.endsWith(".csv"));
        } finally {
            saved.delete();
        }
    }

    @Test
    public void finishingWithoutSamplesDoesNotCreateSessionFile() {
        Set<String> before = sessionFiles();
        OverlaySessionRecorder recorder = new OverlaySessionRecorder(context());

        recorder.start();
        assertTrue(recorder.isRecording());
        recorder.append(null, "ANDROID");
        recorder.finish();
        recorder.finish();

        assertFalse(recorder.isRecording());
        Set<String> after = sessionFiles();
        after.removeAll(before);
        assertTrue(after.isEmpty());
    }

    @Test
    public void startingAgainResetsPreviousUnfinishedRecording() {
        Set<String> before = sessionFiles();
        OverlaySessionRecorder recorder = new OverlaySessionRecorder(context());

        recorder.start();
        Sample discarded = new Sample();
        discarded.app = "Discarded";
        discarded.fps = 10;
        recorder.append(discarded, "ANDROID");

        recorder.start();
        Sample retained = new Sample();
        retained.app = "Retained";
        retained.fps = 55;
        retained.thermalStatus = ThermalStatusReader.LIGHT;
        recorder.append(retained, "SHIZUKU");
        recorder.finish();

        Set<String> after = sessionFiles();
        after.removeAll(before);
        assertEquals(1, after.size());

        File saved = new File(context().getFilesDir(), after.iterator().next());
        try {
            SessionData data = SessionData.load(saved);
            assertNotNull(data);
            assertEquals(1, data.times.size());
            assertEquals(55.0, data.fps.get(0), 0.0001);
            assertEquals("SHIZUKU", data.mode);
            assertEquals((double) ThermalStatusReader.LIGHT,
                    data.thermalStatus.get(0), 0.0001);
        } finally {
            saved.delete();
        }
    }
}
