package com.onyx.perfmonitor;

import android.graphics.Color;
import android.graphics.Typeface;
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

        String[] appColorNames = {"ONYX", "OCEAN", "VIOLET", "CRIMSON", "CUSTOM"};
        int[] appColors = {
                Color.rgb(101, 214, 165),
                Color.rgb(69, 168, 245),
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
                Color.rgb(55, 210, 130), "Custom Accent Color", 8);
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

        // DATA COLLECTION OPTIONS
        LinearLayout dataCollectionCard = new LinearLayout(activity);
        dataCollectionCard.setOrientation(LinearLayout.VERTICAL);
        dataCollectionCard.setPadding(
                activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(12));
        dataCollectionCard.setBackground(activity.cardBg(16));

        activity.add(dataCollectionCard, activity.section("DATA COLLECTION"), 0, 8);
        TextView dataCollectionNote = activity.text(
                "Choose exactly which metrics are saved to session files and shown in Session Analysis and Session Comparison. "
                        + "Disabled metrics are not stored. Existing sessions are unchanged.",
                12, false, Color.rgb(145, 158, 175));
        activity.add(dataCollectionCard, dataCollectionNote, 0, 12);

        addDataCollectionCategory(dataCollectionCard, "FPS & FRAME PACING",
                new String[][]{
                        {"FPS", SessionDataPreferences.FPS_VALUE},
                        {"Average frame time", SessionDataPreferences.FRAME_AVERAGE},
                        {"Frame time variation", SessionDataPreferences.FRAME_VARIATION},
                        {"Stutters", SessionDataPreferences.FRAME_STUTTERS},
                        {"Frame intervals", SessionDataPreferences.FRAME_INTERVALS}
                }, SessionDataPreferences.FPS);
        addDataCollectionSpacer(dataCollectionCard);

        addDataCollectionCategory(dataCollectionCard, "BATTERY & POWER",
                new String[][]{
                        {"Level", SessionDataPreferences.BATTERY_LEVEL},
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

        activity.add(content, dataCollectionCard, 0, 14);

        // Keep system/background controls after the requested appearance and
        // recording sections.
        activity.add(content, backgroundCard, 0, 14);
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
        option.setChecked(SessionDataPreferences.metricEnabledForSettings(
                activity, key, legacyCategory));
        option.setOnCheckedChangeListener((button, checked) ->
                SessionDataPreferences.get(activity).edit().putBoolean(key, checked).apply());
        parent.addView(option, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
    }
}
