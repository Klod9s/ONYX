package com.onyx.perfmonitor;

import android.os.Handler;

/**
 * Coordinates background sampling and delivery of samples to the Activity.
 * The Activity remains the owner of session state and UI rendering.
 */
final class SamplingController {
    private final MainActivity activity;
    private boolean busy;

    private final Runnable sampler = new Runnable() {
        @Override
        public void run() {
            if (busy) return;
            busy = true;

            final int currentSessionId = activity.sessionId;
            final boolean recording = activity.running
                    && !activity.isOverlaySession();

            activity.worker.execute(() -> {
                String detected = "ANDROID";
                Sample taken = null;
                try {
                    taken = activity.monitoringEngine.sample(recording);
                    detected = activity.monitoringEngine.getMonitoringMode();
                    activity.cachedMonitoringMode = detected;

                    if ("SHIZUKU".equals(detected)
                            && activity.shizukuService == null
                            && !activity.shizukuBindRequested) {
                        activity.runOnUiThread(() -> {
                            if (activity.shizukuService == null
                                    && !activity.shizukuBindRequested) {
                                activity.shizukuBindRequested = true;
                                activity.startShizukuService();
                            }
                        });
                    }
                } catch (Throwable ignored) {
                }

                final String mode = detected;
                final Sample sample = taken;

                activity.runOnUiThread(() -> {
                    busy = false;

                    if (currentSessionId != activity.sessionId) {
                        schedule();
                        return;
                    }
                    if (activity.isAppForeground) {
                        activity.setStatus(mode
                                + (activity.running ? " · RECORDING" : " · READY"), false);
                    }
                    if (sample != null) {
                        activity.overlayMetricsStore.publish(
                                sample.fps,
                                sample.temperatureC,
                                sample.averageFrameTimeMs,
                                sample.cpu,
                                sample.gpuLoadPercent
                        );
                        if (activity.running && !activity.isOverlaySession()) {
                            if (!activity.samples.isEmpty()) {
                                Sample previous = activity.samples.get(
                                        activity.samples.size() - 1);

                                int drop = previous.battery - sample.battery;
                                long elapsed = sample.timeMs - previous.timeMs;

                                boolean previousBatteryValid =
                                        previous.battery >= 0 && previous.battery <= 100;
                                boolean currentBatteryValid =
                                        sample.battery >= 0 && sample.battery <= 100;

                                if (previousBatteryValid && currentBatteryValid
                                        && drop > 0 && elapsed > 0) {
                                    sample.secondsPerPercent =
                                            (elapsed / 1000.0) / drop;
                                }
                            }

                            activity.samples.add(sample);
                            SessionCsvFormatter.appendSample(activity.csv, sample);
                        }

                        if (activity.isAppForeground) {
                            activity.render(sample);
                        }
                    }

                    schedule();
                });
            });
        }
    };

    SamplingController(MainActivity activity) {
        this.activity = activity;
    }

    private boolean isNeeded() {
        return activity.isAppForeground || activity.running;
    }

    void schedule() {
        if (isNeeded()) {
            activity.handler.postDelayed(sampler, 500);
        } else {
            activity.monitoringEngine.resetCpuBaseline();
        }
    }

    void wake() {
        activity.handler.removeCallbacks(sampler);
        sampler.run();
    }

    void stop() {
        activity.handler.removeCallbacks(sampler);
    }
}
