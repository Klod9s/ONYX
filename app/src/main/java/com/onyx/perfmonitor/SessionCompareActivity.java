package com.onyx.perfmonitor;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Stacked session compare: A on top, B on bottom (side by side in landscape).
 * While LINK SCROLL is on, scrolling either pane scrolls both.
 */
public class SessionCompareActivity extends Activity {

    private final ExecutorService loadExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "onyx-session-compare");
                thread.setDaemon(true);
                return thread;
            });

    private boolean linked = true;
    private boolean syncing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String firstName = getIntent().getStringExtra("a");
        String secondName = getIntent().getStringExtra("b");
        if (!isValidSessionName(firstName) || !isValidSessionName(secondName)) {
            showError("Invalid session file.");
            return;
        }

        showLoading();
        loadExecutor.execute(() -> {
            SessionData a = loadSession(firstName);
            SessionData b = loadSession(secondName);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (a == null || b == null) {
                    showError("Could not load one of the sessions.");
                } else {
                    showComparison(a, b);
                }
            });
        });
    }

    private boolean isValidSessionName(String filename) {
        return filename != null && filename.startsWith("session_")
                && filename.endsWith(".csv");
    }

    private void showLoading() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(13, 18, 26));

        android.widget.ProgressBar progress = new android.widget.ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView label = new TextView(this);
        label.setText("Loading sessions…");
        label.setTextSize(13);
        label.setTextColor(Color.rgb(180, 194, 210));
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        root.addView(label, params);
        setContentView(root);
    }

    private void showComparison(SessionData a, SessionData b) {
        boolean landscape =
                getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;

        final int accent = getAppAccentColor();
        final int colorB = compareBColor();

        ScrollView scrollA = new ScrollView(this);
        ScrollView scrollB = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 18, 26));

        // ---- header: back arrow, title, link pill ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(6), dp(12), dp(6));
        header.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(21, 29, 41), Color.rgb(14, 20, 29)}));
        header.setElevation(dp(6));

        TextView back = new TextView(this);
        back.setText("✕");
        back.setTextSize(20);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));

        TextView title = new TextView(this);
        title.setText("SESSION COMPARE");
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.12f);
        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // link pill: dot + label, same style as the status pill on Monitor
        LinearLayout linkPill = new LinearLayout(this);
        linkPill.setGravity(Gravity.CENTER_VERTICAL);
        linkPill.setPadding(dp(12), dp(7), dp(12), dp(7));

        android.graphics.drawable.GradientDrawable pillBg =
                new android.graphics.drawable.GradientDrawable();
        pillBg.setColor(Color.rgb(25, 33, 45));
        pillBg.setCornerRadius(dp(50));
        linkPill.setBackground(pillBg);

        View dot = new View(this);
        android.graphics.drawable.GradientDrawable dotBg =
                new android.graphics.drawable.GradientDrawable();
        dotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotParams.rightMargin = dp(8);
        linkPill.addView(dot, dotParams);

        TextView linkLabel = new TextView(this);
        linkLabel.setText("LINK SCROLL");
        linkLabel.setTextSize(10);
        linkLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        linkLabel.setLetterSpacing(0.08f);
        linkLabel.setSingleLine(true);
        linkPill.addView(linkLabel);

        Runnable refreshLink = () -> {
            pillBg.setStroke(dp(1), linked ? accent : Color.rgb(43, 52, 65));
            dotBg.setColor(linked ? accent : Color.rgb(90, 102, 118));
            linkLabel.setTextColor(linked ? Color.WHITE : Color.rgb(145, 160, 180));
        };
        refreshLink.run();

        linkPill.setOnClickListener(v -> {
            linked = !linked;
            refreshLink.run();
            if (linked) {                       // snap B to A when linking again
                syncing = true;
                scrollB.scrollTo(0, scrollA.getScrollY());
                syncing = false;
            }
        });
        header.addView(linkPill);

        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- line under the header: fades from A's color into B's ----
        View line = new View(this);
        line.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.argb(210, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.argb(210, Color.red(colorB), Color.green(colorB), Color.blue(colorB)),
                        Color.argb(0, Color.red(colorB), Color.green(colorB), Color.blue(colorB))}));
        root.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        // ---- the two panes, in their own container below the header ----
        LinearLayout panes = new LinearLayout(this);
        panes.setOrientation(landscape
                ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        panes.addView(buildPane("A", a, accent, scrollA), paneParams(landscape));
        addDivider(panes, landscape);
        panes.addView(buildPane("B", b, colorB, scrollB), paneParams(landscape));

        root.addView(panes, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        link(scrollA, scrollB);
        link(scrollB, scrollA);

        setContentView(root);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                root, (v, insets) -> {
                    androidx.core.graphics.Insets bars =
                            insets.getInsets(
                                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            );

                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    private SessionData loadSession(String filename) {
        File file = getFileStreamPath(filename);
        return SessionData.load(this, file);
    }

    @Override
    protected void onDestroy() {
        loadExecutor.shutdownNow();
        super.onDestroy();
    }

    /** Pinned label bar + scrolling analysis content. */
    private LinearLayout buildPane(String tag, SessionData d,
                                   int color, ScrollView scroll) {
        LinearLayout pane = new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);

        // Label bar: filled A/B badge + info text, tinted with the pane colour
        LinearLayout labelBar = new LinearLayout(this);
        labelBar.setOrientation(LinearLayout.HORIZONTAL);
        labelBar.setGravity(Gravity.CENTER_VERTICAL);
        labelBar.setPadding(dp(10), dp(6), dp(10), dp(6));
        labelBar.setBackgroundColor(paneBarColor(color));

        TextView chip = new TextView(this);
        chip.setText(tag);
        chip.setTextSize(11);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        double lum = (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        chip.setTextColor(lum > 0.6 ? Color.rgb(13, 18, 26) : Color.WHITE);
        chip.setPadding(dp(7), dp(2), dp(7), dp(2));
        android.graphics.drawable.GradientDrawable chipBg =
                new android.graphics.drawable.GradientDrawable();
        chipBg.setColor(color);
        chipBg.setCornerRadius(dp(6));
        chip.setBackground(chipBg);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        chipParams.setMargins(0, 0, dp(8), 0);
        labelBar.addView(chip, chipParams);

        TextView label = new TextView(this);
        label.setText(d.deviceLabel()
                + (d.imported ? "  ·  IMPORTED " + d.originalName : "")
                + "  ·  " + d.dateLabel());
        label.setTextSize(11);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setTextColor(Color.rgb(225, 232, 240));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        labelBar.addView(label, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        pane.addView(labelBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Thin underline in the pane colour: unmistakable start of each pane
        View underline = new View(this);
        underline.setBackgroundColor(color);
        pane.addView(underline, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));

        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13, 18, 26));
        scroll.addView(new AnalysisContentBuilder(this, d)
                .showHeader(false)
                .chartHeightDp(160)
                .build());

        pane.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return pane;
    }

    /** Scrolling "from" moves "to" to the same offset. syncing blocks the echo. */
    private void link(ScrollView from, ScrollView to) {
        from.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (!linked || syncing) return;
            syncing = true;
            to.scrollTo(0, y);
            syncing = false;
        });
    }

    private LinearLayout.LayoutParams paneParams(boolean landscape) {
        return landscape
                ? new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                : new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
    }

    /** Divider that fades from A's colour into B's colour. */
    private void addDivider(LinearLayout root, boolean landscape) {
        View line = new View(this);
        android.graphics.drawable.GradientDrawable grad =
                new android.graphics.drawable.GradientDrawable();
        grad.setOrientation(landscape
                ? android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                : android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM);
        grad.setColors(new int[]{getAppAccentColor(), compareBColor()});
        line.setBackground(grad);
        root.addView(line, landscape
                ? new LinearLayout.LayoutParams(dp(3),
                LinearLayout.LayoutParams.MATCH_PARENT)
                : new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));
    }

    private int getAppAccentColor() {
        return AppColors.accent(this);
    }

    /** B's colour: the app accent with its hue rotated 120°, kept vivid.
     *  Default green accent lands on the familiar amber; any other accent
     *  gets a contrasting partner that still belongs to the theme. */
    private int compareBColor() {
        return AppColors.compareB(this);
    }

    /** Mix a pane colour into the dark label-bar base (19,26,36). */
    /** Dark version of the pane colour that keeps its hue — no muddy gray. */
    private int paneBarColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 0.9f);  // keep the colour's saturation
        hsv[2] = 0.26f;                        // dark, but clearly tinted
        return Color.HSVToColor(hsv);
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