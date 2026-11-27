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
    private volatile boolean frameCaptureStopRequested;
    private volatile long frameCaptureStartNs = Long.MAX_VALUE;
    private long startMs = System.currentTimeMillis();
    private String lastPackage = "Unknown";
    private final CpuUsageTracker cpuUsageTracker = new CpuUsageTracker();
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
        frameCaptureStopRequested = false;
        frameCaptureStartNs = System.nanoTime();
        frameCaptureRequested = true;
        frameCaptureActive = true;
    }

    void endFrameCapture() {
        frameCaptureActive = false;
        frameCaptureRequested = false;
        frameCaptureStopRequested = true;
        frameCaptureStartNs = Long.MAX_VALUE;
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
    }

    Sample sample(boolean recording) {
        if (frameCaptureStopRequested) {
            fpsDetector.endFrameCapture();
            frameCaptureStopRequested = false;
        }
        if (frameCaptureRequested) {
            fpsDetector.beginFrameCapture(frameCaptureStartNs);
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

        Sample sample;
        if ("ROOT".equals(mode)) {
            sample = readSample(recording);
        } else if ("SHIZUKU".equals(mode)) {
            sample = readShizukuSample(recording);
        } else {
            sample = readAndroidSample(recording);
        }
        if (sample != null) {
            // Android thermal status is available independently of ROOT/SHIZUKU mode.
            sample.thermalStatus = ThermalStatusReader.read(context);
            sample.ramUsagePercent = readRamUsagePercent();
        }
        return sample;
    }

    private double readRamUsagePercent() {
        try {
            android.app.ActivityManager manager =
                    (android.app.ActivityManager) context.getSystemService(
                            Context.ACTIVITY_SERVICE);
            if (manager == null) return -1;

            android.app.ActivityManager.MemoryInfo memoryInfo =
                    new android.app.ActivityManager.MemoryInfo();
            manager.getMemoryInfo(memoryInfo);

            if (memoryInfo.totalMem <= 0 || memoryInfo.availMem < 0) return -1;

            double used = Math.max(0,
                    memoryInfo.totalMem - Math.min(memoryInfo.availMem, memoryInfo.totalMem));
            return Math.max(0, Math.min(100, used * 100.0 / memoryInfo.totalMem));
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

private Sample readSample(boolean recording) {
        String script =
                "APP=$(dumpsys window 2>/dev/null | " +
                        "grep -m 1 'mCurrentFocus=Window' | " +
                        "sed 's/.* u[0-9]* //; s#/.*##'); " +
                        "[ -n \"$APP\" ] || APP=Unknown; " +
                        "echo __APP__; echo \"$APP\"; " +
                        "echo __TEMP__; cat /sys/class/power_supply/battery/temp 2>/dev/null; " +
                        "echo __BAT__; " +
                        "cat /sys/class/power_supply/battery/current_now 2>/dev/null; " +
                        "cat /sys/class/power_supply/battery/voltage_now 2>/dev/null; " +
                        "cat /sys/class/power_supply/battery/capacity 2>/dev/null; " +
                        "echo __CPU__; cat /proc/stat; " +
                        "echo __CPU_FREQ__; " +
                        "for i in 0 1 2 3 4 5 6 7; do " +
                        "F=/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq; V=; " +
                        "[ -r \"$F\" ] && V=$(cat \"$F\" 2>/dev/null); " +
                        "case \"$V\" in \"\"|*[!0-9]*) V=;; esac; " +
                        "if [ -z \"$V\" ] || [ \"$V\" -le 0 ] 2>/dev/null; then " +
                        " F=/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_cur_freq; V=; " +
                        " [ -r \"$F\" ] && V=$(cat \"$F\" 2>/dev/null); " +
                        " case \"$V\" in \"\"|*[!0-9]*) V=;; esac; " +
                        "fi; " +
                        "if [ -n \"$V\" ] && [ \"$V\" -gt 0 ] 2>/dev/null; then echo \"$V\"; else echo -1; fi; " +
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
                        "echo __THERMAL_BLOCK__; " +
                        "for Z in /sys/class/thermal/thermal_zone*; do " +
                        " T=; IFS= read -r T < \"$Z/type\" 2>/dev/null; " +
                        " case \"$T\" in cpu-*) K=CPU;; gpu-*|gpuss-*) K=GPU;; *) continue;; esac; " +
                        " V=; IFS= read -r V < \"$Z/temp\" 2>/dev/null; " +
                        " echo \"$K $V\"; " +
                        "done; " +
                        "echo __COOLING_BLOCK__; " +
                        "for C in /sys/class/thermal/cooling_device*; do " +
                        " [ -d \"$C\" ] || continue; " +
                        " T=; IFS= read -r T < \"$C/type\" 2>/dev/null; " +
                        " case \"$T\" in *cpu*|*gpu*|*cpufreq*|*devfreq*) ;; *) continue;; esac; " +
                        " S=; M=; IFS= read -r S < \"$C/cur_state\" 2>/dev/null; " +
                        " IFS= read -r M < \"$C/max_state\" 2>/dev/null; " +
                        " echo \"$T $S $M\"; " +
                        "done; " +
                        "echo __MALI_BLOCK__; " +
                        "[ -n \"$MALI_PATH\" ] && echo \"$MALI_PATH\" || echo none; " +
                        "echo __END__; ";

        String output = rootCommand(script);
        if (output == null) return null;

        try {
            String app = between(output, "__APP__\n", "__TEMP__").trim();
            String tempRaw = between(output, "__TEMP__\n", "__BAT__").trim();
            int batIndex = output.indexOf("__BAT__");

            Sample sample = new Sample();
            sample.currentMa = -1;
            sample.voltage = -1;
            sample.power = -1;
            sample.temperatureC = -1;
            sample.battery = -1;
            sample.timeMs = System.currentTimeMillis() - startMs;
            sample.app = app.isEmpty() ? "Unknown" : app;
            lastPackage = sample.app;

            FpsDetector.Result r = fpsDetector.detect(rootFpsShell(), sample.app);

            if (r.valid()) {
                sample.fps = r.fps;
                sample.fpsSource = r.source;
                sample.averageFrameTimeMs = r.averageFrameTimeMs;
                sample.frameTimeStdDevMs = r.frameTimeStdDevMs;
                sample.stutterCount = r.stutterCount;
                sample.frameIntervalsMs.addAll(r.frameIntervalsMs);
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
                SysfsBatteryValues batteryValues = parseSysfsBatteryValues(bat);

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

                sample.currentMa = batteryValues.hasCurrent()
                        ? batteryCurrent.toMilliampsFromSysfs(batteryValues.currentMicroamps, unplugged)
                        : -1;
                sample.voltage = batteryValues.hasVoltage()
                        ? batteryValues.voltageMicrovolts / 1000000.0 : -1;
                sample.power = calculatePowerWatts(
                        sample.currentMa, sample.voltage,
                        batteryValues.hasCurrent() && batteryValues.hasVoltage());
                sample.battery = batteryValues.batteryPercent;
            }

            // Independent CPU Usage parsing
            String cpu = between(output, "__CPU__\n", "__CPU_FREQ__");
            if (!cpu.isEmpty()) {
                sample.cpu = parseCpu(cpu);
            }

            // Independent Frequency parsing using explicit markers
            String cpuFreqRaw = between(output, "__CPU_FREQ__\n", "__GPU_FREQ_BLOCK__").trim();
            parseCpuFrequencies(cpuFreqRaw, sample.cpuFreqMHz);

            // GPU Freq Block (FIXED: uses 'output', not 'freqOutput')
            String gpuFreqBlock = between(output, "__GPU_FREQ_BLOCK__\n", "__GPU_LOAD_BLOCK__").trim();
            String[] gpuFreqParts = gpuFreqBlock.split("\\s+");
            sample.gpuFreqMHz = parseGpuFrequencyMHz(gpuFreqBlock);
            if (gpuFreqParts.length >= 2 && !gpuFreqParts[1].equals("none")) {
                cachedGpuFreqPath = validateSysfsPath(gpuFreqParts[1]);
            }

            // GPU Load Block (FIXED: uses 'output', not 'freqOutput')
            String gpuLoadBlock = between(output, "__GPU_LOAD_BLOCK__\n", "__THERMAL_BLOCK__").trim();
            String[] gpuLoadParts = gpuLoadBlock.split("\\s+");
            sample.gpuLoadPercent = parseGpuLoadPercent(gpuLoadBlock);
            if (gpuLoadParts.length >= 2) {
                String path = gpuLoadParts[gpuLoadParts.length - 1];
                if (!path.equals("none")) cachedGpuLoadPath = validateSysfsPath(path);
            }

            parseThermalTemperatures(
                    between(output, "__THERMAL_BLOCK__\n", "__COOLING_BLOCK__"), sample);
            applyCoolingReading(
                    between(output, "__COOLING_BLOCK__\n", "__MALI_BLOCK__"), sample);

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
                String freqOutput = service.readMetrics(
                        cachedGpuFreqPath, cachedGpuLoadPath, cachedMaliPath,
                        !maliSearchCompleted);


                if (freqOutput != null) {
                    // BatteryManager current reporting is missing on some vendor ROMs.
                    // Fall back to the Battery-type power-supply sysfs nodes via Shizuku.
                    String batteryBlock = between(
                            freqOutput, "__BATTERY_BLOCK__\n", "__PROC_STAT__"
                    ).trim();
                    if (!batteryBlock.isEmpty()) {
                        SysfsBatteryValues batteryValues =
                                parseSysfsBatteryValues(batteryBlock);
                        if (batteryValues.hasCurrent()
                                && (batteryValues.currentMicroamps != 0 || sample.currentMa < 0)) {
                            sample.currentMa = batteryCurrent.toMilliampsFromSysfs(
                                    batteryValues.currentMicroamps, sample.batteryUnplugged);
                        }
                        if (batteryValues.hasVoltage()) {
                            sample.voltage = batteryValues.voltageMicrovolts / 1000000.0;
                        }
                        if (batteryValues.batteryPercent >= 0) {
                            sample.battery = batteryValues.batteryPercent;
                        }
                        sample.power = calculatePowerWatts(
                                sample.currentMa, sample.voltage,
                                sample.currentMa != -1 && sample.voltage > 0);
                    }

                    String cpuOutput = between(
                            freqOutput, "__PROC_STAT__\n", "__CPU_FREQ__"
                    ).trim();

                    if (!cpuOutput.isEmpty()) {
                        sample.cpu = parseCpu(cpuOutput);
                    }
                    // Parse CPU Freq
                    String cpuFreqRaw = between(freqOutput, "__CPU_FREQ__\n", "__GPU_FREQ_BLOCK__").trim();
                    parseCpuFrequencies(cpuFreqRaw, sample.cpuFreqMHz);

                    // Parse GPU Freq
                    String gpuFreqBlock = between(freqOutput, "__GPU_FREQ_BLOCK__\n", "__GPU_LOAD_BLOCK__").trim();
                    String[] gpuFreqParts = gpuFreqBlock.split("\\s+");
                    sample.gpuFreqMHz = parseGpuFrequencyMHz(gpuFreqBlock);
                    if (gpuFreqParts.length >= 2 && !gpuFreqParts[1].equals("none")) {
                        cachedGpuFreqPath = validateSysfsPath(gpuFreqParts[1]);
                    }

                    // Parse GPU Load
                    String gpuLoadBlock = between(freqOutput, "__GPU_LOAD_BLOCK__\n", "__THERMAL_BLOCK__").trim();
                    String[] gpuLoadParts = gpuLoadBlock.split("\\s+");
                    sample.gpuLoadPercent = parseGpuLoadPercent(gpuLoadBlock);
                    if (gpuLoadParts.length >= 2) {
                        String path = gpuLoadParts[gpuLoadParts.length - 1];
                        if (!path.equals("none")) cachedGpuLoadPath = validateSysfsPath(path);
                    }

                    parseThermalTemperatures(
                            between(freqOutput, "__THERMAL_BLOCK__\n", "__COOLING_BLOCK__"), sample);
                    applyCoolingReading(
                            between(freqOutput, "__COOLING_BLOCK__\n", "__MALI_BLOCK__"), sample);

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
                String foregroundOutput = service.getResumedActivityDump();
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
                    String windowOutput = service.getWindowDump();
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
            FpsDetector.Result r = fpsDetector.detect(shizukuFpsShell(service), foregroundPackage);

            if (r.valid()) {
                sample.fps = r.fps;
                sample.fpsSource = r.source;
                sample.averageFrameTimeMs = r.averageFrameTimeMs;
                sample.frameTimeStdDevMs = r.frameTimeStdDevMs;
                sample.stutterCount = r.stutterCount;
                sample.frameIntervalsMs.addAll(r.frameIntervalsMs);

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
            sample.batteryUnplugged = batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_PLUGGED, -1) == 0;
            boolean unplugged = sample.batteryUnplugged;
            boolean currentValid = current != Long.MIN_VALUE;

            sample.currentMa = currentValid ? batteryCurrent.toMilliamps(current, unplugged) : -1;
            sample.power = calculatePowerWatts(sample.currentMa, sample.voltage, currentValid);

            return sample;

        } catch (Exception e) {
            return null;
        }
    }

private double[] parseCpu(String raw) {
        return cpuUsageTracker.parse(raw);
    }

    static final class SysfsBatteryValues {
        final long currentMicroamps;
        final long voltageMicrovolts;
        final int batteryPercent;

        SysfsBatteryValues(long currentMicroamps, long voltageMicrovolts, int batteryPercent) {
            this.currentMicroamps = currentMicroamps;
            this.voltageMicrovolts = voltageMicrovolts;
            this.batteryPercent = batteryPercent;
        }

        boolean hasCurrent() {
            return currentMicroamps != Long.MIN_VALUE;
        }

        boolean hasVoltage() {
            return voltageMicrovolts > 0;
        }
    }

    /**
     * Parses current, voltage, and capacity independently so one malformed
     * sysfs field cannot discard the other valid battery readings.
     */
    static SysfsBatteryValues parseSysfsBatteryValues(String raw) {
        long current = Long.MIN_VALUE;
        long voltage = -1;
        int percent = -1;
        if (raw == null || raw.trim().isEmpty()) {
            return new SysfsBatteryValues(current, voltage, percent);
        }

        String[] values = raw.trim().split("\\s+");
        if (values.length > 0 && !"-".equals(values[0])) {
            try {
                long parsed = Long.parseLong(values[0]);
                if (parsed != Long.MIN_VALUE) current = parsed;
            } catch (NumberFormatException ignored) {
                // Keep only the current unavailable.
            }
        }
        if (values.length > 1 && !"-".equals(values[1])) {
            try {
                long parsed = Long.parseLong(values[1]);
                if (parsed > 0) voltage = parsed;
            } catch (NumberFormatException ignored) {
                // Keep only the voltage unavailable.
            }
        }
        if (values.length > 2 && !"-".equals(values[2])) {
            try {
                int parsed = Integer.parseInt(values[2]);
                if (parsed >= 0 && parsed <= 100) percent = parsed;
            } catch (NumberFormatException ignored) {
                // Keep only the battery percentage unavailable.
            }
        }
        return new SysfsBatteryValues(current, voltage, percent);
    }

    static double calculatePowerWatts(
            double currentMa, double voltageV, boolean currentValid) {
        if (!currentValid || !Double.isFinite(currentMa)
                || !Double.isFinite(voltageV) || voltageV <= 0) return -1;
        return currentMa * voltageV / 1000.0;
    }

    static void applyCoolingReading(String raw, Sample sample) {
        if (sample == null) return;
        ThermalCoolingReader.Reading reading = ThermalCoolingReader.read(raw);
        sample.coolingDeviceActive = reading.overallState();
        sample.cpuCoolingState = reading.cpuState;
        sample.cpuCoolingMaxState = reading.cpuMaxState;
        sample.gpuCoolingState = reading.gpuState;
        sample.gpuCoolingMaxState = reading.gpuMaxState;
    }

    static void parseThermalTemperatures(String raw, Sample sample) {
        if (raw == null || raw.isEmpty() || sample == null) return;
        double maxCpu = -1;
        double maxGpu = -1;
        for (String line : raw.split("\\r?\\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 2) continue;
            try {
                double milliCelsius = Double.parseDouble(parts[1]);
                if (!Double.isFinite(milliCelsius)
                        || milliCelsius <= 0 || milliCelsius > 150000) continue;
                double celsius = milliCelsius / 1000.0;
                if ("CPU".equals(parts[0])) maxCpu = Math.max(maxCpu, celsius);
                else if ("GPU".equals(parts[0])) maxGpu = Math.max(maxGpu, celsius);
            } catch (NumberFormatException ignored) {
                // Ignore unreadable or vendor sentinel values.
            }
        }
        sample.cpuTemperatureC = maxCpu;
        sample.gpuTemperatureC = maxGpu;
    }

    /**
     * Parses per-core scaling_cur_freq values. Linux cpufreq sysfs reports kHz;
     * the app stores MHz. Missing, malformed, non-finite, and non-positive
     * readings remain unavailable (-1), without shifting later core indices.
     */
    static void parseCpuFrequencies(String raw, double[] destination) {
        if (destination == null) return;
        java.util.Arrays.fill(destination, -1);
        if (raw == null || raw.trim().isEmpty()) return;

        String[] values = raw.trim().split("\\s+");
        for (int i = 0; i < destination.length && i < values.length; i++) {
            try {
                double khz = Double.parseDouble(values[i]);
                if (Double.isFinite(khz) && khz > 0) {
                    destination[i] = khz / 1000.0;
                }
            } catch (NumberFormatException ignored) {
                // Keep this core unavailable; preserve later core indices.
            }
        }
    }

    static double parseGpuFrequencyMHz(String raw) {
        if (raw == null || raw.trim().isEmpty()) return -1;
        String[] parts = raw.trim().split("\\s+");
        try {
            double value = Double.parseDouble(parts[0]);
            if (!Double.isFinite(value) || value <= 0) return -1;
            if (value > 100000) return value / 1000000.0;
            if (value > 100) return value / 1000.0;
            return value;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static double parseGpuLoadPercent(String raw) {
        if (raw == null || raw.trim().isEmpty()) return -1;
        String[] parts = raw.trim().split("\\s+");
        try {
            if (parts.length == 3) {
                double busy = Double.parseDouble(parts[0]);
                double total = Double.parseDouble(parts[1]);
                if (!Double.isFinite(busy) || !Double.isFinite(total) || busy < 0) return -1;
                if (total == -1 && busy <= 100) return busy;
                if (total > 0) return Math.max(0, Math.min(100, busy / total * 100.0));
            } else if (parts.length == 2) {
                double load = Double.parseDouble(parts[0]);
                if (Double.isFinite(load) && load >= 0 && load <= 100) return load;
            }
        } catch (NumberFormatException ignored) {
            // Unsupported or malformed driver output.
        }
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

private FpsDetector.Shell rootFpsShell() {
        return new FpsDetector.Shell() {
            @Override public String surfaceFlingerDump() {
                String out = rootCommand("dumpsys SurfaceFlinger 2>/dev/null");
                return out == null ? "" : out;
            }
            @Override public String surfaceFlingerLayers() {
                String out = rootCommand("dumpsys SurfaceFlinger --list 2>/dev/null");
                return out == null ? "" : out;
            }
            @Override public String surfaceFlingerLatency(String layerName) {
                String out = rootCommand("dumpsys SurfaceFlinger --latency " + shellQuote(layerName));
                return out == null ? "" : out;
            }
            @Override public String surfaceFlingerLayerLatencySnapshot(
                    String packageName, boolean blastOnly) {
                String out = rootCommand(
                        FpsDetector.buildSurfaceFlingerLayerLatencyScript(
                                packageName, blastOnly));
                return out == null ? "" : out;
            }
            @Override public String surfaceFlingerTimeStatsDump() {
                String out = rootCommand(
                        "dumpsys SurfaceFlinger --timestats -maxlayers 64 -dump 2>/dev/null");
                return out == null ? "" : out;
            }
            @Override public String surfaceFlingerTimeStatsSnapshot() {
                String out = rootCommand(
                        "echo __ONYX_LAYERS__; " +
                        "dumpsys SurfaceFlinger --list 2>/dev/null; " +
                        "echo __ONYX_TIMESTATS__; " +
                        "dumpsys SurfaceFlinger --timestats -maxlayers 64 -dump 2>/dev/null");
                return out == null ? "" : out;
            }
        };
    }

    private FpsDetector.Shell shizukuFpsShell(IOnyxUserService service) {
        return new FpsDetector.Shell() {
            @Override public String surfaceFlingerDump() throws Exception {
                return service.getSurfaceFlingerDump();
            }
            @Override public String surfaceFlingerLayers() throws Exception {
                return service.getSurfaceFlingerLayers();
            }
            @Override public String surfaceFlingerLatency(String layerName) throws Exception {
                return service.getSurfaceFlingerLatency(layerName);
            }
            @Override public String surfaceFlingerLayerLatencySnapshot(
                    String packageName, boolean blastOnly) throws Exception {
                return service.getSurfaceFlingerLayerLatencySnapshot(
                        packageName, blastOnly);
            }
            @Override public String surfaceFlingerTimeStatsDump() throws Exception {
                return service.getSurfaceFlingerTimeStatsDump();
            }
            @Override public String surfaceFlingerTimeStatsSnapshot() throws Exception {
                return service.getSurfaceFlingerTimeStatsSnapshot();
            }
        };
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
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
