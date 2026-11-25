package com.onyx.perfmonitor;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.IntConsumer;

/** Builds the custom app/overlay color selection dialog. */
final class CustomColorPickerDialog {
    private final MainActivity activity;

    CustomColorPickerDialog(MainActivity activity) {
        this.activity = activity;
    }

    void show(String title, int currentColor, IntConsumer onApply) {
        final int[] selectedColor = {currentColor};
        final boolean[] updating = {false};

        AlertDialog.Builder builder = activity.darkDialogBuilder();
        builder.setTitle(title);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(activity.dp(20), activity.dp(12),
                activity.dp(20), activity.dp(12));

        TextView preview = new TextView(activity);
        preview.setText("COLOR PREVIEW");
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(14);
        preview.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        preview.setPadding(0, activity.dp(14), 0, activity.dp(14));

        android.graphics.drawable.GradientDrawable previewBg =
                new android.graphics.drawable.GradientDrawable();
        previewBg.setCornerRadius(activity.dp(12));
        preview.setBackground(previewBg);
        layout.addView(preview);

        ColorWheelView wheel = new ColorWheelView(activity);
        wheel.setColor(currentColor);

        LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wheelParams.gravity = Gravity.CENTER_HORIZONTAL;
        wheelParams.topMargin = activity.dp(14);
        layout.addView(wheel, wheelParams);

        TextView brightnessLabel = new TextView(activity);
        brightnessLabel.setText("BRIGHTNESS");
        brightnessLabel.setTextColor(Color.WHITE);
        brightnessLabel.setTextSize(12);
        brightnessLabel.setPadding(0, activity.dp(8), 0, activity.dp(2));
        layout.addView(brightnessLabel);

        android.widget.SeekBar brightness = new android.widget.SeekBar(activity);
        brightness.setMax(100);
        brightness.setProgress(Math.round(wheel.getBrightness() * 100f));

        android.content.res.ColorStateList tint =
                android.content.res.ColorStateList.valueOf(activity.getAppAccentColor());
        brightness.setProgressTintList(tint);
        brightness.setThumbTintList(tint);
        layout.addView(brightness);

        TextView hexLabel = new TextView(activity);
        hexLabel.setText("HEX COLOR");
        hexLabel.setTextColor(Color.WHITE);
        hexLabel.setTextSize(12);
        hexLabel.setPadding(0, activity.dp(12), 0, activity.dp(4));
        layout.addView(hexLabel);

        android.widget.EditText hexInput = new android.widget.EditText(activity);
        hexInput.setSingleLine(true);
        hexInput.setHint("#65D6A5");
        hexInput.setText(String.format(Locale.US, "#%06X", (0xFFFFFF & currentColor)));
        hexInput.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hexInput.setTextColor(Color.WHITE);
        hexInput.setHintTextColor(Color.rgb(115, 130, 148));
        hexInput.setBackgroundTintList(tint);
        layout.addView(hexInput);

        Runnable refreshPreview = () -> {
            int c = selectedColor[0];
            previewBg.setColor(c);
            preview.setTextColor(
                    androidx.core.graphics.ColorUtils.calculateLuminance(c) > 0.6
                            ? Color.rgb(13, 18, 26) : Color.WHITE);
            preview.invalidate();
        };
        refreshPreview.run();

        wheel.setOnColorChangedListener(color -> {
            selectedColor[0] = color;
            refreshPreview.run();

            updating[0] = true;
            hexInput.setText(String.format(Locale.US, "#%06X", (0xFFFFFF & color)));
            updating[0] = false;
        });

        brightness.setOnSeekBarChangeListener(
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(android.widget.SeekBar bar,
                                                  int progress, boolean fromUser) {
                        if (!fromUser) return;

                        wheel.setBrightness(progress / 100f);
                        selectedColor[0] = wheel.getColor();
                        refreshPreview.run();

                        updating[0] = true;
                        hexInput.setText(String.format(Locale.US,
                                "#%06X", (0xFFFFFF & selectedColor[0])));
                        updating[0] = false;
                    }

                    @Override
                    public void onStartTrackingTouch(android.widget.SeekBar bar) {
                    }

                    @Override
                    public void onStopTrackingTouch(android.widget.SeekBar bar) {
                    }
                });

        hexInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (updating[0]) return;

                String hex = s.toString().trim();
                if (!hex.matches("#[0-9A-Fa-f]{6}")) return;

                try {
                    int parsed = Color.parseColor(hex);
                    selectedColor[0] = parsed;

                    wheel.setColor(parsed);
                    brightness.setProgress(Math.round(wheel.getBrightness() * 100f));
                    refreshPreview.run();
                } catch (IllegalArgumentException ignored) {
                }
            }
        });

        android.widget.ScrollView scroller = new android.widget.ScrollView(activity);
        scroller.addView(layout);
        builder.setView(scroller);

        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("APPLY", (dialog, which) ->
                onApply.accept(selectedColor[0]));

        activity.showDarkDialog(builder, false);
    }
}
