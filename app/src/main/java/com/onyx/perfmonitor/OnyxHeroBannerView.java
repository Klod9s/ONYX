package com.onyx.perfmonitor;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;

public class OnyxHeroBannerView extends LinearLayout {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maskPaint = new Paint();

    private Bitmap bg;                 // static background, redrawn only when size/accent changes
    private int accentColor;
    private int lastW = -1;
    private int lastH = -1;
    private String versionText = "v2.0.4";

    public OnyxHeroBannerView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        // extra room so the logo clears the brackets and inner frame
        setPadding(dp(22), dp(26), dp(22), dp(24));

        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            if (pi.versionName != null && !pi.versionName.isEmpty()) {
                versionText = "v" + pi.versionName;
            }
        } catch (Exception ignored) {
        }

        refreshAccentColor();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float dpf(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setVersionText(String text) {
        versionText = text;
        lastW = -1;
        invalidate();
    }

    public void refreshAccentColor() {
        accentColor = AppColors.accent(getContext());
        lastW = -1;            // rebuild background on next draw
        invalidate();
    }

    // ---------------------------------------------------------------- geometry

    private Path frame(float l, float t, float r, float b,
                       float cut, float nw, float nd) {
        float cx = (l + r) / 2f;
        Path q = new Path();
        q.moveTo(l + cut, t);
        q.lineTo(cx - nw / 2 - nd, t);
        q.lineTo(cx - nw / 2, t + nd);
        q.lineTo(cx + nw / 2, t + nd);
        q.lineTo(cx + nw / 2 + nd, t);
        q.lineTo(r - cut, t);
        q.lineTo(r, t + cut);
        q.lineTo(r, b - cut);
        q.lineTo(r - cut, b);
        q.lineTo(cx + nw / 2 + nd, b);
        q.lineTo(cx + nw / 2, b - nd);
        q.lineTo(cx - nw / 2, b - nd);
        q.lineTo(cx - nw / 2 - nd, b);
        q.lineTo(l + cut, b);
        q.lineTo(l, b - cut);
        q.lineTo(l, t + cut);
        q.close();
        return q;
    }

    private void stroke(Canvas c, Path path, int color, float width, float alpha) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setColor(color);
        p.setAlpha(Math.round(255 * alpha));
        c.drawPath(path, p);
    }

    /** Neon look: a few wide, faint strokes under one crisp stroke. */
    private void glowStroke(Canvas c, Path path, int color, float width, float alpha) {
        stroke(c, path, color, width + dpf(8f),   alpha * 0.08f);
        stroke(c, path, color, width + dpf(5f),   alpha * 0.14f);
        stroke(c, path, color, width + dpf(2.5f), alpha * 0.28f);
        stroke(c, path, color, width, alpha);
    }

    private void rebuild(int w, int h) {
        if (bg != null) bg.recycle();
        bg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bg);

        final int acc = accentColor;
        final int r = Color.red(acc), g = Color.green(acc), b = Color.blue(acc);
        final float d = getResources().getDisplayMetrics().density;

        float m = 5 * d;            // outer frame inset (keeps the glow inside the bitmap)
        float gap = 8 * d;          // gap to the inner frame
        float cut = 18 * d;
        float nw = 0.34f * w;
        float nd = 6 * d;

        Path outer = frame(m, m, w - m, h - m, cut, nw, nd);
        Path inner = frame(m + gap, m + gap, w - m - gap, h - m - gap,
                cut * 0.8f, nw * 0.9f, nd);

        // 1. dark fill
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(11, 17, 26));
        c.drawPath(outer, p);

        // 2. everything else is clipped to the outer frame
        c.save();
        c.clipPath(outer);

        // soft glow behind the logo
        p.setShader(new RadialGradient(w * 0.5f, h * 0.42f, Math.max(w, h) * 0.55f,
                new int[]{Color.argb(65, r, g, b), Color.argb(24, 13, 24, 38), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        // hex grid, faded toward the middle so it never fights the logo
        int sc = c.saveLayer(0, 0, w, h, null);
        Path hex = new Path();
        float hr = 13 * d;
        float hw = (float) Math.sqrt(3) * hr;
        int row = 0;
        for (float y = 0; y < h + hr; y += 1.5f * hr, row++) {
            float off = (row % 2 == 0) ? 0 : hw / 2f;
            for (float x = -hw + off; x < w + hw; x += hw) {
                for (int i = 0; i < 6; i++) {
                    double a = Math.toRadians(60 * i - 30);
                    float px = x + hr * (float) Math.cos(a);
                    float py = y + hr * (float) Math.sin(a);
                    if (i == 0) hex.moveTo(px, py); else hex.lineTo(px, py);
                }
                hex.close();
            }
        }
        stroke(c, hex, acc, 1f * d, 0.22f);
        maskPaint.setShader(new RadialGradient(w / 2f, h * 0.45f, w * 0.55f,
                Color.argb(55, 255, 255, 255), Color.WHITE, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, maskPaint);
        c.restoreToCount(sc);

        // circuit traces (normalized coords, mirrored left/right)
        Path traces = new Path();
        float[][] t = {
                {0.05f, 0.70f, 0.11f, 0.70f, 0.14f, 0.77f, 0.24f, 0.77f},
                {0.05f, 0.82f, 0.09f, 0.82f, 0.12f, 0.87f, 0.19f, 0.87f},
                {0.05f, 0.20f, 0.11f, 0.20f, 0.14f, 0.14f, 0.24f, 0.14f}
        };
        float[][] ends = new float[t.length * 2][];
        int e = 0;
        for (float[] s : t) {
            for (int mirror = 0; mirror < 2; mirror++) {
                float lx = 0, ly = 0;
                for (int i = 0; i < s.length; i += 2) {
                    lx = (mirror == 0 ? s[i] : 1f - s[i]) * w;
                    ly = s[i + 1] * h;
                    if (i == 0) traces.moveTo(lx, ly); else traces.lineTo(lx, ly);
                }
                ends[e++] = new float[]{lx, ly};
            }
        }
        stroke(c, traces, acc, 1.2f * d, 0.35f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(acc);
        p.setAlpha(120);
        for (float[] pt : ends) c.drawCircle(pt[0], pt[1], 2.5f * d, p);

        c.restore(); // end clip

        // 3. frames
        stroke(c, inner, acc, 1f * d, 0.35f);
        glowStroke(c, outer, acc, 1.6f * d, 0.85f);

        // 4. thick corner brackets
        Path br = new Path();
        float o = m + gap + 7 * d;
        float arm = 20 * d;
        br.moveTo(o + arm, o);         br.lineTo(o, o);         br.lineTo(o, o + arm);
        br.moveTo(w - o - arm, o);     br.lineTo(w - o, o);     br.lineTo(w - o, o + arm);
        br.moveTo(o + arm, h - o);     br.lineTo(o, h - o);     br.lineTo(o, h - o - arm);
        br.moveTo(w - o - arm, h - o); br.lineTo(w - o, h - o); br.lineTo(w - o, h - o - arm);
        glowStroke(c, br, acc, 3f * d, 0.9f);

        // 5. side ticks
        Path ticks = new Path();
        float xl = m + gap + 3 * d;
        for (int k = -2; k <= 2; k++) {
            float y = h / 2f + k * 6 * d;
            float len = (k == 0 ? 10 : 6) * d;
            ticks.moveTo(xl, y);     ticks.lineTo(xl + len, y);
            ticks.moveTo(w - xl, y); ticks.lineTo(w - xl - len, y);
        }
        stroke(c, ticks, acc, 1.2f * d, 0.55f);

        // 6. version tag, top right
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(acc);
        p.setAlpha(210);
        p.setTextSize(11 * d);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        c.drawText(versionText, w - o - 6 * d, o + 14 * d, p);

        lastW = w;
        lastH = h;
    }

    // -------------------------------------------------------------------- draw

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        if (w > 0 && h > 0) {
            if (bg == null || w != lastW || h != lastH) {
                rebuild(w, h);
            }
            canvas.drawBitmap(bg, 0, 0, null);
        }

        super.dispatchDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (bg != null) {
            bg.recycle();
            bg = null;
            lastW = -1;
        }
    }
}