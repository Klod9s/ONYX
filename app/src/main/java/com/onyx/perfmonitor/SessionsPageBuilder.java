package com.onyx.perfmonitor;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

final class SessionsPageBuilder {
    private final MainActivity activity;

    SessionsPageBuilder(MainActivity activity) {
        this.activity = activity;
    }

    View build() {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(13, 18, 26));
        page.setPadding(activity.dp(18), activity.dp(8), activity.dp(18), 0);

        LinearLayout sessionsHeader = activity.tabHeader(R.drawable.ic_nav_sessions, "SESSIONS");

        // count pill, same style as the status pill on Monitor
        LinearLayout countPill = new LinearLayout(activity);
        countPill.setGravity(Gravity.CENTER_VERTICAL);
        countPill.setPadding(activity.dp(12), activity.dp(7), activity.dp(12), activity.dp(7));

        android.graphics.drawable.GradientDrawable countBg =
                new android.graphics.drawable.GradientDrawable();
        countBg.setColor(Color.rgb(25, 33, 45));
        countBg.setCornerRadius(activity.dp(50));
        countBg.setStroke(activity.dp(1), Color.rgb(43, 52, 65));
        countPill.setBackground(countBg);

        activity.sessionsCount = activity.text("0 SAVED", 10, true, Color.rgb(205, 214, 225));
        activity.sessionsCount.setLetterSpacing(0.08f);
        activity.sessionsCount.setSingleLine(true);
        countPill.addView(activity.sessionsCount);

        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.rightMargin = activity.dp(4);
        sessionsHeader.addView(countPill, countParams);

        activity.sessionsActions = new LinearLayout(activity);
        activity.sessionsActions.setOrientation(LinearLayout.HORIZONTAL);
        activity.sessionsActions.addView(activity.headerIcon(R.drawable.ic_import, "Import CSV",
                v -> activity.importSession()), new LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)));
        activity.sessionsActions.addView(activity.headerIcon(R.drawable.ic_more_vert, "More options",
                v -> activity.showTopMenu(v)), new LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)));
        sessionsHeader.addView(activity.sessionsActions);

        activity.sessionsCancel = activity.text("CANCEL", 13, true, activity.getAppAccentColor());
        activity.sessionsCancel.setPadding(activity.dp(12), activity.dp(10), activity.dp(4), activity.dp(10));

        activity.add(page, sessionsHeader, 0, 12);
        page.setClipToPadding(false);

        activity.pinBanner = new LinearLayout(activity);
        activity.pinBanner.setOrientation(LinearLayout.HORIZONTAL);
        activity.pinBanner.setGravity(Gravity.CENTER_VERTICAL);
        activity.pinBanner.setPadding(activity.dp(12), 0, activity.dp(4), 0);
        activity.pinBanner.setVisibility(View.GONE);

        activity.pinBannerBg = new android.graphics.drawable.GradientDrawable();
        activity.pinBannerBg.setCornerRadius(activity.dp(10));
        activity.pinBanner.setBackground(activity.pinBannerBg);

        activity.pinBannerText = activity.text("Pinned as A · swipe or tap another to compare",
                13, false, activity.getAppAccentColor());
        activity.pinBanner.addView(activity.pinBannerText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        activity.pinBannerCancel = activity.text("Cancel", 13, true, Color.WHITE);
        activity.pinBannerCancel.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));
        activity.pinBannerCancel.setOnClickListener(v -> {
            activity.pinnedFile = null;
            activity.updatePinUi(true);
        });
        activity.pinBanner.addView(activity.pinBannerCancel);

        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(40));
        bannerParams.bottomMargin = activity.dp(12);
        page.addView(activity.pinBanner, bannerParams);

        // Empty-state card
        activity.sessionsEmpty = activity.text(
                "No saved sessions yet.\nStart a session on the Monitor tab and it will show up here.",
                13, false, Color.rgb(145, 160, 180));
        activity.sessionsEmpty.setLineSpacing(activity.dp(3), 1f);
        activity.sessionsEmpty.setPadding(activity.dp(14), activity.dp(16), activity.dp(14), activity.dp(16));
        activity.sessionsEmpty.setBackground(activity.cardBg(14));
        activity.sessionsEmpty.setElevation(activity.dp(3));
        activity.add(page, activity.sessionsEmpty, 0, 8);

        // The list
        activity.sessionsAdapter = new SessionsAdapter(activity, new SessionsAdapter.Host() {
            @Override public boolean isPinned(String name) {
                return name.equals(activity.pinnedFile);
            }
            @Override public void onCardTap(String name) { activity.onSessionTapped(name); }
            @Override public void onMenu(View anchor, String name) {
                activity.showSessionMenu(anchor, name);
            }
        });

        activity.sessionsList = new RecyclerView(activity);
        activity.sessionsList.setLayoutManager(new LinearLayoutManager(activity));
        activity.sessionsList.setAdapter(activity.sessionsAdapter);
        activity.sessionsList.setClipToPadding(false);
        activity.sessionsList.setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(90));
        activity.sessionsList.setOverScrollMode(View.OVER_SCROLL_NEVER);

        if (activity.sessionsList.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) activity.sessionsList.getItemAnimator())
                    .setSupportsChangeAnimations(false);
        }
        new ItemTouchHelper(activity.createSessionsSwipeCallback()).attachToRecyclerView(activity.sessionsList);

        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.setMargins(-activity.dp(8), -activity.dp(8), -activity.dp(8), 0);
        page.addView(activity.sessionsList, listParams);

        return page;
    }
}
