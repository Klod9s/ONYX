package com.onyx.perfmonitor;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

final class MonitorPageBuilder {
    private final MainActivity activity;

    MonitorPageBuilder(MainActivity activity) {
        this.activity = activity;
    }

    void build(LinearLayout page) {
        // HEADER
        
                LinearLayout statusPill = new LinearLayout(activity);
                statusPill.setOrientation(LinearLayout.HORIZONTAL);
                statusPill.setGravity(Gravity.CENTER_VERTICAL);
                statusPill.setPadding(activity.dp(12), activity.dp(7), activity.dp(12), activity.dp(7));
        
                statusPill.setBackground(activity.glassBg(50));
        
                activity.statusDot = new View(activity);
                activity.statusDotBg = new android.graphics.drawable.GradientDrawable();
                activity.statusDotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                activity.statusDotBg.setColor(activity.getAppAccentColor());
                activity.statusDot.setBackground(activity.statusDotBg);
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(activity.dp(7), activity.dp(7));
                dotParams.rightMargin = activity.dp(8);
                statusPill.addView(activity.statusDot, dotParams);
        
                activity.status = activity.text("CHECKING…", 10, true, Color.rgb(205, 214, 225));
                activity.status.setLetterSpacing(0.08f);
                activity.status.setSingleLine(true);
                statusPill.addView(activity.status);
        
                LinearLayout monitorHeader = activity.tabHeader(R.drawable.ic_nav_monitor, "MONITOR");
                monitorHeader.addView(statusPill);
                activity.add(page, monitorHeader, 0, 12);
        
                Button shizukuButton = new Button(activity);
                shizukuButton.setText("CONNECT SHIZUKU");
                shizukuButton.setTextSize(12);
                shizukuButton.setAllCaps(false);
                shizukuButton.setTextColor(Color.WHITE);
        
                shizukuButton.setBackground(activity.flatBg(14));
                shizukuButton.setOnClickListener(v -> activity.startShizukuService());
        
        // SESSION CONTROLS
                activity.startButton = new Button(activity);
                activity.startButton.setText("START SESSION");
                activity.startButton.setTextSize(13);
                activity.startButton.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
                activity.startButton.setLetterSpacing(0.04f);
                activity.startButton.setAllCaps(false);
                activity.startButton.setTextColor(Color.WHITE);
        
                int appAccentColor = activity.getAppAccentColor();
                activity.startButton.setBackground(
                        GlassStyle.primaryButton(appAccentColor, activity.dp(14), activity.dp(1)));
                activity.startButton.setElevation(0f);
                activity.startButton.setOnClickListener(v -> activity.toggleSessionFromMonitor());
        
        
                // FLOATING OVERLAY
                activity.overlayButton = new Button(activity);
                activity.overlayButton.setTextSize(12);
                activity.overlayButton.setAllCaps(false);
                activity.overlayButton.setTextColor(Color.WHITE);
                activity.overlayButton.setBackground(activity.flatBg(14));
                activity.overlayButton.setElevation(0f);
                activity.overlayButton.setStateListAnimator(null);
                shizukuButton.setStateListAnimator(null);
                activity.startButton.setStateListAnimator(null);
                activity.overlayButton.setOnClickListener(v -> activity.toggleOverlay());
                activity.compactButton(activity.startButton);
                activity.compactButton(shizukuButton);
                activity.compactButton(activity.overlayButton);
        
        // Start Session: full width, on top
                LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(48));
                startParams.bottomMargin = activity.dp(8);
                page.addView(activity.startButton, startParams);
        
        // Connect Shizuku + overlay: side by side
                LinearLayout buttonRow = new LinearLayout(activity);
                buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        
                LinearLayout.LayoutParams leftParams =
                        new LinearLayout.LayoutParams(0, activity.dp(48), 1f);
                leftParams.rightMargin = activity.dp(4);
                buttonRow.addView(shizukuButton, leftParams);
        
                LinearLayout.LayoutParams rightParams =
                        new LinearLayout.LayoutParams(0, activity.dp(48), 1f);
                rightParams.leftMargin = activity.dp(4);
                buttonRow.addView(activity.overlayButton, rightParams);
        
