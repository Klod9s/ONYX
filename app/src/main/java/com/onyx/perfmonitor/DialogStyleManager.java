package com.onyx.perfmonitor;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.app.AlertDialog;

/** Applies ONYX's shared dark styling to standard alert dialogs. */
final class DialogStyleManager {
    private final MainActivity activity;

    DialogStyleManager(MainActivity activity) {
        this.activity = activity;
    }

    void show(AlertDialog.Builder builder, boolean destructive) {
        final AlertDialog dialog = builder.create();

        dialog.setOnShowListener(ignored -> {
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.rgb(25, 33, 45));
            background.setCornerRadius(activity.dp(16));
            background.setStroke(activity.dp(1), Color.rgb(55, 70, 88));

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(background);
            }

            int titleId = activity.getResources()
                    .getIdentifier("alertTitle", "id", "android");
            TextView title = dialog.findViewById(titleId);
            if (title != null) title.setTextColor(Color.WHITE);

            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) {
                message.setTextColor(Color.rgb(210, 220, 232));
            }

            int accent = activity.getAppAccentColor();
            int positiveColor = destructive
                    ? Color.rgb(240, 135, 135) : accent;

            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (positive != null) positive.setTextColor(positiveColor);
            if (negative != null) {
                negative.setTextColor(Color.rgb(145, 158, 175));
            }
        });

        dialog.show();
    }
}
