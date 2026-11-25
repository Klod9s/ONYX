package com.onyx.perfmonitor;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.content.pm.PackageManager;
import rikka.shizuku.Shizuku;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Context-independent owner for the live metric sampling state.
 * The Activity and foreground overlay service can each own an instance;
 * only one should sample at a time.
 */
final class MonitoringEngine {
    private final Context context;
    private final BatteryCurrent batteryCurrent;
    private IOnyxUserService shizukuService;
    private final FpsDetector fpsDetector = new FpsDetector();
    private volatile boolean frameCaptureRequested;
    private volatile boolean frameCaptureActive;
    private long startMs = System.currentTimeMillis();
    private String lastPackage = "Unknown";
    private final CpuUsageTracker cpuUsageTracker = new CpuUsageTracker();
    private double lastDisplayedFps = -1;
    private long lastDisplayedFpsTime = 0;
    private String lastDisplayedFpsPackage = "";
    private String cachedForegroundPackage = "";
    private long lastForegroundCheckMs = 0;
    private String cachedGpuFreqPath = "";
    private String cachedGpuLoadPath = "";
    private String cachedMaliPath = "";
    private boolean maliSearchCompleted = false;
    private volatile boolean rootChecked = false;
    private volatile boolean cachedRoot = false;
    private volatile int rootFailStreak = 0;
    private long lastRootCheckMs = 0;
    private String cachedMonitoringMode = "";
    private long lastModeCheckMs = 0;
    private String lastSamplingMode = "";

    MonitoringEngine(Context context) {
        this.context = context.getApplicationContext();
        this.batteryCurrent = new BatteryCurrent(this.context);
    }

    void setShizukuService(IOnyxUserService service) {
        this.shizukuService = service;
    }

    String getMonitoringMode() {
        return cachedMonitoringMode.isEmpty() ? "ANDROID" : cachedMonitoringMode;
    }

    void resetCpuBaseline() {
        cpuUsageTracker.reset();
    }

    void resetSessionClock() {
        startMs = System.currentTimeMillis();
    }

    void beginFrameCapture() {
        frameCaptureRequested = true;
        frameCaptureActive = true;
    }

    void endFrameCapture() {
        frameCaptureActive = false;
        frameCaptureRequested = false;
    }

    void resetMonitoringModeCheck() {
        rootChecked = false;
        rootFailStreak = 0;
        lastRootCheckMs = 0;
        lastModeCheckMs = 0;
        cachedMonitoringMode = "";
    }

    void resetBaselines() {
        resetCpuBaseline();
        fpsDetector.reset();
        lastDisplayedFps = -1;
        lastDisplayedFpsTime = 0;
        lastDisplayedFpsPackage = "";
    }

