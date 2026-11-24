package com.onyx.perfmonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

import java.util.function.IntConsumer;

/** Hue/saturation wheel. Brightness is set separately with setBrightness(). */
public class ColorWheelView extends View {

    private final float[] hsv = {0f, 0f, 1f};

    private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint satPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerOutline = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float density;
    private float cx, cy, radius;
    private IntConsumer listener;

    public ColorWheelView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;

        dimPaint.setStyle(Paint.Style.FILL);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(density);
        ringPaint.setColor(Color.argb(70, 255, 255, 255));

        markerFill.setStyle(Paint.Style.FILL);

        markerRing.setStyle(Paint.Style.STROKE);
        markerRing.setStrokeWidth(2.5f * density);
        markerRing.setColor(Color.WHITE);

        markerOutline.setStyle(Paint.Style.STROKE);
        markerOutline.setStrokeWidth(density);
        markerOutline.setColor(Color.argb(110, 0, 0, 0));
    }

    /** Called only when the user drags on the wheel. */
    public void setOnColorChangedListener(IntConsumer l) {
        listener = l;
    }

    public void setColor(int color) {
        Color.colorToHSV(color, hsv);
        invalidate();
    }

    public int getColor() {
        return Color.HSVToColor(hsv);
    }

    public void setBrightness(float value) {
        hsv[2] = Math.max(0f, Math.min(1f, value));
        invalidate();
    }

    public float getBrightness() {
        return hsv[2];
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int max = (int) (240 * density);
        int mode = MeasureSpec.getMode(widthSpec);
        int size = mode == MeasureSpec.UNSPECIFIED
                ? max
                : Math.min(MeasureSpec.getSize(widthSpec), max);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        cx = w / 2f;
        cy = h / 2f;
        radius = Math.min(w, h) / 2f - 12 * density;   // room for the marker

        int[] colors = new int[7];
        for (int i = 0; i < 7; i++) {
            colors[i] = Color.HSVToColor(new float[]{i * 60f, 1f, 1f});
        }
        huePaint.setShader(new SweepGradient(cx, cy, colors, null));

        satPaint.setShader(new RadialGradient(
                cx, cy, radius,
                0xFFFFFFFF, 0x00FFFFFF,
                Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(cx, cy, radius, huePaint);
        canvas.drawCircle(cx, cy, radius, satPaint);

        int dim = Math.round((1f - hsv[2]) * 255f);
        if (dim > 0) {
            dimPaint.setColor(Color.argb(dim, 0, 0, 0));
            canvas.drawCircle(cx, cy, radius, dimPaint);
        }

        canvas.drawCircle(cx, cy, radius, ringPaint);

        double angle = Math.toRadians(hsv[0]);
        float mx = cx + (float) (Math.cos(angle) * hsv[1] * radius);
        float my = cy + (float) (Math.sin(angle) * hsv[1] * radius);
        float r = 10 * density;

        markerFill.setColor(Color.HSVToColor(hsv));
        canvas.drawCircle(mx, my, r, markerFill);
        canvas.drawCircle(mx, my, r, markerRing);
        canvas.drawCircle(mx, my, r + 1.75f * density, markerOutline);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                updateFromTouch(e.getX(), e.getY());
                return true;

            case MotionEvent.ACTION_MOVE:
                updateFromTouch(e.getX(), e.getY());
                return true;

            case MotionEvent.ACTION_UP:
                updateFromTouch(e.getX(), e.getY());
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void updateFromTouch(float x, float y) {
        float dx = x - cx;
        float dy = y - cy;
        float dist = (float) Math.hypot(dx, dy);

        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360f;

        hsv[0] = angle % 360f;
        hsv[1] = radius > 0 ? Math.min(1f, dist / radius) : 0f;

        invalidate();
        if (listener != null) listener.accept(getColor());
    }
}