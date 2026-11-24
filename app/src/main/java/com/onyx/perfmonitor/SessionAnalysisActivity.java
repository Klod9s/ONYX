package com.onyx.perfmonitor;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * Single-session analysis screen.
 * Parsing lives in SessionData, layout lives in AnalysisContentBuilder.
 */
public class SessionAnalysisActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String filename = getIntent().getStringExtra("session_filename");

        if (filename == null || !filename.startsWith("session_")
                || !filename.endsWith(".csv")) {
            showError("Invalid session file.");
            return;
        }

        File file = getFileStreamPath(filename);
        SessionData data = SessionData.load(this, file);

        if (data == null) {
            showError("Could not read this session's CSV data.");
            return;
        }

        int accent = AppColors.accent(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 18, 26));

        // ---- header: back arrow, title, session date ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(6), dp(16), dp(6));
        header.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(21, 29, 41), Color.rgb(14, 20, 29)}));
        header.setElevation(dp(6));

        TextView back = new TextView(this);
        back.setText("✕");
        back.setTextSize(22);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        TextView title = new TextView(this);
        title.setText("SESSION ANALYSIS");
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.12f);
        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- glowing accent line under the header ----
        View line = new View(this);
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
        line.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(0, r, g, b),
                        Color.argb(210, r, g, b),
                        Color.argb(0, r, g, b)}));
        root.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        // ---- content ----
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13, 18, 26));
        scroll.addView(new AnalysisContentBuilder(this, data)
                .showHeader(false)
                .build());

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                root, (v, insets) -> {
                    androidx.core.graphics.Insets bars =
                            insets.getInsets(
                                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            );

                    v.setPadding(0, bars.top, 0, bars.bottom);
                    return insets;
                });
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showError(String message) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextSize(17);
        view.setTextColor(Color.WHITE);
        view.setPadding(24, 32, 24, 24);
        view.setBackgroundColor(Color.rgb(18, 18, 18));
        setContentView(view);
    }
}