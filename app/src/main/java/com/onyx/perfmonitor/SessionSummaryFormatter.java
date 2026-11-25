/*
 * Copyright (C) 2026 Klod9s
 *
 * This file is part of ONYX Performance Monitor.
 *
 * ONYX Performance Monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ONYX Performance Monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ONYX Performance Monitor. If not, see <https://www.gnu.org/licenses/>.
 */

package com.onyx.perfmonitor;

import java.util.List;
import java.util.Locale;

/**
 * Creates the human-readable summary shown for a recorded session.
 */
final class SessionSummaryFormatter {

    private SessionSummaryFormatter() {
    }

    static String build(List<Sample> samples) {
        return build(samples, null);
    }

    static String build(List<Sample> samples, android.content.Context context) {
        if (samples.isEmpty()) return "No valid readings collected.";

        long duration = samples.get(samples.size() - 1).timeMs;
        if (context != null && !SessionDataPreferences.metricEnabledForSettings(
                context, SessionDataPreferences.BATTERY_LEVEL, SessionDataPreferences.BATTERY)) {
            return String.format(Locale.US,
                    "Run time: %.1f minutes\nBattery level: not collected",
                    duration / 60000.0);
        }

        int startBattery = samples.get(0).battery;
        int endBattery = samples.get(samples.size() - 1).battery;
        return String.format(Locale.US,
                "Run time: %.1f minutes\n" +
                        "Battery level: %d%% → %d%% (%d%% change)",
                duration / 60000.0,
                startBattery,
                endBattery,
                endBattery - startBattery);
    }
}
