package com.onyx.perfmonitor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * ONYX logo drawn from two image layers:
 *  - onyx_logo_metal : the gunmetal letters / arrow body (white + alpha)
 *  - onyx_logo_glow  : the neon edges, arrow tips and subtitle (white + alpha)
 * The glow layer is tinted with the app accent color, so the logo follows
 * the "App appearance" setting.
 */
public class OnyxLogoView extends View {

    private static final float MAX_WIDTH_DP = 340f;

    private final Bitmap metal, glow;
    private final Rect src;
    private final RectF dst = new RectF();

    private final Paint metalPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint shapePaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint bandPaint = new Paint();
    private final Matrix bandMatrix = new Matrix();
    private LinearGradient bandShader;

    private float shineT = 1f;   // 0..1 animation clock, 1 = idle
    private boolean shining = false;
    private boolean animationsEnabled = true;
    private boolean hasShineStarted = false;
    private long shineStartNs;
    private static final long SHINE_PERIOD_MS = 5200;

    private final Runnable shineTick = new Runnable() {
        @Override
        public void run() {
            if (!shining) return;
            long elapsedMs = (System.nanoTime() - shineStartNs) / 1_000_000L;
            shineT = (elapsedMs % SHINE_PERIOD_MS) / (float) SHINE_PERIOD_MS;
            invalidate();
            postOnAnimation(this);
        }
    };

    public OnyxLogoView(Context context) {
        super(context);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        metal = BitmapFactory.decodeResource(getResources(), R.drawable.onyx_logo_metal, o);
        glow = BitmapFactory.decodeResource(getResources(), R.drawable.onyx_logo_glow, o);
        src = new Rect(0, 0, metal.getWidth(), metal.getHeight());
        bandPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        applyAccent();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** Pure, fully saturated version of the accent (min channel -> 0, max channel -> 255). */
    private void applyAccent() {
        int acc = AppColors.accent(getContext());
        int r = Color.red(acc), g = Color.green(acc), b = Color.blue(acc);
        int mn = Math.min(r, Math.min(g, b));
        int mx = Math.max(r, Math.max(g, b));
        float range = Math.max(1, mx - mn);
        int hue = Color.rgb(
                Math.round((r - mn) * 255f / range),
                Math.round((g - mn) * 255f / range),
                Math.round((b - mn) * 255f / range));
        glowPaint.setColorFilter(new PorterDuffColorFilter(hue, PorterDuff.Mode.SRC_IN));
    }

    private void buildBand() {
        int w = getWidth();
        if (w <= 0) return;

        float bw = w * 0.40f;
        int light = lightAccent();
        int lr = Color.red(light), lg = Color.green(light), lb = Color.blue(light);

        bandShader = new LinearGradient(0, 0, bw, 0,
                new int[]{
                        Color.argb(0, lr, lg, lb),
                        Color.argb(120, lr, lg, lb),
                        Color.argb(255, lr, lg, lb),
                        Color.argb(120, lr, lg, lb),
                        Color.argb(0, lr, lg, lb)},
                new float[]{0f, 0.35f, 0.5f, 0.65f, 1f},
                Shader.TileMode.CLAMP);
        bandPaint.setShader(bandShader);
    }

    private int lightAccent() {
        int acc = AppColors.accent(getContext());
        return Color.rgb(
                (Color.red(acc) + 255 * 2) / 3,
                (Color.green(acc) + 255 * 2) / 3,
                (Color.blue(acc) + 255 * 2) / 3);
    }

    /** Called by MainActivity when the app accent color changes. */
    public void refreshAccentColor() {
        applyAccent();
        buildBand();
        invalidate();
    }

    public void setAnimationsEnabled(boolean enabled) {
        if (animationsEnabled == enabled) return;
        animationsEnabled = enabled;
        if (!enabled) {
            stopShine();
        } else {
            invalidate();
        }
    }

    public void startShine() {
        if (!animationsEnabled || shining) return;
        shining = true;

        // Resume from the last animation phase instead of restarting at
        // the dimmest point whenever MainActivity returns to the foreground.
        if (!hasShineStarted) {
            shineT = 0f;
            hasShineStarted = true;
        }
        shineStartNs = System.nanoTime()
                - (long) (shineT * SHINE_PERIOD_MS * 1_000_000L);
        postOnAnimation(shineTick);
    }


    public void stopShine() {
        shining = false;
        removeCallbacks(shineTick);
        // Keep shineT so the animation resumes from its current visual phase.
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopShine();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int avail = MeasureSpec.getSize(wSpec);
        if (MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED) avail = (int) dp(300);
        int w = (int) Math.min(avail, dp(MAX_WIDTH_DP));
        int h = Math.round(w * (float) metal.getHeight() / metal.getWidth());
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        dst.set(0, 0, w, h);
        buildBand();
    }

    // how many extra neon passes sit on top of the base glow (1 = subtle, 3 = intense)
    private static final int GLOW_EXTRA_PASSES = 2;

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();

        int sc = canvas.saveLayer(0, 0, w, h, null);

        // metal body (always fully visible)
        canvas.drawBitmap(metal, src, dst, metalPaint);

        // neon glow: pulses from 1% to 100%
        // Keep the last animated brightness while paused. Switching to a
        // full glow when shining becomes false causes a visible flash during
        // Activity transitions.
        float pulse = animationsEnabled
                ? (hasShineStarted
                    ? 0.5f - 0.5f * (float) Math.cos(shineT * 2.0 * Math.PI)
                    : 1f)
                : 0.55f;
        float level = 0.01f + 0.99f * pulse;    // animated pulse or steady glow
        glowPaint.setAlpha(Math.round(255 * level));
        canvas.drawBitmap(glow, src, dst, glowPaint);
        glowPaint.setAlpha(255);

        // shine band, clipped to the letter shapes by SRC_ATOP
        if (animationsEnabled && shining) {
            float t = shineT / 0.5f;
            if (t < 1f) {
                float bw = w * 0.40f;
                float tx = -bw + t * (w + bw);
                bandMatrix.setSkew(-0.35f, 0f, 0f, h / 2f);
                bandMatrix.postTranslate(tx, 0f);
                bandShader.setLocalMatrix(bandMatrix);
                canvas.drawRect(0, 0, w, h, bandPaint);
            }
        }

        canvas.restoreToCount(sc);
    }
}