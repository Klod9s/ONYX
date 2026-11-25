package com.onyx.perfmonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the whole analysis layout (info card, frame pacing, charts, battery drain)
 * for one SessionData. Used by the single analysis screen and the compare screen.
 *
 * Usage:
 *   LinearLayout content = new AnalysisContentBuilder(context, data).build();
 *   // compare pane: new AnalysisContentBuilder(context, data)
 *   //                   .showHeader(false).chartHeightDp(160).build();
 */
public class AnalysisContentBuilder {

    private final Context context;
    private final SessionData d;

    private boolean showHeader = true;
    private int chartHeightDp = 180;

    public AnalysisContentBuilder(Context context, SessionData data) {
        this.context = context;
        this.d = data;
    }

    /** false = hide the big ONYX title (used inside the compare panes). */
    public AnalysisContentBuilder showHeader(boolean show) {
        this.showHeader = show;
        return this;
    }

    public AnalysisContentBuilder chartHeightDp(int heightDp) {
        this.chartHeightDp = heightDp;
        return this;
    }

    // ---------- Helpers ----------

    private int dp(float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private int accentOverride = 0;

    /** Optional: use this color instead of the app accent (the compare screen uses it per pane). */
    public AnalysisContentBuilder accentColor(int color) {
        this.accentOverride = color;
        return this;
    }

    private int getAppAccentColor() {
        return accentOverride != 0 ? accentOverride : AppColors.accent(context);
    }

    /** Accent-bar heading, same look as the section headings on Monitor. */
    private TextView sectionHeading(String value) {
        int accent = getAppAccentColor();
        TextView heading = new TextView(context);
        heading.setText(value);
        heading.setTextSize(13);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(accent);
        heading.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_section_mark, 0, 0, 0);
        heading.setCompoundDrawablePadding(dp(8));
        heading.setCompoundDrawableTintList(
                android.content.res.ColorStateList.valueOf(accent));
        return heading;
    }

