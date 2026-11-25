package com.onyx.perfmonitor;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

/** Builds the Classic/Strip overlay-style selector in Settings. */
final class OverlayStylePickerBuilder {
    private final MainActivity activity;

    OverlayStylePickerBuilder(MainActivity activity) {
        this.activity = activity;
    }

    void build(LinearLayout card) {
        TextView label = new TextView(activity);
        label.setText("Overlay style");
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        activity.add(card, label, 0, 2);

        RadioGroup group = new RadioGroup(activity);
        group.setOrientation(RadioGroup.HORIZONTAL);

        String[] names = {"Classic", "Strip"};
        int saved = activity.getPrefs().getInt("overlay_style", 0);
        ColorStateList tint = ColorStateList.valueOf(activity.getAppAccentColor());

        for (int i = 0; i < names.length; i++) {
            RadioButton button = new RadioButton(activity);
            button.setId(View.generateViewId());
            button.setText(names[i]);
            button.setTextColor(Color.WHITE);
            button.setButtonTintList(tint);
            activity.appAccentRadioButtons.add(button);
            group.addView(button, new RadioGroup.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (i == saved) button.setChecked(true);
        }

        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            int index = radioGroup.indexOfChild(
                    radioGroup.findViewById(checkedId));
            activity.getPrefs().edit().putInt("overlay_style", index).apply();
        });

        activity.add(card, group, 0, 2);
    }
}
