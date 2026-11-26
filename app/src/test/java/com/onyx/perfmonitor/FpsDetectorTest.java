package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

public class FpsDetectorTest {

    private static FpsDetector.Shell shell(
            java.util.function.Supplier<String> dump,
            java.util.function.Supplier<String> layers,
            java.util.function.Function<String, String> latency,
            java.util.function.Supplier<String> timeStats) {
        return new FpsDetector.Shell() {
            @Override public String surfaceFlingerDump() { return dump.get(); }
            @Override public String surfaceFlingerLayers() { return layers.get(); }
            @Override public String surfaceFlingerLatency(String layerName) {
                return latency.apply(layerName);
            }
            @Override public String surfaceFlingerTimeStatsDump() { return timeStats.get(); }
        };
    }

    @Test
    public void capturesOnlyNewSurfaceFlingerIntervalsAcrossOverlappingPolls() {
        AtomicLong clock = new AtomicLong(1000);
        AtomicLong latest = new AtomicLong(System.nanoTime() - 500_000_000L);
        FpsDetector detector = new FpsDetector(clock::get);
        detector.beginFrameCapture(latest.get() - 500_000_000L);

        FpsDetector.Shell shell = shell(
                () -> "",
                () -> "SurfaceView com.example.game\n",
                layerName -> {
                    long end = latest.get();
                    StringBuilder dump = new StringBuilder("10000000\n");
                    for (int i = 99; i >= 0; i--) {
                        long timestamp = end - i * 10_000_000L;
                        dump.append("0 ").append(timestamp).append(" 0\n");
                    }
                    return dump.toString();
                },
                () -> "");

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
        List<String> calls = new ArrayList<>();

        FpsDetector.Result result = detector.detect(shell(
                () -> { calls.add("dump"); return ""; },
                () -> { calls.add("layers"); return ""; },
                layer -> { calls.add("latency"); return ""; },
                () -> { calls.add("stats"); return ""; }
        ), "Unknown");

        assertFalse(result.valid());
        assertEquals("none", result.source);
        assertTrue(calls.isEmpty());
    }

    @Test
    public void doesNotUseDisplayRateAsAppFps() {
        AtomicLong clock = new AtomicLong(1000);
        FpsDetector detector = new FpsDetector(clock::get);
        List<String> calls = new ArrayList<>();

        FpsDetector.Result result = detector.detect(shell(
                () -> { calls.add("dump"); return ""; },
                () -> { calls.add("layers"); return ""; },
                layer -> { calls.add("latency"); return ""; },
                () -> { calls.add("stats"); return ""; }
        ), "com.example.game");

        assertFalse(result.valid());
        assertEquals("none", result.source);
        assertEquals("Only the first FPS probe should run per sample", 1, calls.size());
    }

    @Test
    public void calculatesFrameCounterFpsFromConsecutiveSamples() {
        AtomicLong clock = new AtomicLong(1000);
        AtomicLong frames = new AtomicLong(100);
        FpsDetector detector = new FpsDetector(clock::get);

        FpsDetector.Shell shell = shell(
                () -> "SurfaceView com.example.game\nframe=" + frames.get() + "\n",
                () -> "",
                layer -> "",
                () -> "");

        // BLAST and regular latency are checked on separate samples before
        // the frame-counter fallback is reached.
        detector.detect(shell, "com.example.game");
        detector.detect(shell, "com.example.game");
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

        FpsDetector.Shell shell = shell(
                () -> "SurfaceView com.example.game\nframe=" + frames.get() + "\n",
                () -> "",
                layer -> "",
                () -> "");

        // Move through BLAST and regular latency before establishing
        // the frame-counter baseline.
        detector.detect(shell, "com.example.game");
        detector.detect(shell, "com.example.game");
        detector.detect(shell, "com.example.game");

        frames.set(50);
        clock.addAndGet(1000);
        FpsDetector.Result afterReset = detector.detect(shell, "com.example.game");
        assertFalse(afterReset.valid());

        frames.set(110);
        // The detector staggers fallback probes: BLAST, latency, then counter.
        clock.addAndGet(1000);
        detector.detect(shell, "com.example.game");
        clock.addAndGet(500);
        detector.detect(shell, "com.example.game");
        clock.addAndGet(500);
        FpsDetector.Result recovered = detector.detect(shell, "com.example.game");

        assertTrue(recovered.valid());
        assertEquals(30.0, recovered.fps, 0.0001);
    }
}
