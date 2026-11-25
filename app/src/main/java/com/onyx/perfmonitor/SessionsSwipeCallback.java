package com.onyx.perfmonitor;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

final class SessionsSwipeCallback extends ItemTouchHelper.Callback {

private final MainActivity activity;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
private final int RED = Color.rgb(240, 104, 122);

    SessionsSwipeCallback(MainActivity activity) {
        this.activity = activity;
        labelPaint.setTextSize(activity.dp(13) * 1f);
        labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
        letterPaint.setTextSize(activity.dp(13) * 1f);
        letterPaint.setTypeface(Typeface.DEFAULT_BOLD);
        letterPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public int getMovementFlags(RecyclerView rv, RecyclerView.ViewHolder vh) {
        // Check if we have enough sessions to allow pinning (left swipe)
        boolean canPin = activity.visibleSessionNames().size() >= 2;

        // Always allow right swipe (delete)
        int swipeFlags = ItemTouchHelper.RIGHT;

        // Only allow left swipe (pin/compare) if we have >= 2 sessions
        if (canPin) {
            swipeFlags |= ItemTouchHelper.LEFT;
        }

        return makeMovementFlags(0, swipeFlags);
    }

    @Override
    public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh,
                          RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public float getSwipeThreshold(RecyclerView.ViewHolder vh) {
        return 0.4f;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder vh, int direction) {
        int pos = vh.getBindingAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) return;

        String name = activity.sessionsAdapter.fileNameAt(pos);
        if (name == null) return;

        if (direction == ItemTouchHelper.RIGHT) {
            activity.sessionsList.performHapticFeedback(
                    android.view.HapticFeedbackConstants.GESTURE_END);
            activity.deleteSessionWithUndo(name);
            return;
        }

        // LEFT: put the card back in place first, then decide what the swipe means
        activity.sessionsAdapter.notifyItemChanged(pos);
        activity.sessionsList.performHapticFeedback(
                android.view.HapticFeedbackConstants.CONFIRM);

        if (name.equals(activity.pinnedFile)) {
            activity.pinnedFile = null;
            activity.updatePinUi(true);
        } else if (activity.pinnedFile == null) {
            activity.setPinned(name);
        } else {
            activity.launchPinnedCompare(name);
        }
    }

    @Override
    public void onChildDraw(Canvas c, RecyclerView rv, RecyclerView.ViewHolder vh,
                            float dX, float dY, int actionState, boolean active) {
        View item = vh.itemView;

        // The card's elevation shadow can remain visible around the revealed
        // action strip, making it look like an unwanted dark edge. Remove it
        // while the row is being dragged, then restore its normal elevation
        // once the swipe settles.
        if (dX != 0f) {
            item.setElevation(0f);
        } else {
            boolean pinned = vh instanceof SessionsAdapter.Holder
                    && ((SessionsAdapter.Holder) vh).fileName.equals(activity.pinnedFile);
            item.setElevation(pinned ? 0f : activity.dp(3));
        }

        if (dX > 0) {
            // ---- swipe right: red delete strip ----
            fill.setColor(Color.rgb(58, 27, 34));
            labelPaint.setColor(RED);

            c.save();
            c.clipRect(item.getLeft(), item.getTop(),
                    item.getLeft() + dX, item.getBottom());
            c.drawRoundRect(item.getLeft(), item.getTop(),
                    item.getRight(), item.getBottom(), activity.dp(14), activity.dp(14), fill);

            android.graphics.drawable.Drawable icon =
                    activity.getDrawable(R.drawable.ic_delete_outline_x).mutate();
            icon.setTint(RED);

            int size = activity.dp(20);
            int cy = (item.getTop() + item.getBottom()) / 2;
            int ix = item.getLeft() + activity.dp(18);
            icon.setBounds(ix, cy - size / 2, ix + size, cy + size / 2);
            icon.draw(c);

            Paint.FontMetrics fm = labelPaint.getFontMetrics();
            c.drawText("Delete", ix + size + activity.dp(8),
                    cy - (fm.ascent + fm.descent) / 2f, labelPaint);
            c.restore();

        } else if (dX < 0) {
            // ---- swipe left: pin / compare / unpin strip ----
            String name = vh instanceof SessionsAdapter.Holder
                    ? ((SessionsAdapter.Holder) vh).fileName : "";

            int color;
            String letter;
            String label;

            if (name.equals(activity.pinnedFile)) {
                color = Color.rgb(145, 158, 175);
                letter = null;
                label = "Unpin";
            } else if (activity.pinnedFile != null) {
                color = AppColors.compareB(activity);
                letter = "B";
                label = "Compare as B";
            } else {
                color = AppColors.accent(activity);
                letter = "A";
                label = "Pin as A";
            }

            // dark base so the strip is clearly visible, then the colour on top
            fill.setColor(Color.rgb(25, 33, 45));
            c.save();
            c.clipRect(item.getRight() + dX, item.getTop(),
                    item.getRight(), item.getBottom());
            c.drawRoundRect(item.getLeft(), item.getTop(),
                    item.getRight(), item.getBottom(), activity.dp(14), activity.dp(14), fill);
            fill.setColor(Color.argb(90,
                    Color.red(color), Color.green(color), Color.blue(color)));
            c.drawRoundRect(item.getLeft(), item.getTop(),
                    item.getRight(), item.getBottom(), activity.dp(14), activity.dp(14), fill);

            labelPaint.setColor(color);
            float cy = (item.getTop() + item.getBottom()) / 2f;
            float right = item.getRight() - activity.dp(18);
            float textW = labelPaint.measureText(label);

            Paint.FontMetrics fm = labelPaint.getFontMetrics();
            c.drawText(label, right - textW,
                    cy - (fm.ascent + fm.descent) / 2f, labelPaint);

            if (letter != null) {
                float size = activity.dp(24);
                float bx = right - textW - activity.dp(8) - size;

                badgePaint.setColor(color);
                c.drawRoundRect(bx, cy - size / 2, bx + size, cy + size / 2,
                        activity.dp(8), activity.dp(8), badgePaint);

                letterPaint.setColor(
                        androidx.core.graphics.ColorUtils
                                .calculateLuminance(color) > 0.6
                                ? Color.rgb(13, 18, 26) : Color.WHITE);
                Paint.FontMetrics lf = letterPaint.getFontMetrics();
                c.drawText(letter, bx + size / 2,
                        cy - (lf.ascent + lf.descent) / 2f, letterPaint);
            }
            c.restore();
        }

        super.onChildDraw(c, rv, vh, dX, dY, actionState, active);
    }
}

