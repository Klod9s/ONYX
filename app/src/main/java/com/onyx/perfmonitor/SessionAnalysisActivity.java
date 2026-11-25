package com.onyx.perfmonitor;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single-session analysis screen.
 * CSV parsing runs off the UI thread; layout lives in AnalysisContentBuilder.
 */
public class SessionAnalysisActivity extends Activity {

    private static final int REQUEST_EXPORT_IMAGE = 4102;
    private static final int MAX_IMAGE_DIMENSION = 30000;
    private static final long MAX_IMAGE_PIXELS = 20_000_000L;

    private final ExecutorService loadExecutor =
            Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "onyx-session-analysis");
                thread.setDaemon(true);
                return thread;
            });

    private ScrollView analysisScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String filename = getIntent().getStringExtra("session_filename");

        if (filename == null || !filename.startsWith("session_")
                || !filename.endsWith(".csv")) {
            showError("Invalid session file.");
            return;
        }

        // Display the screen immediately instead of blocking startup while
        // the complete CSV is read and parsed.
        showLoading();

        File file = getFileStreamPath(filename);
        loadExecutor.execute(() -> {
            SessionData data = SessionData.load(this, file);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;

                if (data == null) {
                    showError("Could not read this session's CSV data.");
                } else {
                    showAnalysis(data);
                }
            });
        });
    }

    private void showLoading() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(13, 18, 26));

        ProgressBar progress = new ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView label = new TextView(this);
        label.setText("Loading session…");
        label.setTextSize(13);
        label.setTextColor(Color.rgb(180, 194, 210));
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(14);
        root.addView(label, labelParams);

        setContentView(root);
    }

    private void showAnalysis(SessionData data) {
        int accent = AppColors.accent(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 18, 26));

        // ---- header: back arrow, title, export action ----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(6), dp(12), dp(6));
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

        TextView export = new TextView(this);
        export.setText("EXPORT PNG");
        export.setTextSize(10);
        export.setTypeface(null, android.graphics.Typeface.BOLD);
        export.setLetterSpacing(0.06f);
        export.setTextColor(Color.WHITE);
        export.setGravity(Gravity.CENTER);
        export.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable exportBackground = new GradientDrawable();
        exportBackground.setColor(Color.rgb(31, 48, 65));
        exportBackground.setCornerRadius(dp(9));
        exportBackground.setStroke(dp(1), accent);
        export.setBackground(exportBackground);
        export.setContentDescription("Export full session analysis as PNG");
        export.setOnClickListener(v -> requestImageExport());
        header.addView(export, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)));

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
        analysisScroll = new ScrollView(this);
        analysisScroll.setFillViewport(true);
        analysisScroll.setBackgroundColor(Color.rgb(13, 18, 26));
        analysisScroll.addView(new AnalysisContentBuilder(this, data)
                .showHeader(false)
                .build());

        root.addView(analysisScroll, new LinearLayout.LayoutParams(
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

    private void requestImageExport() {
        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss", Locale.US).format(new Date());
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE,
                "ONYX_Session_Analysis_" + timestamp + ".png");

        try {
            startActivityForResult(intent, REQUEST_EXPORT_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open save location",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_EXPORT_IMAGE || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }

        exportAnalysisImage(data.getData());
    }

    private void exportAnalysisImage(Uri destination) {
        if (analysisScroll == null || analysisScroll.getChildCount() == 0) {
            Toast.makeText(this, "Analysis is not ready to export",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        View content = analysisScroll.getChildAt(0);
        int width = content.getWidth();
        int height = content.getHeight();

        if (width <= 0 || height <= 0) {
            Toast.makeText(this, "Could not measure session analysis",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Keep very long sessions within safe Bitmap dimensions and memory use.
        double scale = Math.min(1.0,
                (double) MAX_IMAGE_DIMENSION / Math.max(width, height));
        long sourcePixels = (long) width * height;
        if (sourcePixels > MAX_IMAGE_PIXELS) {
            scale = Math.min(scale,
                    Math.sqrt((double) MAX_IMAGE_PIXELS / sourcePixels));
        }

        int bitmapWidth = Math.max(1, (int) Math.floor(width * scale));
        int bitmapHeight = Math.max(1, (int) Math.floor(height * scale));

        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(
                    bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.rgb(13, 18, 26));
            canvas.scale((float) scale, (float) scale);
            content.draw(canvas);
        } catch (OutOfMemoryError | RuntimeException e) {
            Toast.makeText(this, "Not enough memory to export this image",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, "Exporting full session analysis…",
                Toast.LENGTH_SHORT).show();

        loadExecutor.execute(() -> {
            boolean saved = false;
            try (OutputStream output = getContentResolver()
                    .openOutputStream(destination, "w")) {
                if (output != null) {
                    saved = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                    output.flush();
                }
            } catch (Exception ignored) {
                saved = false;
            } finally {
                bitmap.recycle();
            }

            final boolean result = saved;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(this,
                        result ? "Session analysis PNG saved"
                                : "Failed to save session analysis",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    @Override
    protected void onDestroy() {
        loadExecutor.shutdownNow();
        super.onDestroy();
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
