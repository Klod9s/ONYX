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
    public void defaultTimeStatsSnapshotKeepsLayerAndStatsSectionsSeparate() throws Exception {
        FpsDetector.Shell shell = shell(
                () -> "",
                () -> "SurfaceView com.example.game\\n",
                layer -> "",
                () -> "layerName = SurfaceView com.example.game\\ntotalFrames = 120\\n");

        assertEquals("__ONYX_LAYERS__\\nSurfaceView com.example.game\\n"
                        + "__ONYX_TIMESTATS__\\n"
                        + "layerName = SurfaceView com.example.game\\n"
                        + "totalFrames = 120\\n",
                shell.surfaceFlingerTimeStatsSnapshot());
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

        // The first sample probes BLAST only; this fixture exposes a regular
        // SurfaceView layer, so the regular-latency fallback is tried next.
        FpsDetector.Result firstProbe = detector.detect(shell, "com.example.game");
        assertFalse(firstProbe.valid());

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
    public void recoversIntervalsWhenRollingBufferOverwritesPreviousCursor() {
        AtomicLong clock = new AtomicLong(1000);
        AtomicLong latest = new AtomicLong(System.nanoTime() - 500_000_000L);
        FpsDetector detector = new FpsDetector(clock::get);
        detector.beginFrameCapture(latest.get() - 500_000_000L);

        FpsDetector.Shell shell = shell(
                () -> "",
                () -> "BLAST SurfaceView com.example.game\n",
                layerName -> {
                    long end = latest.get();
                    StringBuilder dump = new StringBuilder("8333333\n");
                    // Model a rolling 100-frame SurfaceFlinger buffer.
                    for (int i = 99; i >= 0; i--) {
                        long timestamp = end - i * 8_333_333L;
                        dump.append("0 ").append(timestamp).append(" 0\n");
                    }
                    return dump.toString();
                },
                () -> "");

        FpsDetector.Result first = detector.detect(shell, "com.example.game");
        assertTrue(first.valid());
        assertTrue(first.frameIntervalsMs.size() > 0);

        // Advance far enough that the previous cursor is no longer present
        // in the 100-entry rolling buffer.
        latest.addAndGet(2_000_000_000L);
        clock.addAndGet(500);
        FpsDetector.Result recovered = detector.detect(shell, "com.example.game");

        assertTrue(recovered.valid());
        assertTrue("Should recover intervals from the newer buffer",
                recovered.frameIntervalsMs.size() > 0);
        assertTrue(recovered.frameIntervalsMs.size() <= 99);
    }

    @Test
    public void cachesSelectedSurfaceFlingerLayerForFiveSeconds() {
        AtomicLong clock = new AtomicLong(1000);
        AtomicLong latest = new AtomicLong(System.nanoTime() - 500_000_000L);
        AtomicLong layerQueries = new AtomicLong();
        FpsDetector detector = new FpsDetector(clock::get);

        FpsDetector.Shell shell = shell(
                () -> "",
                () -> {
                    layerQueries.incrementAndGet();
                    return "BLAST SurfaceView com.example.game\n";
                },
                layerName -> {
                    long end = latest.get();
                    StringBuilder dump = new StringBuilder("10000000\n");
                    for (int i = 99; i >= 0; i--) {
                        dump.append("0 ").append(end - i * 10_000_000L).append(" 0\n");
                    }
                    return dump.toString();
                },
                () -> "");

        assertTrue(detector.detect(shell, "com.example.game").valid());
        assertEquals(1, layerQueries.get());

        clock.addAndGet(4900);
        latest.addAndGet(4_900_000_000L);
        assertTrue(detector.detect(shell, "com.example.game").valid());
        assertEquals("Layer lookup should remain cached before five seconds",
                1, layerQueries.get());

        clock.addAndGet(100);
        latest.addAndGet(100_000_000L);
        assertTrue(detector.detect(shell, "com.example.game").valid());
        assertEquals("Layer lookup should refresh at five seconds",
                2, layerQueries.get());
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
    public void keepsSfCounterValidWhenLayerIsIdleAndMeasuresWhenItResumes() {
        AtomicLong clock = new AtomicLong(1000);
        AtomicLong frames = new AtomicLong(100);
        FpsDetector detector = new FpsDetector(clock::get);

        FpsDetector.Shell shell = shell(
                () -> "SurfaceView com.example.game\nframe=" + frames.get() + "\n",
                () -> "",
                layer -> "",
                () -> "");

        // BLAST and regular latency are checked before the counter fallback.
        detector.detect(shell, "com.example.game");
        detector.detect(shell, "com.example.game");
        FpsDetector.Result baseline = detector.detect(shell, "com.example.game");
        assertFalse(baseline.valid());

        clock.addAndGet(1000);
        FpsDetector.Result idle = detector.detect(shell, "com.example.game");
        assertTrue(idle.valid());
        assertEquals(0.0, idle.fps, 0.0001);
        assertEquals("SF Counter", idle.source);

        // The counter starts moving again; FPS uses only this latest interval.
        frames.set(160);
        clock.addAndGet(1000);

        // A failed higher-priority retry must fall back to the already
        // established SF Counter in this same sampling cycle.
        FpsDetector.Result resumed = detector.detect(shell, "com.example.game");
        assertTrue(resumed.valid());
        assertEquals(60.0, resumed.fps, 0.0001);
        assertEquals("SF Counter", resumed.source);
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
        // A failed higher-priority retry falls back to SF Counter immediately.
        // The 60 new frames are measured over the one-second interval since
        // the reset baseline was established.
        clock.addAndGet(1000);
        FpsDetector.Result recovered = detector.detect(shell, "com.example.game");

        assertTrue(recovered.valid());
        assertEquals(60.0, recovered.fps, 0.0001);
        assertEquals("SF Counter", recovered.source);
    }
}
