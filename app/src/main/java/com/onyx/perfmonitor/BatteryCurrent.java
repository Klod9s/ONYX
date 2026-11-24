package com.onyx.perfmonitor;

import android.content.Context;
import android.content.SharedPreferences;

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
    private boolean inverted;   // true = this phone reports discharging as positive
    private int lastVote = -1;
    private int voteCount = 0;

    public BatteryCurrent(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        inverted = prefs.getBoolean(KEY_INVERTED, false);
    }

    /** raw = BATTERY_PROPERTY_CURRENT_NOW or sysfs current_now. Returns mA. */
    public synchronized double toMilliamps(long raw, boolean unplugged) {


        double ma = raw / 1000.0;

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
        }

        return inverted ? -ma : ma;
    }
}