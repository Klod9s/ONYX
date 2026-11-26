package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class FpsDetectorTest {

    @Test
    public void capturesOnlyNewSurfaceFlingerIntervalsAcrossOverlappingPolls() {
        AtomicLong clock = new AtomicLong(1000);
        AtomicLong latest = new AtomicLong(System.nanoTime() - 500_000_000L);
        FpsDetector detector = new FpsDetector(clock::get);
        detector.beginFrameCapture(latest.get() - 500_000_000L);

        FpsDetector.Shell shell = command -> {
            if (command.contains("SurfaceFlinger --list")) {
                return "SurfaceView com.example.game\n";
            }
            if (command.contains("SurfaceFlinger --latency")) {
                long end = latest.get();
                StringBuilder dump = new StringBuilder("10000000\n");
                for (int i = 99; i >= 0; i--) {
                    long timestamp = end - i * 10_000_000L;
                    dump.append("0 ").append(timestamp).append(" 0\n");
                }
                return dump.toString();
            }
            return "";
        };

        FpsDetector.Result baseline = detector.detect(shell, "com.example.game");
        assertTrue(baseline.valid());
        assertTrue(baseline.frameIntervalsMs.size() > 0);
        assertTrue(baseline.frameIntervalsMs.size() <= 50);

        latest.addAndGet(500_000_000L);
        clock.addAndGet(500);
        FpsDetector.Result second = detector.detect(shell, "com.example.game");
        assertTrue(second.valid());
        assertTrue(second.frameIntervalsMs.size() > 0);

        latest.addAndGet(500_000_000L);
        clock.addAndGet(500);
        FpsDetector.Result third = detector.detect(shell, "com.example.game");
        assertTrue(third.valid());
        assertTrue(third.frameIntervalsMs.size() > 0);
        assertTrue(third.frameIntervalsMs.size() <= 51);
    }

    @Test
    public void rejectsUnknownPackageWithoutRunningShellCommands() {
        AtomicLong clock = new AtomicLong(1000);
        FpsDetector detector = new FpsDetector(clock::get);
        List<String> commands = new ArrayList<>();

        FpsDetector.Result result = detector.detect(command -> {
            commands.add(command);
            return "";
        }, "Unknown");

        assertFalse(result.valid());
        assertEquals("none", result.source);
        assertTrue(commands.isEmpty());
    }

    @Test
    public void doesNotUseDisplayRateAsAppFps() {
        AtomicLong clock = new AtomicLong(1000);
        FpsDetector detector = new FpsDetector(clock::get);
        List<String> commands = new ArrayList<>();

        FpsDetector.Result result = detector.detect(command -> {
            commands.add(command);
            if (command.contains("measured_fps")) return "fps: 90";
            return "";
        }, "com.example.game");

        assertFalse(result.valid());
        assertEquals("none", result.source);
        assertTrue(commands.stream().noneMatch(command -> command.contains("measured_fps")));
    }

    @Test
    public void calculatesFrameCounterFpsFromConsecutiveSamples() {
        AtomicLong clock = new AtomicLong(1000);
        AtomicLong frames = new AtomicLong(100);
        FpsDetector detector = new FpsDetector(clock::get);

        FpsDetector.Shell shell = command -> {
            if (command.contains("dumpsys SurfaceFlinger 2>/dev/null")) {
                return "SurfaceView com.example.game\nframe=" + frames.get() + "\n";
            }
            return "";
        };

        FpsDetector.Result baseline = detector.detect(shell, "com.example.game");
        assertFalse(baseline.valid());

        frames.set(160);
        clock.addAndGet(1000);

        FpsDetector.Result measured = detector.detect(shell, "com.example.game");

        assertTrue(measured.valid());
        assertEquals(60.0, measured.fps, 0.0001);
        assertEquals("SF Counter", measured.source);
    }

    @Test
    public void frameCounterResetEstablishesANewBaseline() {
        AtomicLong clock = new AtomicLong(1000);
        AtomicLong frames = new AtomicLong(100);
        FpsDetector detector = new FpsDetector(clock::get);

        FpsDetector.Shell shell = command -> {
            if (command.contains("dumpsys SurfaceFlinger 2>/dev/null")) {
                return "SurfaceView com.example.game\nframe=" + frames.get() + "\n";
            }
            return "";
        };

        detector.detect(shell, "com.example.game");

        frames.set(50);
        clock.addAndGet(1000);
        FpsDetector.Result afterReset = detector.detect(shell, "com.example.game");
        assertFalse(afterReset.valid());

        frames.set(110);
        clock.addAndGet(1000);
        FpsDetector.Result recovered = detector.detect(shell, "com.example.game");

        assertTrue(recovered.valid());
        assertEquals(60.0, recovered.fps, 0.0001);
    }
}
