package com.onyx.perfmonitor;

import android.content.Context;
import android.graphics.Color;

public final class AppColors {
    private AppColors() {}

    public static int accent(Context context) {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getInt("app_accent_color", Color.rgb(101, 214, 165));
    }

    /** B's colour: accent hue rotated by 240 degrees, kept vivid. */
    public static int compareB(Context context) {
        float[] hsv = new float[3];
        Color.colorToHSV(accent(context), hsv);
        hsv[0] = (hsv[0] + 240f) % 360f;
        hsv[1] = Math.max(0.70f, hsv[1]);
        hsv[2] = Math.max(0.95f, hsv[2]);
        return Color.HSVToColor(hsv);
    }
}