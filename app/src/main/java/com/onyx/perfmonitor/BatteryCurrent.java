
package com.onyx.perfmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Converts battery current to mA using one convention:
 * negative = discharging, positive = charging.
 * BatteryManager and sysfs maintain independent sign calibration.
 */
public final class BatteryCurrent {

    private static final String PREFS = "battery_calibration";
    private static final String KEY_BATTERY_MANAGER_INVERTED =
            "battery_manager_current_inverted";
    private static final String KEY_SYSFS_INVERTED =
            "sysfs_current_inverted";

    private static final double MIN_LEARN_MA = 150;
    private static final int VOTES_NEEDED = 4;

    private final SharedPreferences prefs;
    private final boolean rawInMilliamps;

    private final SignCalibration batteryManagerCalibration;
    private final SignCalibration sysfsCalibration;

    public BatteryCurrent(Context context) {
        Context appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        rawInMilliamps = Build.MANUFACTURER != null
                && Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT)
                .contains("honor");

        // Migrate the previous shared calibration to both sources.
        boolean legacyInverted = prefs.getBoolean("current_inverted", false);

        boolean hasBatteryManagerCalibration =
                prefs.contains(KEY_BATTERY_MANAGER_INVERTED);
        boolean hasSysfsCalibration =
                prefs.contains(KEY_SYSFS_INVERTED);

        boolean batteryManagerInverted = prefs.getBoolean(
                KEY_BATTERY_MANAGER_INVERTED, legacyInverted);
        boolean sysfsInverted = prefs.getBoolean(
                KEY_SYSFS_INVERTED, legacyInverted);

        batteryManagerCalibration = new SignCalibration(batteryManagerInverted);
        sysfsCalibration = new SignCalibration(sysfsInverted);

        if (!hasBatteryManagerCalibration || !hasSysfsCalibration) {
            prefs.edit()
                    .putBoolean(KEY_BATTERY_MANAGER_INVERTED, batteryManagerInverted)
                    .putBoolean(KEY_SYSFS_INVERTED, sysfsInverted)
                    .apply();
        }
    }

    /** raw = BATTERY_PROPERTY_CURRENT_NOW. Returns mA. */
    public synchronized double toMilliamps(long raw, boolean unplugged) {
        double ma = rawInMilliamps ? (double) raw : raw / 1000.0;
        boolean before = batteryManagerCalibration.isInverted();

        batteryManagerCalibration.addSample(ma, unplugged);

        if (before != batteryManagerCalibration.isInverted()) {
            prefs.edit().putBoolean(
                    KEY_BATTERY_MANAGER_INVERTED,
                    batteryManagerCalibration.isInverted()).apply();
        }

        return batteryManagerCalibration.isInverted() ? -ma : ma;
    }

    /** Linux power_supply current_now is reported in microamps. */
    public synchronized double toMilliampsFromSysfs(
            long rawMicroamps, boolean unplugged) {
        double ma = sysfsMicroampsToMilliamps(rawMicroamps);
        boolean before = sysfsCalibration.isInverted();

        sysfsCalibration.addSample(ma, unplugged);

        if (before != sysfsCalibration.isInverted()) {
            prefs.edit().putBoolean(
                    KEY_SYSFS_INVERTED,
                    sysfsCalibration.isInverted()).apply();
        }

        return sysfsCalibration.isInverted() ? -ma : ma;
    }

    static double sysfsMicroampsToMilliamps(long rawMicroamps) {
        return rawMicroamps / 1000.0;
    }

    static final class SignCalibration {
        private boolean inverted;
        private int lastVote = -1;
        private int voteCount;

        SignCalibration(boolean initiallyInverted) {
            inverted = initiallyInverted;
        }

        boolean isInverted() {
            return inverted;
        }

        void addSample(double ma, boolean unplugged) {
            if (unplugged && Double.isFinite(ma)
                    && Math.abs(ma) >= MIN_LEARN_MA) {
                int vote = ma > 0 ? 1 : 0;

                if (vote == lastVote) {
                    voteCount++;
                } else {
                    lastVote = vote;
                    voteCount = 1;
                }

                if (voteCount >= VOTES_NEEDED) {
                    inverted = vote == 1;
                }
            } else {
                lastVote = -1;
                voteCount = 0;
            }
        }
    }
}
