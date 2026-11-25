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
            if (running) {
                stopSession();
            } else {
                startSession();
            }
        }
    
        private void startSession() {
            if (running) return;
    
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
            if (!running) return;
    
            activity.running = false;
            getSharedPreferences("session_state", MODE_PRIVATE)
                    .edit()
                    .putBoolean("recording", false)
                    .apply();
            sessionId++;
    
            startButton.setText("START SESSION");
    
            int accentColor = activity.getAppAccentColor();
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(accentColor);
            bg.setCornerRadius(dp(12));
            bg.setStroke(dp(1), Color.argb(
                    180,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor)));
            startButton.setBackground(bg);
    
            String result = activity.makeSummary();
    
            summary.setText(result);
            refreshStatusPill();
    
            if (!activity.samples.isEmpty()) {
                activity.saveSession(result);
            }
        }
}