    private android.graphics.drawable.GradientDrawable cardBackground() {
        int a = getAppAccentColor();
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{Color.rgb(22, 32, 48), Color.rgb(15, 22, 34)});
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.argb(110, Color.red(a), Color.green(a), Color.blue(a)));
        return bg;
    }

    // ---------- Main build ----------

    public LinearLayout build() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(showHeader ? 40 : 12), dp(18), dp(28));
        layout.setBackgroundColor(Color.rgb(13, 18, 26));

        int green = getAppAccentColor();
        int white = Color.WHITE;
        int muted = Color.rgb(145, 160, 180);

        // HEADER
        if (showHeader) {
            TextView brand = new TextView(context);
            brand.setText("ONYX");
            brand.setTextSize(36);
            brand.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-black",
                    android.graphics.Typeface.NORMAL));
            brand.setLetterSpacing(0.18f);
            brand.setTextColor(Color.WHITE);
            brand.setShadowLayer(14f, 0f, 0f, Color.rgb(55, 190, 135));
            layout.addView(brand);

            TextView heading = new TextView(context);
            heading.setText("PERFORMANCE MONITOR");
            heading.setTextSize(12);
            heading.setTypeface(null, android.graphics.Typeface.BOLD);
            heading.setLetterSpacing(0.22f);
            heading.setTextColor(getAppAccentColor());
            heading.setPadding(0, dp(2), 0, dp(16));
            layout.addView(heading);
        }

        // SESSION INFORMATION CARD
        LinearLayout infoCard = new LinearLayout(context);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        infoCard.setBackground(cardBackground());
        infoCard.setElevation(dp(4));

        java.util.Date when = d.sessionDate();
        String datePart = new java.text.SimpleDateFormat(
                "MMM dd, yyyy", Locale.getDefault()).format(when);
        String timePart = new java.text.SimpleDateFormat(
                "hh:mm a", Locale.getDefault()).format(when);

        android.text.SpannableStringBuilder titleText =
                new android.text.SpannableStringBuilder(datePart + "  ·  " + timePart);
        titleText.setSpan(
                new android.text.style.ForegroundColorSpan(getAppAccentColor()),
                datePart.length() + 5, titleText.length(), 0);

        TextView sessionName = new TextView(context);
        sessionName.setText(titleText);
        sessionName.setTextSize(15);
        sessionName.setTypeface(null, android.graphics.Typeface.BOLD);
        sessionName.setTextColor(white);
        infoCard.addView(sessionName);

        TextView deviceLine = new TextView(context);
        deviceLine.setText(d.deviceLabel()
                + (d.mode.isEmpty() ? "" : "  ·  " + d.mode));
        deviceLine.setTextSize(12);
        deviceLine.setTextColor(getAppAccentColor());
        deviceLine.setPadding(0, dp(5), 0, 0);
        infoCard.addView(deviceLine);

        if (d.imported) {
            TextView importedLine = new TextView(context);
            importedLine.setText("IMPORTED  ·  " + d.originalName);
            importedLine.setTextSize(11);
            importedLine.setTypeface(null, android.graphics.Typeface.BOLD);
            importedLine.setTextColor(Color.rgb(255, 190, 80));
            importedLine.setPadding(0, dp(4), 0, 0);
            infoCard.addView(importedLine);
        }

        TextView readings = new TextView(context);
        readings.setText(d.times.size() + " readings collected");
        readings.setTextSize(12);
        readings.setTextColor(muted);
        readings.setPadding(0, dp(5), 0, 0);
        infoCard.addView(readings);

        TextView fileLine = new TextView(context);
        fileLine.setText(d.fileName);
        fileLine.setTextSize(10);
        fileLine.setTextColor(Color.rgb(125, 136, 150));
        fileLine.setPadding(0, dp(2), 0, 0);
        infoCard.addView(fileLine);

        layout.addView(infoCard);

        // Show only metric groups that contain saved readings. This keeps disabled
        // collection categories out of both Analysis and Comparison.
        boolean hasFps = hasAny(d.fps);
        FramePacingSummaryCalculator.Result capturedFrameSummary =
                FramePacingSummaryCalculator.calculate(d.frameIntervalsMs);
        boolean hasAverageFrameTime = capturedFrameSummary.valid()
                || hasAny(d.averageFrameTime);
        boolean hasFrameTimeVariation = capturedFrameSummary.valid()
                || hasAny(d.frameTimeStdDev);
        boolean hasStutterData = capturedFrameSummary.valid()
                || hasAny(d.stutterCount);
        boolean hasFpsStability = hasFps;
        boolean hasOnePercentLow = FramePercentileCalculator.averageLowFps(
                d.frameIntervalsMs, 0.01) >= 0;
        boolean hasPointOnePercentLow = FramePercentileCalculator.averageLowFps(
                d.frameIntervalsMs, 0.001) >= 0;

        // The summary contains six metrics. Hide the entire card when none
        // of those metrics has any collected data.
        boolean hasFramePacingSummary = hasAverageFrameTime
                || hasFrameTimeVariation
                || hasStutterData
                || hasFpsStability
                || hasOnePercentLow
                || hasPointOnePercentLow;
        if (hasFramePacingSummary) addFramePacingSummary(layout);

        if (hasFps) addChart(layout, "FPS OVER TIME", d.fps, "FPS");
        if (hasAny(d.ramUsage)) addChart(layout, "RAM USAGE", d.ramUsage, "%");
        if (hasAny(d.cpuTemperature)) {
            addChart(layout, "CPU TEMPERATURE", d.cpuTemperature, "°C");
        }
        if (hasAny(d.gpuTemperature)) {
            addChart(layout, "GPU TEMPERATURE", d.gpuTemperature, "°C");
        }

        // CPU chart data
        List<List<Double>> cpuSeries = new ArrayList<>();
        List<String> cpuNames = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            cpuSeries.add(d.cpu.get(i));
            cpuNames.add("CPU" + i);
        }

        List<List<Double>> cpuFreqSeries = new ArrayList<>();
        List<String> cpuFreqNames = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            cpuFreqSeries.add(d.cpuFreq.get(i));
            cpuFreqNames.add("CPU" + i);
        }

        if (hasAnySeries(cpuSeries)) {
            addMultiChart(layout, "CPU CORE LOAD", cpuSeries, cpuNames, "%");
        }
        if (hasAnySeries(cpuFreqSeries)) {
            addMultiChart(layout, "CPU CORE FREQUENCY", cpuFreqSeries, cpuFreqNames, "MHz");
        }

        // GPU charts
        List<List<Double>> gpuLoadSeries = new ArrayList<>();
        gpuLoadSeries.add(d.gpuLoad);
        List<String> gpuLoadNames = new ArrayList<>();
        gpuLoadNames.add("GPU");
        if (hasAny(d.gpuLoad)) {
            addMultiChart(layout, "GPU LOAD", gpuLoadSeries, gpuLoadNames, "%");
        }

        List<List<Double>> gpuSeries = new ArrayList<>();
        gpuSeries.add(d.gpuFreq);
        List<String> gpuNames = new ArrayList<>();
        gpuNames.add("GPU");
        if (hasAny(d.gpuFreq)) {
            addMultiChart(layout, "GPU FREQUENCY", gpuSeries, gpuNames, "MHz");
        }

        // BATTERY charts and drain summary
        if (hasAny(d.temperature)) addChart(layout, "BATTERY TEMPERATURE", d.temperature, "°C");
        if (hasAny(d.battery)) addChart(layout, "BATTERY LEVEL", d.battery, "%");
        if (hasAnySigned(d.current)) addChart(layout, "BATTERY CURRENT", d.current, "mA");
        if (hasAny(d.voltage)) addChart(layout, "BATTERY VOLTAGE", d.voltage, "V");
        if (hasAny(d.power)) addChart(layout, "BATTERY POWER", d.power, "W");
        // Seconds per 1% Drain chart removed from analysis and comparison.
        if (hasAny(d.battery)) addBatteryDrain(layout, green, white, muted);

        return layout;
    }

    // ---------- Battery drain ----------

    private void addBatteryDrain(LinearLayout layout, int green, int white, int muted) {
        List<Double> battery = d.battery;
        List<Double> times = d.times;

        TextView drainTitle = sectionHeading("BATTERY DRAIN INTERVALS");
        drainTitle.setPadding(0, dp(18), 0, dp(10));
        layout.addView(drainTitle);

        LinearLayout drainCard = new LinearLayout(context);
        drainCard.setOrientation(LinearLayout.VERTICAL);
        drainCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        drainCard.setBackground(cardBackground());
        drainCard.setElevation(dp(4));

        LinearLayout intervalList = new LinearLayout(context);
        intervalList.setOrientation(LinearLayout.VERTICAL);

        int dropNumber = 0;
        int previousBattery = -1;
        double previousTime = 0;

        for (int i = 0; i < battery.size() && i < times.size(); i++) {
            double batteryValue = battery.get(i);
            double currentTime = times.get(i);

            // -1 (and any out-of-range/non-finite value) means the
            // battery reading is unavailable, not an actual percentage.
            if (!Double.isFinite(batteryValue)
                    || batteryValue < 0 || batteryValue > 100
                    || !Double.isFinite(currentTime)) {
                continue;
            }

            int currentBattery = (int) Math.round(batteryValue);

            if (previousBattery >= 0 && currentBattery < previousBattery) {
                dropNumber++;

                long elapsedSeconds = Math.max(0,
                        Math.round(currentTime - previousTime));

                String elapsed = String.format(Locale.US,
                        "%02d:%02d",
                        elapsedSeconds / 60,
                        elapsedSeconds % 60);

                LinearLayout intervalRow = new LinearLayout(context);
                intervalRow.setOrientation(LinearLayout.HORIZONTAL);
                intervalRow.setGravity(Gravity.CENTER_VERTICAL);
                intervalRow.setPadding(0, dp(14), 0, dp(14));

                TextView percentage = new TextView(context);
                percentage.setText(String.format(Locale.US,
                        "%d%% → %d%%", previousBattery, currentBattery));
                percentage.setTextSize(16);
                percentage.setTypeface(null, android.graphics.Typeface.BOLD);
                percentage.setTextColor(white);

                intervalRow.addView(percentage,
                        new LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f));

                LinearLayout timeColumn = new LinearLayout(context);
                timeColumn.setOrientation(LinearLayout.VERTICAL);
                timeColumn.setGravity(Gravity.END);

                TextView timeValue = new TextView(context);
                timeValue.setText(elapsed);
                timeValue.setTextSize(22);
                timeValue.setTypeface(null, android.graphics.Typeface.BOLD);
                timeValue.setTextColor(green);

                TextView timeLabel = new TextView(context);
                timeLabel.setText("Time between drops");
                timeLabel.setTextSize(10);
                timeLabel.setTextColor(muted);

                timeColumn.addView(timeValue);
                timeColumn.addView(timeLabel);

                intervalRow.addView(timeColumn);
                intervalList.addView(intervalRow);

                View separator = new View(context);
                separator.setBackgroundColor(Color.rgb(52, 65, 81));
                intervalList.addView(separator,
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                dp(1)));
            }

            if (previousBattery < 0 || currentBattery < previousBattery) {
                previousTime = currentTime;
            }

            previousBattery = currentBattery;
        }

        if (dropNumber == 0) {
            TextView empty = new TextView(context);
            empty.setText("No battery percentage drops recorded.");
            empty.setTextSize(14);
            empty.setTextColor(muted);
            empty.setPadding(0, dp(12), 0, dp(12));
            intervalList.addView(empty);
        }

        drainCard.addView(intervalList);

        View totalSeparator = new View(context);
        totalSeparator.setBackgroundColor(Color.rgb(52, 65, 81));
        drainCard.addView(totalSeparator,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout totalRow = new LinearLayout(context);
        totalRow.setOrientation(LinearLayout.HORIZONTAL);
        totalRow.setGravity(Gravity.CENTER_VERTICAL);
        totalRow.setPadding(0, dp(14), 0, 0);

        TextView totalLabel = new TextView(context);
        totalLabel.setText("TOTAL DROPS");
        totalLabel.setTextSize(12);
        totalLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        totalLabel.setTextColor(muted);

        totalRow.addView(totalLabel,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f));

        TextView totalValue = new TextView(context);
        totalValue.setText(String.valueOf(dropNumber));
        totalValue.setTextSize(22);
        totalValue.setTypeface(null, android.graphics.Typeface.BOLD);
        totalValue.setTextColor(white);

        totalRow.addView(totalValue);
        drainCard.addView(totalRow);

        layout.addView(drainCard);
    }

    private boolean hasAny(List<Double> values) {
        if (values == null) return false;
        for (Double value : values) {
            if (value != null && Double.isFinite(value) && value >= 0) return true;
        }
        return false;
    }

    /** Battery current is signed: negative values represent discharge. */
    private boolean hasAnySigned(List<Double> values) {
        if (values == null) return false;
        for (Double value : values) {
            // -1 is the existing unavailable-value sentinel.
            if (value != null && Double.isFinite(value) && value != -1) return true;
        }
        return false;
    }

    private boolean hasAnySeries(List<List<Double>> series) {
        if (series == null) return false;
        for (List<Double> values : series) {
            if (hasAny(values)) return true;
        }
        return false;
    }

    // ---------- Single chart ----------

    private void addChart(LinearLayout layout, String title,
                          List<Double> values, String unit) {
        List<List<Double>> series = new ArrayList<>();
        series.add(values);

        List<String> names = new ArrayList<>();
        names.add(unit);

        addMultiChart(layout, title, series, names, unit);
    }

    // ---------- Frame pacing ----------

    private void addFramePacingSummary(LinearLayout layout) {

        int white = Color.WHITE;
        int muted = Color.rgb(145, 160, 180);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(cardBackground());
        card.setElevation(dp(4));

        TextView heading = sectionHeading("FRAME PACING SUMMARY");
        heading.setPadding(0, 0, 0, dp(12));
        card.addView(heading);

        // Prefer session-wide calculations from every unique captured frame interval.
        FramePacingSummaryCalculator.Result frameSummary =
                FramePacingSummaryCalculator.calculate(d.frameIntervalsMs);

        double averageFrameTime = frameSummary.averageFrameTimeMs;
        double frameTimeVariation = frameSummary.standardDeviationMs;
        double stutters = frameSummary.stutterCount;
        String stutterUnit = "total";

        // Older/imported sessions may contain sampled pacing metrics but no
        // individual frame intervals. Preserve their previous summary behavior.
        if (!frameSummary.valid()) {
            double frameTimeTotal = 0;
            double stdDevTotal = 0;
            double stutterTotal = 0;
            int frameTimeCount = 0;
            int stdDevCount = 0;
            int stutterCountValid = 0;

            for (double value : d.averageFrameTime) {
                if (Double.isFinite(value) && value >= 0) {
                    frameTimeTotal += value;
                    frameTimeCount++;
                }
            }

            for (double value : d.frameTimeStdDev) {
                if (Double.isFinite(value) && value >= 0) {
                    stdDevTotal += value;
                    stdDevCount++;
                }
            }

            for (double value : d.stutterCount) {
                if (Double.isFinite(value) && value >= 0) {
                    stutterTotal += value;
                    stutterCountValid++;
                }
            }

            averageFrameTime = frameTimeCount > 0
                    ? frameTimeTotal / frameTimeCount : -1;
            frameTimeVariation = stdDevCount > 0
                    ? stdDevTotal / stdDevCount : -1;
            stutters = stutterCountValid > 0
                    ? stutterTotal / stutterCountValid : -1;
            stutterUnit = "avg/1s";
        }

        // FPS STABILITY: percentage within 5% of median FPS
        List<Double> validFps = new ArrayList<>();

        for (double value : d.fps) {
            if (Double.isFinite(value) && value > 0) {
                validFps.add(value);
            }
        }

        double stability = -1;

        if (!validFps.isEmpty()) {
            List<Double> sortedFps = new ArrayList<>(validFps);
            java.util.Collections.sort(sortedFps);

            int middle = sortedFps.size() / 2;
            double median;

            if (sortedFps.size() % 2 == 0) {
                median = (sortedFps.get(middle - 1)
                        + sortedFps.get(middle)) / 2.0;
            } else {
                median = sortedFps.get(middle);
            }

            if (median > 0) {
                int stableCount = 0;

                for (double value : validFps) {
                    if (Math.abs(value - median) <= median * 0.05) {
                        stableCount++;
                    }
                }

                stability = stableCount * 100.0 / validFps.size();
            }
        }

        // Frame-level lows are calculated from unique SurfaceFlinger
        // presentation intervals, not the 500 ms sampled FPS values.
        double onePercentLow = FramePercentileCalculator.averageLowFps(
                d.frameIntervalsMs, 0.01);
        double pointOnePercentLow = FramePercentileCalculator.averageLowFps(
                d.frameIntervalsMs, 0.001);

        String[] labels = {
                "AVG FRAME TIME",
                "FRAME-TIME VARIATION",
                "STUTTERS",
                "FPS STABILITY",
                "1% FRAME LOW",
                "0.1% FRAME LOW"
        };

        String[] values = {
                averageFrameTime >= 0
                        ? String.format(Locale.US, "%.2f", averageFrameTime) : "--",

                frameTimeVariation >= 0
                        ? String.format(Locale.US, "%.2f", frameTimeVariation) : "--",

                stutters >= 0
                        ? String.format(Locale.US, "%.1f", stutters) : "--",

                stability >= 0
                        ? String.format(Locale.US, "%.1f%%", stability) : "--",

                onePercentLow >= 0
                        ? String.format(Locale.US, "%.1f", onePercentLow) : "--",

                pointOnePercentLow >= 0
                        ? String.format(Locale.US, "%.1f", pointOnePercentLow) : "--"
        };

        String[] units = {"ms", "ms", stutterUnit, "", "FPS", "FPS"};

        LinearLayout metricsContainer = new LinearLayout(context);
        metricsContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout firstRow = new LinearLayout(context);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout secondRow = new LinearLayout(context);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.setGravity(Gravity.CENTER_VERTICAL);

        metricsContainer.addView(firstRow);
        metricsContainer.addView(secondRow);

        for (int i = 0; i < labels.length; i++) {

            LinearLayout metric = new LinearLayout(context);
            metric.setOrientation(LinearLayout.VERTICAL);
            metric.setGravity(Gravity.CENTER);

            TextView label = new TextView(context);
            label.setText(labels[i]);
            label.setTextSize(10);
            label.setTextColor(muted);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setIncludeFontPadding(false);

            metric.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

            TextView value = new TextView(context);
            value.setText(values[i]);
            value.setTextSize(20);
            value.setTypeface(null, android.graphics.Typeface.BOLD);
            value.setTextColor(white);
            value.setGravity(Gravity.CENTER);
            value.setSingleLine(true);
            value.setIncludeFontPadding(false);

            metric.addView(value, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(32)));

            TextView unit = new TextView(context);
            unit.setText(units[i]);
            unit.setTextSize(12);
            unit.setTextColor(muted);
            unit.setGravity(Gravity.CENTER);
            unit.setIncludeFontPadding(false);

            metric.addView(unit, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(18)));

            LinearLayout currentRow = i < 3 ? firstRow : secondRow;

            currentRow.addView(metric, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (i % 3 != 2) {
                View divider = new View(context);
                divider.setBackgroundColor(Color.rgb(52, 65, 81));

                currentRow.addView(divider,
                        new LinearLayout.LayoutParams(dp(1), dp(82)));
            }
        }

        View horizontalDivider = new View(context);
        horizontalDivider.setBackgroundColor(Color.rgb(52, 65, 81));

        LinearLayout.LayoutParams horizontalParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        horizontalParams.topMargin = dp(8);
        horizontalParams.bottomMargin = dp(8);

        metricsContainer.addView(horizontalDivider, 1, horizontalParams);

        card.addView(metricsContainer);

        if (d.frameIntervalsMs.isEmpty()) {
            TextView unavailable = new TextView(context);
            unavailable.setText(
                    "Frame-level lows unavailable: this session has no individual frame timestamps.");
            unavailable.setTextSize(11);
            unavailable.setTextColor(muted);
            unavailable.setPadding(0, dp(10), 0, 0);
            card.addView(unavailable);
        } else {
            TextView frameCount = new TextView(context);
            frameCount.setText(d.frameIntervalsMs.size()
                    + " individual frame intervals analyzed");
            frameCount.setTextSize(11);
            frameCount.setTextColor(muted);
            frameCount.setPadding(0, dp(10), 0, 0);
            card.addView(frameCount);
        }

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);

        layout.addView(card, params);
    }

    // ---------- Chart card (single and multi-series) ----------

    private void addMultiChart(LinearLayout layout, String title,
                               List<List<Double>> series,
                               List<String> names, String unit) {

        List<Double> times = d.times;

        int headingColor = getAppAccentColor();
        int green = getAppAccentColor();
        int muted = Color.rgb(145, 160, 180);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(10), dp(11), dp(9));
        card.setBackground(cardBackground());
        card.setElevation(dp(4));

        TextView heading = sectionHeading(title);
        heading.setPadding(0, 0, 0, dp(6));
        card.addView(heading);

        // GRAPH
        boolean showThermalPeriods = "FPS OVER TIME".equals(title);
        ChartView chart = new ChartView(context, series, times, getAppAccentColor(),
                "CPU CORE LOAD".equals(title),
                showThermalPeriods ? d.thermalStatus : null);

        android.graphics.drawable.GradientDrawable chartBg =
                new android.graphics.drawable.GradientDrawable();
        chartBg.setColor(Color.rgb(13, 18, 26));
        chartBg.setStroke(dp(1), Color.rgb(55, 70, 88));
        chartBg.setCornerRadius(dp(9));
        chart.setBackground(chartBg);

        LinearLayout.LayoutParams chartParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(chartHeightDp));
        chartParams.bottomMargin = dp(8);
        card.addView(chart, chartParams);

        if (showThermalPeriods) {
            LinearLayout thermalLegend = new LinearLayout(context);
            thermalLegend.setOrientation(LinearLayout.HORIZONTAL);
            thermalLegend.setGravity(Gravity.CENTER_VERTICAL);
            thermalLegend.setPadding(dp(3), 0, 0, dp(5));

            TextView statusLabel = new TextView(context);
            statusLabel.setText("THERMAL STATUS  ·");
            statusLabel.setTextSize(10);
            statusLabel.setTextColor(muted);
            statusLabel.setSingleLine(true);
            thermalLegend.addView(statusLabel,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView lightLabel = new TextView(context);
            lightLabel.setText("🟠 LIGHT");
            lightLabel.setTextSize(10);
            lightLabel.setTextColor(muted);
            lightLabel.setGravity(Gravity.CENTER);
            lightLabel.setSingleLine(true);
            thermalLegend.addView(lightLabel,
                    new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView moderateLabel = new TextView(context);
            moderateLabel.setText("🔴 MODERATE+");
            moderateLabel.setTextSize(10);
            moderateLabel.setTextColor(muted);
            moderateLabel.setGravity(Gravity.CENTER);
            moderateLabel.setSingleLine(true);
            thermalLegend.addView(moderateLabel,
                    new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            card.addView(thermalLegend);

            TextView thermalInfo = new TextView(context);
            thermalInfo.setText("FYI: Thermal throttling data may not be available on all devices.");
            thermalInfo.setTextSize(9);
            thermalInfo.setTextColor(muted);
            thermalInfo.setPadding(dp(3), dp(2), 0, dp(4));
            card.addView(thermalInfo);
        }

        // CPU CORE CARDS INSIDE CHART CARD
        if (title.equals("CPU CORE LOAD")
                || title.equals("CPU CORE FREQUENCY")) {

            LinearLayout cpuGrid = new LinearLayout(context);
            cpuGrid.setOrientation(LinearLayout.VERTICAL);
            cpuGrid.setPadding(0, dp(2), 0, 0);

            for (int row = 0; row < 4; row++) {
                LinearLayout cpuRow = new LinearLayout(context);
                cpuRow.setOrientation(LinearLayout.HORIZONTAL);

                for (int col = 0; col < 2; col++) {
                    int core = row * 2 + col;
                    List<Double> values = title.equals("CPU CORE LOAD")
                            ? d.cpu.get(core)
                            : d.cpuFreq.get(core);

                    double total = 0;
                    double min = Double.MAX_VALUE;
                    double max = -Double.MAX_VALUE;
                    int count = 0;

                    for (double value : values) {
                        if (!Double.isFinite(value) || value == -1) continue;

                        total += value;
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                        count++;
                    }

                    LinearLayout coreCard = new LinearLayout(context);
                    coreCard.setOrientation(LinearLayout.VERTICAL);
                    coreCard.setPadding(dp(6), dp(6), dp(6), dp(6));

                    android.graphics.drawable.GradientDrawable coreBg =
                            new android.graphics.drawable.GradientDrawable();
                    coreBg.setColor(Color.rgb(31, 42, 56));
                    coreBg.setCornerRadius(dp(8));
                    coreBg.setStroke(dp(1), Color.rgb(55, 70, 88));

                    coreCard.setBackground(coreBg);
                    coreCard.setElevation(dp(2));

                    TextView coreName = new TextView(context);
                    coreName.setText("CPU " + core);
                    coreName.setTextSize(11);
                    coreName.setTypeface(null, android.graphics.Typeface.BOLD);
                    coreName.setTextColor(ChartView.colorsForCore(core));

                    LinearLayout statsRow = new LinearLayout(context);
                    statsRow.setOrientation(LinearLayout.HORIZONTAL);
                    statsRow.setGravity(Gravity.CENTER_VERTICAL);

                    TextView avgValue = null;
                    android.widget.ProgressBar bar = null;

                    if (title.equals("CPU CORE LOAD")) {

                        coreCard.addView(coreName);

                        statsRow.setPadding(0, dp(4), 0, 0);

                        LinearLayout avgColumn = new LinearLayout(context);
                        avgColumn.setOrientation(LinearLayout.VERTICAL);
                        avgColumn.setLayoutParams(
                                new LinearLayout.LayoutParams(
                                        dp(45),
                                        LinearLayout.LayoutParams.WRAP_CONTENT));

                        TextView avgLabel = new TextView(context);
                        avgLabel.setText("AVG LOAD");
                        avgLabel.setTextSize(8);
                        avgLabel.setTextColor(muted);

                        avgValue = new TextView(context);
                        avgValue.setTextSize(14);
                        avgValue.setTypeface(null, android.graphics.Typeface.BOLD);
                        avgValue.setTextColor(green);

                        avgColumn.addView(avgLabel);
                        avgColumn.addView(avgValue);
                        statsRow.addView(avgColumn);

                        bar = new android.widget.ProgressBar(
                                context, null,
                                android.R.attr.progressBarStyleHorizontal);

                        bar.setMax(100);
                        bar.setProgressTintList(
                                android.content.res.ColorStateList.valueOf(green));
                        bar.setProgressBackgroundTintList(
                                android.content.res.ColorStateList.valueOf(
                                        Color.rgb(52, 65, 81)));

                        LinearLayout.LayoutParams barParams =
                                new LinearLayout.LayoutParams(0, dp(7), 1f);
                        barParams.setMargins(dp(3), 0, dp(3), 0);
                        statsRow.addView(bar, barParams);

                    } else {
                        coreName.setGravity(Gravity.CENTER_VERTICAL);
                        statsRow.setMinimumHeight(dp(36));

                        statsRow.addView(coreName,
                                new LinearLayout.LayoutParams(0, dp(36), 1f));
                    }

                    LinearLayout minMax = new LinearLayout(context);
                    minMax.setOrientation(LinearLayout.VERTICAL);
                    minMax.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                    minMax.setPadding(dp(3), 0, 0, 0);

                    TextView minValue = new TextView(context);
                    minValue.setTextSize(8);
                    minValue.setTextColor(muted);

                    TextView maxValue = new TextView(context);
                    maxValue.setTextSize(8);
                    maxValue.setTextColor(muted);

                    minMax.addView(minValue);
                    minMax.addView(maxValue);

                    statsRow.addView(minMax);
                    coreCard.addView(statsRow);

                    if (title.equals("CPU CORE LOAD")) {
                        if (count > 0) {
                            double average = total / count;

                            avgValue.setText(String.format(
                                    Locale.US, "%.0f%%", average));
                            minValue.setText(String.format(
                                    Locale.US, "MIN %.0f%%", min));
                            maxValue.setText(String.format(
                                    Locale.US, "MAX %.0f%%", max));
                            bar.setProgress((int) Math.round(
                                    Math.max(0, Math.min(100, average))));
                        } else {
                            avgValue.setText("--");
                            minValue.setText("MIN --");
                            maxValue.setText("MAX --");
                            bar.setProgress(0);
                        }
                    } else {
                        if (count > 0) {
                            minValue.setText(String.format(
                                    Locale.US, "MIN %.1f MHz", min));
                            maxValue.setText(String.format(
                                    Locale.US, "MAX %.1f MHz", max));
                        } else {
                            minValue.setText("MIN --");
                            maxValue.setText("MAX --");
                        }
                    }

                    LinearLayout.LayoutParams cardParams =
                            new LinearLayout.LayoutParams(
                                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    cardParams.setMargins(dp(2), dp(2), dp(2), dp(2));
                    cpuRow.addView(coreCard, cardParams);
                }

                cpuGrid.addView(cpuRow);
            }

            card.addView(cpuGrid);
        }

        // STATISTICS (AVG / MIN / MAX)
        // Use the same full recording range as the chart above.

        if (!title.equals("CPU CORE LOAD")
                && !title.equals("CPU CORE FREQUENCY")) {

            for (int s = 0; s < series.size(); s++) {

                List<Double> values = series.get(s);

                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                double total = 0;
                int count = 0;

                for (int i = 0; i < values.size() && i < times.size(); i++) {
                    double value = values.get(i);

                    if (!Double.isFinite(times.get(i))
                            || !Double.isFinite(value)
                            || value == -1
                            || (value < 0
                            && !title.equals("BATTERY CURRENT")
                            && !title.equals("BATTERY POWER"))) continue;

                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    total += value;
                    count++;
                }

                boolean minMaxOnly = title.equals("CPU TEMPERATURE")
                        || title.equals("GPU TEMPERATURE");
                String[] statLabels = minMaxOnly
                        ? new String[]{"MIN", "MAX"}
                        : new String[]{"AVG", "MIN", "MAX"};

                String[] statValues = count > 0
                        ? (minMaxOnly
                        ? new String[]{
                        String.format(Locale.US, "%.2f %s", min, unit),
                        String.format(Locale.US, "%.2f %s", max, unit)
                        }
                        : new String[]{
                        String.format(Locale.US, "%.2f %s", total / count, unit),
                        String.format(Locale.US, "%.2f %s", min, unit),
                        String.format(Locale.US, "%.2f %s", max, unit)
                        })
                        : (minMaxOnly
                        ? new String[]{"--", "--"}
                        : new String[]{"--", "--", "--"});

                if (title.equals("GPU FREQUENCY") && count > 0) {
                    statValues[1] = String.format(Locale.US, "%.1f %s", min, unit);
                    statValues[2] = String.format(Locale.US, "%.1f %s", max, unit);
                    statValues[0] = String.format(Locale.US, "%.1f %s", total / count, unit);
                }

                if (series.size() > 1) {
                    TextView seriesName = new TextView(context);
                    seriesName.setText(names.get(s));
                    seriesName.setTextSize(11);
                    seriesName.setTextColor(muted);
                    seriesName.setTypeface(null, android.graphics.Typeface.BOLD);
                    seriesName.setPadding(0, dp(8), 0, dp(4));
                    card.addView(seriesName);
                }

                LinearLayout statsRow = new LinearLayout(context);
                statsRow.setOrientation(LinearLayout.HORIZONTAL);
                statsRow.setGravity(Gravity.CENTER_VERTICAL);
                statsRow.setPadding(0, dp(5), 0, dp(5));

                for (int i = 0; i < statLabels.length; i++) {

                    LinearLayout statColumn = new LinearLayout(context);
                    statColumn.setOrientation(LinearLayout.VERTICAL);
                    statColumn.setGravity(Gravity.CENTER);
                    statColumn.setTranslationY(-dp(3));

                    TextView statLabel = new TextView(context);
                    statLabel.setText(statLabels[i]);
                    statLabel.setTextSize(10);
                    statLabel.setTextColor(muted);
                    statLabel.setGravity(Gravity.CENTER);
                    statLabel.setIncludeFontPadding(false);

                    TextView statValue = new TextView(context);
                    statValue.setText(statValues[i]);
                    statValue.setTextSize(15);
                    statValue.setTypeface(null, android.graphics.Typeface.BOLD);
                    statValue.setTextColor(Color.WHITE);
                    statValue.setGravity(Gravity.CENTER);
                    statValue.setSingleLine(true);
                    statValue.setIncludeFontPadding(false);

                    statColumn.addView(statLabel);
                    statColumn.addView(statValue);

                    statsRow.addView(statColumn, new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                    if (i < statLabels.length - 1) {
                        View statDivider = new View(context);
                        statDivider.setBackgroundColor(Color.rgb(52, 65, 81));

                        statsRow.addView(statDivider,
                                new LinearLayout.LayoutParams(dp(1), dp(36)));
                    }
                }

                card.addView(statsRow);
            }
        }

        // CPU cluster legend: align its three equal sections with the
        // two-column core cards above. Other charts have no footer.
        if (title.equals("CPU CORE LOAD")
                || title.equals("CPU CORE FREQUENCY")) {

            View divider = new View(context);
            divider.setBackgroundColor(Color.rgb(65, 82, 101));
            card.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

            LinearLayout legendRow = new LinearLayout(context);
            legendRow.setOrientation(LinearLayout.HORIZONTAL);
            legendRow.setGravity(Gravity.CENTER_VERTICAL);
            legendRow.setPadding(dp(3), dp(7), dp(3), 0);

            String[] legendLabels = {
                    "🟥 Performance",
                    "🟩 Normal",
                    "🟦 Efficiency"
            };

            for (String label : legendLabels) {
                TextView legend = new TextView(context);
                legend.setText(label);
                legend.setTextSize(10);
                legend.setTextColor(muted);
                legend.setGravity(Gravity.CENTER);
                legend.setSingleLine(true);

                legendRow.addView(legend, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            }

            card.addView(legendRow);
        }

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(10);

        layout.addView(card, cardParams);
    }

    // ---------- The line chart itself ----------

    private static class ChartView extends View {

        static int colorsForCore(int index) {
            switch (CpuCoreClassifier.getCoreType(index)) {
                case CpuCoreClassifier.EFFICIENCY:
                    return Color.rgb(65, 190, 255);
                case CpuCoreClassifier.NORMAL:
                    return Color.rgb(100, 220, 130);
                case CpuCoreClassifier.PERFORMANCE:
                    return Color.rgb(255, 100, 100);
                default:
                    int[] fallback = {
                            Color.rgb(65, 190, 255),
                            Color.rgb(255, 100, 100),
                            Color.rgb(100, 220, 130),
                            Color.rgb(255, 190, 70),
                            Color.rgb(190, 130, 255),
                            Color.rgb(255, 130, 200),
                            Color.rgb(100, 220, 220),
                            Color.rgb(220, 220, 220)
                    };
                    return fallback[index % fallback.length];
            }
        }

        private final List<List<Double>> series;
        private final List<Double> times;
        private final int accent;
        private final boolean wholeNumberYAxis;
        private final List<Double> thermalStatus;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int selectedThermalPeriod = -1;

        private static class ThermalPeriod {
            final int severity;
            final double start;
            final double end;
            ThermalPeriod(int severity, double start, double end) {
                this.severity = severity;
                this.start = start;
                this.end = end;
            }
        }

        private final float chartLeft = 65f;
        private float chartRight;
        private float chartTop;
        private float chartBottom;

        ChartView(Context context,
                  List<List<Double>> series,
                  List<Double> times,
                  int accent,
                  boolean wholeNumberYAxis,
                  List<Double> thermalStatus) {
            super(context);
            this.series = series;
            this.times = times;
            this.accent = accent;
            this.wholeNumberYAxis = wholeNumberYAxis;
            this.thermalStatus = thermalStatus;
        }

        private List<ThermalPeriod> getThermalPeriods() {
            List<ThermalPeriod> periods = new ArrayList<>();
            if (thermalStatus == null) return periods;
            int count = Math.min(Math.min(thermalStatus.size(), times.size()),
                    series.isEmpty() ? 0 : series.get(0).size());
            for (int i = 0; i + 1 < count; i++) {
                double statusValue = thermalStatus.get(i);
                double from = times.get(i);
                double to = times.get(i + 1);
                if (!Double.isFinite(statusValue) || statusValue < 1
                        || !Double.isFinite(from) || !Double.isFinite(to) || to <= from) continue;
                int severity = statusValue >= 2 ? 2 : 1;
                if (!periods.isEmpty()) {
                    ThermalPeriod previous = periods.get(periods.size() - 1);
                    if (previous.severity == severity && Math.abs(previous.end - from) < 0.001) {
                        periods.set(periods.size() - 1, new ThermalPeriod(severity, previous.start, to));
                        continue;
                    }
                }
                periods.add(new ThermalPeriod(severity, from, to));
            }
            return periods;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (thermalStatus == null) return false;
            if (event.getAction() == MotionEvent.ACTION_UP) {
                double startTime = Double.POSITIVE_INFINITY;
                double endTime = Double.NEGATIVE_INFINITY;
                for (double time : times) {
                    if (Double.isFinite(time)) {
                        startTime = Math.min(startTime, time);
                        endTime = Math.max(endTime, time);
                    }
                }
                double range = endTime - startTime;
                if (range <= 0) return false;
                List<ThermalPeriod> periods = getThermalPeriods();
                int hit = -1;
                if (event.getY() <= chartTop + dp(12)) {
                    for (int i = 0; i < periods.size(); i++) {
                        ThermalPeriod period = periods.get(i);
                        float left = chartLeft + (chartRight - chartLeft)
                                * (float) ((period.start - startTime) / range);
                        float right = chartLeft + (chartRight - chartLeft)
                                * (float) ((period.end - startTime) / range);
                        if (event.getX() >= left - dp(8) && event.getX() <= right + dp(8)) {
                            hit = i;
                            break;
                        }
                    }
                }
                selectedThermalPeriod = hit;
                invalidate();
                performClick();
                return true;
            }
            return true;
        }

        private int dp(float value) {
            return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private String formatTime(double seconds) {
            int total = Math.max(0, (int) seconds);
            int minutes = total / 60;
            int secs = total % 60;

            if (minutes >= 60) {
                return String.format(Locale.US, "%d:%02d:%02d",
                        minutes / 60, minutes % 60, secs);
            }

            return String.format(Locale.US, "%02d:%02d", minutes, secs);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (times.isEmpty()) return;

            chartTop = dp(26);                    // room for the thermal track + tooltip
            chartRight = getWidth() - 15f;
            chartBottom = getHeight() - 42f;

            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;

            for (List<Double> values : series) {
                int pointCount = Math.min(values.size(), times.size());

                for (int i = 0; i < pointCount; i++) {
                    double value = values.get(i);
                    double time = times.get(i);

                    if (Double.isFinite(value) && value != -1
                            && Double.isFinite(time)) {
                        min = Math.min(min, value);
                        max = Math.max(max, value);
                    }
                }
            }

            if (min == Double.MAX_VALUE) return;

            if (max - min < 0.001) {
                max += 1;
                min -= 1;
            }

            double range = max - min;

            double startTime = Double.POSITIVE_INFINITY;
            double endTime = Double.NEGATIVE_INFINITY;

            for (double time : times) {
                if (!Double.isFinite(time)) continue;
                startTime = Math.min(startTime, time);
                endTime = Math.max(endTime, time);
            }

            if (startTime == Double.POSITIVE_INFINITY) return;

            double timeRange = endTime - startTime;

            paint.setStrokeWidth(1.2f);
            paint.setColor(Color.rgb(65, 78, 94));
            paint.setStyle(Paint.Style.STROKE);

            for (int i = 0; i <= 4; i++) {
                float y = chartTop + (chartBottom - chartTop) * i / 4f;
                canvas.drawLine(chartLeft, y, chartRight, y, paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(8f * getResources().getDisplayMetrics().scaledDensity);
            paint.setColor(Color.LTGRAY);

            for (int i = 0; i <= 4; i++) {
                double value = max - range * i / 4.0;
                float y = chartTop + (chartBottom - chartTop) * i / 4f;

                canvas.drawText(
                        String.format(Locale.US,
                                wholeNumberYAxis ? "%.0f" : "%.1f", value),
                        5f, y + 3f, paint);
            }

            for (int s = 0; s < series.size(); s++) {
                List<Double> values = series.get(s);

                Path path = new Path();
                boolean started = false;
                float firstX = 0, lastX = 0;

                int pointCount = Math.min(values.size(), times.size());

                for (int i = 0; i < pointCount; i++) {
                    double value = values.get(i);
                    double time = times.get(i);

                    if (!Double.isFinite(value) || value == -1
                            || !Double.isFinite(time)) {
                        continue;
                    }

                    double timePosition = timeRange > 0
                            ? (time - startTime) / timeRange
                            : (double) i / Math.max(1, pointCount - 1);

                    float x = chartLeft
                            + (chartRight - chartLeft)
                            * (float) Math.max(0, Math.min(1, timePosition));

                    float y = chartBottom - (float)
                            ((value - min) / range)
                            * (chartBottom - chartTop);

                    if (!started) {
                        path.moveTo(x, y);
                        firstX = x;
                        started = true;
                    } else {
                        path.lineTo(x, y);
                    }
                    lastX = x;
                }

                if (!started) continue;

                int lineColor = series.size() == 1 ? accent : colorsForCore(s);

                // soft fill under single-line charts
                if (series.size() == 1) {
                    Path fill = new Path(path);
                    fill.lineTo(lastX, chartBottom);
                    fill.lineTo(firstX, chartBottom);
                    fill.close();

                    paint.setStyle(Paint.Style.FILL);
                    paint.setShader(new android.graphics.LinearGradient(
                            0, chartTop, 0, chartBottom,
                            Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent)),
                            Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent)),
                            android.graphics.Shader.TileMode.CLAMP));
                    canvas.drawPath(fill, paint);
                    paint.setShader(null);
                }

                paint.setColor(lineColor);
                paint.setStrokeWidth(3f);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawPath(path, paint);
            }

            // Mark the exact start and end of each elevated thermal period.
            // Draw these over the FPS plot with a subtle dashed stroke.
            if (thermalStatus != null && timeRange > 0) {
                List<ThermalPeriod> periods = getThermalPeriods();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1));
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setPathEffect(new DashPathEffect(
                        new float[]{dp(4), dp(3)}, 0));
                for (ThermalPeriod period : periods) {
                    int boundaryColor = period.severity >= 2
                            ? Color.rgb(245, 91, 91) : Color.rgb(255, 174, 66);
                    paint.setColor(Color.argb(175,
                            Color.red(boundaryColor),
                            Color.green(boundaryColor),
                            Color.blue(boundaryColor)));

                    float startX = chartLeft + (chartRight - chartLeft)
                            * (float) ((period.start - startTime) / timeRange);
                    float endX = chartLeft + (chartRight - chartLeft)
                            * (float) ((period.end - startTime) / timeRange);
                    float boundaryTop = chartTop - dp(10);
                    canvas.drawLine(startX, boundaryTop, startX, chartBottom, paint);
                    canvas.drawLine(endX, boundaryTop, endX, chartBottom, paint);
                }
                paint.setPathEffect(null);
            }

            // Compact thermal timeline; event details are shown only on tap.
            if (thermalStatus != null && timeRange > 0) {
                List<ThermalPeriod> periods = getThermalPeriods();
                float trackY = chartTop - dp(10);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(3));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(Color.rgb(52, 65, 81));
                canvas.drawLine(chartLeft, trackY, chartRight, trackY, paint);
                for (ThermalPeriod period : periods) {
                    float left = chartLeft + (chartRight - chartLeft)
                            * (float) ((period.start - startTime) / timeRange);
                    float right = chartLeft + (chartRight - chartLeft)
                            * (float) ((period.end - startTime) / timeRange);
                    float segmentEnd = Math.max(left + dp(2), right);
                    int segmentColor = period.severity >= 2
                            ? Color.rgb(245, 91, 91) : Color.rgb(255, 174, 66);

                    // Soft, wider under-stroke creates a subtle glow without
                    // relying on hardware-accelerated shadow rendering.
                    paint.setStrokeWidth(dp(7));
                    paint.setColor(Color.argb(65,
                            Color.red(segmentColor),
                            Color.green(segmentColor),
                            Color.blue(segmentColor)));
                    canvas.drawLine(left, trackY, segmentEnd, trackY, paint);

                    paint.setStrokeWidth(dp(3));
                    paint.setColor(segmentColor);
                    canvas.drawLine(left, trackY, segmentEnd, trackY, paint);
                }
                paint.setStrokeCap(Paint.Cap.BUTT);

                if (selectedThermalPeriod >= 0 && selectedThermalPeriod < periods.size()) {
                    ThermalPeriod selected = periods.get(selectedThermalPeriod);
                    String label = (selected.severity >= 2
                            ? "MODERATE+ THERMAL STATUS" : "LIGHT THERMAL STATUS")
                            + " (" + formatTime(selected.start)
                            + " - " + formatTime(selected.end) + ")";
                    paint.setTextSize(9f * getResources().getDisplayMetrics().scaledDensity);
                    paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    float textWidth = paint.measureText(label);
                    float boxWidth = Math.min(getWidth() - dp(12), textWidth + dp(14));
                    float boxLeft = Math.max(dp(6), Math.min(chartRight - boxWidth, chartLeft));
                    android.graphics.RectF tooltip = new android.graphics.RectF(
                            boxLeft, dp(2), boxLeft + boxWidth, chartTop - dp(3));
                    paint.setColor(Color.rgb(39, 48, 61));
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawRoundRect(tooltip, dp(5), dp(5), paint);
                    paint.setColor(selected.severity >= 2
                            ? Color.rgb(255, 125, 125) : Color.rgb(255, 194, 105));
                    canvas.drawText(label, boxLeft + dp(7), chartTop - dp(9), paint);
                    paint.setTypeface(android.graphics.Typeface.DEFAULT);
                }
            }

            // Start and end timestamps at the bottom
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(9f * getResources().getDisplayMetrics().scaledDensity);
            paint.setColor(Color.rgb(145, 160, 180));

            canvas.drawText(formatTime(startTime), chartLeft, chartBottom + 29f, paint);
            String endStr = formatTime(endTime);
            float endWidth = paint.measureText(endStr);
            canvas.drawText(endStr, chartRight - endWidth, chartBottom + 29f, paint);
        }
    }
}