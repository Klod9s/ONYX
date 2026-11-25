package com.onyx.perfmonitor;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

/** Keeps the Live Readings power-state pill in sync with the app accent. */
final class PowerStateBadgeStyle {
    private static final int DISCHARGING_COLOR = Color.rgb(255, 126, 112);

    private static final class State {
        final GradientDrawable background = new GradientDrawable();
        boolean charging;
        int color;
    }

    private PowerStateBadgeStyle() {
    }

    static void apply(TextView badge, boolean charging, int accentColor) {
        if (badge == null) return;

        Object tag = badge.getTag();
        State state;
        if (tag instanceof State) {
            state = (State) tag;
        } else {
            state = new State();
            state.background.setShape(GradientDrawable.RECTANGLE);
            state.background.setCornerRadius(dp(badge, 6));
            badge.setTag(state);
            badge.setBackground(state.background);
        }

        state.charging = charging;
        int color = charging ? accentColor : DISCHARGING_COLOR;
        if (state.color != color) {
            state.color = color;
            state.background.setColor(withAlpha(color, 34));
            state.background.setStroke(dp(badge, 1), withAlpha(color, 115));
            badge.setTextColor(color);
        }
    }

    static void refresh(TextView badge, int accentColor) {
        if (badge == null || !(badge.getTag() instanceof State)) return;
        State state = (State) badge.getTag();
        apply(badge, state.charging, accentColor);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int dp(TextView view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }
}
