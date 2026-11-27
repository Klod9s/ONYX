package com.onyx.perfmonitor;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.RadialGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/** Glass-look drawables and colours for the Monitor page. */
final class GlassStyle {
    static final int GOOD = Color.rgb(88, 224, 150);
    static final int WARN = Color.rgb(255, 184, 76);
    static final int MUTED = Color.rgb(155, 174, 194);

    private GlassStyle() {
    }

    static int mix(int a, int b, float t) {
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    /** Deep page backdrop with soft accent glows, so the glass cards have something to refract. */
    static Drawable pageBackground(final android.content.Context ctx) {
        return new Drawable() {
            private final Paint base = new Paint();
            private final Paint glowA = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint glowB = new Paint(Paint.ANTI_ALIAS_FLAG);
            private int builtFor = 0;
            private int builtW = -1, builtH = -1;

            private void build(int w, int h, int accent) {
                base.setShader(new LinearGradient(0, 0, 0, h,
                        Color.rgb(7, 12, 20), Color.rgb(4, 8, 14), Shader.TileMode.CLAMP));
                float[] hsv = new float[3];
                Color.colorToHSV(accent, hsv);
                hsv[0] = (hsv[0] + 40f) % 360f;
                int second = Color.HSVToColor(hsv);
                glowA.setShader(new RadialGradient(w * 0.95f, h * 0.12f, w * 0.85f,
                        Color.argb(26, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Shader.TileMode.CLAMP));
                glowB.setShader(new RadialGradient(w * 0.05f, h * 0.62f, w * 0.9f,
                        Color.argb(14, Color.red(second), Color.green(second), Color.blue(second)),
                        Color.argb(0, Color.red(second), Color.green(second), Color.blue(second)),
                        Shader.TileMode.CLAMP));
                builtFor = accent;
                builtW = w;
                builtH = h;
            }

            @Override
            public void draw(Canvas c) {
                int w = getBounds().width(), h = getBounds().height();
                int accent = AppColors.accent(ctx);
                if (w != builtW || h != builtH || accent != builtFor) build(w, h, accent);
                c.drawRect(getBounds(), base);
                c.drawRect(getBounds(), glowA);
                c.drawRect(getBounds(), glowB);
            }

            @Override public void setAlpha(int a) { }
            @Override public void setColorFilter(ColorFilter f) { }
            @Override public int getOpacity() { return PixelFormat.OPAQUE; }
        };
    }

    /** What the renderer needs to update on a tile: dot grid or frame graph. */
    static final class TileTag {
        DotMatrixView matrix;      // may be null
        FrameGraphView graph;      // FPS tile only
        TextView label;            // metric title, used for source-aware FPS labeling
    }

    /** Live frame-time graph (ms per frame), fed one point per monitoring sample. */
    static final class FrameGraphView extends View {
        private static final int MAX_POINTS = 15;
        private final float[] history = new float[MAX_POINTS];
        private int count = 0;
        private int misses = 0;
        private boolean animationsEnabled = true;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path linePath = new Path();
        private final Path areaPath = new Path();

        FrameGraphView(android.content.Context c) {
            super(c);
            float d = c.getResources().getDisplayMetrics().density;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(1.4f * d);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            halo.setStyle(Paint.Style.STROKE);
            halo.setStrokeWidth(4f * d);
            halo.setStrokeCap(Paint.Cap.ROUND);
            halo.setStrokeJoin(Paint.Join.ROUND);
            area.setStyle(Paint.Style.FILL);
        }

        void setAnimationsEnabled(boolean enabled) {
            if (animationsEnabled == enabled) return;
            animationsEnabled = enabled;
            if (!enabled) {
                count = 0;
                misses = 0;
            }
            invalidate();
        }

        void noData() {
            if (!animationsEnabled || count == 0) return;
            if (++misses >= 3) {
                count = 0;
                invalidate();
            }
        }

        void addFrameTime(float ms) {
            if (!animationsEnabled) return;
            if (ms <= 0 || Float.isNaN(ms) || Float.isInfinite(ms)) return;
            misses = 0;
            if (count < MAX_POINTS) {
                history[count++] = ms;
            } else {
                System.arraycopy(history, 1, history, 0, MAX_POINTS - 1);
                history[MAX_POINTS - 1] = ms;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (!animationsEnabled || w <= 0 || h <= 0 || count == 0) return;
            float pad = 2f * getResources().getDisplayMetrics().density;
            if (count == 1) {
                int accent = AppColors.accent(getContext());
                line.setColor(accent);
                halo.setColor(Color.argb(45, Color.red(accent),
                        Color.green(accent), Color.blue(accent)));
                float x = w - pad;
                float y = h * 0.48f;
                canvas.drawCircle(x, y, 2.2f * getResources().getDisplayMetrics().density, halo);
                canvas.drawCircle(x, y, 1.2f * getResources().getDisplayMetrics().density, line);
                return;
            }

            float lo = Float.MAX_VALUE, hi = 0f, sum = 0f;
            for (int i = 0; i < count; i++) {
                lo = Math.min(lo, history[i]);
                hi = Math.max(hi, history[i]);
                sum += history[i];
            }
            float span = Math.max(4f, hi - lo);
            float mid = (hi - lo < 4f) ? sum / count : (hi + lo) / 2f;
            float vLo = mid - span / 2f, vHi = mid + span / 2f;

            float step = (w - 2 * pad) / (MAX_POINTS - 1);
            float[] px = new float[count], py = new float[count];
            float minY = h;
            for (int i = 0; i < count; i++) {
                px[i] = (w - pad) - (count - 1 - i) * step;
                float t = (history[i] - vLo) / (vHi - vLo);
                py[i] = pad + (h - 2 * pad) * (1f - Math.max(0f, Math.min(1f, t)));
                minY = Math.min(minY, py[i]);
            }

            linePath.rewind();
            linePath.moveTo(px[0], py[0]);
            for (int i = 1; i < count; i++) linePath.lineTo(px[i], py[i]);

            int accent = AppColors.accent(getContext());
            int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);
            areaPath.set(linePath);
            areaPath.lineTo(px[count - 1], h);
            areaPath.lineTo(px[0], h);
            areaPath.close();
            area.setShader(new LinearGradient(0, minY, 0, h,
                    Color.argb(120, r, g, b), Color.argb(0, r, g, b), Shader.TileMode.CLAMP));
            canvas.drawPath(areaPath, area);
            halo.setColor(Color.argb(45, r, g, b));
            canvas.drawPath(linePath, halo);
            line.setColor(accent);
            canvas.drawPath(linePath, line);
        }
    }

    /** Small grid of dots that fills from the bottom up with the metric level. */
    static final class DotMatrixView extends View {
        private static final int COLS = 5, ROWS = 8;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float level = 0f;
        private boolean animationsEnabled = true;

        DotMatrixView(android.content.Context c) {
            super(c);
        }

        void setAnimationsEnabled(boolean enabled) {
            if (animationsEnabled == enabled) return;
            animationsEnabled = enabled;
            if (!enabled) level = 0f;
            invalidate();
        }

        void setLevel(float f) {
            if (!animationsEnabled) return;
            f = Math.max(0f, Math.min(1f, f));
            if (Math.abs(f - level) > 0.001f) { level = f; invalidate(); }
        }

        @Override
        protected void onDraw(Canvas c) {
            int col = AppColors.accent(getContext());
            float cellW = getWidth() / (float) COLS;
            float cellH = getHeight() / (float) ROWS;
            float r = Math.min(cellW, cellH) * 0.30f;
            int lit = Math.round(level * ROWS);
            if (level > 0.02f && lit == 0) lit = 1;
            for (int row = 0; row < ROWS; row++) {
                int fromBottom = ROWS - 1 - row;
                boolean on = fromBottom < lit;
                int a = on ? (int) (255 - 120f * fromBottom / Math.max(1, lit)) : 0;
                for (int cIdx = 0; cIdx < COLS; cIdx++) {
                    if (on) p.setColor(Color.argb(a, Color.red(col), Color.green(col), Color.blue(col)));
                    else p.setColor(Color.argb(28, 255, 255, 255));
                    c.drawCircle(cellW * (cIdx + 0.5f), cellH * (row + 0.5f), r, p);
                }
            }
        }
    }

    /** START SESSION button: steel-blue glass with a diagonal reflection and bright top/bottom edge glints. */
    static Drawable primaryButton(int accent, float radiusPx, float strokePx) {
        return new GlassButtonDrawable(radiusPx, strokePx, GlassButtonDrawable.BLUE);
    }

    /** STOP SESSION button (recording): the same glass construction in crimson. */
    static Drawable recordingButton(float radiusPx, float strokePx) {
        return new GlassButtonDrawable(radiusPx, strokePx, GlassButtonDrawable.RED);
    }

    private static final class GlassButtonDrawable extends Drawable {
        /** Colour set for one glass button flavour. Alphas for the layers are fixed in the drawable. */
        static final class Tint {
            final int fillLeft, fillRight, sheen, border, glint, glow, sheenTopAlpha, sheenBottomAlpha;
            Tint(int fillLeft, int fillRight, int sheen, int border, int glint, int glow,
                 int sheenTopAlpha, int sheenBottomAlpha) {
                this.fillLeft = fillLeft;
                this.fillRight = fillRight;
                this.sheen = sheen;
                this.border = border;
                this.glint = glint;
                this.glow = glow;
                this.sheenTopAlpha = sheenTopAlpha;
                this.sheenBottomAlpha = sheenBottomAlpha;
            }
        }

        // Steel-blue glass (START SESSION).
        static final Tint BLUE = new Tint(
                Color.rgb(34, 57, 82), Color.rgb(37, 72, 104),
                Color.rgb(200, 224, 247), Color.rgb(112, 134, 152),
                Color.rgb(190, 222, 245), Color.rgb(180, 218, 255), 20, 8);
        // Crimson glass (STOP SESSION): a little more saturated so the live state stays obvious.
        static final Tint RED = new Tint(
                Color.rgb(150, 38, 54), Color.rgb(116, 30, 50),
                Color.rgb(250, 205, 212), Color.rgb(186, 108, 120),
                Color.rgb(255, 206, 212), Color.rgb(255, 170, 180), 24, 10);

        // Glint strength (0..255) at fixed positions across the button width.
        private static final float[] GLINT_POS_TOP = {0f, 0.09f, 0.23f, 0.45f, 0.65f, 0.79f, 0.93f, 1f};
        private static final int[] GLINT_A_TOP = {20, 61, 102, 199, 97, 51, 28, 20};
        private static final float[] GLINT_POS_BOTTOM = {0f, 0.20f, 0.35f, 0.50f, 0.65f, 0.75f, 1f};
        private static final int[] GLINT_A_BOTTOM = {0, 5, 84, 140, 115, 26, 0};

        private final float radius;
        private final float strokeWidth;
        private final Tint tint;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glintTop = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glintBottom = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final RectF strokeBounds = new RectF();
        private final RectF innerBounds = new RectF();
        private final Path bodyPath = new Path();
        private final Path diagonalPath = new Path();
        private final Path sheenPath = new Path();
        private float glintX0, glintX1;
        private int alpha = 255;

        GlassButtonDrawable(float radiusPx, float strokePx, Tint tint) {
            this.tint = tint;
            radius = radiusPx;
            strokeWidth = Math.max(1f, strokePx);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(strokeWidth);
            glintTop.setStyle(Paint.Style.STROKE);
            glintTop.setStrokeWidth(strokeWidth);
            glintTop.setStrokeCap(Paint.Cap.BUTT);
            glintBottom.setStyle(Paint.Style.STROKE);
            glintBottom.setStrokeWidth(strokeWidth);
            glintBottom.setStrokeCap(Paint.Cap.BUTT);
            inner.setStyle(Paint.Style.STROKE);
            inner.setStrokeWidth(1f);
        }

        private static int withAlpha(int rgb, int a) {
            return Color.argb(a, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
        }

        private static int[] glintColors(int rgb, int[] alphas) {
            int[] c = new int[alphas.length];
            for (int i = 0; i < alphas.length; i++) c[i] = withAlpha(rgb, alphas[i]);
            return c;
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect b) {
            bounds.set(b);
            float l = b.left, t = b.top;
            float w = bounds.width();
            float h = bounds.height();
            float r = Math.min(radius, h / 2f);
            float half = strokeWidth / 2f;
            strokeBounds.set(l + half, t + half, b.right - half, b.bottom - half);
            float inset = Math.max(1f, strokeWidth);
            innerBounds.set(l + inset, t + inset, b.right - inset, b.bottom - inset);

            // Body: dark translucent tint that drifts slightly toward the right-hand colour.
            fill.setShader(new LinearGradient(l, 0, l + w, 0,
                    withAlpha(tint.fillLeft, 210), withAlpha(tint.fillRight, 210),
                    Shader.TileMode.CLAMP));

            // Soft haze near the top centre.
            glow.setShader(new RadialGradient(l + w * 0.5f, t + h * 0.08f,
                    Math.max(w * 0.75f, h),
                    withAlpha(tint.glow, 35), withAlpha(tint.glow, 0), Shader.TileMode.CLAMP));

            // Diagonal reflection: lighter above a line running from the bottom-left
            // up to the top-right, clipped to the rounded body.
            sheen.setShader(new LinearGradient(0, t, 0, t + h,
                    withAlpha(tint.sheen, tint.sheenTopAlpha), withAlpha(tint.sheen, tint.sheenBottomAlpha),
                    Shader.TileMode.CLAMP));
            bodyPath.rewind();
            bodyPath.addRoundRect(bounds, r, r, Path.Direction.CW);
            diagonalPath.rewind();
            diagonalPath.moveTo(l, t);
            diagonalPath.lineTo(l + w, t);
            diagonalPath.lineTo(l + w, t + h * 0.12f);
            diagonalPath.lineTo(l, t + h * 0.92f);
            diagonalPath.close();
            sheenPath.rewind();
            sheenPath.set(bodyPath);
            if (!sheenPath.op(diagonalPath, Path.Op.INTERSECT)) sheenPath.rewind();

            // Edge: a steady line all round, plus brighter glints on the top and
            // bottom edges that peak just left of centre.
            border.setShader(null);
            border.setColor(withAlpha(tint.border, 128));
            glintX0 = l + r * 0.55f;
            glintX1 = l + w - r * 0.55f;
            glintTop.setShader(new LinearGradient(l, 0, l + w, 0,
                    glintColors(tint.glint, GLINT_A_TOP), GLINT_POS_TOP, Shader.TileMode.CLAMP));
            glintBottom.setShader(new LinearGradient(l, 0, l + w, 0,
                    glintColors(tint.glint, GLINT_A_BOTTOM), GLINT_POS_BOTTOM, Shader.TileMode.CLAMP));
            inner.setColor(Color.argb(38, 238, 247, 255));
        }

        @Override
        public void draw(Canvas canvas) {
            if (bounds.isEmpty()) return;
            float r = Math.min(radius, bounds.height() / 2f);
            int saved = -1;
            if (alpha < 255) saved = canvas.saveLayerAlpha(bounds, alpha);
            canvas.drawRoundRect(bounds, r, r, fill);
            canvas.drawRoundRect(bounds, r, r, glow);
            if (!sheenPath.isEmpty()) canvas.drawPath(sheenPath, sheen);
            float rb = Math.max(0f, r - strokeWidth / 2f);
            canvas.drawRoundRect(strokeBounds, rb, rb, border);
            canvas.drawLine(glintX0, strokeBounds.top, glintX1, strokeBounds.top, glintTop);
            canvas.drawLine(glintX0, strokeBounds.bottom, glintX1, strokeBounds.bottom, glintBottom);
            if (!innerBounds.isEmpty()) {
                canvas.drawRoundRect(innerBounds, Math.max(0f, r - strokeWidth),
                        Math.max(0f, r - strokeWidth), inner);
            }
            if (saved >= 0) canvas.restoreToCount(saved);
        }

        @Override public void setAlpha(int a) {
            alpha = Math.max(0, Math.min(255, a));
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter filter) {
            fill.setColorFilter(filter);
            sheen.setColorFilter(filter);
            glow.setColorFilter(filter);
            border.setColorFilter(filter);
            glintTop.setColorFilter(filter);
            glintBottom.setColorFilter(filter);
            inner.setColorFilter(filter);
            invalidateSelf();
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Small outlined pill for the CHARGING / DISCHARGING state. */
    static void styleStateBadge(TextView badge, boolean charging) {
        if (badge == null) return;
        Object tag = badge.getTag();
        if (tag instanceof Boolean && ((Boolean) tag) == charging) return;
        badge.setTag(charging);

        int c = charging ? GOOD : WARN;
        float d = badge.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(34, Color.red(c), Color.green(c), Color.blue(c)));
        bg.setCornerRadius(50 * d);
        bg.setStroke((int) Math.max(1, d), Color.argb(170, Color.red(c), Color.green(c), Color.blue(c)));
        badge.setBackground(bg);
        badge.setTextColor(c);
    }

    /** Soft-glass card: dark gradient body, faint accent haze and a low-contrast border. */
    static final class GlassDrawable extends Drawable {
        private final float radius;
        private final float strokeW;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF strokeRect = new RectF();
        private int accent;

        private boolean flat = false;
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);

        GlassDrawable flat() {
            this.flat = true;
            rebuild();
            return this;
        }

        GlassDrawable(float radiusPx, float strokePx, int accent) {
            this.radius = radiusPx;
            this.strokeW = strokePx;
            this.accent = accent;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(strokePx);
        }

        void setAccent(int accent) {
            this.accent = accent;
            rebuild();
            invalidateSelf();
        }

        private void rebuild() {
            if (getBounds().isEmpty()) return;
            float w = getBounds().width();
            float h = getBounds().height();

            if (flat) {
                // plain dark button: solid fill, one thin even slate border
                fill.setShader(new LinearGradient(0, 0, 0, h,
                        Color.rgb(24, 36, 52), Color.rgb(17, 27, 41), Shader.TileMode.CLAMP));
                stroke.setShader(null);
                stroke.setColor(Color.argb(150, 70, 92, 114));
                glow.setShader(null);
                return;
            }

            // soft glass: dark body, faint haze, low-contrast border, no shine
            int top = mix(Color.rgb(18, 29, 44), accent, 0.04f);
            int bottom = mix(Color.rgb(12, 20, 32), accent, 0.02f);
            fill.setShader(new LinearGradient(0, 0, 0, h,
                    Color.argb(235, Color.red(top), Color.green(top), Color.blue(top)),
                    Color.argb(235, Color.red(bottom), Color.green(bottom), Color.blue(bottom)),
                    Shader.TileMode.CLAMP));

            glow.setShader(new RadialGradient(w * 0.88f, h * 1.0f, Math.max(w, h) * 0.8f,
                    Color.argb(26, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Shader.TileMode.CLAMP));

            int hi = mix(accent, Color.rgb(120, 150, 175), 0.5f);
            stroke.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{Color.argb(105, Color.red(hi), Color.green(hi), Color.blue(hi)),
                            Color.argb(60, 85, 110, 135),
                            Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent))},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onBoundsChange(android.graphics.Rect bounds) {
            rect.set(bounds);
            float inset = strokeW / 2f;
            strokeRect.set(bounds.left + inset, bounds.top + inset,
                    bounds.right - inset, bounds.bottom - inset);
            rebuild();
        }

        private float r() {
            return Math.min(radius, Math.min(rect.width(), rect.height()) / 2f);
        }

        @Override
        public void draw(Canvas canvas) {
            if (rect.isEmpty()) return;
            float r = r();
            canvas.drawRoundRect(rect, r, r, fill);
            if (!flat) {
                canvas.drawRoundRect(rect, r, r, glow);
            }
            canvas.drawRoundRect(strokeRect, r, r, stroke);

        }

        @Override
        public void getOutline(Outline outline) {
            outline.setRoundRect(getBounds(), r());
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}