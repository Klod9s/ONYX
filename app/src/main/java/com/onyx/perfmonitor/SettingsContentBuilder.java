package com.onyx.perfmonitor;

import android.graphics.Color;
import android.graphics.Typeface;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class SettingsContentBuilder {
    private final MainActivity activity;

    SettingsContentBuilder(MainActivity activity) {
        this.activity = activity;
    }

    void build(ScrollView settingsPage) {
        LinearLayout content =
                (LinearLayout) settingsPage.getChildAt(0);
        content.removeViewAt(0);
        LinearLayout settingsHeader = activity.tabHeader(R.drawable.ic_nav_settings, "SETTINGS");

        activity.add(content, settingsHeader, 0, 12);

        // BACKGROUND ACTIVITY CARD
        LinearLayout backgroundCard = new LinearLayout(activity);
        backgroundCard.setOrientation(LinearLayout.VERTICAL);
        backgroundCard.setPadding(
                activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16));
        backgroundCard.setBackground(activity.cardBg(16));

        activity.add(backgroundCard, activity.section("BACKGROUND ACTIVITY"), 0, 8);

        TextView backgroundNote = activity.text(
                "Allow ONYX to keep monitoring when you're using other apps. "
                        + "For better reliability, set ONYX battery usage to Unrestricted.",
                12,
                false,
                Color.rgb(145, 158, 175)
        );

        activity.add(backgroundCard, backgroundNote, 0, 12);

        TextView batterySettingsButton = activity.text(
                "OPEN BATTERY SETTINGS  →",
                13,
                true,
                activity.getAppAccentColor()
        );
        activity.appAccentTextViews.add(batterySettingsButton);

        batterySettingsButton.setPadding(
                activity.dp(4), activity.dp(10), activity.dp(4), activity.dp(10));

        batterySettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + activity.getPackageName())
            );
            activity.startActivity(intent);
        });

        activity.add(backgroundCard, batterySettingsButton, 0, 0);

        // DEVICE CHECK CARD
        LinearLayout checkCard = new LinearLayout(activity);
        checkCard.setOrientation(LinearLayout.VERTICAL);
        checkCard.setPadding(
                activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16));
        checkCard.setBackground(activity.cardBg(16));

        activity.add(checkCard, activity.section("DEVICE CHECK"), 0, 8);

        TextView checkNote = activity.text(
                "Scan this device to see which metrics ONYX can actually read. "
                        + "Availability depends on your ROM, kernel and access mode. ",
                12, false, Color.rgb(145, 158, 175));
        activity.add(checkCard, checkNote, 0, 12);

        // Terminal-style panel: monospace, near-black, subtle border.
        LinearLayout checkResults = new LinearLayout(activity);
        checkResults.setOrientation(LinearLayout.VERTICAL);
        checkResults.setPadding(
                activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));

        android.graphics.drawable.GradientDrawable terminalBg =
                new android.graphics.drawable.GradientDrawable();
        terminalBg.setColor(Color.rgb(8, 11, 15));
        terminalBg.setCornerRadius(activity.dp(10));
        terminalBg.setStroke(activity.dp(1), Color.rgb(38, 46, 58));
        checkResults.setBackground(terminalBg);
        checkResults.setVisibility(View.GONE);

        TextView checkButton = activity.text(
                "CHECK THIS DEVICE  →", 13, true, activity.getAppAccentColor());
        activity.appAccentTextViews.add(checkButton);
        checkButton.setPadding(activity.dp(4), activity.dp(10), activity.dp(4), activity.dp(10));

        checkButton.setOnClickListener(v -> {
            checkButton.setText("CHECKING…");
            checkButton.setEnabled(false);
            checkResults.removeAllViews();
            checkResults.setVisibility(View.GONE);

            activity.checkDeviceCapabilities(rows -> {
                if (activity.isFinishing()) return;

                checkButton.setText("CHECK THIS DEVICE  →");
                checkButton.setEnabled(true);
                checkResults.setVisibility(View.VISIBLE);

                int tick = Color.rgb(120, 230, 170);
                int dash = Color.rgb(105, 118, 135);
                int dot = Color.rgb(230, 190, 110);

                // Legend: one row, three fixed-width cells. Text that does not
                // fit wraps inside its own cell instead of pushing the next
                // entry out of place.
                String[] legendMarks = {"✓", "●", "–"};
                String[] legendLabels = {"supported", "test in a game", "not available"};
                int[] legendColors = {tick, dot, dash};

                LinearLayout legendRow = new LinearLayout(activity);
                legendRow.setOrientation(LinearLayout.HORIZONTAL);
                legendRow.setGravity(Gravity.TOP);

                for (int i = 0; i < legendMarks.length; i++) {
                    LinearLayout cell = new LinearLayout(activity);
                    cell.setOrientation(LinearLayout.HORIZONTAL);
                    cell.setGravity(Gravity.TOP);

                    TextView markView = new TextView(activity);
                    markView.setText(legendMarks[i]);
                    markView.setTextSize(11);
                    markView.setTextColor(legendColors[i]);
                    markView.setTypeface(android.graphics.Typeface.MONOSPACE);
                    cell.addView(markView, new LinearLayout.LayoutParams(
                            activity.dp(14), LinearLayout.LayoutParams.WRAP_CONTENT));

                    TextView labelView = new TextView(activity);
                    labelView.setText(legendLabels[i]);
                    labelView.setTextSize(11);
                    labelView.setTextColor(Color.rgb(145, 158, 175));
                    labelView.setTypeface(android.graphics.Typeface.MONOSPACE);
                    cell.addView(labelView, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));

                    // Marks and short labels size to content; the last cell takes
                    // the remaining width so entries never crowd each other on a
                    // screen that is wide enough to fit them unwrapped.
                    LinearLayout.LayoutParams cellParams =
                            new LinearLayout.LayoutParams(
                                    i == legendMarks.length - 1
                                            ? 0
                                            : LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    i == legendMarks.length - 1 ? 1f : 0f);
                    cellParams.rightMargin = i == legendMarks.length - 1
                            ? 0 : activity.dp(12);
                    legendRow.addView(cell, cellParams);
                }

                checkResults.addView(legendRow, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

                // Thin separator under the legend.
                LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(1));
                ruleParams.topMargin = activity.dp(6);
                ruleParams.bottomMargin = activity.dp(6);
                View legendRule = new View(activity);
                legendRule.setBackgroundColor(Color.rgb(38, 46, 58));
                checkResults.addView(legendRule, ruleParams);

                for (DeviceCapabilityCheck.Row row : rows) {
                    String mark;
                    int markColor;
                    int labelColor;

                    if (row.state == DeviceCapabilityCheck.AVAILABLE) {
                        mark = "✓";
                        markColor = tick;
                        labelColor = Color.rgb(210, 220, 232);
                    } else if (row.state == DeviceCapabilityCheck.UNTESTED) {
                        mark = "●";
                        markColor = dot;
                        labelColor = Color.rgb(210, 220, 232);
                    } else {
                        mark = "–";
                        markColor = dash;
                        labelColor = dash;
                    }

                    // Label on the left, mark pushed to the right edge.
                    LinearLayout lineRow = new LinearLayout(activity);
                    lineRow.setOrientation(LinearLayout.HORIZONTAL);
                    lineRow.setGravity(Gravity.CENTER_VERTICAL);
                    lineRow.setPadding(0, activity.dp(4), 0, activity.dp(4));

                    TextView name = new TextView(activity);
                    name.setText(row.label);
                    name.setTextSize(12);
                    name.setTextColor(labelColor);
                    name.setTypeface(android.graphics.Typeface.MONOSPACE);
                    lineRow.addView(name, new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                    TextView markView = new TextView(activity);
                    markView.setText(mark);
                    markView.setTextSize(13);
                    markView.setTextColor(markColor);
                    markView.setGravity(Gravity.END);
                    markView.setTypeface(android.graphics.Typeface.MONOSPACE);
                    lineRow.addView(markView, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));

                    checkResults.addView(lineRow, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                }

                // Explain the one state that isn't pass/fail.
                TextView hint = new TextView(activity);
                hint.setText("Frame pacing needs a game open — start a session "
                        + "in a game to confirm it.");
                hint.setTextSize(11);
                hint.setTextColor(Color.rgb(145, 158, 175));
                hint.setPadding(0, activity.dp(8), 0, 0);
                checkResults.addView(hint);
            });
        });

        activity.add(checkCard, checkButton, 0, 0);
        activity.add(checkCard, checkResults, 0, 0);

        LinearLayout appAppearanceCard = new LinearLayout(activity);
        appAppearanceCard.setOrientation(LinearLayout.VERTICAL);
        appAppearanceCard.setPadding(
                activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16));

        android.graphics.drawable.GradientDrawable appCardBg =
                new android.graphics.drawable.GradientDrawable();

        appCardBg.setColor(Color.rgb(25, 32, 42));
        appCardBg.setCornerRadius(activity.dp(16));
        appCardBg.setStroke(activity.dp(1), Color.rgb(43, 52, 65));

        appAppearanceCard.setBackground(activity.cardBg(16));

        activity.add(appAppearanceCard, activity.section("APP APPEARANCE"), 0, 8);

        TextView appColorNote = activity.text(
                "Customize the appearance of the ONYX interface.",
                12,
                false,
                Color.rgb(145, 158, 175)
        );

        activity.add(appAppearanceCard, appColorNote, 0, 12);

        LinearLayout appColorRow = new LinearLayout(activity);
        appColorRow.setOrientation(LinearLayout.HORIZONTAL);

        String[] appColorNames = {"ONYX", "EMERALD", "VIOLET", "CRIMSON", "CUSTOM"};
        int[] appColors = {
                Color.rgb(69, 168, 245),
                Color.rgb(101, 214, 165),
                Color.rgb(167, 139, 250),
                Color.rgb(241, 107, 117),
                0   // custom
        };

        for (int i = 0; i < appColorNames.length; i++) {
            final boolean isCustom = i == 4;
            final int presetColor = appColors[i];
            boolean isSelected = !isCustom && activity.getAppAccentColor() == presetColor;

            // Tile
            LinearLayout tile = new LinearLayout(activity);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(activity.dp(2), activity.dp(12), activity.dp(2), activity.dp(10));

            android.graphics.drawable.GradientDrawable tileBg =
                    new android.graphics.drawable.GradientDrawable();
            tileBg.setColor(Color.rgb(30, 38, 49));
            tileBg.setCornerRadius(activity.dp(12));
            tileBg.setStroke(activity.dp(isSelected ? 2 : 1),
                    isSelected ? activity.getAppAccentColor() : Color.rgb(55, 65, 80));
            tile.setBackground(tileBg);

            // Circle + check
            android.widget.FrameLayout swatchFrame = new android.widget.FrameLayout(activity);

            View circle = new View(activity);
            android.graphics.drawable.GradientDrawable circleBg =
                    new android.graphics.drawable.GradientDrawable();
            circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            if (isCustom) {
                circleBg.setGradientType(
                        android.graphics.drawable.GradientDrawable.SWEEP_GRADIENT);
                circleBg.setColors(new int[]{Color.RED, Color.YELLOW, Color.GREEN,
                        Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED});
            } else {
                circleBg.setColor(presetColor);
            }
            circle.setBackground(circleBg);
            swatchFrame.addView(circle,
                    new android.widget.FrameLayout.LayoutParams(activity.dp(30), activity.dp(30)));

            TextView check = new TextView(activity);
            check.setText("✓");
            check.setTextSize(16);
            check.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            check.setIncludeFontPadding(false);
            check.setTextColor(Color.WHITE);
            check.setGravity(Gravity.CENTER);
            check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            swatchFrame.addView(check,
                    new android.widget.FrameLayout.LayoutParams(activity.dp(30), activity.dp(30)));

            tile.addView(swatchFrame, new LinearLayout.LayoutParams(activity.dp(30), activity.dp(30)));

            // Label
            TextView name = activity.text(appColorNames[i], 9, true, Color.WHITE);
            name.setSingleLine(true);
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = activity.dp(8);
            tile.addView(name, nameParams);

            if (isCustom) {
                activity.customColorButtonBg = tileBg;
                activity.customColorCheck = check;
                activity.customRainbowDrawable = circleBg;
                tile.setOnClickListener(v -> activity.showCustomAppColorPicker());
            } else {
                activity.appColorOptions.add(tile);
                activity.appColorChecks.add(check);
                activity.appColorPillBackgrounds.add(tileBg);
                tile.setOnClickListener(v -> {
                    activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .putInt("app_accent_color", presetColor)
                            .apply();
                    activity.updateAppAccentColor();
                    activity.refreshOverlayColorRows();
                    activity.updateAppColorSelection();
                });
            }

            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tp.setMargins(i == 0 ? 0 : activity.dp(4), 0, i == 4 ? 0 : activity.dp(4), 0);
            appColorRow.addView(tile, tp);
        }

        activity.add(appAppearanceCard, appColorRow, 0, 12);

        android.widget.CheckBox allowAnimations = new android.widget.CheckBox(activity);
        allowAnimations.setText("Allow animations");
        allowAnimations.setTextColor(Color.WHITE);
        allowAnimations.setTextSize(13);
        allowAnimations.setGravity(Gravity.CENTER_VERTICAL);
        allowAnimations.setPadding(0, activity.dp(4), 0, activity.dp(4));
        allowAnimations.setButtonTintList(android.content.res.ColorStateList.valueOf(
                activity.getAppAccentColor()));
        activity.appAccentCheckBoxes.add(allowAnimations);
        allowAnimations.setChecked(activity.getSharedPreferences(
                "app_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("allow_animations", true));
        allowAnimations.setOnCheckedChangeListener((button, enabled) -> {
            activity.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                    .edit().putBoolean("allow_animations", enabled).apply();
            activity.setAnimationsEnabled(enabled);
        });
        activity.add(appAppearanceCard, allowAnimations, 0, 0);

        activity.add(content, appAppearanceCard, 0, 14);

        // OVERLAY APPEARANCE CARD
        LinearLayout overlayAppearanceCard = new LinearLayout(activity);
        overlayAppearanceCard.setOrientation(LinearLayout.VERTICAL);
        overlayAppearanceCard.setPadding(
                activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16));

        android.graphics.drawable.GradientDrawable overlayCardBg =
                new android.graphics.drawable.GradientDrawable();

        overlayCardBg.setColor(Color.rgb(25, 32, 42));
        overlayCardBg.setCornerRadius(activity.dp(16));
        overlayCardBg.setStroke(activity.dp(1), Color.rgb(43, 52, 65));

        overlayAppearanceCard.setBackground(activity.cardBg(16));

        activity.add(overlayAppearanceCard, activity.section("OVERLAY APPEARANCE"), 0, 8);

        TextView overlayColorNote = activity.text(
                "Customize the appearance of your floating performance overlay.",
                12,
                false,
                Color.rgb(145, 158, 175)
        );

        activity.add(overlayAppearanceCard, overlayColorNote, 0, 12);

        activity.overlayColorRefreshers.clear();

        activity.add(overlayAppearanceCard, activity.section("OVERLAY ACCENT COLOR"), 0, 8);
        activity.addOverlayColorRow(overlayAppearanceCard, "overlay_accent_color",
                Color.rgb(69, 168, 245), "Custom Accent Color", 8);
        activity.addOverlayDivider(overlayAppearanceCard);

        activity.add(overlayAppearanceCard, activity.section("OVERLAY TEXT COLOR"), 0, 8);
        activity.addOverlayColorRow(overlayAppearanceCard, "overlay_text_color",
                Color.WHITE, "Custom Text Color", 8);
        activity.addOverlayDivider(overlayAppearanceCard);

        activity.add(overlayAppearanceCard, activity.section("OVERLAY BACKGROUND COLOR"), 0, 8);
        activity.addOverlayColorRow(overlayAppearanceCard, "overlay_background_color",
                Color.rgb(20, 25, 32), "Custom Background Color", 0);

        activity.add(content, overlayAppearanceCard, 0, 14);

        // FLOATING OVERLAY SETTINGS follow all appearance/color controls.
        activity.add(content, activity.overlaySettingsCard, 0, 14);

        // ANALYSIS DISPLAY OPTIONS
        LinearLayout analysisDisplayCard = new LinearLayout(activity);
        analysisDisplayCard.setOrientation(LinearLayout.VERTICAL);
        analysisDisplayCard.setPadding(
                activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(12));
        analysisDisplayCard.setBackground(activity.cardBg(16));

        addInfoSectionHeader(analysisDisplayCard, "ANALYSIS DISPLAY",
                "Choose which visual indicators appear in Session Analysis and Comparison.");

        android.widget.CheckBox showThermalThrottling =
                new android.widget.CheckBox(activity);
        showThermalThrottling.setText("Show thermal throttling");
        showThermalThrottling.setTextColor(Color.WHITE);
        showThermalThrottling.setTextSize(12);
        showThermalThrottling.setGravity(Gravity.CENTER_VERTICAL);
        showThermalThrottling.setPadding(0, activity.dp(3), 0, activity.dp(3));
        showThermalThrottling.setButtonTintList(
                android.content.res.ColorStateList.valueOf(activity.getAppAccentColor()));
        activity.appAccentCheckBoxes.add(showThermalThrottling);
        showThermalThrottling.setChecked(activity.getSharedPreferences(
                "app_settings", android.content.Context.MODE_PRIVATE)
                .getBoolean("show_thermal_throttling", true));
        showThermalThrottling.setOnCheckedChangeListener((button, enabled) ->
                activity.getSharedPreferences(
                        "app_settings", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("show_thermal_throttling", enabled).apply());
        activity.add(analysisDisplayCard, showThermalThrottling, 0, 0);

        activity.add(content, analysisDisplayCard, 0, 14);

        // DATA COLLECTION OPTIONS
        LinearLayout dataCollectionCard = new LinearLayout(activity);
        dataCollectionCard.setOrientation(LinearLayout.VERTICAL);
        dataCollectionCard.setPadding(
                activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(12));
        dataCollectionCard.setBackground(activity.cardBg(16));

        addInfoSectionHeader(dataCollectionCard, "DATA COLLECTION",
                "Choose exactly which metrics are saved to session files and shown in Session Analysis and Session Comparison. "
                        + "Disabled metrics are not stored or collected. Existing sessions are unchanged.");

        addDataCollectionCategory(dataCollectionCard, "FPS & FRAME PACING",
                new String[][]{
                        {"FPS", SessionDataPreferences.FPS_VALUE},
                        {"Frame pacing summary (all metrics)", SessionDataPreferences.FRAME_PACING}
                }, SessionDataPreferences.FPS);
        addDataCollectionSpacer(dataCollectionCard);

        addDataCollectionCategory(dataCollectionCard, "BATTERY & POWER",
                new String[][]{
                        {"Level (%)", SessionDataPreferences.BATTERY_LEVEL},
                        {"Current", SessionDataPreferences.BATTERY_CURRENT},
                        {"Voltage", SessionDataPreferences.BATTERY_VOLTAGE},
                        {"Power", SessionDataPreferences.BATTERY_POWER},
                        {"Drain rate", SessionDataPreferences.BATTERY_DRAIN},
                        {"Temperature", SessionDataPreferences.BATTERY_TEMP}
                }, SessionDataPreferences.BATTERY);
        addDataCollectionSpacer(dataCollectionCard);

        addDataCollectionCategory(dataCollectionCard, "CPU",
                new String[][]{
                        {"Usage", SessionDataPreferences.CPU_USAGE},
                        {"Temperature", SessionDataPreferences.CPU_TEMP},
                        {"Frequency", SessionDataPreferences.CPU_FREQUENCY}
                }, SessionDataPreferences.CPU);
        addDataCollectionSpacer(dataCollectionCard);

        addDataCollectionCategory(dataCollectionCard, "GPU",
                new String[][]{
                        {"Usage", SessionDataPreferences.GPU_USAGE},
                        {"Temperature", SessionDataPreferences.GPU_TEMP},
                        {"Frequency", SessionDataPreferences.GPU_FREQUENCY}
                }, SessionDataPreferences.GPU);
        addDataCollectionSpacer(dataCollectionCard);

        addDataCollectionCategory(dataCollectionCard, "RAM",
                new String[][]{
                        {"Usage", SessionDataPreferences.RAM_USAGE}
                }, SessionDataPreferences.RAM);

        // Device check sits here: it is declared further up, so it can only be
        // attached after its contents exist.
        activity.add(content, checkCard, 0, 14);

        activity.add(content, dataCollectionCard, 0, 14);

        // Keep system/background controls after the requested appearance and
        // recording sections.
        activity.add(content, backgroundCard, 0, 14);
    }

    private void addInfoSectionHeader(
            LinearLayout parent, String title, String message) {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView sectionTitle = "DATA COLLECTION".equals(title)
                ? activity.text("DATA COLLECTION:", 15, true, Color.WHITE)
                : activity.section(title);
        header.addView(sectionTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView info = new TextView(activity);
        info.setText("ⓘ");
        info.setTextSize(20);
        info.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.setTextColor(Color.rgb(145, 158, 175));
        info.setGravity(Gravity.CENTER);
        info.setIncludeFontPadding(false);
        info.setContentDescription(title + " information");
        info.setBackground(null);
        info.setPadding(0, 0, 0, 0);
        info.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .create();
            dialog.setOnShowListener(ignored -> {
                dialog.getWindow().setBackgroundDrawable(
                        activity.cardBg(16));
                TextView messageView = dialog.findViewById(android.R.id.message);
                if (messageView != null) messageView.setTextColor(Color.WHITE);
                int titleId = activity.getResources().getIdentifier(
                        "alertTitle", "id", "android");
                TextView titleView = titleId != 0
                        ? dialog.findViewById(titleId) : null;
                if (titleView != null) titleView.setTextColor(Color.WHITE);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setTextColor(activity.getAppAccentColor());
            });
            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(activity.cardBg(16));
            }
        });

        header.addView(info, new LinearLayout.LayoutParams(
                activity.dp(32), activity.dp(24)));
        activity.add(parent, header, 0, 8);
    }

    private void addDataCollectionCategory(
            LinearLayout parent, String category, String[][] options, String legacyCategory) {
        activity.add(parent, activity.section(category), 0, 6);
        for (String[] option : options) {
            addDataCollectionOption(parent, option[0], option[1], legacyCategory);
        }
    }

    private void addDataCollectionSpacer(LinearLayout parent) {
        View spacer = new View(activity);
        parent.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(10)));
    }

    private void addDataCollectionOption(
            LinearLayout parent, String title, String key, String legacyCategory) {
        android.widget.CheckBox option = new android.widget.CheckBox(activity);
        option.setText(title);
        option.setTextColor(Color.WHITE);
        option.setTextSize(12);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setPadding(0, activity.dp(3), 0, activity.dp(3));
        option.setButtonTintList(
                android.content.res.ColorStateList.valueOf(activity.getAppAccentColor()));
        activity.appAccentCheckBoxes.add(option); // recolours live when the accent changes
        option.setChecked(SessionDataPreferences.FRAME_PACING.equals(key)
                ? SessionDataPreferences.framePacingEnabled(activity)
                : SessionDataPreferences.metricEnabledForSettings(
                        activity, key, legacyCategory));
        option.setOnCheckedChangeListener((button, checked) ->
                SessionDataPreferences.get(activity).edit().putBoolean(key, checked).apply());
        parent.addView(option, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (SessionDataPreferences.GPU_FREQUENCY.equals(key)) {
            TextView note = activity.text(
                    "Not available on every device. Run Device Check in Settings to see "
                            + "whether this phone exposes it.",
                    11, false, Color.rgb(145, 158, 175));
            note.setPadding(activity.dp(32), 0, activity.dp(8), activity.dp(6));
            parent.addView(note, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        if (SessionDataPreferences.FRAME_PACING.equals(key)) {
            TextView note = activity.text(
                    "Needs SurfaceFlinger frame timestamps, which most games provide. "
                            + "Without them FPS still works, but frame time, hitches and "
                            + "jank are unavailable.",
                    11, false, Color.rgb(145, 158, 175));
            note.setPadding(activity.dp(32), 0, activity.dp(8), activity.dp(6));
            parent.addView(note, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }
}
