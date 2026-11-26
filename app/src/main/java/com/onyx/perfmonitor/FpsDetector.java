
package com.onyx.perfmonitor;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Multi-method FPS detection that works over ROOT or Shizuku.
 *
 * Priority:
 * 1. BLAST SF latency (frame pacing)
 * 2. SF latency (frame pacing)
 * 3. DISPLAY (device-wide, no pacing)
 * 4. SF_COUNTER
 * 5. TIMESTATS
 */
public class FpsDetector {

    public interface Shell {
        String surfaceFlingerDump() throws Exception;
        String surfaceFlingerLayers() throws Exception;
        String surfaceFlingerLatency(String layerName) throws Exception;
        String surfaceFlingerTimeStatsDump() throws Exception;
    }

    public static final class Result {
        public final double fps;
        public final String source;
        final boolean pending;

        // Frame pacing metrics (-1 means unavailable)
        public final double averageFrameTimeMs;
        public final double frameTimeStdDevMs;
        public final int stutterCount;
        public final List<Double> frameIntervalsMs;

        Result(double fps, String source, boolean pending) {
            this(fps, source, pending, -1, -1, -1, Collections.emptyList());
        }

        Result(
                double fps,
                String source,
                boolean pending,
                double averageFrameTimeMs,
                double frameTimeStdDevMs,
                int stutterCount) {
            this(fps, source, pending, averageFrameTimeMs,
                    frameTimeStdDevMs, stutterCount, Collections.emptyList());
        }

        Result(
                double fps,
                String source,
                boolean pending,
                double averageFrameTimeMs,
                double frameTimeStdDevMs,
                int stutterCount,
                List<Double> frameIntervalsMs) {

            this.fps = fps;
            this.source = source;
            this.pending = pending;
            this.averageFrameTimeMs = averageFrameTimeMs;
            this.frameTimeStdDevMs = frameTimeStdDevMs;
            this.stutterCount = stutterCount;
            this.frameIntervalsMs = Collections.unmodifiableList(
                    new ArrayList<>(frameIntervalsMs));
        }

        public boolean valid() {
            return fps >= 0;
        }
    }

    private static final Result NONE = new Result(-1, "none", false);

    private static final int M_BLAST = 0;
    private static final int M_COUNTER = 2;
    private static final int M_LATENCY = 3;
    private static final int M_TIMESTATS = 4;

    private static final long LAYER_CACHE_MS = 1000;
    // How long a method waits after returning nothing.
// One that worked a moment ago retries fast (loading screen, pause);
// one that never worked for this app backs off slowly.
    private static final long[] FAST_RETRY_MS = {500, 1000, 2000, 5000};
    private static final long[] SLOW_RETRY_MS = {2000, 4000, 6000};
    private static final long RECENT_OK_MS = 15000;

    private static final Pattern PKG_RE =
            Pattern.compile("^[A-Za-z0-9_.]+$");

    private static final Pattern FRAME_RE =
            Pattern.compile("frame=([0-9]+)");


    private final java.util.function.LongSupplier elapsedRealtime;

    public FpsDetector() {
        this(SystemClock::elapsedRealtime);
    }

    // Package-private clock injection keeps retry and frame-counter tests deterministic.
    FpsDetector(java.util.function.LongSupplier elapsedRealtime) {
        this.elapsedRealtime = elapsedRealtime;
    }

    private final long[] retryAt = new long[5];

    private final long[] lastAliveMs = new long[5];
    private final int[] misses = new int[5];
    private String pkg = "";

    // BLAST latency state
    private String blastLayerName = "";
    private long blastLayerCheckMs = 0;

    // Other SurfaceView latency state
    private String otherLayerName = "";
    private long otherLayerCheckMs = 0;

    // Session-scoped cursor through SurfaceFlinger presentation timestamps.
    private boolean frameCaptureActive = false;
    private boolean frameCaptureBaselinePending = false;
    private long lastCapturedPresentationNs = 0;
    private long frameCaptureStartNs = Long.MAX_VALUE;

    // SF Counter state
    private long lastSfTime = 0;
    private long lastSfFrames = 0;



    // TimeStats state
    private boolean timeStatsWorking = false;
    private String timeStatsLayerName = "";
    private long lastTimeStatsFrames = -1;
    private long lastTimeStatsTimeMs = 0;

