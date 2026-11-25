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
        
                android.graphics.drawable.GradientDrawable pillBg =
                        new android.graphics.drawable.GradientDrawable();
                pillBg.setColor(Color.rgb(25, 33, 45));
                pillBg.setCornerRadius(activity.dp(50));
                pillBg.setStroke(activity.dp(1), Color.rgb(43, 52, 65));
                statusPill.setBackground(pillBg);
        
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
        
                android.graphics.drawable.GradientDrawable shizukuBackground =
                        new android.graphics.drawable.GradientDrawable();
                shizukuBackground.setColor(Color.rgb(39, 53, 69));
                shizukuBackground.setCornerRadius(activity.dp(12));
                shizukuBackground.setStroke(activity.dp(1), Color.rgb(65, 85, 105));
        
                shizukuButton.setBackground(shizukuBackground);
                shizukuButton.setOnClickListener(v -> activity.startShizukuService());
        
        // SESSION CONTROLS
                activity.startButton = new Button(activity);
                activity.startButton.setText("START SESSION");
                activity.startButton.setTextSize(13);
                activity.startButton.setAllCaps(false);
                activity.startButton.setTextColor(Color.WHITE);
        
                android.graphics.drawable.GradientDrawable sessionButtonBackground =
                        new android.graphics.drawable.GradientDrawable();
                sessionButtonBackground.setCornerRadius(activity.dp(12));
                int appAccentColor = activity.getAppAccentColor();
        
                sessionButtonBackground.setColor(appAccentColor);
                sessionButtonBackground.setStroke(
                        activity.dp(1),
                        Color.argb(
                                180,
                                Color.red(appAccentColor),
                                Color.green(appAccentColor),
                                Color.blue(appAccentColor)
                        )
                );
        
        
                activity.startButton.setBackground(sessionButtonBackground);
                activity.startButton.setElevation(activity.dp(3));
                activity.startButton.setOnClickListener(v -> activity.toggleSessionFromMonitor());
        
        
                // FLOATING OVERLAY
                activity.overlayButton = new Button(activity);
                activity.overlayButton.setTextSize(12);
                activity.overlayButton.setAllCaps(false);
                activity.overlayButton.setTextColor(Color.WHITE);
                android.graphics.drawable.GradientDrawable overlayButtonBackground =
                        new android.graphics.drawable.GradientDrawable();
        
                overlayButtonBackground.setColor(Color.rgb(39, 53, 69));
                overlayButtonBackground.setCornerRadius(activity.dp(12));
                overlayButtonBackground.setStroke(activity.dp(1), Color.rgb(65, 85, 105));
        
                activity.overlayButton.setBackground(overlayButtonBackground);
                activity.overlayButton.setElevation(activity.dp(3));
                activity.overlayButton.setOnClickListener(v -> activity.toggleOverlay());
                activity.compactButton(activity.startButton);
                activity.compactButton(shizukuButton);
                activity.compactButton(activity.overlayButton);
        
        // Start Session: full width, on top
                LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(46));
                startParams.bottomMargin = activity.dp(8);
                page.addView(activity.startButton, startParams);
        
        // Connect Shizuku + overlay: side by side
                LinearLayout buttonRow = new LinearLayout(activity);
                buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        
                LinearLayout.LayoutParams leftParams =
                        new LinearLayout.LayoutParams(0, activity.dp(46), 1f);
                leftParams.rightMargin = activity.dp(4);
                buttonRow.addView(shizukuButton, leftParams);
        
                LinearLayout.LayoutParams rightParams =
                        new LinearLayout.LayoutParams(0, activity.dp(46), 1f);
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
                activity.fps = activity.tile(gridRow1, "FPS", false);
                activity.cpuTile = activity.tile(gridRow1, "CPU", true);
                activity.add(page, gridRow1, 0, 8);
        
                LinearLayout gridRow2 = new LinearLayout(activity);
                gridRow2.setOrientation(LinearLayout.HORIZONTAL);
                activity.gpuTile = activity.tile(gridRow2, "GPU", false);
                activity.temperature = activity.tile(gridRow2, "TEMP", true);
                activity.add(page, gridRow2, 0, 8);
        
                LinearLayout gridRow3 = new LinearLayout(activity);
                gridRow3.setOrientation(LinearLayout.HORIZONTAL);
                activity.power = activity.tile(gridRow3, "POWER", false);
                activity.current = activity.tile(gridRow3, "BATTERY CURRENT", true);
                activity.setPowerStateView(activity.addSubLine(activity.power));
        
        // make both cards in this row the same height
                for (int i = 0; i < gridRow3.getChildCount(); i++) {
                    gridRow3.getChildAt(i).getLayoutParams().height =
                            ViewGroup.LayoutParams.MATCH_PARENT;
                }
                activity.add(page, gridRow3, 0, 8);
        
                LinearLayout gridRow4 = new LinearLayout(activity);
                gridRow4.setOrientation(LinearLayout.HORIZONTAL);
                activity.voltage = activity.tile(gridRow4, "BATTERY VOLTAGE", false);
                activity.battery = activity.tile(gridRow4, "BATTERY LEVEL", true);
                activity.add(page, gridRow4, 0, 18);
        
                // SESSION SUMMARY
                activity.add(page, activity.section("SESSION SUMMARY"), 0, 8);
        
                LinearLayout summaryCard = new LinearLayout(activity);
                summaryCard.setOrientation(LinearLayout.VERTICAL);
                summaryCard.setPadding(activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14));
                android.graphics.drawable.GradientDrawable summaryBackground =
                        new android.graphics.drawable.GradientDrawable();
        
                summaryBackground.setColor(Color.rgb(25, 33, 45));
                summaryBackground.setCornerRadius(activity.dp(14));
                summaryBackground.setStroke(activity.dp(1), Color.rgb(55, 70, 88));
        
                summaryCard.setBackground(activity.cardBg(14));
                summaryCard.setElevation(activity.dp(3));
        
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
}
