package com.onyx.perfmonitor;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One parsed ONYX session CSV: the readings plus info about where it came from.
 * Used by the analysis screen and (later) the compare screen.
 */
public class SessionData {

    // ---------- Info about the session ----------
    public String fileName = "";
    public String deviceName = "";      // empty = old session, device not recorded
    public String androidVersion = "";
    public String mode = "";            // ROOT / SHIZUKU / ANDROID
    public boolean imported = false;
    public String originalName = "";    // original file name, for imported sessions
    public long fileModifiedMs = 0;

    // ---------- Readings (one entry per sample, -1 = unavailable) ----------
    public final List<Double> times = new ArrayList<>();
    public final List<Double> fps = new ArrayList<>();
    public final List<String> fpsSource = new ArrayList<>();
    public final List<Double> current = new ArrayList<>();
    public final List<Double> voltage = new ArrayList<>();
    public final List<Double> power = new ArrayList<>();
    public final List<Double> battery = new ArrayList<>();
    public final List<Double> secondsPerPercent = new ArrayList<>();
    public final List<Double> temperature = new ArrayList<>();
    public final List<List<Double>> cpu = new ArrayList<>();
    public final List<List<Double>> cpuFreq = new ArrayList<>();
    public final List<Double> gpuFreq = new ArrayList<>();
    public final List<Double> gpuLoad = new ArrayList<>();
    public final List<Double> averageFrameTime = new ArrayList<>();
    public final List<Double> frameTimeStdDev = new ArrayList<>();
    public final List<Double> stutterCount = new ArrayList<>();
    boolean legacyStutterCountColumn = false;
    public final List<Double> frameIntervalsMs = new ArrayList<>();
    /** Refresh period (ms) captured with each frame interval; -1 when unavailable. */
    public final List<Double> frameIntervalRefreshPeriodsMs = new ArrayList<>();
    public final List<Double> ramUsage = new ArrayList<>();
    public final List<Double> cpuTemperature = new ArrayList<>();
    public final List<Double> gpuTemperature = new ArrayList<>();
    public final List<Double> thermalStatus = new ArrayList<>();
    public final List<Double> coolingDeviceActive = new ArrayList<>();

    private boolean foundReadingsMarker = false;

    /** True when the file carries a millisecond-precision time column. */
    boolean hasTimeMsColumn = false;

    private SessionData() {
        for (int i = 0; i < 8; i++) {
            cpu.add(new ArrayList<>());
            cpuFreq.add(new ArrayList<>());
        }
    }

    // ---------- Public API ----------

    /** Full load without knowing if it was imported. Returns null if unreadable. */
    public static SessionData load(File file) {
        return load(null, file);
    }

    /** Full load including the "imported" info. Returns null if unreadable. */
    public static SessionData load(Context context, File file) {
        SessionData data = parse(context, file, false);
        if (data == null || !data.foundReadingsMarker || data.times.isEmpty()) {
            return null;
        }
        return data;
    }

    /** Fast load of only the info (device, imported, date). No readings. */
    public static SessionData readMeta(Context context, File file) {
        return parse(context, file, true);
    }

    /** "Honor 400 Pro" or "Unknown device". */
    public String deviceLabel() {
        return deviceName.isEmpty() ? "Unknown device" : deviceName;
    }

    /** Date of the session, e.g. "Nov 22, 2026 03:15 PM". */
    public String dateLabel() {
        SimpleDateFormat out =
                new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        // Prefer the date in the original file name, then the file's own name.
        Date fromName = dateFromName(originalName);
        if (fromName == null) fromName = dateFromName(fileName);
        if (fromName != null) return out.format(fromName);

        return out.format(new Date(fileModifiedMs));
    }

    /** The session's date: from the original or own file name, else the file's modified time. */
    public Date sessionDate() {
        Date d = dateFromName(originalName);
        if (d == null) d = dateFromName(fileName);
        return d != null ? d : new Date(fileModifiedMs);
    }

    // ---------- Parsing ----------

