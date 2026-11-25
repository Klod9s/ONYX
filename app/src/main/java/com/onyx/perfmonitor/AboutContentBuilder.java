package com.onyx.perfmonitor;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AboutContentBuilder {
    private final MainActivity activity;

    AboutContentBuilder(MainActivity activity) {
        this.activity = activity;
    }

    void build(android.widget.ScrollView aboutPage) {
        LinearLayout content = (LinearLayout) aboutPage.getChildAt(0);
        content.removeViewAt(0);
        activity.aboutRefreshers.clear();

        // Simple header matching the other tabs
        activity.add(content, activity.tabHeader(R.drawable.ic_nav_about, "ABOUT"), 0, 12);

        // ================= CARD 1: app info =================
        LinearLayout infoCard = aboutCard();

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView appTitle = activity.section("ONYX PERFORMANCE MONITOR");
        appTitle.setSingleLine(true);
        titleRow.addView(appTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String versionName = "1.0.0";
        try {
            versionName = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }

        TextView versionPill = activity.text("v" + versionName + " • STABLE", 9, true,
                activity.getAppAccentColor());
        versionPill.setSingleLine(true);
        versionPill.setPadding(activity.dp(10), activity.dp(4), activity.dp(10), activity.dp(4));
        android.graphics.drawable.GradientDrawable pillBg =
                new android.graphics.drawable.GradientDrawable();
        pillBg.setCornerRadius(activity.dp(50));
        pillBg.setColor(Color.rgb(27, 38, 54));
        pillBg.setStroke(activity.dp(1), activity.getAppAccentColor());
        versionPill.setBackground(pillBg);
        activity.appAccentTextViews.add(versionPill);
        activity.aboutRefreshers.add(() -> pillBg.setStroke(activity.dp(1), activity.getAppAccentColor()));

        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pillParams.leftMargin = activity.dp(8);
        titleRow.addView(versionPill, pillParams);
        activity.add(infoCard, titleRow, 0, 8);

        TextView desc = activity.text(
                "A comprehensive, lightweight performance analysis overlay. Measures FPS, frametime, 1% lows, stutters, and hardware telemetry with smart foreground-only refreshing.",
                12, false, Color.rgb(145, 158, 175));
        desc.setLineSpacing(activity.dp(2), 1f);
        activity.add(infoCard, desc, 0, 12);

        LinearLayout infoRow = new LinearLayout(activity);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        left.rightMargin = activity.dp(4);
        infoRow.addView(aboutInfoBox("DEVELOPER", "Klod9s"), left);

        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        right.leftMargin = activity.dp(4);
        infoRow.addView(aboutInfoBox("LICENSE", "GNU GPLv3"), right);
        activity.add(infoCard, infoRow, 0, 0);

        activity.add(content, infoCard, 0, 14);

        // ================= CARD 2: support =================
        LinearLayout supportCard = aboutCard();
        activity.add(supportCard, activity.section("SUPPORT DEVELOPMENT"), 0, 8);

        TextView supportText = activity.text(
                "ONYX is free, ad-free, and open source. If this tool helps you benchmark or optimize your device, consider supporting the project.",
                12, false, Color.rgb(145, 158, 175));
        supportText.setLineSpacing(activity.dp(2), 1f);
        activity.add(supportCard, supportText, 0, 12);

        LinearLayout coffee = aboutButton("BUY ME A COFFEE", true, R.drawable.ic_coffee);
        coffee.setOnClickListener(v -> activity.openUrl(activity.URL_COFFEE));
        LinearLayout.LayoutParams coffeeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(46));
        coffeeParams.bottomMargin = activity.dp(8);
        supportCard.addView(coffee, coffeeParams);

        LinearLayout linkRow = new LinearLayout(activity);
        linkRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout github = aboutButton("GITHUB", false, R.drawable.ic_github);
        github.setOnClickListener(v -> activity.openUrl(activity.URL_GITHUB));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, activity.dp(44), 1f);
        gp.rightMargin = activity.dp(4);
        linkRow.addView(github, gp);

        LinearLayout xda = aboutButton("XDA THREAD", false, R.drawable.ic_xda);
        xda.setOnClickListener(v -> activity.openUrl(activity.URL_XDA));
        LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(0, activity.dp(44), 1f);
        xp.leftMargin = activity.dp(4);
        linkRow.addView(xda, xp);
        activity.add(supportCard, linkRow, 0, 0);

        activity.add(content, supportCard, 0, 14);

        // ================= CARD 3: how metrics work =================
        LinearLayout metricsCard = aboutCard();
        activity.add(metricsCard, activity.section("HOW METRICS WORK"), 0, 10);

        addAboutBullet(metricsCard, "Frame Pacing & Analysis",
                "Real-time FPS, frametime graph, 1% and 0.1% lows, and stutter counts for deep performance analysis.", 10);
        addAboutBullet(metricsCard, "Hardware Telemetry",
                "Live CPU and GPU utilization percentages fetched directly from hardware performance counters.", 10);
        addAboutBullet(metricsCard, "Thermal & Power",
                "Battery temperature and power state monitoring to help identify performance throttling.", 10);
        addAboutBullet(metricsCard, "Smart Foreground Refresh",
                "Overlay metrics and UI only update when actively in use, ensuring zero background battery drain.", 0);

        activity.add(content, metricsCard, 0, 14);

        // ================= CARD 4: credits =================
        LinearLayout creditsCard = aboutCard();
        activity.add(creditsCard, activity.section("CREDITS & ACKNOWLEDGMENTS"), 0, 8);

        TextView creditsText = activity.text(
                "Independently developed and tested. Powered by RikkaApps' Shizuku API for privileged telemetry access.",
                12, false, Color.rgb(145, 158, 175));
        creditsText.setLineSpacing(activity.dp(2), 1f);
        activity.add(creditsCard, creditsText, 0, 10);

        View divider = new View(activity);
        divider.setBackgroundColor(Color.rgb(43, 52, 65));
        creditsCard.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(1)));

        LinearLayout licensesRow = new LinearLayout(activity);
        licensesRow.setOrientation(LinearLayout.HORIZONTAL);
        licensesRow.setGravity(Gravity.CENTER_VERTICAL);
        licensesRow.setPadding(0, activity.dp(12), 0, activity.dp(2));

        licensesRow.addView(activity.text("View Open Source Licenses", 14, false, Color.WHITE),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        licensesRow.addView(activity.text("›", 20, false, Color.rgb(145, 158, 175)));

        licensesRow.setOnClickListener(v -> activity.showDarkDialog(
                activity.darkDialogBuilder()
                        .setTitle("Open Source Licenses")
                        .setMessage("• AndroidX Libraries & Jetpack Compose\n"
                                + "  License: Apache License 2.0\n\n"
                                + "• Shizuku API & Provider (RikkaApps)\n"
                                + "  License: Apache License 2.0\n\n"
                                + "• ONYX Performance Monitor\n"
                                + "  License: GNU General Public License v3.0 (or later)\n\n"
                                + "ONYX is free, open-source software. Tap below to read the full legal license text or view the source code.")
                        .setPositiveButton("CLOSE", null)
                        .setNeutralButton("VIEW LICENSE", (dialog, which) -> {
                            // Link to your GitHub repo's LICENSE file, or the official GNU site
                            activity.openUrl("https://www.gnu.org/licenses/gpl-3.0.html");
                        }),
                false));
        creditsCard.addView(licensesRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        activity.add(content, creditsCard, 0, 10);
    }

    private LinearLayout aboutCard() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16));
        card.setBackground(activity.cardBg(16));
        return card;
    }

    private LinearLayout aboutInfoBox(String label, String value) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10));

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(27, 38, 54));
        bg.setCornerRadius(activity.dp(10));
        box.setBackground(bg);

        TextView l = activity.text(label, 11, true, activity.getAppAccentColor());
        activity.appAccentTextViews.add(l);
        box.addView(l);

        TextView v = activity.text(value, 14, true, Color.WHITE);
        v.setSingleLine(true);
        box.addView(v);
        return box;
    }

    private LinearLayout aboutButton(String label, boolean filled, int iconRes) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        android.widget.ImageView icon = new android.widget.ImageView(activity);
        icon.setImageResource(iconRes);
        LinearLayout.LayoutParams ip =
                new LinearLayout.LayoutParams(activity.dp(18), activity.dp(18));
        ip.rightMargin = activity.dp(8);
        row.addView(icon, ip);

        TextView t = activity.text(label, 13, true, Color.WHITE);
        t.setSingleLine(true);
        t.setLetterSpacing(0.04f);
        row.addView(t);

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(activity.dp(12));

        if (filled) {
            Runnable style = () -> {
                int accent = activity.getAppAccentColor();
                bg.setColor(accent);
                t.setTextColor(androidx.core.graphics.ColorUtils
                        .calculateLuminance(accent) > 0.5
                        ? Color.rgb(13, 18, 26) : Color.WHITE);
            };
            style.run();
            activity.aboutRefreshers.add(style);
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(activity.dp(1), Color.rgb(70, 82, 100));
        }

        row.setBackground(bg);
        return row;
    }

    private void addAboutBullet(LinearLayout card, String title, String body, int bottom) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);

        View dot = new View(activity);
        android.graphics.drawable.GradientDrawable dotBg =
                new android.graphics.drawable.GradientDrawable();
        dotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dotBg.setColor(activity.getAppAccentColor());
        dot.setBackground(dotBg);
        activity.aboutRefreshers.add(() -> dotBg.setColor(activity.getAppAccentColor()));

        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(activity.dp(7), activity.dp(7));
        dotParams.topMargin = activity.dp(7);
        dotParams.rightMargin = activity.dp(10);
        row.addView(dot, dotParams);

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(activity.text(title, 14, true, Color.WHITE));

        TextView desc = activity.text(body, 12, false, Color.rgb(145, 158, 175));
        desc.setLineSpacing(activity.dp(2), 1f);
        column.addView(desc);

        row.addView(column, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        activity.add(card, row, 0, bottom);
    }
}
