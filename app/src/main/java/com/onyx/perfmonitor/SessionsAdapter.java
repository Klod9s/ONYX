package com.onyx.perfmonitor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionsAdapter extends RecyclerView.Adapter<SessionsAdapter.Holder> {

    public interface Host {
        boolean isPinned(String fileName);
        void onCardTap(String fileName);
        void onMenu(View anchor, String fileName);
    }

    private final Context context;
    private final Host host;
    private final List<SessionItem> items = new ArrayList<>();

    private static final Object STATS_CHANGED = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService statsWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "onyx-session-stats");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, String> statsCache = new HashMap<>();
    private final Set<String> statsLoading = new HashSet<>();

    private void applyCardPadding(View card) {
        card.setPadding(dp(14), dp(4), dp(6), dp(10));
    }

    private void bindStats(Holder h, String fileName) {
        String cached = statsCache.get(fileName);
        if (cached != null) {
            h.stats.setText(cached);
            return;
        }
        h.stats.setText(" ");   // keeps the card height while loading
        loadStats(fileName);
    }

    private void loadStats(final String fileName) {
        if (!statsLoading.add(fileName)) return;   // already on its way

        statsWorker.execute(() -> {
            String line = buildStatsLine(
                    SessionData.load(new File(context.getFilesDir(), fileName)));

            mainHandler.post(() -> {
                statsLoading.remove(fileName);
                statsCache.put(fileName, line);
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i).fileName.equals(fileName)) {
                        notifyItemChanged(i, STATS_CHANGED);   // partial rebind only
                        break;
                    }
                }
            });
        });
    }

    private String buildStatsLine(SessionData d) {
        if (d == null || d.times.isEmpty()) return "No readings";

        List<String> parts = new ArrayList<>();

        double seconds = d.times.get(d.times.size() - 1);
        parts.add(seconds < 60
                ? String.format(Locale.US, "%.0f s", seconds)
                : String.format(Locale.US, "%.1f min", seconds / 60.0));

        double fpsSum = 0;
        int fpsCount = 0;
        for (double v : d.fps) {
            if (v > 0) { fpsSum += v; fpsCount++; }
        }
        if (fpsCount > 0) {
            parts.add(String.format(Locale.US, "avg %.0f FPS", fpsSum / fpsCount));
        }

        double first = -1, last = -1;
        for (double v : d.battery) {
            if (v >= 0) {
                if (first < 0) first = v;
                last = v;
            }
        }
        if (first >= 0) {
            int change = (int) Math.round(last - first);
            parts.add(change == 0
                    ? "0% battery"
                    : (change > 0 ? "+" : "−") + Math.abs(change) + "% battery");
        }

        return TextUtils.join("  ·  ", parts);
    }

    public SessionsAdapter(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    public void submit(List<SessionItem> newItems) {
        final List<SessionItem> old = new ArrayList<>(items);

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return newItems.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                return old.get(o).fileName.equals(newItems.get(n).fileName);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                return false;
            }
        });

        items.clear();
        items.addAll(newItems);
        result.dispatchUpdatesTo(this);
    }

    public String fileNameAt(int position) {
        return position >= 0 && position < items.size()
                ? items.get(position).fileName : null;
    }

    private int dp(float v) {
        return (int) (v * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(int size, boolean bold, int color) {
        TextView t = new TextView(context);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    /** Card with a still, constant glow: two faint rings behind an accent border. */
    private Drawable pinnedBackground(int accent) {
        int r = Color.red(accent), g = Color.green(accent), b = Color.blue(accent);

        GradientDrawable outer = new GradientDrawable();
        outer.setColor(Color.argb(20, r, g, b));
        outer.setCornerRadius(dp(21));

        GradientDrawable inner = new GradientDrawable();
        inner.setColor(Color.argb(56, r, g, b));
        inner.setCornerRadius(dp(17));

        GradientDrawable card = new GradientDrawable();
        card.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        card.setColors(new int[]{Color.rgb(22, 32, 48), Color.rgb(15, 22, 34)});
        card.setCornerRadius(dp(14));
        card.setStroke(dp(1.5f), accent);

        LayerDrawable layers = new LayerDrawable(new Drawable[]{outer, inner, card});
        layers.setLayerInset(0, -dp(7), -dp(7), -dp(7), -dp(7));
        layers.setLayerInset(1, -dp(3), -dp(3), -dp(3), -dp(3));
        return layers;
    }

    static class Holder extends RecyclerView.ViewHolder {
        LinearLayout card;
        GradientDrawable bg;
        ImageButton menu;
        TextView badge, date, stats, device, imported;
        String fileName = "";

        Holder(LinearLayout card) {
            super(card);
            this.card = card;
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        applyCardPadding(card);

        // Same gradient as the Monitor cards. Keep the radius at 14dp:
        // the swipe strips in SwipeCallback are drawn with the same radius.
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(22, 32, 48), Color.rgb(15, 22, 34)});
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        card.setElevation(dp(3));

        RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(8);
        card.setLayoutParams(rlp);

        Holder h = new Holder(card);
        h.bg = bg;

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        h.date = text(15, true, Color.WHITE);
        titleRow.addView(h.date, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        h.menu = new ImageButton(context);
        h.menu.setImageResource(R.drawable.ic_more_vert);
        h.menu.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.menu.setTooltipText("Options");
        h.menu.setContentDescription("Session options");
        h.menu.setBackgroundColor(Color.TRANSPARENT);
        h.menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        h.menu.setOnClickListener(v -> host.onMenu(v, h.fileName));
        titleRow.addView(h.menu, new LinearLayout.LayoutParams(dp(40), dp(40)));

        // "A" badge: shown instead of the menu on the pinned card
        h.badge = new TextView(context);
        h.badge.setText("A");
        h.badge.setTextSize(14);
        h.badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        h.badge.setGravity(Gravity.CENTER);
        h.badge.setVisibility(View.GONE);
        LinearLayout.LayoutParams badgeParams =
                new LinearLayout.LayoutParams(dp(26), dp(26));
        badgeParams.setMargins(0, 0, dp(7), 0);
        titleRow.addView(h.badge, badgeParams);

        card.addView(titleRow);

        h.stats = text(12, false, Color.rgb(180, 194, 210));
        card.addView(h.stats, lp(0));

        h.device = text(10, false, Color.rgb(125, 136, 150));
        card.addView(h.device, lp(3));

        h.imported = text(10, true, Color.rgb(255, 190, 80));
        card.addView(h.imported, lp(2));

        int side = dp(6);
        h.date.setPadding(side, 0, 0, 0);
        h.stats.setPadding(side, 0, 0, 0);
        h.device.setPadding(side, 0, 0, 0);
        h.imported.setPadding(side, 0, 0, 0);

        return h;
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        SessionItem item = items.get(position);
        h.fileName = item.fileName;

        boolean pinned = host.isPinned(item.fileName);
        int accent = AppColors.accent(context);

        if (pinned) {
            h.card.setBackground(pinnedBackground(accent));
            h.card.setElevation(0);
        } else {
            h.bg.setStroke(dp(1), Color.argb(110,
                    Color.red(accent), Color.green(accent), Color.blue(accent)));
            h.card.setBackground(h.bg);
            h.card.setElevation(dp(3));
        }
        // setBackground can change the padding (pinned glow), so always reapply
        applyCardPadding(h.card);

        h.menu.setVisibility(pinned ? View.GONE : View.VISIBLE);

        h.badge.setVisibility(pinned ? View.VISIBLE : View.GONE);
        if (pinned) {
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(accent);
            badgeBg.setCornerRadius(dp(8));
            h.badge.setBackground(badgeBg);
            h.badge.setTextColor(
                    androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.6
                            ? Color.rgb(13, 18, 26) : Color.WHITE);
        }

        Date when = new Date(item.sessionTimeMs);
        String d = new SimpleDateFormat("MMM dd, yyyy",
                Locale.getDefault()).format(when);
        String t = new SimpleDateFormat("hh:mm a",
                Locale.getDefault()).format(when);

        SpannableStringBuilder line = new SpannableStringBuilder(d + "  ·  " + t);
        line.setSpan(new ForegroundColorSpan(accent),
                d.length() + 5, line.length(), 0);
        h.date.setText(line);

        bindStats(h, item.fileName);
        h.device.setText(item.deviceLabel);

        h.imported.setVisibility(item.imported ? View.VISIBLE : View.GONE);
        if (item.imported) {
            h.imported.setText("IMPORTED  ·  " + item.originalName);
        }

        h.card.setOnClickListener(v -> host.onCardTap(h.fileName));
    }

    // When stats finish loading, only the stats line is updated, so a card
// that's mid-swipe isn't disturbed.
    @Override
    public void onBindViewHolder(@NonNull Holder h, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(STATS_CHANGED)
                && position < items.size()) {
            bindStats(h, items.get(position).fileName);
            return;
        }
        super.onBindViewHolder(h, position, payloads);
    }

    // Return the currently displayed session filenames without touching disk.
    public List<String> fileNames() {
        List<String> names = new ArrayList<>(items.size());
        for (SessionItem item : items) {
            names.add(item.fileName);
        }
        return names;
    }

    public int getItemCount() {
        return items.size();
    }
}