package com.onyx.perfmonitor;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import java.util.ArrayList;

/** Creates card backgrounds and keeps their borders synchronized with the app accent. */
final class CardStyleManager {
    private final MainActivity activity;
    private final ArrayList<GradientDrawable> cardBackgrounds = new ArrayList<>();
    private final ArrayList<GlassStyle.GlassDrawable> glassBackgrounds = new ArrayList<>();

    CardStyleManager(MainActivity activity) {
        this.activity = activity;
    }

    private int cardStrokeColor() {
        int accent = activity.getAppAccentColor();
        return Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent));
    }

    GradientDrawable createCardBackground(int radiusDp) {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(22, 32, 48), Color.rgb(15, 22, 34)});
        background.setCornerRadius(activity.dp(radiusDp));
        background.setStroke(activity.dp(1), cardStrokeColor());
        cardBackgrounds.add(background);
        return background;
    }

    /** Frosted-glass background used on the Monitor page. */
    GlassStyle.GlassDrawable createGlassBackground(int radiusDp) {
        GlassStyle.GlassDrawable glass = new GlassStyle.GlassDrawable(
                activity.dp(radiusDp), activity.dp(1), activity.getAppAccentColor());
        glassBackgrounds.add(glass);
        return glass;
    }

    /** Plain dark button background (no glass effects). */
    GlassStyle.GlassDrawable createFlatBackground(int radiusDp) {
        GlassStyle.GlassDrawable d = new GlassStyle.GlassDrawable(
                activity.dp(radiusDp), activity.dp(1), activity.getAppAccentColor()).flat();
        return d;
    }

    void refreshStrokes() {
        int strokeWidth = activity.dp(1);
        int color = cardStrokeColor();
        for (GradientDrawable background : cardBackgrounds) {
            background.setStroke(strokeWidth, color);
        }
        int accent = activity.getAppAccentColor();
        for (GlassStyle.GlassDrawable glass : glassBackgrounds) {
            glass.setAccent(accent);
        }
    }
}