    private static SessionData parse(Context context, File file, boolean metaOnly) {
        if (file == null || !file.exists()) return null;

        SessionData data = new SessionData();
        data.fileName = file.getName();
        data.fileModifiedMs = file.lastModified();

        if (context != null) {
            SharedPreferences meta =
                    context.getSharedPreferences("session_meta", Context.MODE_PRIVATE);
            String original = meta.getString(file.getName() + ".original", null);
            if (original != null) {
                data.imported = true;
                data.originalName = original;
            }
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file),
                        StandardCharsets.UTF_8))) {

            String line;
            boolean inReadings = false;
            boolean skipHeader = false;
            boolean hasSecondsPerPercentColumn = true;
            int frameRefreshPeriodColumn = -1;
            int timeMsColumn = -1;

            while ((line = reader.readLine()) != null) {

                // Everything before "PERFORMANCE READINGS" is session info.
                if (!inReadings) {
                    if (line.startsWith("Device,")) {
                        data.deviceName = secondColumn(line);
                    } else if (line.startsWith("Android,")) {
                        data.androidVersion = secondColumn(line);
                    } else if (line.startsWith("Mode,")) {
                        data.mode = secondColumn(line);
                    } else if (line.equals("PERFORMANCE READINGS")) {
                        inReadings = true;
                        skipHeader = true;
                        data.foundReadingsMarker = true;
                        if (metaOnly) break;
                    }
                    continue;
                }

                // The first line after the marker is the column header row.
                if (skipHeader) {
                    hasSecondsPerPercentColumn =
                            line.contains("Seconds per 1% drain");
                    String normalizedHeader = line.toLowerCase(Locale.US);
                    data.legacyStutterCountColumn = normalizedHeader.contains("stutter count")
                            || normalizedHeader.contains("stuttercount");

                    List<String> headerColumns = parseCsvLine(line);
                    for (int i = 0; i < headerColumns.size(); i++) {
                        String name = headerColumns.get(i).trim();
                        if ("Frame Refresh Period (ms)".equals(name)) {
                            frameRefreshPeriodColumn = i;
                        } else if ("Time (ms)".equalsIgnoreCase(name)) {
                            timeMsColumn = i;
                        }
                    }

                    // Older files have no millisecond column, so every later
                    // index shifts by one when it is present.
                    data.hasTimeMsColumn = timeMsColumn >= 0;
                    skipHeader = false;
                    continue;
                }

                data.addRow(parseCsvLine(line), hasSecondsPerPercentColumn,
                        frameRefreshPeriodColumn, timeMsColumn);
            }

            return data;

        } catch (Exception e) {
            return null;
        }
    }

    private void addRow(List<String> columns, boolean hasSecondsPerPercentColumn,
                        int frameRefreshPeriodColumn, int timeMsColumn) {
        if (columns.size() < 8) return;

        // Files without the drain column are one shorter from that point on;
        // files with the millisecond column are one longer from the start.
        int offset = hasSecondsPerPercentColumn ? 0 : -1;
        int timeOffset = timeMsColumn >= 0 ? 1 : 0;
        offset += timeOffset;

        // Prefer the millisecond value; MM:SS is whole seconds only and several
        // samples can share the same second.
        double timestamp = timeMsColumn >= 0
                ? number(columns, timeMsColumn) / 1000.0
                : -1;
        if (timestamp < 0) timestamp = parseTime(columns.get(0));
        times.add(timestamp);

        fps.add(number(columns, 2 + timeOffset));
        fpsSource.add(columns.size() > 30 + offset ? columns.get(30 + offset) : "");
        current.add(number(columns, 3 + timeOffset));
        voltage.add(number(columns, 4 + timeOffset));
        power.add(number(columns, 5 + timeOffset));
        battery.add(number(columns, 6 + timeOffset));
        secondsPerPercent.add(hasSecondsPerPercentColumn
                ? number(columns, 7 + timeOffset) : -1);
        temperature.add(number(columns, 8 + offset));

        for (int i = 0; i < 8; i++) {
            cpu.get(i).add(number(columns, 9 + offset + i));
        }
        for (int i = 0; i < 8; i++) {
            cpuFreq.get(i).add(number(columns, 17 + offset + i));
        }

        gpuFreq.add(number(columns, 25 + offset));
        gpuLoad.add(number(columns, 26 + offset));
        averageFrameTime.add(number(columns, 27 + offset));
        frameTimeStdDev.add(number(columns, 28 + offset));
        stutterCount.add(number(columns, 29 + offset));
        if (columns.size() > 31 + offset) {
            double refreshPeriod = frameRefreshPeriodColumn >= 0
                    ? number(columns, frameRefreshPeriodColumn) : -1;
            addFrameIntervals(columns.get(31 + offset), refreshPeriod);
        }
        ramUsage.add(number(columns, 32 + offset));
        cpuTemperature.add(number(columns, 33 + offset));
        gpuTemperature.add(number(columns, 34 + offset));
        thermalStatus.add(number(columns, 35 + offset));
        coolingDeviceActive.add(number(columns, 36 + offset));
    }

    private void addFrameIntervals(String encoded, double refreshPeriodMs) {
        if (encoded == null || encoded.isEmpty()) return;

        for (String part : encoded.split(";")) {
            try {
                double interval = Double.parseDouble(part.trim());
                if (Double.isFinite(interval) && interval > 0) {
                    frameIntervalsMs.add(interval);
                    frameIntervalRefreshPeriodsMs.add(refreshPeriodMs);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed interval values in imported or edited CSVs.
            }
        }
    }

    private static String secondColumn(String line) {
        List<String> columns = parseCsvLine(line);
        return columns.size() > 1 ? columns.get(1).trim() : "";
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (quoted && i + 1 < line.length()
                        && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                result.add(value.toString());
                value.setLength(0);
            } else {
                value.append(c);
            }
        }

        result.add(value.toString());
        return result;
    }

    private static double number(List<String> columns, int index) {
        if (index < 0 || index >= columns.size()) return -1;

        try {
            String value = columns.get(index).trim();
            if (value.isEmpty()) return -1;

            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private double parseTime(String value) {
        try {
            String[] parts = value.split(":");
            double seconds;

            if (parts.length == 3) {
                seconds = Double.parseDouble(parts[0]) * 3600
                        + Double.parseDouble(parts[1]) * 60
                        + Double.parseDouble(parts[2]);
            } else if (parts.length == 2) {
                seconds = Double.parseDouble(parts[0]) * 60
                        + Double.parseDouble(parts[1]);
            } else {
                seconds = Double.parseDouble(value);
            }

            if (Double.isFinite(seconds)) return seconds;
        } catch (Exception ignored) {
            // Fall through to a safe sequential timestamp.
        }

        return times.isEmpty() ? 0 : times.get(times.size() - 1) + 1;
    }

    private static final Pattern STAMP =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})");

    private static Date dateFromName(String name) {
        if (name == null || name.isEmpty()) return null;

        Matcher m = STAMP.matcher(name);
        if (!m.find()) return null;

        try {
            return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                    .parse(m.group(1));
        } catch (Exception e) {
            return null;
        }
    }
}