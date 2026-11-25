package com.onyx.perfmonitor;

import android.graphics.Color;

/**
 * Owns the start/stop lifecycle for a monitoring session.
 * MainActivity remains responsible for rendering the associated UI.
 */
final class SessionController {
    private final MainActivity activity;

    SessionController(MainActivity activity) {
        this.activity = activity;
    }

        void toggle() {
            if (activity.running) {
                stopSession();
            } else {
                startSession();
            }
        }
    
        private void startSession() {
            if (activity.running) return;
    
            activity.samples.clear();
            activity.csv.setLength(0);
    
            SessionCsvFormatter.appendHeader(activity.csv);
    
            activity.monitoringEngine.resetCpuBaseline();
            activity.monitoringEngine.resetSessionClock();
            activity.sessionId++;
            activity.running = true;
    
            activity.getSharedPreferences("session_state", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("recording", true)
                    .apply();
    
            activity.startButton.setText("STOP SESSION");
    
    
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.rgb(170, 55, 65));
            bg.setCornerRadius(activity.dp(12));
            bg.setStroke(activity.dp(1), Color.rgb(205, 85, 95));
            activity.startButton.setBackground(bg);
    
            activity.summary.setText("Session in progress…");
            activity.refreshStatusPill();
    
        }
    
        private void stopSession() {
            if (!activity.running) return;
    
            activity.running = false;
            activity.getSharedPreferences("session_state", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("recording", false)
                    .apply();
            activity.sessionId++;
    
            activity.startButton.setText("START SESSION");
    
            int accentColor = activity.getAppAccentColor();
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(accentColor);
            bg.setCornerRadius(activity.dp(12));
            bg.setStroke(activity.dp(1), Color.argb(
                    180,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor)));
            activity.startButton.setBackground(bg);
    
            String result = activity.makeSummary();
    
            activity.summary.setText(result);
            activity.refreshStatusPill();
    
            if (!activity.samples.isEmpty()) {
                activity.saveSession(result);
            }
        }
}