    public void reset() {
        boolean sessionBaselineNotYetEstablished =
                frameCaptureActive && frameCaptureBaselinePending;
        pkg = "";
        lastCapturedPresentationNs = 0;
        frameCaptureBaselinePending = frameCaptureActive;
        if (frameCaptureActive && !sessionBaselineNotYetEstablished) {
            frameCaptureStartNs = Long.MAX_VALUE;
        }

        blastLayerName = "";
        blastLayerCheckMs = 0;

        otherLayerName = "";
        otherLayerCheckMs = 0;

        lastSfTime = 0;
        lastSfFrames = 0;

        timeStatsWorking = false;
        timeStatsLayerName = "";
        lastTimeStatsFrames = -1;
        lastTimeStatsTimeMs = 0;

        java.util.Arrays.fill(lastAliveMs, 0);
        java.util.Arrays.fill(misses, 0);
        java.util.Arrays.fill(retryAt, 0);
    }

    /** Starts collecting unique frame intervals for the next recorded session. */
    public void beginFrameCapture(long sessionStartNs) {
        frameCaptureActive = true;
        frameCaptureBaselinePending = true;
        lastCapturedPresentationNs = 0;
        frameCaptureStartNs = sessionStartNs;
    }

    public void endFrameCapture() {
        frameCaptureActive = false;
        frameCaptureBaselinePending = false;
        lastCapturedPresentationNs = 0;
        frameCaptureStartNs = Long.MAX_VALUE;
    }

    public Result detect(Shell shell, String packageName) {

        boolean havePackage = packageName != null
                && !"Unknown".equals(packageName)
                && PKG_RE.matcher(packageName).matches();

        if (!havePackage) {
            reset();
            return NONE;
        }

        if (!packageName.equals(pkg)) {
            reset();
            pkg = packageName;
        }

        long now = elapsedRealtime.getAsLong();

        // 1. BLAST SurfaceFlinger latency
        Result blast = attempt(M_BLAST, shell, now);
        if (blast.valid()) return blast;

        // 2. Other SurfaceFlinger latency
        // Provides actual frame timestamps for frame pacing metrics.
        Result latency = attempt(M_LATENCY, shell, now);
        if (latency.valid()) return latency;

        // 3. SurfaceFlinger frame counter fallback
        Result counter = attempt(M_COUNTER, shell, now);
        if (counter.valid()) return counter;

        // 4. TimeStats fallback
        Result timeStats = attempt(M_TIMESTATS, shell, now);
        if (timeStats.valid()) return timeStats;

        return NONE;
    }

    private Result attempt(int method, Shell shell, long now) {

        if (now < retryAt[method]) {
            return NONE;
        }

        Result r;

        try {
            switch (method) {

                case M_BLAST:
                    r = bySurfaceFlingerLatency(shell, now, true);
                    break;

                case M_COUNTER:
                    r = bySurfaceFlingerCounter(shell, now);
                    break;

                case M_LATENCY:
                    r = bySurfaceFlingerLatency(shell, now, false);
                    break;

                case M_TIMESTATS:
                    r = byTimeStats(shell);
                    break;

                default:
                    r = NONE;
                    break;
            }

        } catch (Exception e) {
            r = NONE;
        }

        if (r == NONE) {
            misses[method]++;

            boolean workedRecently = lastAliveMs[method] != 0
                    && now - lastAliveMs[method] < RECENT_OK_MS;
            long[] steps = workedRecently ? FAST_RETRY_MS : SLOW_RETRY_MS;

            retryAt[method] = now
                    + steps[Math.min(misses[method] - 1, steps.length - 1)];

        } else {
            // Found something, even a "pending" baseline reading: the method is alive
            misses[method] = 0;
            lastAliveMs[method] = now;
            retryAt[method] = 0;

            if (method == M_TIMESTATS && r.valid()) {
                timeStatsWorking = true;
            }
        }

        return r;
    }

    // ------------------------------------------------------------
    // SF COUNTER
    // Reads frame counters from SurfaceFlinger layers.

