package com.onyx.perfmonitor;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Builds the ONYX/CUSTOM color selector rows used by overlay settings. */
final class OverlayColorRowBuilder {
    private final MainActivity activity;

    OverlayColorRowBuilder(MainActivity activity) {
        this.activity = activity;
    }

    private Drawable createRainbowDrawable(
            java.util.function.BooleanSupplier customSelected) {

        Drawable drawable = new Drawable() {
            private final Paint paint =
                    new Paint(Paint.ANTI_ALIAS_FLAG);
            private final int[] colors = {
                    Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                    Color.BLUE, Color.MAGENTA, Color.RED
            };

            @Override
            public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                float stroke = activity.dp(4);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(stroke);
                paint.setShader(new SweepGradient(
                        bounds.exactCenterX(), bounds.exactCenterY(),
                        colors, null));

                float inset = stroke / 2f;
                canvas.drawOval(bounds.left + inset, bounds.top + inset,
                        bounds.right - inset, bounds.bottom - inset, paint);

                if (customSelected.getAsBoolean()) {
                    paint.setShader(null);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.WHITE);
                    paint.setTextSize(activity.dp(12));
                    paint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText("✓", bounds.exactCenterX(),
                            bounds.exactCenterY() + activity.dp(4), paint);
                }
            }

            @Override public int getIntrinsicWidth() { return activity.dp(20); }
            @Override public int getIntrinsicHeight() { return activity.dp(20); }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(
                    android.graphics.ColorFilter filter) {
                paint.setColorFilter(filter);
            }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };

        drawable.setBounds(0, 0, activity.dp(20), activity.dp(20));
        return drawable;
    }

    void add(LinearLayout card, String prefKey, int onyxColor,
             String dialogTitle, int bottomMargin) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout onyx = new LinearLayout(activity);
        onyx.setOrientation(LinearLayout.HORIZONTAL);
        onyx.setGravity(Gravity.CENTER);
        onyx.setPadding(activity.dp(9), activity.dp(5),
                activity.dp(9), activity.dp(5));

        android.graphics.drawable.GradientDrawable onyxBg =
                new android.graphics.drawable.GradientDrawable();
        onyxBg.setColor(Color.rgb(30, 38, 49));
        onyxBg.setCornerRadius(activity.dp(50));
        onyx.setBackground(onyxBg);

        android.widget.FrameLayout swatch = createSwatchWithCheck(onyxColor);
        TextView check = (TextView) swatch.getChildAt(1);
        onyx.addView(swatch, new LinearLayout.LayoutParams(
                activity.dp(22), activity.dp(22)));

        TextView name = activity.text("OCEAN", 9, true, Color.WHITE);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(activity.dp(5), 0, 0, 0);
        onyx.addView(name, nameParams);

        onyx.setOnClickListener(v -> {
            activity.getPrefs().edit().putInt(prefKey, onyxColor).apply();
            activity.refreshOverlayColorRows();
        });

        LinearLayout.LayoutParams onyxParams = new LinearLayout.LayoutParams(
                0, activity.dp(36), 1f);
        onyxParams.setMargins(0, 0, activity.dp(4), 0);
        row.addView(onyx, onyxParams);

        LinearLayout custom = new LinearLayout(activity);
        custom.setOrientation(LinearLayout.HORIZONTAL);
        custom.setGravity(Gravity.CENTER);
        custom.setPadding(activity.dp(9), activity.dp(5),
                activity.dp(9), activity.dp(5));

        android.graphics.drawable.GradientDrawable customBg =
                new android.graphics.drawable.GradientDrawable();
        customBg.setColor(Color.rgb(30, 38, 49));
        customBg.setCornerRadius(activity.dp(50));
        custom.setBackground(customBg);

        Drawable rainbow = createRainbowDrawable(
                () -> activity.getPrefs().getInt(prefKey, onyxColor) != onyxColor);
        android.widget.ImageView rainbowView = new android.widget.ImageView(activity);
        rainbowView.setImageDrawable(rainbow);
        custom.addView(rainbowView, new LinearLayout.LayoutParams(
                activity.dp(20), activity.dp(20)));

        TextView customName = activity.text("CUSTOM", 9, true, Color.WHITE);
        LinearLayout.LayoutParams customNameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        customNameParams.setMargins(activity.dp(6), 0, 0, 0);
        custom.addView(customName, customNameParams);

        custom.setOnClickListener(v -> activity.showCustomColorPicker(
                dialogTitle,
                activity.getPrefs().getInt(prefKey, onyxColor),
                color -> {
                    activity.getPrefs().edit().putInt(prefKey, color).apply();
                    activity.refreshOverlayColorRows();
                }));

        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(
                0, activity.dp(36), 1f);
        customParams.setMargins(activity.dp(4), 0, 0, 0);
        row.addView(custom, customParams);

        Runnable refresh = () -> {
            boolean customSelected =
                    activity.getPrefs().getInt(prefKey, onyxColor) != onyxColor;
            int accent = activity.getAppAccentColor();
            int idle = Color.rgb(55, 65, 80);

            onyxBg.setStroke(activity.dp(1), customSelected ? idle : accent);
            check.setVisibility(customSelected ? View.GONE : View.VISIBLE);
            customBg.setStroke(activity.dp(1), customSelected ? accent : idle);

            onyx.invalidate();
            custom.invalidate();
            rainbow.invalidateSelf();
        };
        activity.overlayColorRefreshers.add(refresh);
        refresh.run();

        activity.add(card, row, 0, bottomMargin);
    }

    private android.widget.FrameLayout createSwatchWithCheck(int color) {
        android.widget.FrameLayout frame = new android.widget.FrameLayout(activity);

        View circle = new View(activity);
        android.graphics.drawable.GradientDrawable circleBg =
                new android.graphics.drawable.GradientDrawable();
        circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circleBg.setColor(color);
        circle.setBackground(circleBg);

        frame.addView(circle, new android.widget.FrameLayout.LayoutParams(
                activity.dp(22), activity.dp(22)));

        TextView check = new TextView(activity);
        check.setText("✓");
        check.setTextSize(12);
        check.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        check.setIncludeFontPadding(false);
        check.setGravity(Gravity.CENTER);
        check.setTextColor(
                androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.6
                        ? Color.BLACK : Color.WHITE);
        check.setVisibility(View.GONE);

        frame.addView(check, new android.widget.FrameLayout.LayoutParams(
                activity.dp(22), activity.dp(22)));
        return frame;
    }
}
