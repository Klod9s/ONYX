package com.onyx.perfmonitor;


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
        if (activity.isOverlaySession()) {
            android.content.SharedPreferences overlayPrefs =
                    activity.getSharedPreferences("overlay_settings",
                            android.content.Context.MODE_PRIVATE);
            if (!overlayPrefs.getBoolean("overlay_enabled", false)) {
                // The service may already have been stopped. Clear stale state
                // locally instead of starting it again just to send STOP.
                activity.getSharedPreferences("session_state",
                        android.content.Context.MODE_PRIVATE).edit()
                        .putBoolean("recording", false)
                        .putString("owner", FloatingFpsService.SESSION_OWNER_NONE)
                        .apply();
                syncOverlaySessionState();
                return;
            }
            android.content.Intent stop = new android.content.Intent(
                    activity, FloatingFpsService.class);
            stop.setAction(FloatingFpsService.ACTION_STOP_RECORDING);
            activity.startService(stop);
        } else if (activity.running) {
            stopSession();
        } else {
            startSession();
        }
    }

    void restoreOverlaySession() {
        activity.running = true;
        activity.overlaySessionUi = true;
        activity.sessionId++;
        updateRecordingButton(true);
        activity.summary.setText("Session in progress… (floating overlay)");
        activity.refreshStatusPill();
    }

    void syncOverlaySessionState() {
        if (activity.isOverlaySession()) {
            if (!activity.overlaySessionUi) {
                restoreOverlaySession();
            }
            return;
        }

        if (!activity.overlaySessionUi) return;

        activity.running = false;
        activity.overlaySessionUi = false;
        activity.sessionId++;
        activity.samples.clear();
        activity.csv.setLength(0);
        updateRecordingButton(false);
        activity.summary.setText("Session saved from floating overlay.");
        activity.refreshStatusPill();
        activity.loadHistory(true);
    }

    private void updateRecordingButton(boolean recording) {
        activity.startButton.setText(recording
                ? "STOP SESSION" : "START SESSION");

        activity.startButton.setBackground(recording
                ? GlassStyle.recordingButton(activity.dp(14), activity.dp(1))
                : GlassStyle.primaryButton(activity.getAppAccentColor(),
                        activity.dp(14), activity.dp(1)));
    }
    
        void startSession() {
            if (activity.running) return;
    
            activity.samples.clear();
            activity.csv.setLength(0);
    
            SessionCsvFormatter.appendHeader(activity.csv);
    
            activity.monitoringEngine.resetCpuBaseline();
            activity.monitoringEngine.resetSessionClock();
            if (SessionDataPreferences.framePacingEnabled(activity)) {
                activity.monitoringEngine.beginFrameCapture();
            }
            activity.sessionId++;
            activity.running = true;
            activity.overlaySessionUi = false;

            activity.getSharedPreferences("session_state", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("recording", true)
                    .putString("owner", FloatingFpsService.SESSION_OWNER_APP)
                    .apply();

            updateRecordingButton(true);
    
            activity.summary.setText("Session in progress…");
            activity.refreshStatusPill();
    
        }
    
        void stopSession() {
            if (!activity.running) return;
    
            activity.running = false;
            activity.monitoringEngine.endFrameCapture();
            activity.getSharedPreferences("session_state", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("recording", false)
                    .putString("owner", FloatingFpsService.SESSION_OWNER_NONE)
                    .apply();
            activity.sessionId++;
    
            updateRecordingButton(false);
    
            String result = activity.makeSummary();
    
            activity.summary.setText(result);
            activity.refreshStatusPill();
    
            if (!activity.samples.isEmpty()) {
                activity.saveSession(result);
            }
        }
}
