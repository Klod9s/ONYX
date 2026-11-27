package com.onyx.perfmonitor;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Owns pending session deletions and the undo window/UI. */
final class SessionDeleteController {
    private static final long UNDO_MS = 5500;

    private final MainActivity activity;
    private final LinkedHashSet<String> pendingDeletes = new LinkedHashSet<>();
    private boolean historyFileDeleteRequested;
    private LinearLayout undoBar;
    private TextView undoText;
    private TextView undoAction;
    private final Runnable finalizeRunnable = this::finalizePendingDeletes;

    SessionDeleteController(MainActivity activity) {
        this.activity = activity;
    }

    Set<String> getPendingDeletes() {
        return pendingDeletes;
    }

    /** Hides sessions immediately; files are removed when the undo window ends. */
    void queueDelete(Collection<String> names, boolean alsoHistoryFile) {
        if (names.isEmpty()) return;

        pendingDeletes.addAll(names);
        if (alsoHistoryFile) historyFileDeleteRequested = true;
        if (activity.pinnedFile != null && names.contains(activity.pinnedFile)) {
            activity.pinnedFile = null;
            activity.updatePinUi(false);
        }
        activity.loadHistory();
        showUndoBar();
    }

    private void showUndoBar() {
        if (undoBar == null) {
            undoBar = new LinearLayout(activity);
            undoBar.setOrientation(LinearLayout.HORIZONTAL);
            undoBar.setGravity(Gravity.CENTER_VERTICAL);
            undoBar.setPadding(activity.dp(14), 0, activity.dp(4), 0);
            undoBar.setElevation(activity.dp(8));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.rgb(42, 52, 68));
            bg.setCornerRadius(activity.dp(10));
            bg.setStroke(activity.dp(1), Color.rgb(52, 66, 90));
            undoBar.setBackground(bg);

            undoText = activity.text("", 14, false, Color.WHITE);
            undoBar.addView(undoText, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            undoAction = activity.text("Undo", 14, true,
                    activity.getAppAccentColor());
            undoAction.setPadding(activity.dp(14), activity.dp(12),
                    activity.dp(14), activity.dp(12));
            undoAction.setOnClickListener(v -> undoDeletes());
            undoBar.addView(undoAction);
        }

        int count = pendingDeletes.size();
        undoText.setText(count == 1
                ? "Session deleted" : count + " sessions deleted");
        undoAction.setTextColor(activity.getAppAccentColor());

        if (undoBar.getParent() == null) {
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            activity.dp(48));
            lp.gravity = Gravity.BOTTOM;
            lp.setMargins(activity.dp(12), 0, activity.dp(12),
                    activity.dp(16));
            activity.pageContainer.addView(undoBar, lp);
        }

        // Every additional deletion restarts the countdown.
        activity.handler.removeCallbacks(finalizeRunnable);
        activity.handler.postDelayed(finalizeRunnable, UNDO_MS);
    }

    private void hideUndoBar() {
        if (undoBar != null && undoBar.getParent() != null) {
            activity.pageContainer.removeView(undoBar);
        }
    }

    private void undoDeletes() {
        activity.handler.removeCallbacks(finalizeRunnable);
        pendingDeletes.clear();
        historyFileDeleteRequested = false;
        hideUndoBar();
        // Scroll only after the restored items have been submitted to the adapter.
        activity.loadHistory(true);
    }

    /** Permanently deletes all pending files. Safe to call at any time. */
    void finalizePendingDeletes() {
        activity.handler.removeCallbacks(finalizeRunnable);

        // Snapshot UI-owned state, then perform filesystem deletion on the
        // shared file worker so tab changes never block the main thread.
        Set<String> deleting = new LinkedHashSet<>(pendingDeletes);
        boolean deleteHistory = historyFileDeleteRequested;
        pendingDeletes.clear();
        historyFileDeleteRequested = false;
        hideUndoBar();

        activity.executeFileTask(() -> SessionFileManager.finalizePendingDeletes(
                activity.getFilesDir(),
                activity.getSharedPreferences("session_meta", MainActivity.MODE_PRIVATE),
                deleting,
                deleteHistory));
    }
}