                activity.add(page, buttonRow, 0, 18);
        
        
                // OVERLAY SETTINGS (this card is shown in the Settings tab)
                LinearLayout overlaySettings = new LinearLayout(activity);
                overlaySettings.setOrientation(LinearLayout.VERTICAL);
                overlaySettings.setBackground(activity.cardBg(16));
                overlaySettings.setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(12));
                overlaySettings.setElevation(activity.dp(4));
        
                activity.add(overlaySettings, activity.section("FLOATING OVERLAY SETTINGS"), 0, 8);
        
                activity.showFps = new CheckBox(activity);
                activity.showFps.setText("Show FPS counter");
                activity.styleCheckBox(activity.showFps);
                activity.showFps.setChecked(activity.getPrefs().getBoolean("show_fps", true));
                activity.showFps.setOnCheckedChangeListener((button, checked) ->
                        activity.getPrefs().edit().putBoolean("show_fps", checked).apply());
                activity.add(overlaySettings, activity.showFps, 0, 2);
        
                activity.showTemperature = new CheckBox(activity);
                activity.showTemperature.setText("Show temperature");
                activity.styleCheckBox(activity.showTemperature);
                activity.showTemperature.setChecked(
                        activity.getPrefs().getBoolean("show_temperature", true));
                activity.showTemperature.setOnCheckedChangeListener((button, checked) ->
                        activity.getPrefs().edit()
                                .putBoolean("show_temperature", checked)
                                .apply());
                activity.add(overlaySettings, activity.showTemperature, 0, 2);
        
                CheckBox showFrameTimeGraph = new CheckBox(activity);
                showFrameTimeGraph.setText("Show frame-time graph");
                activity.styleCheckBox(showFrameTimeGraph);
                showFrameTimeGraph.setChecked(
                        activity.getPrefs().getBoolean("show_frame_time_graph", true));
                showFrameTimeGraph.setOnCheckedChangeListener((button, checked) ->
                        activity.getPrefs().edit()
                                .putBoolean("show_frame_time_graph", checked)
                                .apply());
        
                activity.add(overlaySettings, showFrameTimeGraph, 0, 2);
        
                CheckBox showCpu = new CheckBox(activity);
                showCpu.setText("Show CPU load");
                activity.styleCheckBox(showCpu);
                showCpu.setChecked(activity.getPrefs().getBoolean("show_cpu", true));
                showCpu.setOnCheckedChangeListener((button, checked) ->
                        activity.getPrefs().edit().putBoolean("show_cpu", checked).apply());
                activity.add(overlaySettings, showCpu, 0, 2);
        
                CheckBox showGpu = new CheckBox(activity);
                showGpu.setText("Show GPU load");
                activity.styleCheckBox(showGpu);
                showGpu.setChecked(activity.getPrefs().getBoolean("show_gpu", true));
                showGpu.setOnCheckedChangeListener((button, checked) ->
                        activity.getPrefs().edit().putBoolean("show_gpu", checked).apply());
                activity.add(overlaySettings, showGpu, 0, 2);

                CheckBox showRam = new CheckBox(activity);
                showRam.setText("Show RAM usage");
                activity.styleCheckBox(showRam);
                showRam.setChecked(activity.getPrefs().getBoolean("show_ram", false));
                showRam.setOnCheckedChangeListener((button, checked) ->
                        activity.getPrefs().edit().putBoolean("show_ram", checked).apply());
                activity.add(overlaySettings, showRam, 0, 2);

                activity.addStylePicker(overlaySettings);
        
                android.widget.TextView opacityLabel = new android.widget.TextView(activity);
                opacityLabel.setTextColor(Color.WHITE);
                opacityLabel.setTextSize(13);
        
                android.widget.SeekBar opacitySlider =
                        new android.widget.SeekBar(activity);
        
                opacitySlider.setMax(100);
        
                opacitySlider.setProgressTintList(
                        android.content.res.ColorStateList.valueOf(appAccentColor));
                opacitySlider.setThumbTintList(
                        android.content.res.ColorStateList.valueOf(appAccentColor));
                activity.appAccentSeekBars.add(opacitySlider);
        
                int savedOpacity = activity.getPrefs().getInt("overlay_opacity", 85);
                opacitySlider.setProgress(savedOpacity);
        
                opacityLabel.setText("Overlay opacity: " + savedOpacity + "%");
        
                opacitySlider.setOnSeekBarChangeListener(
                        new android.widget.SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onProgressChanged(
                                    android.widget.SeekBar seekBar,
                                    int progress,
                                    boolean fromUser) {
        
                                opacityLabel.setText(
                                        "Overlay opacity: " + progress + "%");
        
                                if (fromUser) {
                                    activity.getPrefs().edit()
                                            .putInt("overlay_opacity", progress)
                                            .apply();
                                }
                            }
        
                            @Override
                            public void onStartTrackingTouch(
                                    android.widget.SeekBar seekBar) {
                            }
        
                            @Override
                            public void onStopTrackingTouch(
                                    android.widget.SeekBar seekBar) {
                            }
                        });
        
                activity.add(overlaySettings, opacityLabel, 0, 2);
                activity.add(overlaySettings, opacitySlider, 0, 2);
        
                activity.overlaySettingsCard = overlaySettings;
        
                TextView scaleLabel = new TextView(activity);
                scaleLabel.setTextColor(android.graphics.Color.WHITE);
        
                android.widget.SeekBar scaleSlider =
                        new android.widget.SeekBar(activity);
        
                scaleSlider.setMax(12);
                scaleSlider.setProgressTintList(
                        android.content.res.ColorStateList.valueOf(appAccentColor));
                scaleSlider.setThumbTintList(
                        android.content.res.ColorStateList.valueOf(appAccentColor));
                activity.appAccentSeekBars.add(scaleSlider);
        
                int savedScale = activity.getPrefs().getInt("overlay_scale", 100);
                scaleSlider.setProgress((savedScale - 70) / 5);
        
                scaleLabel.setText("Overlay scale: " + savedScale + "%");
        
                scaleSlider.setOnSeekBarChangeListener(
                        new android.widget.SeekBar.OnSeekBarChangeListener() {
                            @Override
                            public void onProgressChanged(
                                    android.widget.SeekBar seekBar,
                                    int progress,
                                    boolean fromUser) {
        
                                int scale = 70 + (progress * 5);
        
                                scaleLabel.setText("Overlay scale: " + scale + "%");
        
                                if (fromUser) {
                                    activity.getPrefs().edit()
                                            .putInt("overlay_scale", scale)
                                            .apply();
                                }
                            }
        
                            @Override
                            public void onStartTrackingTouch(
                                    android.widget.SeekBar seekBar) {
                            }
        
                            @Override
                            public void onStopTrackingTouch(
                                    android.widget.SeekBar seekBar) {
                            }
                        });
        
                activity.add(overlaySettings, scaleLabel, 0, 2);
                activity.add(overlaySettings, scaleSlider, 0, 2);
        
                // LIVE READINGS
                activity.add(page, activity.section("LIVE READINGS"), 0, 8);
        
                LinearLayout gridRow1 = new LinearLayout(activity);
                gridRow1.setOrientation(LinearLayout.HORIZONTAL);
                activity.fps = glassTile(gridRow1, R.drawable.ic_metric_fps, "FPS", false, false, false);
                activity.power = glassTile(gridRow1, R.drawable.ic_metric_bolt, "POWER", true, false, false);
                activity.setPowerStateView(addStateBadge(activity.power));
                activity.add(page, gridRow1, 0, 8);
                equalHeight(gridRow1);

                LinearLayout gridRow2 = new LinearLayout(activity);
                gridRow2.setOrientation(LinearLayout.HORIZONTAL);
                activity.cpuTile = glassTile(gridRow2, R.drawable.ic_metric_cpu, "CPU", false, true, false);
                activity.gpuTile = glassTile(gridRow2, R.drawable.ic_metric_gpu, "GPU", true, true, false);
                activity.add(page, gridRow2, 0, 8);
                equalHeight(gridRow2);

                LinearLayout gridRow3 = new LinearLayout(activity);
                gridRow3.setOrientation(LinearLayout.HORIZONTAL);
                activity.current = glassTile(gridRow3, R.drawable.ic_metric_bolt, "CURRENT", false, true, false);
                activity.temperature = glassTile(gridRow3, R.drawable.ic_metric_temp, "TEMP", true, true, true);
                activity.add(page, gridRow3, 0, 8);
                equalHeight(gridRow3);

                LinearLayout gridRow4 = new LinearLayout(activity);
                gridRow4.setOrientation(LinearLayout.HORIZONTAL);
                activity.voltage = glassTile(gridRow4, R.drawable.ic_metric_battery, "BATTERY VOLTAGE", false, true, false);
                activity.battery = glassTile(gridRow4, R.drawable.ic_metric_battery, "BATTERY LEVEL", true, true, true);
                activity.add(page, gridRow4, 0, 18);
                equalHeight(gridRow4);

                // SESSION SUMMARY
                activity.add(page, activity.section("SESSION SUMMARY"), 0, 8);
        
                LinearLayout summaryCard = new LinearLayout(activity);
                summaryCard.setOrientation(LinearLayout.VERTICAL);
                summaryCard.setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14));
                summaryCard.setBackground(activity.glassBg(16));
                summaryCard.setElevation(0f);
        
                activity.summary = activity.text("No session yet.", 14, false,
                        Color.rgb(210, 220, 232));
                activity.summary.setLineSpacing(activity.dp(4), 1f);
                summaryCard.addView(activity.summary);
        
                activity.add(page, summaryCard, 0, 22);
        
                // FOOTNOTE
                TextView note = activity.text(
                        "Display FPS is read from the display controller. " +
                                "CPU loads are system-wide. Battery power is battery-side " +
                                "power; unplug the phone for discharge measurements. " +
                                "Root access or Shizuku is required.",
                        11, false, Color.rgb(115, 130, 148));
        
                note.setLineSpacing(activity.dp(3), 1f);
                activity.add(page, note, 0, 12);
    }

    private void equalHeight(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            row.getChildAt(i).getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
    }

    /**
     * One glass metric card: icon + label on top (with an indicator dot on the right),
     * big value underneath. Returns the value view.
     *
     * @param accentValue colour the value with the app accent (otherwise white)
     * @param dynamicDot  dot colour is set by the renderer (temp / battery level);
     *                    otherwise the dot follows the app accent
     */
    private TextView glassTile(LinearLayout row, int iconRes, String label, boolean last,
                               boolean showDot, boolean dynamicDot) {
        // wrapper lets the dot grid sit in the bottom-right corner over the content
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(activity);
        wrap.setBackground(activity.glassBg(16));

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(activity.dp(14), activity.dp(11), activity.dp(14), activity.dp(12));
        wrap.addView(card, new android.widget.FrameLayout.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(activity.dp(20));

        android.widget.ImageView icon = new android.widget.ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setColorFilter(GlassStyle.MUTED);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(activity.dp(14), activity.dp(14));
        ip.rightMargin = activity.dp(6);
        header.addView(icon, ip);

        TextView title = activity.text(label, 10, true, GlassStyle.MUTED);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setLetterSpacing(0.02f);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueView = activity.text("—", 24, true, Color.WHITE);
        valueView.setMaxLines(1);
        valueView.setPadding(0, activity.dp(4), 0, 0);

        GlassStyle.TileTag tag = new GlassStyle.TileTag();
        if (showDot) { // tiles with a dot grid (bottom-right)
            GlassStyle.DotMatrixView matrix = new GlassStyle.DotMatrixView(
                    activity, !dynamicDot, GlassStyle.GOOD);
            android.widget.FrameLayout.LayoutParams mp =
                    new android.widget.FrameLayout.LayoutParams(activity.dp(26), activity.dp(40));
            mp.gravity = Gravity.BOTTOM | Gravity.END;
            mp.rightMargin = activity.dp(12);
            mp.bottomMargin = activity.dp(11);
            wrap.addView(matrix, mp);
            tag.matrix = matrix;
        }
        if (label.equals("FPS")) {
            GlassStyle.FrameGraphView graph = new GlassStyle.FrameGraphView(activity);
            android.widget.FrameLayout.LayoutParams gp =
                    new android.widget.FrameLayout.LayoutParams(activity.dp(88), activity.dp(30));
            gp.gravity = Gravity.BOTTOM | Gravity.END;
            gp.rightMargin = activity.dp(12);
            gp.bottomMargin = activity.dp(12);
            wrap.addView(graph, gp);
            tag.graph = graph;
        }
        valueView.setTag(tag);

        card.addView(header);
        card.addView(valueView);

        // FPS keeps the accent colour like the reference design
        if (label.equals("FPS")) {
            valueView.setTextColor(activity.getAppAccentColor());
            activity.appAccentTextViews.add(valueView);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (!last) lp.rightMargin = activity.dp(8);
        row.addView(wrap, lp);
        return valueView;
    }

    /** CHARGING / DISCHARGING pill in the header row of the power card. */
    private TextView addStateBadge(TextView powerValue) {
        LinearLayout card = (LinearLayout) powerValue.getParent();
        LinearLayout header = (LinearLayout) card.getChildAt(0);

        TextView badge = activity.text("—", 8, true, GlassStyle.MUTED);
        badge.setSingleLine(true);
        badge.setAllCaps(true);
        badge.setLetterSpacing(0.04f);
        badge.setPadding(activity.dp(6), activity.dp(2), activity.dp(6), activity.dp(2));
        header.addView(badge);
        return badge;
    }
}
