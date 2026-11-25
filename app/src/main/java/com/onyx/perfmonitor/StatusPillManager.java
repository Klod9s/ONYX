package com.onyx.perfmonitor;

import android.graphics.Color;

/** Owns the monitor status pill text and indicator color. */
final class StatusPillManager {
    private final MainActivity activity;

    StatusPillManager(MainActivity activity) {
        this.activity = activity;
    }

    void setStatus(String text, boolean error) {
        if (activity.status == null) return;
        activity.status.setText(text);
        if (activity.statusDotBg != null) {
            int dotColor;
            if (error) {
                dotColor = Color.rgb(240, 104, 122); // disconnected
            } else if (activity.running) {
                dotColor = AppColors.compareB(activity);
            } else {
                dotColor = activity.getAppAccentColor();
            }
            activity.statusDotBg.setColor(dotColor);
        }
    }

    void refresh() {
        if (activity.cachedMonitoringMode.isEmpty()) {
            setStatus("CHECKING…", false);
        } else {
            setStatus(activity.cachedMonitoringMode
                    + (activity.running ? " · RECORDING" : " · READY"), false);
        }
    }
}
