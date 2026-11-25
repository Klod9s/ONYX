package com.onyx.perfmonitor;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

/** Applies the selected app accent to registered UI elements. */
final class AppAccentManager {
    private final MainActivity activity;

    AppAccentManager(MainActivity activity) {
        this.activity = activity;
    }

    void update() {
        int color = activity.getAppAccentColor();
        if (activity.heroBanner != null) {
            activity.heroBanner.refreshAccentColor();
        }
        if (activity.brand != null) {
            activity.brand.refreshAccentColor();
        }

        // Navigation
        for (int i = 0; i < activity.navLabels.length; i++) {
            activity.navLabels[i].setTextColor(
                    i == activity.selectedTab ? color : Color.rgb(145, 158, 175));
            activity.navIcons[i].setColorFilter(
                    i == activity.selectedTab ? color : Color.rgb(145, 158, 175));
            activity.navIndicators[i].setBackgroundColor(
                    i == activity.selectedTab ? color : Color.TRANSPARENT);
        }

        // Section headings
        for (TextView heading : activity.appAccentTextViews) {
            heading.setTextColor(color);
            heading.setCompoundDrawableTintList(
                    android.content.res.ColorStateList.valueOf(color));
        }

        // Checkboxes
        for (CheckBox box : activity.appAccentCheckBoxes) {
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(color));
        }
        // Style picker radio buttons
        for (android.widget.RadioButton rb : activity.appAccentRadioButtons) {
            rb.setButtonTintList(android.content.res.ColorStateList.valueOf(color));
        }
        // View Analysis buttons
        for (Button button : activity.appAccentButtons) {
            button.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));
        }

        // Overlay sliders
        for (android.widget.SeekBar slider : activity.appAccentSeekBars) {
            slider.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
            slider.setThumbTintList(android.content.res.ColorStateList.valueOf(color));
        }

        // Overlay color rows (ONYX / CUSTOM highlights)
        activity.refreshOverlayColorRows();

        // Card borders follow the app accent
        activity.refreshCardStrokes();
        for (Runnable refresher : activity.aboutRefreshers) {
            refresher.run();
        }
        activity.updateHeaderLine();

        // ONYX glow
        if (activity.brand != null) {
            activity.brand.refreshAccentColor();
        }

        // Status text
        if (activity.status != null) {
            activity.refreshStatusPill();
        }

        // Start Session button
        if (activity.startButton != null && !activity.running) {
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(activity.dp(12));
            background.setColor(color);
            background.setStroke(activity.dp(1), Color.argb(
                    180, Color.red(color), Color.green(color), Color.blue(color)));
            activity.startButton.setBackground(background);
        }
        if (activity.customColorButtonBg != null) {
            activity.customColorButtonBg.setStroke(activity.dp(1), color);
        }
    }
}