    Sample sample(boolean recording) {
        if (frameCaptureRequested) {
            fpsDetector.beginFrameCapture();
            frameCaptureRequested = false;
        }
        if (!recording && frameCaptureActive) {
            fpsDetector.endFrameCapture();
            frameCaptureActive = false;
        }

        String mode = detectMonitoringMode();

        // CPU counters and FPS frame baselines are source-dependent. Do not
        // calculate across a transition between ROOT, SHIZUKU, and ANDROID.
        if (!mode.equals(lastSamplingMode)) {
            resetBaselines();
            lastSamplingMode = mode;
        }

        if ("ROOT".equals(mode)) return readSample(recording);
        if ("SHIZUKU".equals(mode)) return readShizukuSample(recording);
        return readAndroidSample(recording);
    }

private Sample readSample(boolean recording) {
        String script =
                "APP=$(dumpsys window 2>/dev/null | " +
                        "grep -m 1 'mCurrentFocus=Window' | " +
                        "sed 's/.* u[0-9]* //; s#/.*##'); " +
                        "[ -n \"$APP\" ] || APP=Unknown; " +
                        "echo __APP__; echo \"$APP\"; " +
                        "echo __TEMP__; cat /sys/class/power_supply/battery/temp 2>/dev/null; " +
                        "echo __FPS__; cat /sys/devices/platform/soc/ae00000.qcom,mdss_mdp/drm/card0/card0-sde-crtc-0/measured_fps 2>/dev/null; " +
                        "echo __BAT__; " +
                        "cat /sys/class/power_supply/battery/current_now 2>/dev/null; " +
                        "cat /sys/class/power_supply/battery/voltage_now 2>/dev/null; " +
                        "cat /sys/class/power_supply/battery/capacity 2>/dev/null; " +
                        "echo __CPU__; cat /proc/stat; " +
                        "echo __CPU_FREQ__; " +
                        "for i in 0 1 2 3 4 5 6 7; do " +
                        "F=/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq; " +
                        "if [ -r \"$F\" ]; then cat \"$F\"; else echo -1; fi; " +
                        "done; " +

                        // --- STRICT UNIVERSAL GPU DETECTION ---
                        "CACHED_FREQ='" + validateSysfsPath(cachedGpuFreqPath) + "'; " +
                        "CACHED_LOAD='" + validateSysfsPath(cachedGpuLoadPath) + "'; " +
                        "CACHED_MALI='" + validateSysfsPath(cachedMaliPath) + "'; " +
                        "GPU_FREQ=-1; GPU_LOAD='-1 -1'; FOUND_FREQ='none'; FOUND_LOAD='none'; " +
                        "MALI_PATH=\"$CACHED_MALI\"; " +
                        "MALI_RETRY=0; " +
                        "if [ -n \"$MALI_PATH\" ] && [ ! -d \"$MALI_PATH\" ]; then " +
                        " MALI_PATH=''; " +
                        " MALI_RETRY=1; " +
                        "fi; " +
                        "if [ -z \"$MALI_PATH\" ] && { " +
                        (!maliSearchCompleted ? "[ 1 = 1 ]" : "[ 1 = 0 ]") +
                        " || [ \"$MALI_RETRY\" = 1 ]; }; then " +
                        " MALI_PATH=$(find /sys/devices/platform/soc -name '*.mali' -type d 2>/dev/null | head -n 1); " +
                        "fi; " +

                        // GPU Frequency
                        "if [ -r /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq ]; then " +
                        "  GPU_FREQ=$(cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq); FOUND_FREQ='/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq'; " +
                        "elif [ -n \"$CACHED_FREQ\" ] && [ -r \"$CACHED_FREQ\" ]; then " +
                        "  GPU_FREQ=$(cat \"$CACHED_FREQ\"); FOUND_FREQ=\"$CACHED_FREQ\"; " +
                        "elif [ -n \"$MALI_PATH\" ]; then " +
                        " for F in \"$MALI_PATH\"/devfreq/cur_freq \"$MALI_PATH\"/devfreq/*/cur_freq; do " +
                        "  if [ -r \"$F\" ]; then " +
                        "   GPU_FREQ=$(cat \"$F\"); FOUND_FREQ=\"$F\"; break; " +
                        "  fi; " +
                        " done; " +
                        " if [ \"$FOUND_FREQ\" = 'none' ] && [ -r \"$MALI_PATH/device/clock\" ]; then " +
                        "  GPU_FREQ=$(cat \"$MALI_PATH/device/clock\"); FOUND_FREQ=\"$MALI_PATH/device/clock\"; " +
                        " fi; " +
                        "fi; " +
                        "echo __GPU_FREQ_BLOCK__; echo $GPU_FREQ; echo $FOUND_FREQ; " +

                        // GPU Load
                        "if [ -r /sys/class/kgsl/kgsl-3d0/gpubusy ]; then " +
                        "  GPU_LOAD=$(cat /sys/class/kgsl/kgsl-3d0/gpubusy); FOUND_LOAD='/sys/class/kgsl/kgsl-3d0/gpubusy'; " +
                        "elif [ -n \"$CACHED_LOAD\" ] && [ -r \"$CACHED_LOAD\" ]; then " +
                        "  GPU_LOAD=$(cat \"$CACHED_LOAD\"); FOUND_LOAD=\"$CACHED_LOAD\"; " +
                        "elif [ -n \"$MALI_PATH\" ]; then " +
                        " for F in \"$MALI_PATH\"/devfreq/load \"$MALI_PATH\"/devfreq/*/load; do " +
                        "  if [ -r \"$F\" ]; then " +
                        "   GPU_LOAD=$(cat \"$F\"); FOUND_LOAD=\"$F\"; break; " +
                        "  fi; " +
                        " done; " +
                        " if [ \"$FOUND_LOAD\" = 'none' ] && [ -r \"$MALI_PATH/device/utilisation\" ]; then " +
                        "  GPU_LOAD=$(cat \"$MALI_PATH/device/utilisation\"); FOUND_LOAD=\"$MALI_PATH/device/utilisation\"; " +
                        " fi; " +
                        "fi; " +
                        "echo __GPU_LOAD_BLOCK__; echo $GPU_LOAD; echo $FOUND_LOAD; " +
                        "echo __MALI_BLOCK__; " +
                        "[ -n \"$MALI_PATH\" ] && echo \"$MALI_PATH\" || echo none; " +
                        "echo __END__; ";

        String output = rootCommand(script);
        if (output == null) return null;

        try {
            String app = between(output, "__APP__\n", "__TEMP__").trim();
            String tempRaw = between(output, "__TEMP__\n", "__FPS__").trim();
            String fpsRaw;

            int batIndex = output.indexOf("__BAT__");
            if (batIndex >= 0) {
                fpsRaw = between(output, "__FPS__\n", "__BAT__");
            } else {
                fpsRaw = output.substring(output.indexOf("__FPS__") + 7);
            }

            Sample sample = new Sample();
            sample.timeMs = System.currentTimeMillis() - startMs;
            sample.app = app.isEmpty() ? "Unknown" : app;
            lastPackage = sample.app;

            // IMPROVED FPS FALLBACK LOGIC
            double displayFps = parseFps(fpsRaw);
            FpsDetector.Result r = fpsDetector.detect(cmd -> {
                String out = rootCommand(cmd);
                return out == null ? "" : out;
            }, sample.app);

            if (r.valid()) {
                sample.fps = r.fps;
                sample.fpsSource = r.source;
                sample.averageFrameTimeMs = r.averageFrameTimeMs;
                sample.frameTimeStdDevMs = r.frameTimeStdDevMs;
                sample.stutterCount = r.stutterCount;
                sample.frameIntervalsMs.addAll(r.frameIntervalsMs);
            } else if (displayFps > 0) {
                sample.fps = displayFps;
                sample.fpsSource = "Display Controller";
                sample.averageFrameTimeMs = 1000.0 / displayFps;
                sample.frameTimeStdDevMs = -1;
                sample.stutterCount = -1;
            } else {
                sample.fps = -1;
                sample.fpsSource = "none";
                sample.averageFrameTimeMs = -1;
                sample.frameTimeStdDevMs = -1;
                sample.stutterCount = -1;
            }

            // Defensive temperature parsing
            try {
                double rawTemp = Double.parseDouble(tempRaw.trim());
                sample.temperatureC = rawTemp > 100 ? rawTemp / 10.0 : rawTemp;
            } catch (Exception ignored) {
                sample.temperatureC = -1;
            }

            java.util.Arrays.fill(sample.cpu, -1);
            java.util.Arrays.fill(sample.cpuFreqMHz, -1);
            sample.gpuFreqMHz = -1;
            sample.gpuLoadPercent = -1;

            // Defensive battery parsing
            if (batIndex >= 0) {
                String bat = between(output, "__BAT__\n", "__CPU__").trim();
                String[] values = bat.split("\\s+");
                if (values.length >= 3) {
                    try {
                        long rawCurrent = Long.parseLong(values[0].trim());
                        long rawVoltage = Long.parseLong(values[1].trim());
                        int percent = Integer.parseInt(values[2].trim());

                        Intent powerIntent = context.registerReceiver(
                                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                        boolean unplugged = powerIntent != null
                                && powerIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) == 0;

                        int status = powerIntent != null
                                ? powerIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                                : -1;

                        sample.charging =
                                status == BatteryManager.BATTERY_STATUS_CHARGING
                                        || status == BatteryManager.BATTERY_STATUS_FULL;

                        boolean currentValid = rawCurrent != Long.MIN_VALUE;
                        boolean voltageValid = rawVoltage > 0;
                        boolean percentValid = percent >= 0 && percent <= 100;

                        sample.currentMa = currentValid
                                ? batteryCurrent.toMilliamps(rawCurrent, unplugged) : -1;
                        sample.voltage = voltageValid ? rawVoltage / 1000000.0 : -1;
                        sample.power = currentValid && voltageValid
                                ? sample.currentMa * sample.voltage / 1000.0 : -1;
                        sample.battery = percentValid ? percent : -1;
                    } catch (Exception ignored) {
                        sample.currentMa = -1;
                        sample.voltage = -1;
                        sample.power = -1;
                        sample.battery = -1;
                    }
                } else {
                    sample.currentMa = -1;
                    sample.voltage = -1;
                    sample.power = -1;
                    sample.battery = -1;
                }
            }

            // Independent CPU Usage parsing
            String cpu = between(output, "__CPU__\n", "__CPU_FREQ__");
            if (!cpu.isEmpty()) {
                sample.cpu = parseCpu(cpu);
            }

            // Independent Frequency parsing using explicit markers
            String cpuFreqRaw = between(output, "__CPU_FREQ__\n", "__GPU_FREQ_BLOCK__").trim();
            if (!cpuFreqRaw.isEmpty()) {
                String[] cpuFreqValues = cpuFreqRaw.split("\\s+");
                for (int i = 0; i < 8 && i < cpuFreqValues.length; i++) {
                    try {
                        double khz = Double.parseDouble(cpuFreqValues[i]);
                        sample.cpuFreqMHz[i] = khz > 0 ? khz / 1000.0 : -1;
                    } catch (Exception ignored) {
                        sample.cpuFreqMHz[i] = -1;
                    }
                }
            }

            // GPU Freq Block (FIXED: uses 'output', not 'freqOutput')
            String gpuFreqBlock = between(output, "__GPU_FREQ_BLOCK__\n", "__GPU_LOAD_BLOCK__").trim();
            String[] gpuFreqParts = gpuFreqBlock.split("\\s+");
            if (gpuFreqParts.length >= 1) {
                try {
                    double val = Double.parseDouble(gpuFreqParts[0]);
                    if (val > 100000) sample.gpuFreqMHz = val / 1000000.0;
                    else if (val > 100) sample.gpuFreqMHz = val / 1000.0;
                    else sample.gpuFreqMHz = val;
                    if (sample.gpuFreqMHz <= 0) sample.gpuFreqMHz = -1;
                } catch (Exception ignored) {}
            }
            if (gpuFreqParts.length >= 2 && !gpuFreqParts[1].equals("none")) {
                cachedGpuFreqPath = validateSysfsPath(gpuFreqParts[1]);
            }

            // GPU Load Block (FIXED: uses 'output', not 'freqOutput')
            String gpuLoadBlock = between(output, "__GPU_LOAD_BLOCK__\n", "__MALI_BLOCK__").trim();
            String[] gpuLoadParts = gpuLoadBlock.split("\\s+");
            if (gpuLoadParts.length >= 2) {
                try {
                    String path = gpuLoadParts[gpuLoadParts.length - 1];
                    if (!path.equals("none")) {
                        cachedGpuLoadPath = validateSysfsPath(path);
                    }
                    if (gpuLoadParts.length == 3) {
                        double busy = Double.parseDouble(gpuLoadParts[0]);
                        double total = Double.parseDouble(gpuLoadParts[1]);
                        if (total == -1 && busy >= 0 && busy <= 100) {
                            sample.gpuLoadPercent = busy;
                        } else if (total > 0 && busy >= 0) {
                            sample.gpuLoadPercent = Math.max(0, Math.min(100, busy / total * 100.0));
                        }
                    } else if (gpuLoadParts.length == 2) {
                        double load = Double.parseDouble(gpuLoadParts[0]);
                        if (load >= 0 && load <= 100) {
                            sample.gpuLoadPercent = load;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Cache MALI_PATH (FIXED: uses 'output', not 'freqOutput')
            String maliBlock = between(output, "__MALI_BLOCK__\n", "__END__").trim();
            if (!maliBlock.isEmpty()) {
                cachedMaliPath = maliBlock.equals("none")
                        ? ""
                        : validateSysfsPath(maliBlock);
            }

            maliSearchCompleted = true;

            return sample;
        } catch (Exception e) {
            return null;
        }
    }

private Sample readShizukuSample(boolean recording) {
        Sample sample = readAndroidSample(recording);
        if (sample == null) return null;

        IOnyxUserService service = shizukuService;
        if (service == null) {
            clearFps(sample);
            fpsDetector.reset();
            return sample;
        }

        try {
            long now = android.os.SystemClock.elapsedRealtime();

            try {
                java.util.Arrays.fill(sample.cpu, -1);
                java.util.Arrays.fill(sample.cpuFreqMHz, -1);
                sample.gpuFreqMHz = -1;
                sample.gpuLoadPercent = -1;

                // Universal script with safe Mali discovery guard
                String freqScript =
                        "echo __PROC_STAT__; cat /proc/stat; " +
                                "echo __CPU_FREQ__; " +
                                "for i in 0 1 2 3 4 5 6 7; do " +
                                "F=/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq; " +
                                "if [ -r \"$F\" ]; then cat \"$F\"; else echo -1; fi; " +
                                "done; " +
                                "CACHED_FREQ='" + validateSysfsPath(cachedGpuFreqPath) + "'; " +
                                "CACHED_LOAD='" + validateSysfsPath(cachedGpuLoadPath) + "'; " +
                                "CACHED_MALI='" + validateSysfsPath(cachedMaliPath) + "'; " +
                                "GPU_FREQ=-1; GPU_LOAD='-1 -1'; FOUND_FREQ='none'; FOUND_LOAD='none'; " +
                                "MALI_PATH=\"$CACHED_MALI\"; " +
                                "MALI_RETRY=0; " +
                                "if [ -n \"$MALI_PATH\" ] && [ ! -d \"$MALI_PATH\" ]; then " +
                                " MALI_PATH=''; " +
                                " MALI_RETRY=1; " +
                                "fi; " +
                                "if [ -z \"$MALI_PATH\" ] && { " +
                                (!maliSearchCompleted ? "[ 1 = 1 ]" : "[ 1 = 0 ]") +
                                " || [ \"$MALI_RETRY\" = 1 ]; }; then " +
                                " MALI_PATH=$(find /sys/devices/platform/soc -name '*.mali' -type d 2>/dev/null | head -n 1); " +
                                "fi; " +
                                "if [ -r /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq ]; then " +
                                "  GPU_FREQ=$(cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq); FOUND_FREQ='/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq'; " +
                                "elif [ -n \"$CACHED_FREQ\" ] && [ -r \"$CACHED_FREQ\" ]; then " +
                                "  GPU_FREQ=$(cat \"$CACHED_FREQ\"); FOUND_FREQ=\"$CACHED_FREQ\"; " +
                                "elif [ -n \"$MALI_PATH\" ]; then " +
                                " for F in \"$MALI_PATH\"/devfreq/cur_freq \"$MALI_PATH\"/devfreq/*/cur_freq; do " +
                                "  if [ -r \"$F\" ]; then " +
                                "   GPU_FREQ=$(cat \"$F\"); FOUND_FREQ=\"$F\"; break; " +
                                "  fi; " +
                                " done; " +
                                " if [ \"$FOUND_FREQ\" = 'none' ] && [ -r \"$MALI_PATH/device/clock\" ]; then " +
                                "  GPU_FREQ=$(cat \"$MALI_PATH/device/clock\"); FOUND_FREQ=\"$MALI_PATH/device/clock\"; " +
                                " fi; " +
                                "fi; " +
                                "echo __GPU_FREQ_BLOCK__; echo $GPU_FREQ; echo $FOUND_FREQ; " +
                                "if [ -r /sys/class/kgsl/kgsl-3d0/gpubusy ]; then " +
                                "  GPU_LOAD=$(cat /sys/class/kgsl/kgsl-3d0/gpubusy); FOUND_LOAD='/sys/class/kgsl/kgsl-3d0/gpubusy'; " +
                                "elif [ -n \"$CACHED_LOAD\" ] && [ -r \"$CACHED_LOAD\" ]; then " +
                                "  GPU_LOAD=$(cat \"$CACHED_LOAD\"); FOUND_LOAD=\"$CACHED_LOAD\"; " +
                                "elif [ -n \"$MALI_PATH\" ]; then " +
                                " for F in \"$MALI_PATH\"/devfreq/load \"$MALI_PATH\"/devfreq/*/load; do " +
                                "  if [ -r \"$F\" ]; then " +
                                "   GPU_LOAD=$(cat \"$F\"); FOUND_LOAD=\"$F\"; break; " +
                                "  fi; " +
                                " done; " +
                                " if [ \"$FOUND_LOAD\" = 'none' ] && [ -r \"$MALI_PATH/device/utilisation\" ]; then " +
                                "  GPU_LOAD=$(cat \"$MALI_PATH/device/utilisation\"); FOUND_LOAD=\"$MALI_PATH/device/utilisation\"; " +
                                " fi; " +
                                "fi; " +
                                "echo __GPU_LOAD_BLOCK__; echo $GPU_LOAD; echo $FOUND_LOAD; " +
                                "echo __MALI_BLOCK__; " +
                                "[ -n \"$MALI_PATH\" ] && echo \"$MALI_PATH\" || echo none; " +
                                "echo __END__;";

                String freqOutput = service.runCommand(freqScript);

                if (freqOutput != null) {
                    String cpuOutput = between(
                            freqOutput, "__PROC_STAT__\n", "__CPU_FREQ__"
                    ).trim();

                    if (!cpuOutput.isEmpty()) {
                        sample.cpu = parseCpu(cpuOutput);
                    }
                    // Parse CPU Freq
                    String cpuFreqRaw = between(freqOutput, "__CPU_FREQ__\n", "__GPU_FREQ_BLOCK__").trim();
                    if (!cpuFreqRaw.isEmpty()) {
                        String[] cpuFreqValues = cpuFreqRaw.split("\\s+");
                        for (int i = 0; i < 8 && i < cpuFreqValues.length; i++) {
                            try {
                                double khz = Double.parseDouble(cpuFreqValues[i]);
                                sample.cpuFreqMHz[i] = khz > 0 ? khz / 1000.0 : -1;
                            } catch (Exception ignored) {
                                sample.cpuFreqMHz[i] = -1;
                            }
                        }
                    }

                    // Parse GPU Freq
                    String gpuFreqBlock = between(freqOutput, "__GPU_FREQ_BLOCK__\n", "__GPU_LOAD_BLOCK__").trim();
                    String[] gpuFreqParts = gpuFreqBlock.split("\\s+");
                    if (gpuFreqParts.length >= 1) {
                        try {
                            double val = Double.parseDouble(gpuFreqParts[0]);
                            if (val > 100000) sample.gpuFreqMHz = val / 1000000.0;
                            else if (val > 100) sample.gpuFreqMHz = val / 1000.0;
                            else sample.gpuFreqMHz = val;
                            if (sample.gpuFreqMHz <= 0) sample.gpuFreqMHz = -1;
                        } catch (Exception ignored) {}
                    }
                    if (gpuFreqParts.length >= 2 && !gpuFreqParts[1].equals("none")) {
                        cachedGpuFreqPath = validateSysfsPath(gpuFreqParts[1]);
                    }

                    // Parse GPU Load
                    String gpuLoadBlock = between(freqOutput, "__GPU_LOAD_BLOCK__\n", "__MALI_BLOCK__").trim();
                    String[] gpuLoadParts = gpuLoadBlock.split("\\s+");
                    if (gpuLoadParts.length >= 2) {
                        try {
                            String path = gpuLoadParts[gpuLoadParts.length - 1];
                            if (!path.equals("none")) {
                                cachedGpuLoadPath = validateSysfsPath(path);
                            }
                            if (gpuLoadParts.length == 3) {
                                double busy = Double.parseDouble(gpuLoadParts[0]);
                                double total = Double.parseDouble(gpuLoadParts[1]);
                                if (total == -1 && busy >= 0 && busy <= 100) {
                                    sample.gpuLoadPercent = busy;
                                } else if (total > 0 && busy >= 0) {
                                    sample.gpuLoadPercent = Math.max(0, Math.min(100, busy / total * 100.0));
                                }
                            } else if (gpuLoadParts.length == 2) {
                                double load = Double.parseDouble(gpuLoadParts[0]);
                                if (load >= 0 && load <= 100) {
                                    sample.gpuLoadPercent = load;
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    // Cache MALI_PATH safely
                    String maliBlock = between(freqOutput, "__MALI_BLOCK__\n", "__END__").trim();
                    if (!maliBlock.isEmpty()) {
                        cachedMaliPath = maliBlock.equals("none")
                                ? ""
                                : validateSysfsPath(maliBlock);
                    }

                    maliSearchCompleted = true;
                }
            } catch (Exception ignored) {
                // Keep frequency unavailable if the command fails
            }

            // Faster foreground package refresh while recording
            long foregroundRefreshMs = recording ? 1000 : 2000;
            if (cachedForegroundPackage.isEmpty() || now - lastForegroundCheckMs >= foregroundRefreshMs) {
                String foregroundOutput = service.runCommand(
                        "dumpsys activity activities 2>/dev/null" +
                                " | grep -m 1 -E 'topResumedActivity|mResumedActivity'"
                );
                if (foregroundOutput == null) foregroundOutput = "";

                java.util.regex.Pattern componentPattern =
                        java.util.regex.Pattern.compile(
                                "([A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+)/" +
                                        "[A-Za-z0-9_.$]+"
                        );
                java.util.regex.Matcher foregroundMatcher =
                        componentPattern.matcher(foregroundOutput);
                String foundPackage = foregroundMatcher.find() ? foregroundMatcher.group(1) : "";

                if (foundPackage.isEmpty()) {
                    String windowOutput = service.runCommand(
                            "dumpsys window 2>/dev/null" +
                                    " | grep -m 1 -E 'mCurrentFocus|mFocusedApp'"
                    );
                    if (windowOutput != null) {
                        java.util.regex.Matcher windowMatcher =
                                componentPattern.matcher(windowOutput);
                        if (windowMatcher.find()) foundPackage = windowMatcher.group(1);
                    }
                }
                cachedForegroundPackage = foundPackage;
                lastForegroundCheckMs = now;
            }

            String foregroundPackage = cachedForegroundPackage;
            if (foregroundPackage.isEmpty()) {
                clearFps(sample);
                fpsDetector.reset();
                return sample;
            }

            sample.app = foregroundPackage;
            FpsDetector.Result r = fpsDetector.detect(service::runCommand, foregroundPackage);

            if (r.valid()) {
                sample.fps = r.fps;
                sample.fpsSource = r.source;
                sample.averageFrameTimeMs = r.averageFrameTimeMs;
                sample.frameTimeStdDevMs = r.frameTimeStdDevMs;
                sample.stutterCount = r.stutterCount;
                sample.frameIntervalsMs.addAll(r.frameIntervalsMs);

                lastDisplayedFps = r.fps;
                lastDisplayedFpsTime = now;
                lastDisplayedFpsPackage = foregroundPackage;
            } else if (lastDisplayedFps >= 0
                    && foregroundPackage.equals(lastDisplayedFpsPackage)
                    && now - lastDisplayedFpsTime < 2000) {
                sample.fps = lastDisplayedFps;
                sample.fpsSource = "SF Counter";
                sample.averageFrameTimeMs = -1;
                sample.frameTimeStdDevMs = -1;
                sample.stutterCount = -1;
            } else {
                clearFps(sample);
            }

        } catch (Exception e) {
            clearFps(sample);
        }
        return sample;
    }

private Sample readAndroidSample(boolean recording) {
        try {
            Intent batteryIntent = context.registerReceiver(
                    null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            );

            if (batteryIntent == null) return null;

            Sample sample = new Sample();

            sample.timeMs = System.currentTimeMillis() - startMs;
            sample.app = "Unknown";
            sample.fps = -1;
            sample.fpsSource = "none";

            int temp = batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_TEMPERATURE, -1);
            sample.temperatureC = temp >= 0 ? temp / 10.0 : -1;

            // Explicitly initialize all arrays/metrics to -1
            java.util.Arrays.fill(sample.cpu, -1);
            java.util.Arrays.fill(sample.cpuFreqMHz, -1);
            sample.gpuFreqMHz = -1;
            sample.gpuLoadPercent = -1;

            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);

            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            sample.battery = (level >= 0 && scale > 0) ? Math.round(level * 100f / scale) : -1;

            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            sample.charging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL;

            int voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            sample.voltage = voltage >= 0 ? voltage / 1000.0 : -1;

            long current = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            boolean unplugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) == 0;
            boolean currentValid = current != Long.MIN_VALUE;

            sample.currentMa = currentValid ? batteryCurrent.toMilliamps(current, unplugged) : -1;
            sample.power = currentValid && sample.voltage > 0 ? sample.currentMa * sample.voltage / 1000.0 : -1;

            return sample;

        } catch (Exception e) {
            return null;
        }
    }

private double[] parseCpu(String raw) {
        return cpuUsageTracker.parse(raw);
    }

    private double parseFps(String output) {
        if (output == null) return -1;

        try {
            java.util.regex.Matcher m =
                    java.util.regex.Pattern
                            .compile("fps:\\s*([0-9]+(?:\\.[0-9]+)?)")
                            .matcher(output);

            if (m.find()) {
                return Double.parseDouble(m.group(1));
            }
        } catch (Exception ignored) {}

        return -1;
    }

private String validateSysfsPath(String path) {
        if (path == null || path.isEmpty() ||
                !path.startsWith("/sys/") ||
                !path.matches("[A-Za-z0-9_./,-]+") ||
                path.contains("..")) {
            return "";
        }
        return path;
    }

private String between(String value, String start, String end) {
        int x = value.indexOf(start);
        if (x < 0) return "";

        x += start.length();
        int y = value.indexOf(end, x);

        return y < 0 ? "" : value.substring(x, y);
    }

private void clearFps(Sample sample) {
        sample.fps = -1;
        sample.fpsSource = "none";
        sample.averageFrameTimeMs = -1;
        sample.frameTimeStdDevMs = -1;
        sample.stutterCount = -1;
    }

private String rootCommand(String command) {
        Process process = null;
        Thread reader = null;

        java.io.ByteArrayOutputStream buffer =
                new java.io.ByteArrayOutputStream();

        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            final InputStream input = process.getInputStream();

            reader = new Thread(() -> {
                try {
                    byte[] data = new byte[4096];
                    int count;

                    while ((count = input.read(data)) != -1) {
                        int remaining = 2_000_000 - buffer.size();

                        if (remaining > 0) {
                            buffer.write(data, 0, Math.min(count, remaining));
                        }
                    }
                } catch (Exception ignored) {
                }
            });

            reader.setDaemon(true);
            reader.start();

            boolean finished = process.waitFor(
                    8, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(1,
                        java.util.concurrent.TimeUnit.SECONDS);
                input.close();
                reader.join(500);
                return null;
            }

            reader.join(1000);

            if (reader.isAlive()) {
                process.destroyForcibly();
                input.close();
                return null;
            }

            return buffer.toString(StandardCharsets.UTF_8.name());

        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return null;
        }
    }

private boolean checkRootAccess() {
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", "id"});

            boolean finished = process.waitFor(
                    2, java.util.concurrent.TimeUnit.SECONDS);

            if (finished) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                String output = reader.readLine();
                reader.close();
                return output != null && output.contains("uid=0");
            }

            process.destroyForcibly();
            process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
        }
        return false;
    }

private String detectMonitoringMode() {
        long now = android.os.SystemClock.elapsedRealtime();

        // Rooted: re-verify every 30 s. Not rooted: retry every 5 s a few times
        // (covers tapping Allow on a prompt), then only once a minute.
        long interval = cachedRoot ? 30_000
                : (rootFailStreak < 3 ? 5_000 : 60_000);

        if (!rootChecked || now - lastRootCheckMs >= interval) {
            cachedRoot = checkRootAccess();
            rootFailStreak = cachedRoot ? 0 : rootFailStreak + 1;
            lastRootCheckMs = now;
            rootChecked = true;
        }

        String mode;
        if (cachedRoot) {
            mode = "ROOT";
        } else if (Shizuku.pingBinder()
                && Shizuku.checkSelfPermission()
                == PackageManager.PERMISSION_GRANTED) {
            mode = "SHIZUKU";
        } else {
            mode = "ANDROID";
        }

        cachedMonitoringMode = mode;
        lastModeCheckMs = now;
        return mode;
    }
}
