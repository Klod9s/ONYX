package com.onyx.perfmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Turns the raw battery current into mA with one convention on every phone:
 * negative = discharging, positive = charging.
 * Learns the phone's current sign convention and remembers it.
 */
public final class BatteryCurrent {

    private static final String PREFS = "battery_calibration";
    private static final String KEY_INVERTED = "current_inverted";

    private static final double MIN_LEARN_MA = 150;   // ignore tiny, noisy readings
    private static final int VOTES_NEEDED = 4;        // consecutive samples (about 2 s)

    private final SharedPreferences prefs;
    private final boolean rawInMilliamps;
    private boolean inverted;   // true = this phone reports discharging as positive
    private int lastVote = -1;
    private int voteCount = 0;

    public BatteryCurrent(Context context) {
        Context appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        // Honor's BatteryManager reports CURRENT_NOW in mA rather than the
        // standard microamp unit. Preserve the vendor-specific handling used
        // by the original implementation instead of shrinking it by 1000x.
        rawInMilliamps = Build.MANUFACTURER != null
                && Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT)
                .contains("honor");

        inverted = prefs.getBoolean(KEY_INVERTED, false);
    }

    /** raw = BATTERY_PROPERTY_CURRENT_NOW or sysfs current_now. Returns mA. */
    public synchronized double toMilliamps(long raw, boolean unplugged) {
        double ma = rawInMilliamps ? (double) raw : raw / 1000.0;

        // Sign: unplugged means the battery is really discharging.
        // Positive reading while unplugged = this phone reports it backwards.
        if (unplugged && Math.abs(ma) >= MIN_LEARN_MA) {
            int vote = ma > 0 ? 1 : 0;

            if (vote == lastVote) {
                voteCount++;
            } else {
                lastVote = vote;
                voteCount = 1;
            }

            if (voteCount >= VOTES_NEEDED && (vote == 1) != inverted) {
                inverted = vote == 1;
                prefs.edit().putBoolean(KEY_INVERTED, inverted).apply();
            }
        } else {
            // A plugged-in or low-current sample breaks the consecutive vote streak.
            lastVote = -1;
            voteCount = 0;
        }

        return inverted ? -ma : ma;
    }
}