    private Result bySurfaceFlingerCounter(
            Shell shell, long now) throws Exception {

        String out = shell.surfaceFlingerDump();

        if (out == null || out.isEmpty()) {
            return NONE;
        }

        String[] lines = out.split("\n");
        long totalFrames = -1;

        // Prefer SurfaceView, then any layer belonging to the app.
        for (String type : new String[]{"SurfaceView", "Layer"}) {

            for (int i = 0;
                 i < lines.length - 1 && totalFrames < 0;
                 i++) {

                if (lines[i].contains(type)
                        && lines[i].contains(pkg)
                        && lines[i + 1].contains("frame=")) {

                    Matcher frameMatcher =
                            FRAME_RE.matcher(lines[i + 1]);

                    if (frameMatcher.find()) {
                        try {
                            totalFrames = Long.parseLong(
                                    frameMatcher.group(1));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            if (totalFrames >= 0) break;
        }

        if (totalFrames < 0) {
            return NONE;
        }

        // Establish initial baseline.
        if (lastSfTime == 0) {
            lastSfTime = now;
            lastSfFrames = totalFrames;

            return new Result(-1, "SF Counter", true);
        }

        long elapsedMs = now - lastSfTime;

        if (elapsedMs < 200) {
            return new Result(-1, "SF Counter", true);
        }

        long frameDelta = totalFrames - lastSfFrames;

        // Handle layer recreation or frame-counter reset.
        if (frameDelta < 0) {
            lastSfTime = now;
            lastSfFrames = totalFrames;

            return new Result(-1, "SF Counter", true);
        }

        // No new frames yet.
        if (frameDelta == 0) {
            return new Result(-1, "SF Counter", true);
        }

        double fps = (frameDelta * 1000.0) / elapsedMs;

        lastSfTime = now;
        lastSfFrames = totalFrames;

        if (fps > 360) {
            return new Result(-1, "SF Counter", true);
        }

        return new Result(fps, "SF Counter", false);
    }

    // ------------------------------------------------------------
    // SF LATENCY
    // Uses actual presentation timestamps.

    private Result bySurfaceFlingerLatency(
            Shell shell, long now, boolean blastOnly) throws Exception {

        String selectedLayer;
        String source;

        if (blastOnly) {
            if (blastLayerName.isEmpty()
                    || now - blastLayerCheckMs >= LAYER_CACHE_MS) {

                blastLayerName = findLayer(shell, true);
                blastLayerCheckMs = now;
            }

            selectedLayer = blastLayerName;
            source = "BLAST SF latency";

        } else {
            if (otherLayerName.isEmpty()
                    || now - otherLayerCheckMs >= LAYER_CACHE_MS) {

                otherLayerName = findLayer(shell, false);
                otherLayerCheckMs = now;
            }

            selectedLayer = otherLayerName;
            source = "SF latency";
        }

        if (selectedLayer.isEmpty()) {
            return NONE;
        }

        String out = shell.surfaceFlingerLatency(selectedLayer);

        if (out == null || out.isEmpty()
                || out.contains("permission")
                || out.contains("does not exist")) {

            if (blastOnly) {
                blastLayerName = "";
            } else {
                otherLayerName = "";
            }

            return NONE;
        }

        String[] lines = out.split("\n");
        List<Long> presents = new ArrayList<>();

        // First line is the refresh period.
        // Second column is the actual presentation timestamp.
        for (int i = 1; i < lines.length; i++) {

            String[] cols = lines[i].trim().split("\\s+");

            if (cols.length < 3) continue;

            try {
                long timestamp = Long.parseLong(cols[1]);

                if (timestamp > 0
                        && timestamp < Long.MAX_VALUE / 2
                        && (presents.isEmpty()
                        || timestamp != presents.get(presents.size() - 1))) {

                    presents.add(timestamp);
                }

            } catch (NumberFormatException ignored) {
            }
        }

        if (presents.size() < 2) {
            return NONE;
        }
        Collections.sort(presents);

        long latest = 0;

        for (long timestamp : presents) {
            latest = Math.max(latest, timestamp);
        }

// Ignore stale timestamps.
        if (System.nanoTime() - latest > 2_000_000_000L) {
            return NONE;
        }

// Use the latest one-second window.
        long windowStart = latest - 1_000_000_000L;

        List<Long> windowPresents = new ArrayList<>();

        for (long timestamp : presents) {
            if (timestamp >= windowStart) {
                windowPresents.add(timestamp);
            }
        }

        if (windowPresents.size() < 2) {
            return NONE;
        }

        long first = windowPresents.get(0);
        long last = windowPresents.get(windowPresents.size() - 1);

        if (last <= first) {
            return NONE;
        }

        int frameIntervals = windowPresents.size() - 1;
        List<Double> capturedFrameIntervals = collectNewFrameIntervals(presents);

        double fps = frameIntervals * 1.0e9 / (last - first);

        if (fps <= 0 || fps > 360) {
            return NONE;
        }

        // Faster-reacting FPS: only the newest 500 ms, if there are enough frames.
// The pacing stats below still use the full one-second window.
        double shownFps = fps;
        long fastStart = last - 500_000_000L;
        int fastCount = 0;
        long fastFirst = 0;

        for (long timestamp : windowPresents) {
            if (timestamp >= fastStart) {
                if (fastCount == 0) fastFirst = timestamp;
                fastCount++;
            }
        }

        if (fastCount >= 3 && last > fastFirst) {
            double fast = (fastCount - 1) * 1.0e9 / (last - fastFirst);
            if (fast > 0 && fast <= 360) shownFps = fast;
        }

// Calculate individual frame intervals.
        double totalFrameTime = 0;
        double expectedFrameTime = 1000.0 / fps;

        List<Double> frameTimes = new ArrayList<>();

        for (int i = 1; i < windowPresents.size(); i++) {

            double intervalMs =
                    (windowPresents.get(i) - windowPresents.get(i - 1))
                            / 1_000_000.0;

            frameTimes.add(intervalMs);
            totalFrameTime += intervalMs;
        }

        double averageFrameTime =
                totalFrameTime / frameTimes.size();

// Calculate frame-time standard deviation.
        double variance = 0;

        for (double frameTime : frameTimes) {
            double difference = frameTime - averageFrameTime;
            variance += difference * difference;
        }

        variance /= frameTimes.size();

        double standardDeviation = Math.sqrt(variance);

// Count unusually long frame intervals.
        int stutterCount = 0;

        double stutterThreshold = expectedFrameTime * 1.5;

        for (double frameTime : frameTimes) {
            if (frameTime > stutterThreshold) {
                stutterCount++;
            }
        }

        return new Result(
                shownFps,
                source,
                false,
                averageFrameTime,
                standardDeviation,
                stutterCount,
                capturedFrameIntervals
        );
    }

    /**
     * SurfaceFlinger returns a rolling timestamp buffer. Only consume intervals
     * after the last timestamp seen so overlapping polling windows never
     * contribute the same frame twice. The first read after session start is
     * used only to establish a baseline, excluding frames from before recording.
     */
    private List<Double> collectNewFrameIntervals(List<Long> presents) {
        if (!frameCaptureActive || presents.isEmpty()) {
            return Collections.emptyList();
        }

        long latest = presents.get(presents.size() - 1);
        int previousIndex;

        if (frameCaptureBaselinePending || lastCapturedPresentationNs == 0) {
            // On the first read, start at the first presented frame at or
            // after the session start time. After a detector/layer reset,
            // Long.MAX_VALUE means re-baseline at the newest frame instead.
            if (frameCaptureStartNs == Long.MAX_VALUE) {
                previousIndex = presents.size() - 1;
            } else {
                previousIndex = 0;
                while (previousIndex < presents.size()
                        && presents.get(previousIndex) < frameCaptureStartNs) {
                    previousIndex++;
                }
                if (previousIndex >= presents.size()) {
                    lastCapturedPresentationNs = latest;
                    frameCaptureBaselinePending = false;
                    return Collections.emptyList();
                }
            }
            lastCapturedPresentationNs = presents.get(previousIndex);
            frameCaptureBaselinePending = false;
        } else {
            previousIndex = Collections.binarySearch(
                    presents, lastCapturedPresentationNs);
            if (previousIndex < 0) {
                // The layer changed or the rolling buffer no longer contains
                // our cursor. Re-baseline rather than inventing a long interval.
                lastCapturedPresentationNs = latest;
                return Collections.emptyList();
            }
        }

        List<Double> intervals = new ArrayList<>();
        for (int i = previousIndex + 1; i < presents.size(); i++) {
            long deltaNs = presents.get(i) - presents.get(i - 1);
            double intervalMs = deltaNs / 1_000_000.0;
            if (deltaNs > 0 && Double.isFinite(intervalMs)
                    && intervalMs > 0 && intervalMs <= 10_000) {
                intervals.add(intervalMs);
            }
        }

        lastCapturedPresentationNs = latest;
        return intervals;
    }

    private String findLayer(
            Shell shell, boolean blastOnly) throws Exception {

        String out = shell.surfaceFlingerLayers();

        if (out == null) return "";

        String selected = "";

        for (String raw : out.split("\n")) {

            String line = raw.trim();

            if (!line.contains(pkg)) continue;
            if (!line.contains("SurfaceView")) continue;

            boolean isBlast = line.contains("BLAST");

            if (blastOnly && !isBlast) continue;
            if (!blastOnly && isBlast) continue;

            // Extract the layer name from RequestedLayerState.
            int start = line.indexOf('{');
            int end = line.indexOf(" parentId=");

            if (start >= 0 && end > start) {
                line = line.substring(start + 1, end).trim();
            }

            if (blastOnly) {
                return line;
            }

            if (selected.isEmpty()) {
                selected = line;
            }
        }

        return selected;
    }


    // ------------------------------------------------------------
    // TIMESTATS
    // Uses SurfaceFlinger per-layer average FPS.


    private Result byTimeStats(Shell shell) throws Exception {

        String layers = shell.surfaceFlingerLayers();

        if (layers == null || layers.isEmpty()) {
            return NONE;
        }

        List<String> liveLayers = new ArrayList<>();

        for (String raw : layers.split("\n")) {
            String line = raw.trim();

            if (line.contains("SurfaceView")
                    && line.contains(pkg)) {
                liveLayers.add(line);
            }
        }

        if (liveLayers.isEmpty()) {
            return NONE;
        }

        String dump = shell.surfaceFlingerTimeStatsDump();

        if (dump == null || dump.isEmpty()) {
            return NONE;
        }

        Pattern layerPattern =
                Pattern.compile("^layerName\\s*=\\s*(.+)$");

        Pattern framesPattern =
                Pattern.compile("^totalFrames\\s*=\\s*([0-9]+)");

        String currentLayer = null;
        String selectedLayer = null;
        long selectedFrames = -1;

        for (String raw : dump.split("\n")) {
            String line = raw.trim();

            Matcher layerMatcher = layerPattern.matcher(line);

            if (layerMatcher.find()) {
                currentLayer = layerMatcher.group(1).trim();
                continue;
            }

            Matcher framesMatcher = framesPattern.matcher(line);

            if (currentLayer != null && framesMatcher.find()) {
                boolean isLiveLayer = false;

                String normalizedCurrent = currentLayer
                        .replace("\\(", "(")
                        .replace("\\)", ")")
                        .replaceAll("\\s+", " ")
                        .trim();

                for (String liveLayer : liveLayers) {
                    String normalizedLive = liveLayer
                            .replace("\\(", "(")
                            .replace("\\)", ")")
                            .replaceAll("\\s+", " ")
                            .trim();

                    if (normalizedLive.equals(normalizedCurrent)) {
                        isLiveLayer = true;
                        break;
                    }
                }

                if (currentLayer.contains(pkg)
                        && currentLayer.contains("SurfaceView")
                        && isLiveLayer) {

                    try {
                        selectedLayer = currentLayer;
                        selectedFrames = Long.parseLong(
                                framesMatcher.group(1));
                        break;
                    } catch (NumberFormatException ignored) {
                    }
                }

                currentLayer = null;
            }
        }

        if (selectedLayer == null || selectedFrames < 0) {
            return NONE;
        }

        long now = elapsedRealtime.getAsLong();

        // Establish initial baseline.
        if (!selectedLayer.equals(timeStatsLayerName)
                || lastTimeStatsTimeMs == 0
                || selectedFrames < lastTimeStatsFrames) {

            timeStatsLayerName = selectedLayer;
            lastTimeStatsFrames = selectedFrames;
            lastTimeStatsTimeMs = now;

            return new Result(-1, "TimeStats", true);
        }

        long elapsedMs = now - lastTimeStatsTimeMs;
        long frameDelta = selectedFrames - lastTimeStatsFrames;

        if (elapsedMs < 500) {
            return new Result(-1, "TimeStats", true);
        }

        // Update baseline.
        lastTimeStatsFrames = selectedFrames;
        lastTimeStatsTimeMs = now;

        if (frameDelta <= 0) {
            return new Result(-1, "TimeStats", true);
        }

        double fps = frameDelta * 1000.0 / elapsedMs;

        if (fps <= 0 || fps > 360) {
            return new Result(-1, "TimeStats", true);
        }

        return new Result(fps, "TimeStats", false);
    }

}
