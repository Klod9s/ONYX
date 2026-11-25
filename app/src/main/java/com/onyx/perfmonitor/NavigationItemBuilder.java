package com.onyx.perfmonitor;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class NavigationItemBuilder {
    private final MainActivity activity;

    NavigationItemBuilder(MainActivity activity) {
        this.activity = activity;
    }

        void addNavigationItem(LinearLayout bottomNavigation, String label, int tab) {
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(activity.dp(2), activity.dp(4), activity.dp(2), activity.dp(4));
    
            android.widget.ImageView icon = new android.widget.ImageView(activity);
    
            int[] iconResources = {
                    R.drawable.ic_nav_monitor,
                    R.drawable.ic_nav_sessions,
                    R.drawable.ic_nav_settings,
                    R.drawable.ic_nav_about
            };
    
            icon.setImageResource(iconResources[tab]);
            icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
    
            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(activity.dp(24), activity.dp(24));
            item.addView(icon, iconParams);
    
            TextView text = new TextView(activity);
            text.setText(label);
            text.setTextSize(10);
            text.setGravity(Gravity.CENTER);
            text.setTypeface(null, android.graphics.Typeface.BOLD);
    
            LinearLayout.LayoutParams textParams =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
            textParams.topMargin = activity.dp(2);
            item.addView(text, textParams);
    
            View indicator = new View(activity);
            LinearLayout.LayoutParams indicatorParams =
                    new LinearLayout.LayoutParams(activity.dp(32), activity.dp(2));
            indicatorParams.topMargin = activity.dp(3);
            item.addView(indicator, indicatorParams);
            activity.registerNavigationViews(tab, icon, text, indicator);
    
            item.setOnClickListener(v -> activity.showTab(tab));
    
            bottomNavigation.addView(item, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
            ));
        }
    
}
