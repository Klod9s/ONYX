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

    private boolean foundReadingsMarker = false;

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
                    skipHeader = false;
                    continue;
                }

                data.addRow(parseCsvLine(line));
            }

            return data;

        } catch (Exception e) {
            return null;
        }
    }

    private void addRow(List<String> columns) {
        if (columns.size() < 9) return;

        times.add(parseTime(columns.get(0)));
        fps.add(number(columns, 2));
        current.add(number(columns, 3));
        voltage.add(number(columns, 4));
        power.add(number(columns, 5));
        battery.add(number(columns, 6));
        secondsPerPercent.add(number(columns, 7));
        temperature.add(number(columns, 8));

        for (int i = 0; i < 8; i++) {
            cpu.get(i).add(number(columns, 9 + i));
        }
        for (int i = 0; i < 8; i++) {
            cpuFreq.get(i).add(number(columns, 17 + i));
        }

        gpuFreq.add(number(columns, 25));
        gpuLoad.add(number(columns, 26));
        averageFrameTime.add(number(columns, 27));
        frameTimeStdDev.add(number(columns, 28));
        stutterCount.add(number(columns, 29));
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
        if (index >= columns.size()) return -1;

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