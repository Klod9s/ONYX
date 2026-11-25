/*
 * Copyright (C) 2026 Klod9s
 *
 * This file is part of ONYX Performance Monitor.
 *
 * ONYX Performance Monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ONYX Performance Monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ONYX Performance Monitor. If not, see <https://www.gnu.org/licenses/>.
 */

package com.onyx.perfmonitor;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    public MainActivity() {
    }
    private static final int SHIZUKU_REQUEST_CODE = 1001;

    private final Shizuku.OnRequestPermissionResultListener
            shizukuPermissionListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        "Shizuku permission granted!",
                        Toast.LENGTH_SHORT).show();

                startShizukuService();
            } else {
                Toast.makeText(this,
                        "Shizuku permission denied.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    };

    private IOnyxUserService shizukuService;
    private boolean shizukuBindRequested = false;

    private Shizuku.UserServiceArgs shizukuServiceArgs;

    private final ServiceConnection shizukuServiceConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(
                        ComponentName name, IBinder binder) {

                    if (binder != null && binder.pingBinder()) {
                        shizukuService =
                                IOnyxUserService.Stub.asInterface(binder);
                        if (monitoringEngine != null) {
                            monitoringEngine.setShizukuService(shizukuService);
                        }
                        shizukuBindRequested = false;

                        runOnUiThread(() ->
                                Toast.makeText(
                                        MainActivity.this,
                                        "Shizuku service connected!",
                                        Toast.LENGTH_SHORT
                                ).show()
                        );
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    shizukuService = null;
                    if (monitoringEngine != null) {
                        monitoringEngine.setShizukuService(null);
                    }
                    shizukuBindRequested = false;
                }
            };

    private static final String PREFS = "overlay_settings";

    private Button overlayButton;
    private android.graphics.drawable.GradientDrawable customColorButtonBg;
    private TextView customColorCheck;
    private final ArrayList<Runnable> aboutRefreshers = new ArrayList<>();

    // TODO: put your real links here
    private static final String URL_COFFEE = "https://buymeacoffee.com/klod9s";
    private static final String URL_GITHUB = "https://github.com/Klod9s";
    private static final String URL_XDA = "https://xdaforums.com/m/klod9s.13504375/";


    private View statusDot;
    private View headerLine;
    private android.graphics.drawable.GradientDrawable statusDotBg;
    private TextView status, fps, power, current, voltage, battery;
    private OnyxLogoView brand;

    // BOTTOM NAVIGATION
    private android.widget.FrameLayout pageContainer;
    private ScrollView monitorPage, settingsPage, aboutPage;
    private View sessionsPage;
    private RecyclerView sessionsList;
    private SessionsAdapter sessionsAdapter;
    private TextView sessionsEmpty;
    private TextView sessionsCount;
    private String pinnedFile = null;          // not saved anywhere, on purpose
    private LinearLayout pinBanner;
    private TextView pinBannerText, pinBannerCancel;
    private android.graphics.drawable.GradientDrawable pinBannerBg;
    private LinearLayout bottomNavigation;
    private int selectedTab = 0;
    private TextView[] navLabels = new TextView[4];
    private final ArrayList<TextView> appAccentTextViews =
            new ArrayList<>();

    private final ArrayList<android.widget.RadioButton> appAccentRadioButtons =
            new ArrayList<>();

    private final ArrayList<CheckBox> appAccentCheckBoxes =
            new ArrayList<>();

    private final ArrayList<Button> appAccentButtons =
            new ArrayList<>();

    private final ArrayList<android.widget.SeekBar> appAccentSeekBars =
            new ArrayList<>();
    private android.widget.ImageView[] navIcons =
            new android.widget.ImageView[4];

    private View[] navIndicators = new View[4];

    private OverlayMetricsStore overlayMetricsStore;
    private MonitoringEngine monitoringEngine;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        cachedMonitoringMode = "";
        if (monitoringEngine != null) {
            monitoringEngine.resetMonitoringModeCheck();
        }

        runOnUiThread(() -> {
            shizukuBindRequested = false;
            startShizukuService();
        });
    };

    private void setStatus(String text, boolean error) {
        if (status == null) return;
        status.setText(text);
        if (statusDotBg != null) {
            int dotColor;
            if (error) {
                dotColor = Color.rgb(240, 104, 122);        // disconnected
            } else if (running) {
                dotColor = AppColors.compareB(this);        // recording: same partner color as Session Compare
            } else {
                dotColor = getAppAccentColor();             // ready: app accent
            }
            statusDotBg.setColor(dotColor);
        }
    }

    private void refreshStatusPill() {
        if (cachedMonitoringMode.isEmpty()) {
            setStatus("CHECKING…", false);
        } else {
            setStatus(cachedMonitoringMode + (running ? " · RECORDING" : " · READY"), false);
        }
    }

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        shizukuService = null;
        if (monitoringEngine != null) {
            monitoringEngine.setShizukuService(null);
        }
        shizukuBindRequested = false;

        runOnUiThread(() -> {
            setStatus("SHIZUKU · DISCONNECTED", true);
        });
    };
    private TextView temperature, packageName, summary;
    private TextView powerState;
    private TextView cpuTile, gpuTile;
    private String pendingExportFile;
    private boolean compareMode = false;
    private final java.util.ArrayList<String> selectedForCompare =
            new java.util.ArrayList<>();
    private LinearLayout sessionsActions;

    private LinearLayout overlaySettingsCard;
    private TextView sessionsCancel;
    private Button floatingCompareButton;
    private final TextView[] cpuViews = new TextView[8];

    private CheckBox showFps, showTemperature;
    private Button startButton, stopButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService fileWorker = Executors.newSingleThreadExecutor();
    private int historyGeneration = 0;
    private final ArrayList<Sample> samples = new ArrayList<>();

    private final StringBuilder csv = new StringBuilder();

    private final java.util.ArrayList<LinearLayout> appColorOptions =
            new java.util.ArrayList<>();

    private final java.util.ArrayList<TextView> appColorChecks =
            new java.util.ArrayList<>();

    private final java.util.ArrayList<android.graphics.drawable.GradientDrawable>
            appColorPillBackgrounds = new java.util.ArrayList<>();

    private android.graphics.drawable.Drawable customRainbowDrawable;

    private boolean running = false;
    private int sessionId = 0;
    private boolean isAppForeground = false;
    private String cachedMonitoringMode = "";


    private final BroadcastReceiver overlayToggleReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (FloatingFpsService.ACTION_TOGGLE.equals(intent.getAction())) {
                        if (running) {
                            stopSession();
                            if (!isAppForeground
                                    && getPrefs().getBoolean("overlay_enabled", false)) {
                                handoffOverlaySampling();
                            }
                        } else {
                            stopOverlaySampling();
                            startSession();
                            wakeSampler();
                        }
                    }
                }
            };


    private OnyxHeroBannerView heroBanner;


    private boolean samplerBusy = false;

    private void handoffOverlaySampling() {
        if (!getPrefs().getBoolean("overlay_enabled", false)) return;
        getPrefs().edit().putBoolean("overlay_engine_active", true).apply();
        Intent intent = new Intent(this, FloatingFpsService.class);
        intent.setAction(FloatingFpsService.ACTION_ENGINE_HANDOFF);
        try {
            startService(intent);
        } catch (Exception ignored) {
        }
    }

    private void stopOverlaySampling() {
        if (!getPrefs().getBoolean("overlay_enabled", false)) return;
        getPrefs().edit().putBoolean("overlay_engine_active", false).apply();
        Intent intent = new Intent(this, FloatingFpsService.class);
        intent.setAction(FloatingFpsService.ACTION_ENGINE_STOP);
        try {
            startService(intent);
        } catch (Exception ignored) {
        }
    }

    // Only sample while the app is visible or a session is recording.
    private boolean samplerNeeded() {
        return isAppForeground || running;
    }

    private void scheduleSampler() {
        if (samplerNeeded()) {
            handler.postDelayed(sampler, 500);
        } else {
            monitoringEngine.resetCpuBaseline();   // avoid averaging CPU usage across the idle gap
        }
    }

    private void wakeSampler() {
        handler.removeCallbacks(sampler);
        sampler.run();
    }

    private final Runnable sampler = new Runnable() {
        @Override
        public void run() {
            if (samplerBusy) return;     // a cycle is already running and will reschedule itself
            samplerBusy = true;

            final int currentSessionId = sessionId;
            final boolean recording = running;

            worker.execute(() -> {
                String detected = "ANDROID";
                Sample taken = null;
                try {
                    taken = monitoringEngine.sample(recording);
                    detected = monitoringEngine.getMonitoringMode();
                    cachedMonitoringMode = detected;

                    if ("SHIZUKU".equals(detected)
                            && shizukuService == null
                            && !shizukuBindRequested) {
                        runOnUiThread(() -> {
                            if (shizukuService == null && !shizukuBindRequested) {
                                shizukuBindRequested = true;
                                startShizukuService();
                            }
                        });
                    }
                } catch (Throwable ignored) {
                }
                final String mode = detected;
                final Sample sample = taken;

                runOnUiThread(() -> {
                    samplerBusy = false;

                    if (currentSessionId != sessionId) {
                        scheduleSampler();
                        return;
                    }
                    if (isAppForeground) {
                        setStatus(mode + (running ? " · RECORDING" : " · READY"), false);
                    }
                    if (sample != null) {
                        overlayMetricsStore.publish(
                                sample.fps,
                                sample.temperatureC,
                                sample.averageFrameTimeMs,
                                sample.cpu,
                                sample.gpuLoadPercent
                        );
                        if (running) {
                            if (!samples.isEmpty()) {
                                Sample previous = samples.get(samples.size() - 1);

                                int drop = previous.battery - sample.battery;
                                long elapsed = sample.timeMs - previous.timeMs;

                                boolean previousBatteryValid =
                                        previous.battery >= 0 && previous.battery <= 100;
                                boolean currentBatteryValid =
                                        sample.battery >= 0 && sample.battery <= 100;

                                if (previousBatteryValid && currentBatteryValid
                                        && drop > 0 && elapsed > 0) {
                                    sample.secondsPerPercent =
                                            (elapsed / 1000.0) / drop;
                                }
                            }

                            samples.add(sample);
                            appendCsv(sample);
                        }

                        if (isAppForeground) {
                            render(sample);
                        }
                    }

                    scheduleSampler();
                });
            });
        }
    };



    private void requestShizukuAccess() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this,
                    "Shizuku is not running. Please start it first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (Shizuku.isPreV11()) {
            Toast.makeText(this,
                    "Your Shizuku version is not supported.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (Shizuku.checkSelfPermission()
                == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                    "Shizuku access already granted!",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE);
    }



    private void startShizukuService() {
        if (!Shizuku.pingBinder()) {
            shizukuBindRequested = false;
            Toast.makeText(this,
                    "Shizuku is not running.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (Shizuku.checkSelfPermission()
                != PackageManager.PERMISSION_GRANTED) {
            shizukuBindRequested = false;
            requestShizukuAccess();
            return;
        }

        try {
            if (shizukuService != null && shizukuService.asBinder().pingBinder()) {
                shizukuBindRequested = false;
                return;
            }
            shizukuBindRequested = true;
            Shizuku.bindUserService(
                    shizukuServiceArgs,
                    shizukuServiceConnection
            );
        } catch (Exception e) {
            Toast.makeText(this,
                    "Failed to start Shizuku service: "
                            + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            shizukuBindRequested = false;
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        overlayMetricsStore = new OverlayMetricsStore(this);
        monitoringEngine = new MonitoringEngine(getApplicationContext());
        shizukuServiceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(
                        getPackageName(),
                        ShizukuShellService.class.getName()
                )
        )
                .daemon(false)
                .processNameSuffix("onyx")
                .debuggable(false)
                .version(1);


        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addRequestPermissionResultListener(
                shizukuPermissionListener
        );
        buildUi();

        getSharedPreferences("session_state", MODE_PRIVATE)
                .edit()
                .putBoolean("recording", false)
                .apply();

        IntentFilter filter =
                new IntentFilter(FloatingFpsService.ACTION_TOGGLE);

        androidx.core.content.ContextCompat.registerReceiver(
                this,
                overlayToggleReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );

        updateOverlayButton();

        refreshStatusPill();

        loadHistory();
        stopOverlaySampling();
        sampler.run();
    }





    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 18, 26));

        brand = new OnyxLogoView(this);

        pageContainer = new android.widget.FrameLayout(this);

        bottomNavigation = new LinearLayout(this);
        bottomNavigation.setOrientation(LinearLayout.HORIZONTAL);
        bottomNavigation.setGravity(Gravity.CENTER_VERTICAL);
        bottomNavigation.setBackgroundColor(Color.rgb(19, 26, 36));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dp(8), 0, dp(8));

// slightly lighter at the top (matches the bottom nav), fading into the page color
        header.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(21, 29, 41), Color.rgb(14, 20, 29)}));

// soft shadow over the content below it
        header.setElevation(dp(6));

        header.addView(brand, new LinearLayout.LayoutParams(
                dp(190),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

// glowing accent line under the header
        headerLine = new View(this);
        updateHeaderLine();
        root.addView(headerLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)));

        root.addView(pageContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        View navDivider = new View(this);
        navDivider.setBackgroundColor(Color.rgb(43, 52, 65));

        root.addView(navDivider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        root.addView(bottomNavigation, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
        ));

        setContentView(root);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(8), dp(18), dp(28));
        page.setBackgroundColor(Color.rgb(13, 18, 26));

        scroll.setBackgroundColor(Color.rgb(13, 18, 26));
        scroll.addView(page);

        monitorPage = scroll;
        pageContainer.addView(monitorPage, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ));


        try {
            java.io.BufferedReader reader =
                    new java.io.BufferedReader(
                            new java.io.FileReader("/proc/stat"));

            reader.close();
        } catch (Exception e) {
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
            );
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        page.setClipChildren(false);
        page.setClipToPadding(false);

// HEADER

        LinearLayout statusPill = new LinearLayout(this);
        statusPill.setOrientation(LinearLayout.HORIZONTAL);
        statusPill.setGravity(Gravity.CENTER_VERTICAL);
        statusPill.setPadding(dp(12), dp(7), dp(12), dp(7));

        android.graphics.drawable.GradientDrawable pillBg =
                new android.graphics.drawable.GradientDrawable();
        pillBg.setColor(Color.rgb(25, 33, 45));
        pillBg.setCornerRadius(dp(50));
        pillBg.setStroke(dp(1), Color.rgb(43, 52, 65));
        statusPill.setBackground(pillBg);

        statusDot = new View(this);
        statusDotBg = new android.graphics.drawable.GradientDrawable();
        statusDotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        statusDotBg.setColor(getAppAccentColor());
        statusDot.setBackground(statusDotBg);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotParams.rightMargin = dp(8);
        statusPill.addView(statusDot, dotParams);

        status = text("CHECKING…", 10, true, Color.rgb(205, 214, 225));
        status.setLetterSpacing(0.08f);
        status.setSingleLine(true);
        statusPill.addView(status);

        LinearLayout monitorHeader = tabHeader(R.drawable.ic_nav_monitor, "MONITOR");
        monitorHeader.addView(statusPill);
        add(page, monitorHeader, 0, 12);

        Button shizukuButton = new Button(this);
        shizukuButton.setText("CONNECT SHIZUKU");
        shizukuButton.setTextSize(12);
        shizukuButton.setAllCaps(false);
        shizukuButton.setTextColor(Color.WHITE);

        android.graphics.drawable.GradientDrawable shizukuBackground =
                new android.graphics.drawable.GradientDrawable();
        shizukuBackground.setColor(Color.rgb(39, 53, 69));
        shizukuBackground.setCornerRadius(dp(12));
        shizukuBackground.setStroke(dp(1), Color.rgb(65, 85, 105));

        shizukuButton.setBackground(shizukuBackground);
        shizukuButton.setOnClickListener(v -> startShizukuService());

// SESSION CONTROLS
        startButton = new Button(this);
        startButton.setText("START SESSION");
        startButton.setTextSize(13);
        startButton.setAllCaps(false);
        startButton.setTextColor(Color.WHITE);

        android.graphics.drawable.GradientDrawable sessionButtonBackground =
                new android.graphics.drawable.GradientDrawable();
        sessionButtonBackground.setCornerRadius(dp(12));
        int appAccentColor = getAppAccentColor();

        sessionButtonBackground.setColor(appAccentColor);
        sessionButtonBackground.setStroke(
                dp(1),
                Color.argb(
                        180,
                        Color.red(appAccentColor),
                        Color.green(appAccentColor),
                        Color.blue(appAccentColor)
                )
        );


        startButton.setBackground(sessionButtonBackground);
        startButton.setElevation(dp(3));
        startButton.setOnClickListener(v -> {
            if (running) {
                stopSession();
            } else {
                startSession();
            }
        });


        // FLOATING OVERLAY
        overlayButton = new Button(this);
        overlayButton.setTextSize(12);
        overlayButton.setAllCaps(false);
        overlayButton.setTextColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable overlayButtonBackground =
                new android.graphics.drawable.GradientDrawable();

        overlayButtonBackground.setColor(Color.rgb(39, 53, 69));
        overlayButtonBackground.setCornerRadius(dp(12));
        overlayButtonBackground.setStroke(dp(1), Color.rgb(65, 85, 105));

        overlayButton.setBackground(overlayButtonBackground);
        overlayButton.setElevation(dp(3));
        overlayButton.setOnClickListener(v -> toggleOverlay());
        compactButton(startButton);
        compactButton(shizukuButton);
        compactButton(overlayButton);

// Start Session: full width, on top
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        startParams.bottomMargin = dp(8);
        page.addView(startButton, startParams);

// Connect Shizuku + overlay: side by side
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams leftParams =
                new LinearLayout.LayoutParams(0, dp(46), 1f);
        leftParams.rightMargin = dp(4);
        buttonRow.addView(shizukuButton, leftParams);

        LinearLayout.LayoutParams rightParams =
                new LinearLayout.LayoutParams(0, dp(46), 1f);
        rightParams.leftMargin = dp(4);
        buttonRow.addView(overlayButton, rightParams);

        add(page, buttonRow, 0, 18);


        // OVERLAY SETTINGS (this card is shown in the Settings tab)
        LinearLayout overlaySettings = new LinearLayout(this);
        overlaySettings.setOrientation(LinearLayout.VERTICAL);
        overlaySettings.setBackground(cardBg(16));
        overlaySettings.setPadding(dp(16), dp(16), dp(16), dp(12));
        overlaySettings.setElevation(dp(4));

        add(overlaySettings, section("FLOATING OVERLAY SETTINGS"), 0, 8);

        showFps = new CheckBox(this);
        showFps.setText("Show FPS counter");
        styleCheckBox(showFps);
        showFps.setChecked(getPrefs().getBoolean("show_fps", true));
        showFps.setOnCheckedChangeListener((button, checked) ->
                getPrefs().edit().putBoolean("show_fps", checked).apply());
        add(overlaySettings, showFps, 0, 2);

        showTemperature = new CheckBox(this);
        showTemperature.setText("Show temperature");
        styleCheckBox(showTemperature);
        showTemperature.setChecked(
                getPrefs().getBoolean("show_temperature", true));
        showTemperature.setOnCheckedChangeListener((button, checked) ->
                getPrefs().edit()
                        .putBoolean("show_temperature", checked)
                        .apply());
        add(overlaySettings, showTemperature, 0, 2);

        CheckBox showFrameTimeGraph = new CheckBox(this);
        showFrameTimeGraph.setText("Show frame-time graph");
        styleCheckBox(showFrameTimeGraph);
        showFrameTimeGraph.setChecked(
                getPrefs().getBoolean("show_frame_time_graph", true));
        showFrameTimeGraph.setOnCheckedChangeListener((button, checked) ->
                getPrefs().edit()
                        .putBoolean("show_frame_time_graph", checked)
                        .apply());

        add(overlaySettings, showFrameTimeGraph, 0, 2);

        CheckBox showCpu = new CheckBox(this);
        showCpu.setText("Show CPU load");
        styleCheckBox(showCpu);
        showCpu.setChecked(getPrefs().getBoolean("show_cpu", true));
        showCpu.setOnCheckedChangeListener((button, checked) ->
                getPrefs().edit().putBoolean("show_cpu", checked).apply());
        add(overlaySettings, showCpu, 0, 2);

        CheckBox showGpu = new CheckBox(this);
        showGpu.setText("Show GPU load");
        styleCheckBox(showGpu);
        showGpu.setChecked(getPrefs().getBoolean("show_gpu", true));
        showGpu.setOnCheckedChangeListener((button, checked) ->
                getPrefs().edit().putBoolean("show_gpu", checked).apply());
        add(overlaySettings, showGpu, 0, 2);

        addStylePicker(overlaySettings);

        android.widget.TextView opacityLabel = new android.widget.TextView(this);
        opacityLabel.setTextColor(Color.WHITE);
        opacityLabel.setTextSize(13);

        android.widget.SeekBar opacitySlider =
                new android.widget.SeekBar(this);

        opacitySlider.setMax(100);

        opacitySlider.setProgressTintList(
                android.content.res.ColorStateList.valueOf(appAccentColor));
        opacitySlider.setThumbTintList(
                android.content.res.ColorStateList.valueOf(appAccentColor));
        appAccentSeekBars.add(opacitySlider);

        int savedOpacity = getPrefs().getInt("overlay_opacity", 85);
        opacitySlider.setProgress(savedOpacity);

        opacityLabel.setText("Overlay opacity: " + savedOpacity + "%");

        opacitySlider.setOnSeekBarChangeListener(
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            android.widget.SeekBar seekBar,
                            int progress,
                            boolean fromUser) {

                        opacityLabel.setText(
                                "Overlay opacity: " + progress + "%");

                        if (fromUser) {
                            getPrefs().edit()
                                    .putInt("overlay_opacity", progress)
                                    .apply();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(
                            android.widget.SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(
                            android.widget.SeekBar seekBar) {
                    }
                });

        add(overlaySettings, opacityLabel, 0, 2);
        add(overlaySettings, opacitySlider, 0, 2);

        overlaySettingsCard = overlaySettings;

        TextView scaleLabel = new TextView(this);
        scaleLabel.setTextColor(android.graphics.Color.WHITE);

        android.widget.SeekBar scaleSlider =
                new android.widget.SeekBar(this);

        scaleSlider.setMax(12);
        scaleSlider.setProgressTintList(
                android.content.res.ColorStateList.valueOf(appAccentColor));
        scaleSlider.setThumbTintList(
                android.content.res.ColorStateList.valueOf(appAccentColor));
        appAccentSeekBars.add(scaleSlider);

        int savedScale = getPrefs().getInt("overlay_scale", 100);
        scaleSlider.setProgress((savedScale - 70) / 5);

        scaleLabel.setText("Overlay scale: " + savedScale + "%");

        scaleSlider.setOnSeekBarChangeListener(
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            android.widget.SeekBar seekBar,
                            int progress,
                            boolean fromUser) {

                        int scale = 70 + (progress * 5);

                        scaleLabel.setText("Overlay scale: " + scale + "%");

                        if (fromUser) {
                            getPrefs().edit()
                                    .putInt("overlay_scale", scale)
                                    .apply();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(
                            android.widget.SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(
                            android.widget.SeekBar seekBar) {
                    }
                });

        add(overlaySettings, scaleLabel, 0, 2);
        add(overlaySettings, scaleSlider, 0, 2);

        // LIVE READINGS
        add(page, section("LIVE READINGS"), 0, 8);

        LinearLayout gridRow1 = new LinearLayout(this);
        gridRow1.setOrientation(LinearLayout.HORIZONTAL);
        fps = tile(gridRow1, "FPS", false);
        cpuTile = tile(gridRow1, "CPU", true);
        add(page, gridRow1, 0, 8);

        LinearLayout gridRow2 = new LinearLayout(this);
        gridRow2.setOrientation(LinearLayout.HORIZONTAL);
        gpuTile = tile(gridRow2, "GPU", false);
        temperature = tile(gridRow2, "TEMP", true);
        add(page, gridRow2, 0, 8);

        LinearLayout gridRow3 = new LinearLayout(this);
        gridRow3.setOrientation(LinearLayout.HORIZONTAL);
        power = tile(gridRow3, "POWER", false);
        current = tile(gridRow3, "BATTERY CURRENT", true);
        powerState = addSubLine(power);

// make both cards in this row the same height
        for (int i = 0; i < gridRow3.getChildCount(); i++) {
            gridRow3.getChildAt(i).getLayoutParams().height =
                    ViewGroup.LayoutParams.MATCH_PARENT;
        }
        add(page, gridRow3, 0, 8);

        LinearLayout gridRow4 = new LinearLayout(this);
        gridRow4.setOrientation(LinearLayout.HORIZONTAL);
        voltage = tile(gridRow4, "BATTERY VOLTAGE", false);
        battery = tile(gridRow4, "BATTERY LEVEL", true);
        add(page, gridRow4, 0, 18);

        // SESSION SUMMARY
        add(page, section("SESSION SUMMARY"), 0, 8);

        LinearLayout summaryCard = new LinearLayout(this);
        summaryCard.setOrientation(LinearLayout.VERTICAL);
        summaryCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        android.graphics.drawable.GradientDrawable summaryBackground =
                new android.graphics.drawable.GradientDrawable();

        summaryBackground.setColor(Color.rgb(25, 33, 45));
        summaryBackground.setCornerRadius(dp(14));
        summaryBackground.setStroke(dp(1), Color.rgb(55, 70, 88));

        summaryCard.setBackground(cardBg(14));
        summaryCard.setElevation(dp(3));

        summary = text("No session yet.", 14, false,
                Color.rgb(210, 220, 232));
        summary.setLineSpacing(dp(4), 1f);
        summaryCard.addView(summary);

        add(page, summaryCard, 0, 22);

        // FOOTNOTE
        TextView note = text(
                "Display FPS is read from the display controller. " +
                        "CPU loads are system-wide. Battery power is battery-side " +
                        "power; unplug the phone for discharge measurements. " +
                        "Root access or Shizuku is required.",
                11, false, Color.rgb(115, 130, 148));

        note.setLineSpacing(dp(3), 1f);
        add(page, note, 0, 12);
        // CREATE OTHER PAGES
        sessionsPage = buildSessionsPage();
        settingsPage = createPlaceholderPage("SETTINGS");
        buildSettingsContent();
        aboutPage = createPlaceholderPage("ABOUT");
        buildAboutContent();

// BOTTOM NAVIGATION ITEMS
        addNavigationItem("Monitor", 0);
        addNavigationItem("Sessions", 1);
        addNavigationItem("Settings", 2);
        addNavigationItem("About", 3);

// SHOW MONITOR BY DEFAULT
        showTab(0);
    }

    private void updateHeaderLine() {
        if (headerLine == null) return;

        int a = getAppAccentColor();
        int r = Color.red(a), g = Color.green(a), b = Color.blue(a);

        // transparent at both ends, accent in the middle
        headerLine.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.argb(0, r, g, b),
                        Color.argb(210, r, g, b),
                        Color.argb(0, r, g, b)}));
    }

    private void addNavigationItem(String label, int tab) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(2), dp(4), dp(2), dp(4));

        android.widget.ImageView icon = new android.widget.ImageView(this);

        int[] iconResources = {
                R.drawable.ic_nav_monitor,
                R.drawable.ic_nav_sessions,
                R.drawable.ic_nav_settings,
                R.drawable.ic_nav_about
        };

        icon.setImageResource(iconResources[tab]);
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);

        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(dp(24), dp(24));
        item.addView(icon, iconParams);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(10);
        text.setGravity(Gravity.CENTER);
        text.setTypeface(null, android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
        textParams.topMargin = dp(2);
        item.addView(text, textParams);

        View indicator = new View(this);
        LinearLayout.LayoutParams indicatorParams =
                new LinearLayout.LayoutParams(dp(32), dp(2));
        indicatorParams.topMargin = dp(3);
        item.addView(indicator, indicatorParams);
        navIndicators[tab] = indicator;

        navIcons[tab] = icon;
        navLabels[tab] = text;

        item.setOnClickListener(v -> showTab(tab));

        bottomNavigation.addView(item, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
        ));
    }


    private ScrollView createPlaceholderPage(String title) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13, 18, 26));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(28));

        TextView heading = text(title, 20, true, Color.WHITE);
        add(content, heading, 0, 8);

        scroll.addView(content);
        return scroll;
    }


    private View buildSessionsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(13, 18, 26));
        page.setPadding(dp(18), dp(8), dp(18), 0);

        LinearLayout sessionsHeader = tabHeader(R.drawable.ic_nav_sessions, "SESSIONS");

        // count pill, same style as the status pill on Monitor
        LinearLayout countPill = new LinearLayout(this);
        countPill.setGravity(Gravity.CENTER_VERTICAL);
        countPill.setPadding(dp(12), dp(7), dp(12), dp(7));

        android.graphics.drawable.GradientDrawable countBg =
                new android.graphics.drawable.GradientDrawable();
        countBg.setColor(Color.rgb(25, 33, 45));
        countBg.setCornerRadius(dp(50));
        countBg.setStroke(dp(1), Color.rgb(43, 52, 65));
        countPill.setBackground(countBg);

        sessionsCount = text("0 SAVED", 10, true, Color.rgb(205, 214, 225));
        sessionsCount.setLetterSpacing(0.08f);
        sessionsCount.setSingleLine(true);
        countPill.addView(sessionsCount);

        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.rightMargin = dp(4);
        sessionsHeader.addView(countPill, countParams);

        sessionsActions = new LinearLayout(this);
        sessionsActions.setOrientation(LinearLayout.HORIZONTAL);
        sessionsActions.addView(headerIcon(R.drawable.ic_import, "Import CSV",
                v -> importSession()), new LinearLayout.LayoutParams(dp(44), dp(44)));
        sessionsActions.addView(headerIcon(R.drawable.ic_more_vert, "More options",
                v -> showTopMenu(v)), new LinearLayout.LayoutParams(dp(44), dp(44)));
        sessionsHeader.addView(sessionsActions);

        sessionsCancel = text("CANCEL", 13, true, getAppAccentColor());
        sessionsCancel.setPadding(dp(12), dp(10), dp(4), dp(10));

        add(page, sessionsHeader, 0, 12);
        page.setClipToPadding(false);

        pinBanner = new LinearLayout(this);
        pinBanner.setOrientation(LinearLayout.HORIZONTAL);
        pinBanner.setGravity(Gravity.CENTER_VERTICAL);
        pinBanner.setPadding(dp(12), 0, dp(4), 0);
        pinBanner.setVisibility(View.GONE);

        pinBannerBg = new android.graphics.drawable.GradientDrawable();
        pinBannerBg.setCornerRadius(dp(10));
        pinBanner.setBackground(pinBannerBg);

        pinBannerText = text("Pinned as A · swipe or tap another to compare",
                13, false, getAppAccentColor());
        pinBanner.addView(pinBannerText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        pinBannerCancel = text("Cancel", 13, true, Color.WHITE);
        pinBannerCancel.setPadding(dp(12), dp(10), dp(12), dp(10));
        pinBannerCancel.setOnClickListener(v -> {
            pinnedFile = null;
            updatePinUi(true);
        });
        pinBanner.addView(pinBannerCancel);

        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
        bannerParams.bottomMargin = dp(12);
        page.addView(pinBanner, bannerParams);

        // Empty-state card
        sessionsEmpty = text(
                "No saved sessions yet.\nStart a session on the Monitor tab and it will show up here.",
                13, false, Color.rgb(145, 160, 180));
        sessionsEmpty.setLineSpacing(dp(3), 1f);
        sessionsEmpty.setPadding(dp(14), dp(16), dp(14), dp(16));
        sessionsEmpty.setBackground(cardBg(14));
        sessionsEmpty.setElevation(dp(3));
        add(page, sessionsEmpty, 0, 8);

        // The list
        sessionsAdapter = new SessionsAdapter(this, new SessionsAdapter.Host() {
            @Override public boolean isPinned(String name) {
                return name.equals(pinnedFile);
            }
            @Override public void onCardTap(String name) { onSessionTapped(name); }
            @Override public void onMenu(View anchor, String name) {
                showSessionMenu(anchor, name);
            }
        });

        sessionsList = new RecyclerView(this);
        sessionsList.setLayoutManager(new LinearLayoutManager(this));
        sessionsList.setAdapter(sessionsAdapter);
        sessionsList.setClipToPadding(false);
        sessionsList.setPadding(dp(8), dp(8), dp(8), dp(90));
        sessionsList.setOverScrollMode(View.OVER_SCROLL_NEVER);

        if (sessionsList.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) sessionsList.getItemAnimator())
                    .setSupportsChangeAnimations(false);
        }
        new ItemTouchHelper(new SwipeCallback()).attachToRecyclerView(sessionsList);

        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.setMargins(-dp(8), -dp(8), -dp(8), 0);
        page.addView(sessionsList, listParams);

        return page;
    }


    private void buildSettingsContent() {
        LinearLayout content =
                (LinearLayout) settingsPage.getChildAt(0);
        content.removeViewAt(0);
        LinearLayout settingsHeader = tabHeader(R.drawable.ic_nav_settings, "SETTINGS");

        add(content, settingsHeader, 0, 12);
        add(content, overlaySettingsCard, 0, 14);

        // BACKGROUND ACTIVITY CARD
        LinearLayout backgroundCard = new LinearLayout(this);
        backgroundCard.setOrientation(LinearLayout.VERTICAL);
        backgroundCard.setPadding(
                dp(16), dp(16), dp(16), dp(16));
        backgroundCard.setBackground(cardBg(16));

        add(backgroundCard, section("BACKGROUND ACTIVITY"), 0, 8);

        TextView backgroundNote = text(
                "Allow ONYX to keep monitoring when you're using other apps. "
                        + "For better reliability, set ONYX battery usage to Unrestricted.",
                12,
                false,
                Color.rgb(145, 158, 175)
        );

        add(backgroundCard, backgroundNote, 0, 12);

        TextView batterySettingsButton = text(
                "OPEN BATTERY SETTINGS  →",
                13,
                true,
                getAppAccentColor()
        );
        appAccentTextViews.add(batterySettingsButton);

        batterySettingsButton.setPadding(
                dp(4), dp(10), dp(4), dp(10));

        batterySettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        });

        add(backgroundCard, batterySettingsButton, 0, 0);

        add(content, backgroundCard, 0, 14);

        LinearLayout appAppearanceCard = new LinearLayout(this);
        appAppearanceCard.setOrientation(LinearLayout.VERTICAL);
        appAppearanceCard.setPadding(
                dp(16), dp(16), dp(16), dp(16));

        android.graphics.drawable.GradientDrawable appCardBg =
                new android.graphics.drawable.GradientDrawable();

        appCardBg.setColor(Color.rgb(25, 32, 42));
        appCardBg.setCornerRadius(dp(16));
        appCardBg.setStroke(dp(1), Color.rgb(43, 52, 65));

        appAppearanceCard.setBackground(cardBg(16));

        add(appAppearanceCard, section("APP APPEARANCE"), 0, 8);

        TextView appColorNote = text(
                "Customize the appearance of the ONYX interface.",
                12,
                false,
                Color.rgb(145, 158, 175)
        );

        add(appAppearanceCard, appColorNote, 0, 12);

        LinearLayout appColorRow = new LinearLayout(this);
        appColorRow.setOrientation(LinearLayout.HORIZONTAL);

        String[] appColorNames = {"ONYX", "OCEAN", "VIOLET", "CRIMSON", "CUSTOM"};
        int[] appColors = {
                Color.rgb(101, 214, 165),
                Color.rgb(69, 168, 245),
                Color.rgb(167, 139, 250),
                Color.rgb(241, 107, 117),
                0   // custom
        };

        for (int i = 0; i < appColorNames.length; i++) {
            final boolean isCustom = i == 4;
            final int presetColor = appColors[i];
            boolean isSelected = !isCustom && getAppAccentColor() == presetColor;

            // Tile
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(2), dp(12), dp(2), dp(10));

            android.graphics.drawable.GradientDrawable tileBg =
                    new android.graphics.drawable.GradientDrawable();
            tileBg.setColor(Color.rgb(30, 38, 49));
            tileBg.setCornerRadius(dp(12));
            tileBg.setStroke(dp(isSelected ? 2 : 1),
                    isSelected ? getAppAccentColor() : Color.rgb(55, 65, 80));
            tile.setBackground(tileBg);

            // Circle + check
            android.widget.FrameLayout swatchFrame = new android.widget.FrameLayout(this);

            View circle = new View(this);
            android.graphics.drawable.GradientDrawable circleBg =
                    new android.graphics.drawable.GradientDrawable();
            circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            if (isCustom) {
                circleBg.setGradientType(
                        android.graphics.drawable.GradientDrawable.SWEEP_GRADIENT);
                circleBg.setColors(new int[]{Color.RED, Color.YELLOW, Color.GREEN,
                        Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED});
            } else {
                circleBg.setColor(presetColor);
            }
            circle.setBackground(circleBg);
            swatchFrame.addView(circle,
                    new android.widget.FrameLayout.LayoutParams(dp(30), dp(30)));

            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextSize(16);
            check.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            check.setIncludeFontPadding(false);
            check.setTextColor(Color.WHITE);
            check.setGravity(Gravity.CENTER);
            check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            swatchFrame.addView(check,
                    new android.widget.FrameLayout.LayoutParams(dp(30), dp(30)));

            tile.addView(swatchFrame, new LinearLayout.LayoutParams(dp(30), dp(30)));

            // Label
            TextView name = text(appColorNames[i], 9, true, Color.WHITE);
            name.setSingleLine(true);
            name.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            nameParams.topMargin = dp(8);
            tile.addView(name, nameParams);

            if (isCustom) {
                customColorButtonBg = tileBg;
                customColorCheck = check;
                customRainbowDrawable = circleBg;
                tile.setOnClickListener(v -> showCustomAppColorPicker());
            } else {
                appColorOptions.add(tile);
                appColorChecks.add(check);
                appColorPillBackgrounds.add(tileBg);
                tile.setOnClickListener(v -> {
                    getSharedPreferences("app_settings", MODE_PRIVATE)
                            .edit()
                            .putInt("app_accent_color", presetColor)
                            .apply();
                    updateAppAccentColor();
                    refreshOverlayColorRows();
                    updateAppColorSelection();
                });
            }

            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tp.setMargins(i == 0 ? 0 : dp(4), 0, i == 4 ? 0 : dp(4), 0);
            appColorRow.addView(tile, tp);
        }

        add(appAppearanceCard, appColorRow, 0, 0);

        add(content, appAppearanceCard, 0, 14);

        // OVERLAY APPEARANCE CARD
        LinearLayout overlayAppearanceCard = new LinearLayout(this);
        overlayAppearanceCard.setOrientation(LinearLayout.VERTICAL);
        overlayAppearanceCard.setPadding(
                dp(16), dp(16), dp(16), dp(16));

        android.graphics.drawable.GradientDrawable overlayCardBg =
                new android.graphics.drawable.GradientDrawable();

        overlayCardBg.setColor(Color.rgb(25, 32, 42));
        overlayCardBg.setCornerRadius(dp(16));
        overlayCardBg.setStroke(dp(1), Color.rgb(43, 52, 65));

        overlayAppearanceCard.setBackground(cardBg(16));

        add(overlayAppearanceCard, section("OVERLAY APPEARANCE"), 0, 8);

        TextView overlayColorNote = text(
                "Customize the appearance of your floating performance overlay.",
                12,
                false,
                Color.rgb(145, 158, 175)
        );

        add(overlayAppearanceCard, overlayColorNote, 0, 12);

        overlayColorRefreshers.clear();

        add(overlayAppearanceCard, section("OVERLAY ACCENT COLOR"), 0, 8);
        addOverlayColorRow(overlayAppearanceCard, "overlay_accent_color",
                Color.rgb(55, 210, 130), "Custom Accent Color", 8);
        addOverlayDivider(overlayAppearanceCard);

        add(overlayAppearanceCard, section("OVERLAY TEXT COLOR"), 0, 8);
        addOverlayColorRow(overlayAppearanceCard, "overlay_text_color",
                Color.WHITE, "Custom Text Color", 8);
        addOverlayDivider(overlayAppearanceCard);

        add(overlayAppearanceCard, section("OVERLAY BACKGROUND COLOR"), 0, 8);
        addOverlayColorRow(overlayAppearanceCard, "overlay_background_color",
                Color.rgb(20, 25, 32), "Custom Background Color", 0);

        add(content, overlayAppearanceCard, 0, 14);
    }

    private void buildAboutContent() {
        LinearLayout content = (LinearLayout) aboutPage.getChildAt(0);
        content.removeViewAt(0);
        aboutRefreshers.clear();

        // Simple header matching the other tabs
        add(content, tabHeader(R.drawable.ic_nav_about, "ABOUT"), 0, 12);

        // ================= CARD 1: app info =================
        LinearLayout infoCard = aboutCard();

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView appTitle = section("ONYX PERFORMANCE MONITOR");
        appTitle.setSingleLine(true);
        titleRow.addView(appTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String versionName = "1.0.0";
        try {
            versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }

        TextView versionPill = text("v" + versionName + " • STABLE", 9, true,
                getAppAccentColor());
        versionPill.setSingleLine(true);
        versionPill.setPadding(dp(10), dp(4), dp(10), dp(4));
        android.graphics.drawable.GradientDrawable pillBg =
                new android.graphics.drawable.GradientDrawable();
        pillBg.setCornerRadius(dp(50));
        pillBg.setColor(Color.rgb(27, 38, 54));
        pillBg.setStroke(dp(1), getAppAccentColor());
        versionPill.setBackground(pillBg);
        appAccentTextViews.add(versionPill);
        aboutRefreshers.add(() -> pillBg.setStroke(dp(1), getAppAccentColor()));

        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pillParams.leftMargin = dp(8);
        titleRow.addView(versionPill, pillParams);
        add(infoCard, titleRow, 0, 8);

        TextView desc = text(
                "A comprehensive, lightweight performance analysis overlay. Measures FPS, frametime, 1% lows, stutters, and hardware telemetry with smart foreground-only refreshing.",
                12, false, Color.rgb(145, 158, 175));
        desc.setLineSpacing(dp(2), 1f);
        add(infoCard, desc, 0, 12);

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        left.rightMargin = dp(4);
        infoRow.addView(aboutInfoBox("DEVELOPER", "Klod9s"), left);

        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        right.leftMargin = dp(4);
        infoRow.addView(aboutInfoBox("LICENSE", "GNU GPLv3"), right);
        add(infoCard, infoRow, 0, 0);

        add(content, infoCard, 0, 14);

        // ================= CARD 2: support =================
        LinearLayout supportCard = aboutCard();
        add(supportCard, section("SUPPORT DEVELOPMENT"), 0, 8);

        TextView supportText = text(
                "ONYX is free, ad-free, and open source. If this tool helps you benchmark or optimize your device, consider supporting the project.",
                12, false, Color.rgb(145, 158, 175));
        supportText.setLineSpacing(dp(2), 1f);
        add(supportCard, supportText, 0, 12);

        LinearLayout coffee = aboutButton("BUY ME A COFFEE", true, R.drawable.ic_coffee);
        coffee.setOnClickListener(v -> openUrl(URL_COFFEE));
        LinearLayout.LayoutParams coffeeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        coffeeParams.bottomMargin = dp(8);
        supportCard.addView(coffee, coffeeParams);

        LinearLayout linkRow = new LinearLayout(this);
        linkRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout github = aboutButton("GITHUB", false, R.drawable.ic_github);
        github.setOnClickListener(v -> openUrl(URL_GITHUB));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        gp.rightMargin = dp(4);
        linkRow.addView(github, gp);

        LinearLayout xda = aboutButton("XDA THREAD", false, R.drawable.ic_xda);
        xda.setOnClickListener(v -> openUrl(URL_XDA));
        LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        xp.leftMargin = dp(4);
        linkRow.addView(xda, xp);
        add(supportCard, linkRow, 0, 0);

        add(content, supportCard, 0, 14);

        // ================= CARD 3: how metrics work =================
        LinearLayout metricsCard = aboutCard();
        add(metricsCard, section("HOW METRICS WORK"), 0, 10);

        addAboutBullet(metricsCard, "Frame Pacing & Analysis",
                "Real-time FPS, frametime graph, 1% and 0.1% lows, and stutter counts for deep performance analysis.", 10);
        addAboutBullet(metricsCard, "Hardware Telemetry",
                "Live CPU and GPU utilization percentages fetched directly from hardware performance counters.", 10);
        addAboutBullet(metricsCard, "Thermal & Power",
                "Battery temperature and power state monitoring to help identify performance throttling.", 10);
        addAboutBullet(metricsCard, "Smart Foreground Refresh",
                "Overlay metrics and UI only update when actively in use, ensuring zero background battery drain.", 0);

        add(content, metricsCard, 0, 14);

        // ================= CARD 4: credits =================
        LinearLayout creditsCard = aboutCard();
        add(creditsCard, section("CREDITS & ACKNOWLEDGMENTS"), 0, 8);

        TextView creditsText = text(
                "Independently developed and tested. Powered by RikkaApps' Shizuku API for privileged telemetry access.",
                12, false, Color.rgb(145, 158, 175));
        creditsText.setLineSpacing(dp(2), 1f);
        add(creditsCard, creditsText, 0, 10);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(43, 52, 65));
        creditsCard.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout licensesRow = new LinearLayout(this);
        licensesRow.setOrientation(LinearLayout.HORIZONTAL);
        licensesRow.setGravity(Gravity.CENTER_VERTICAL);
        licensesRow.setPadding(0, dp(12), 0, dp(2));

        licensesRow.addView(text("View Open Source Licenses", 14, false, Color.WHITE),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        licensesRow.addView(text("›", 20, false, Color.rgb(145, 158, 175)));

        licensesRow.setOnClickListener(v -> showDarkDialog(
                darkDialogBuilder()
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
                            openUrl("https://www.gnu.org/licenses/gpl-3.0.html");
                        }),
                false));
        creditsCard.addView(licensesRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        add(content, creditsCard, 0, 10);
    }

    private LinearLayout aboutCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(cardBg(16));
        return card;
    }

    private LinearLayout aboutInfoBox(String label, String value) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(27, 38, 54));
        bg.setCornerRadius(dp(10));
        box.setBackground(bg);

        TextView l = text(label, 11, true, getAppAccentColor());
        appAccentTextViews.add(l);
        box.addView(l);

        TextView v = text(value, 14, true, Color.WHITE);
        v.setSingleLine(true);
        box.addView(v);
        return box;
    }

    private LinearLayout aboutButton(String label, boolean filled, int iconRes) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(iconRes);
        LinearLayout.LayoutParams ip =
                new LinearLayout.LayoutParams(dp(18), dp(18));
        ip.rightMargin = dp(8);
        row.addView(icon, ip);

        TextView t = text(label, 13, true, Color.WHITE);
        t.setSingleLine(true);
        t.setLetterSpacing(0.04f);
        row.addView(t);

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(12));

        if (filled) {
            Runnable style = () -> {
                int accent = getAppAccentColor();
                bg.setColor(accent);
                t.setTextColor(androidx.core.graphics.ColorUtils
                        .calculateLuminance(accent) > 0.5
                        ? Color.rgb(13, 18, 26) : Color.WHITE);
            };
            style.run();
            aboutRefreshers.add(style);
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(1), Color.rgb(70, 82, 100));
        }

        row.setBackground(bg);
        return row;
    }

    private void addAboutBullet(LinearLayout card, String title, String body, int bottom) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        View dot = new View(this);
        android.graphics.drawable.GradientDrawable dotBg =
                new android.graphics.drawable.GradientDrawable();
        dotBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dotBg.setColor(getAppAccentColor());
        dot.setBackground(dotBg);
        aboutRefreshers.add(() -> dotBg.setColor(getAppAccentColor()));

        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotParams.topMargin = dp(7);
        dotParams.rightMargin = dp(10);
        row.addView(dot, dotParams);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(text(title, 14, true, Color.WHITE));

        TextView desc = text(body, 12, false, Color.rgb(145, 158, 175));
        desc.setLineSpacing(dp(2), 1f);
        column.addView(desc);

        row.addView(column, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        add(card, row, 0, bottom);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "No app available to open this link",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private TextView pillLabel(LinearLayout pill) {
        for (int c = 0; c < pill.getChildCount(); c++) {
            View child = pill.getChildAt(c);
            if (child instanceof TextView) return (TextView) child;
        }
        return null;
    }


    private final ArrayList<Runnable> overlayColorRefreshers = new ArrayList<>();

    private void refreshOverlayColorRows() {
        for (Runnable r : overlayColorRefreshers) r.run();
    }

    private void addOverlayDivider(LinearLayout card) {
        View d = new View(this);
        d.setBackgroundColor(Color.rgb(43, 52, 65));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        p.setMargins(0, dp(2), 0, dp(10));
        card.addView(d, p);
    }

    // Same rainbow circle as the app appearance CUSTOM button
    private android.graphics.drawable.Drawable createRainbowDrawable(
            java.util.function.BooleanSupplier customSelected) {

        android.graphics.drawable.Drawable d =
                new android.graphics.drawable.Drawable() {
                    private final android.graphics.Paint paint =
                            new android.graphics.Paint(
                                    android.graphics.Paint.ANTI_ALIAS_FLAG);
                    private final int[] colors = {
                            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
                            Color.BLUE, Color.MAGENTA, Color.RED
                    };

                    @Override
                    public void draw(android.graphics.Canvas canvas) {
                        android.graphics.Rect bounds = getBounds();
                        float stroke = dp(4);

                        paint.setStyle(android.graphics.Paint.Style.STROKE);
                        paint.setStrokeWidth(stroke);
                        paint.setShader(new android.graphics.SweepGradient(
                                bounds.exactCenterX(), bounds.exactCenterY(),
                                colors, null));

                        float inset = stroke / 2f;
                        canvas.drawOval(bounds.left + inset, bounds.top + inset,
                                bounds.right - inset, bounds.bottom - inset, paint);

                        if (customSelected.getAsBoolean()) {
                            paint.setShader(null);
                            paint.setStyle(android.graphics.Paint.Style.FILL);
                            paint.setColor(Color.WHITE);
                            paint.setTextSize(dp(12));
                            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                            canvas.drawText("✓", bounds.exactCenterX(),
                                    bounds.exactCenterY() + dp(4), paint);
                        }
                    }

                    @Override public int getIntrinsicWidth() { return dp(20); }
                    @Override public int getIntrinsicHeight() { return dp(20); }
                    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
                    @Override public void setColorFilter(
                            android.graphics.ColorFilter filter) {
                        paint.setColorFilter(filter);
                    }
                    @Override public int getOpacity() {
                        return android.graphics.PixelFormat.TRANSLUCENT;
                    }
                };

        d.setBounds(0, 0, dp(20), dp(20));
        return d;
    }

    // One row: [ONYX pill] [CUSTOM button]
    private void addOverlayColorRow(LinearLayout card, String prefKey,
                                    int onyxColor, String dialogTitle,
                                    int bottomMargin) {

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // ---- ONYX pill ----
        LinearLayout onyx = new LinearLayout(this);
        onyx.setOrientation(LinearLayout.HORIZONTAL);
        onyx.setGravity(Gravity.CENTER);
        onyx.setPadding(dp(9), dp(5), dp(9), dp(5));

        android.graphics.drawable.GradientDrawable onyxBg =
                new android.graphics.drawable.GradientDrawable();
        onyxBg.setColor(Color.rgb(30, 38, 49));
        onyxBg.setCornerRadius(dp(50));
        onyx.setBackground(onyxBg);

        android.widget.FrameLayout swatch = createSwatchWithCheck(onyxColor);
        TextView check = (TextView) swatch.getChildAt(1);
        onyx.addView(swatch, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView name = text("ONYX", 9, true, Color.WHITE);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(dp(5), 0, 0, 0);
        onyx.addView(name, nameParams);

        onyx.setOnClickListener(v -> {
            getPrefs().edit().putInt(prefKey, onyxColor).apply();
            refreshOverlayColorRows();
        });

        LinearLayout.LayoutParams onyxParams = new LinearLayout.LayoutParams(
                0, dp(36), 1f);
        onyxParams.setMargins(0, 0, dp(4), 0);
        row.addView(onyx, onyxParams);

        // ---- CUSTOM pill ----
        LinearLayout custom = new LinearLayout(this);
        custom.setOrientation(LinearLayout.HORIZONTAL);
        custom.setGravity(Gravity.CENTER);
        custom.setPadding(dp(9), dp(5), dp(9), dp(5));

        android.graphics.drawable.GradientDrawable customBg =
                new android.graphics.drawable.GradientDrawable();
        customBg.setColor(Color.rgb(30, 38, 49));
        customBg.setCornerRadius(dp(50));
        custom.setBackground(customBg);

        android.graphics.drawable.Drawable rainbow = createRainbowDrawable(
                () -> getPrefs().getInt(prefKey, onyxColor) != onyxColor);
        android.widget.ImageView rainbowView = new android.widget.ImageView(this);
        rainbowView.setImageDrawable(rainbow);
        custom.addView(rainbowView, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView customName = text("CUSTOM", 9, true, Color.WHITE);
        LinearLayout.LayoutParams customNameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        customNameParams.setMargins(dp(6), 0, 0, 0);
        custom.addView(customName, customNameParams);

        custom.setOnClickListener(v -> showCustomColorPicker(
                dialogTitle,
                getPrefs().getInt(prefKey, onyxColor),
                color -> {
                    getPrefs().edit().putInt(prefKey, color).apply();
                    refreshOverlayColorRows();
                }));

        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(
                0, dp(36), 1f);
        customParams.setMargins(dp(4), 0, 0, 0);
        row.addView(custom, customParams);

        // ---- selection state ----
        Runnable refresh = () -> {
            boolean customSelected =
                    getPrefs().getInt(prefKey, onyxColor) != onyxColor;
            int accent = getAppAccentColor();
            int idle = Color.rgb(55, 65, 80);

            onyxBg.setStroke(dp(1), customSelected ? idle : accent);
            check.setVisibility(customSelected ? View.GONE : View.VISIBLE);
            customBg.setStroke(dp(1), customSelected ? accent : idle);

            onyx.invalidate();
            custom.invalidate();
            rainbow.invalidateSelf();
        };
        overlayColorRefreshers.add(refresh);
        refresh.run();

        add(card, row, 0, bottomMargin);
    }

    private android.widget.FrameLayout createSwatchWithCheck(int color) {
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);

        View circle = new View(this);
        android.graphics.drawable.GradientDrawable circleBg =
                new android.graphics.drawable.GradientDrawable();
        circleBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circleBg.setColor(color);
        circle.setBackground(circleBg);

        frame.addView(circle, new android.widget.FrameLayout.LayoutParams(
                dp(22), dp(22)));

        // Index 1 is the checkmark; callers grab it with getChildAt(1)
        TextView check = new TextView(this);
        check.setText("✓");
        check.setTextSize(12);
        check.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        check.setIncludeFontPadding(false);
        check.setGravity(Gravity.CENTER);
        check.setTextColor(
                androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.6
                        ? Color.BLACK : Color.WHITE);
        check.setVisibility(View.GONE);

        frame.addView(check, new android.widget.FrameLayout.LayoutParams(
                dp(22), dp(22)));

        return frame;
    }


    private class SwipeCallback extends ItemTouchHelper.Callback {

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int RED = Color.rgb(240, 104, 122);

        SwipeCallback() {
            labelPaint.setTextSize(dp(13) * 1f);
            labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
            letterPaint.setTextSize(dp(13) * 1f);
            letterPaint.setTypeface(Typeface.DEFAULT_BOLD);
            letterPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public int getMovementFlags(RecyclerView rv, RecyclerView.ViewHolder vh) {
            // Check if we have enough sessions to allow pinning (left swipe)
            boolean canPin = visibleSessionNames().size() >= 2;

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

            String name = sessionsAdapter.fileNameAt(pos);
            if (name == null) return;

            if (direction == ItemTouchHelper.RIGHT) {
                sessionsList.performHapticFeedback(
                        android.view.HapticFeedbackConstants.GESTURE_END);
                deleteSessionWithUndo(name);
                return;
            }

            // LEFT: put the card back in place first, then decide what the swipe means
            sessionsAdapter.notifyItemChanged(pos);
            sessionsList.performHapticFeedback(
                    android.view.HapticFeedbackConstants.CONFIRM);

            if (name.equals(pinnedFile)) {
                pinnedFile = null;
                updatePinUi(true);
            } else if (pinnedFile == null) {
                setPinned(name);
            } else {
                launchPinnedCompare(name);
            }
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView rv, RecyclerView.ViewHolder vh,
                                float dX, float dY, int actionState, boolean active) {
            View item = vh.itemView;

            if (dX > 0) {
                // ---- swipe right: red delete strip ----
                fill.setColor(Color.rgb(58, 27, 34));
                labelPaint.setColor(RED);

                c.save();
                c.clipRect(item.getLeft(), item.getTop(),
                        item.getLeft() + dX, item.getBottom());
                c.drawRoundRect(item.getLeft(), item.getTop(),
                        item.getRight(), item.getBottom(), dp(14), dp(14), fill);

                android.graphics.drawable.Drawable icon =
                        getDrawable(R.drawable.ic_delete_outline_x).mutate();
                icon.setTint(RED);

                int size = dp(20);
                int cy = (item.getTop() + item.getBottom()) / 2;
                int ix = item.getLeft() + dp(18);
                icon.setBounds(ix, cy - size / 2, ix + size, cy + size / 2);
                icon.draw(c);

                Paint.FontMetrics fm = labelPaint.getFontMetrics();
                c.drawText("Delete", ix + size + dp(8),
                        cy - (fm.ascent + fm.descent) / 2f, labelPaint);
                c.restore();

            } else if (dX < 0) {
                // ---- swipe left: pin / compare / unpin strip ----
                String name = vh instanceof SessionsAdapter.Holder
                        ? ((SessionsAdapter.Holder) vh).fileName : "";

                int color;
                String letter;
                String label;

                if (name.equals(pinnedFile)) {
                    color = Color.rgb(145, 158, 175);
                    letter = null;
                    label = "Unpin";
                } else if (pinnedFile != null) {
                    color = AppColors.compareB(MainActivity.this);
                    letter = "B";
                    label = "Compare as B";
                } else {
                    color = AppColors.accent(MainActivity.this);
                    letter = "A";
                    label = "Pin as A";
                }

                // dark base so the strip is clearly visible, then the colour on top
                fill.setColor(Color.rgb(25, 33, 45));
                c.save();
                c.clipRect(item.getRight() + dX, item.getTop(),
                        item.getRight(), item.getBottom());
                c.drawRoundRect(item.getLeft(), item.getTop(),
                        item.getRight(), item.getBottom(), dp(14), dp(14), fill);
                fill.setColor(Color.argb(90,
                        Color.red(color), Color.green(color), Color.blue(color)));
                c.drawRoundRect(item.getLeft(), item.getTop(),
                        item.getRight(), item.getBottom(), dp(14), dp(14), fill);

                labelPaint.setColor(color);
                float cy = (item.getTop() + item.getBottom()) / 2f;
                float right = item.getRight() - dp(18);
                float textW = labelPaint.measureText(label);

                Paint.FontMetrics fm = labelPaint.getFontMetrics();
                c.drawText(label, right - textW,
                        cy - (fm.ascent + fm.descent) / 2f, labelPaint);

                if (letter != null) {
                    float size = dp(24);
                    float bx = right - textW - dp(8) - size;

                    badgePaint.setColor(color);
                    c.drawRoundRect(bx, cy - size / 2, bx + size, cy + size / 2,
                            dp(8), dp(8), badgePaint);

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


    // Adds a small second line under a tile's value
    private TextView addSubLine(TextView valueView) {
        LinearLayout card = (LinearLayout) valueView.getParent();
        TextView sub = text("—", 11, true, Color.rgb(155, 174, 194));
        sub.setSingleLine(true);
        card.addView(sub);
        return sub;
    }

    private void updateAppColorSelection() {
        int currentColor = getAppAccentColor();

        int[] presetColors = {
                Color.rgb(101, 214, 165),
                Color.rgb(69, 168, 245),
                Color.rgb(167, 139, 250),
                Color.rgb(241, 107, 117)
        };

        boolean isPreset = false;

        for (int i = 0; i < appColorOptions.size(); i++) {
            boolean selected = currentColor == presetColors[i];
            if (selected) isPreset = true;

            appColorPillBackgrounds.get(i).setStroke(
                    dp(selected ? 2 : 1),
                    selected ? currentColor : Color.rgb(55, 65, 80));

            appColorChecks.get(i).setVisibility(
                    selected ? View.VISIBLE : View.GONE);
        }

        if (customColorButtonBg != null) {
            customColorButtonBg.setStroke(
                    dp(!isPreset ? 2 : 1),
                    !isPreset ? currentColor : Color.rgb(55, 65, 80));
        }

        if (customColorCheck != null) {
            customColorCheck.setVisibility(!isPreset ? View.VISIBLE : View.GONE);
        }
    }

    private void showTab(int tab) {
        finalizePendingDeletes();
        pinnedFile = null;
        updatePinUi(false);
        selectedTab = tab;

        View selectedPage;

        switch (tab) {
            case 1:
                selectedPage = sessionsPage;
                break;
            case 2:
                selectedPage = settingsPage;
                break;
            case 3:
                selectedPage = aboutPage;
                break;
            default:
                selectedPage = monitorPage;
                break;
        }

        pageContainer.removeAllViews();

        pageContainer.addView(selectedPage,
                new android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ));

        int accentColor = getAppAccentColor();

        for (int i = 0; i < navLabels.length; i++) {
            int color = i == tab ? accentColor : Color.rgb(145, 158, 175);
            navLabels[i].setTextColor(color);
            navIcons[i].setColorFilter(color);
            navIndicators[i].setBackgroundColor(i == tab ? color : Color.TRANSPARENT);
        }

// Rebuild the cards so they pick up the current accent
        if (tab == 1 && sessionsAdapter != null) {
            loadHistory();
        }
    }

    private LinearLayout tabHeader(int iconRes, String titleText) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(44));

        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(22), dp(22));
        ip.rightMargin = dp(10);
        header.addView(icon, ip);

        TextView title = text(titleText, 20, true, Color.WHITE);
        title.setSingleLine(true);
        title.setTranslationY(dp(-1));
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        return header;
    }


    private int getAppAccentColor() {
        return AppColors.accent(this);
    }

    private void updateAppAccentColor() {
        int color = getAppAccentColor();
        if (heroBanner != null) {
            heroBanner.refreshAccentColor();
        }
        if (brand != null) {
            brand.refreshAccentColor();
        }

        // Navigation
        for (int i = 0; i < navLabels.length; i++) {
            navLabels[i].setTextColor(
                    i == selectedTab ? color : Color.rgb(145, 158, 175));

            navIcons[i].setColorFilter(
                    i == selectedTab ? color : Color.rgb(145, 158, 175));
            navIndicators[i].setBackgroundColor(i == selectedTab ? color : Color.TRANSPARENT);
        }

        // Section headings
        for (TextView heading : appAccentTextViews) {
            heading.setTextColor(color);
            heading.setCompoundDrawableTintList(
                    android.content.res.ColorStateList.valueOf(color));
        }

        // Checkboxes
        for (CheckBox box : appAccentCheckBoxes) {
            box.setButtonTintList(
                    android.content.res.ColorStateList.valueOf(color));
        }
        // Style picker radio buttons
        for (android.widget.RadioButton rb : appAccentRadioButtons) {
            rb.setButtonTintList(
                    android.content.res.ColorStateList.valueOf(color));
        }
        // View Analysis buttons
        for (Button button : appAccentButtons) {
            button.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));
        }

        // Overlay sliders
        for (android.widget.SeekBar slider : appAccentSeekBars) {
            slider.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(color));
            slider.setThumbTintList(
                    android.content.res.ColorStateList.valueOf(color));
        }

        // Overlay color rows (ONYX / CUSTOM highlights)
        refreshOverlayColorRows();

        // Card borders follow the app accent
        refreshCardStrokes();
        for (Runnable r : aboutRefreshers) r.run();
        updateHeaderLine();

        // ONYX glow
        if (brand != null) {
            brand.refreshAccentColor();
        }

// Status text
        if (status != null) {
            refreshStatusPill();
        }

        // Start Session button
        if (startButton != null && !running) {
            android.graphics.drawable.GradientDrawable background =
                    new android.graphics.drawable.GradientDrawable();

            background.setCornerRadius(dp(12));
            background.setColor(color);
            background.setStroke(dp(1), Color.argb(
                    180,
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)));

            startButton.setBackground(background);
        }
        if (customColorButtonBg != null) {
            customColorButtonBg.setStroke(dp(1), color);
        }
    }

    private void styleCheckBox(CheckBox box) {
        box.setTextColor(Color.WHITE);
        box.setButtonTintList(
                android.content.res.ColorStateList.valueOf(
                        getAppAccentColor()));

        appAccentCheckBoxes.add(box);
    }

    private void addStylePicker(LinearLayout card) {
        TextView label = new TextView(this);
        label.setText("Overlay style");
        label.setTextColor(Color.WHITE);
        label.setTextSize(13);
        add(card, label, 0, 2);

        android.widget.RadioGroup group = new android.widget.RadioGroup(this);
        group.setOrientation(android.widget.RadioGroup.HORIZONTAL);

        String[] names = {"Classic", "Strip"};
        int saved = getPrefs().getInt("overlay_style", 0);

        android.content.res.ColorStateList tint =
                android.content.res.ColorStateList.valueOf(getAppAccentColor());

        for (int i = 0; i < names.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(names[i]);
            rb.setTextColor(Color.WHITE);
            rb.setButtonTintList(tint);
            appAccentRadioButtons.add(rb);
            group.addView(rb, new android.widget.RadioGroup.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (i == saved) rb.setChecked(true);
        }

        group.setOnCheckedChangeListener((g, checkedId) -> {
            int index = g.indexOfChild(g.findViewById(checkedId));
            getPrefs().edit().putInt("overlay_style", index).apply();
        });

        add(card, group, 0, 2);
    }

    private void showCustomColorPicker(String title, int currentColor,
                                       java.util.function.IntConsumer onApply) {

        final int[] selectedColor = {currentColor};
        final boolean[] updating = {false};

        android.app.AlertDialog.Builder builder = darkDialogBuilder();
        builder.setTitle(title);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(12), dp(20), dp(12));

        // ---- preview ----
        TextView preview = new TextView(this);
        preview.setText("COLOR PREVIEW");
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(14);
        preview.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        preview.setPadding(0, dp(14), 0, dp(14));

        android.graphics.drawable.GradientDrawable previewBg =
                new android.graphics.drawable.GradientDrawable();
        previewBg.setCornerRadius(dp(12));
        preview.setBackground(previewBg);
        layout.addView(preview);

        // ---- color wheel ----
        ColorWheelView wheel = new ColorWheelView(this);
        wheel.setColor(currentColor);

        LinearLayout.LayoutParams wheelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        wheelParams.gravity = Gravity.CENTER_HORIZONTAL;
        wheelParams.topMargin = dp(14);
        layout.addView(wheel, wheelParams);

        // ---- brightness ----
        TextView brightnessLabel = new TextView(this);
        brightnessLabel.setText("BRIGHTNESS");
        brightnessLabel.setTextColor(Color.WHITE);
        brightnessLabel.setTextSize(12);
        brightnessLabel.setPadding(0, dp(8), 0, dp(2));
        layout.addView(brightnessLabel);

        android.widget.SeekBar brightness = new android.widget.SeekBar(this);
        brightness.setMax(100);
        brightness.setProgress(Math.round(wheel.getBrightness() * 100f));

        android.content.res.ColorStateList tint =
                android.content.res.ColorStateList.valueOf(getAppAccentColor());
        brightness.setProgressTintList(tint);
        brightness.setThumbTintList(tint);
        layout.addView(brightness);

        // ---- hex ----
        TextView hexLabel = new TextView(this);
        hexLabel.setText("HEX COLOR");
        hexLabel.setTextColor(Color.WHITE);
        hexLabel.setTextSize(12);
        hexLabel.setPadding(0, dp(12), 0, dp(4));
        layout.addView(hexLabel);

        android.widget.EditText hexInput = new android.widget.EditText(this);
        hexInput.setSingleLine(true);
        hexInput.setHint("#65D6A5");
        hexInput.setText(String.format("#%06X", (0xFFFFFF & currentColor)));
        hexInput.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hexInput.setTextColor(Color.WHITE);
        hexInput.setHintTextColor(Color.rgb(115, 130, 148));
        hexInput.setBackgroundTintList(tint);
        layout.addView(hexInput);

        // ---- shared preview refresh ----
        Runnable refreshPreview = () -> {
            int c = selectedColor[0];
            previewBg.setColor(c);
            preview.setTextColor(
                    androidx.core.graphics.ColorUtils.calculateLuminance(c) > 0.6
                            ? Color.rgb(13, 18, 26) : Color.WHITE);
            preview.invalidate();
        };
        refreshPreview.run();

        // wheel -> preview + hex
        wheel.setOnColorChangedListener(color -> {
            selectedColor[0] = color;
            refreshPreview.run();

            updating[0] = true;
            hexInput.setText(String.format("#%06X", (0xFFFFFF & color)));
            updating[0] = false;
        });

        // brightness -> wheel + preview + hex
        brightness.setOnSeekBarChangeListener(
                new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(android.widget.SeekBar bar,
                                                  int progress, boolean fromUser) {
                        if (!fromUser) return;

                        wheel.setBrightness(progress / 100f);
                        selectedColor[0] = wheel.getColor();
                        refreshPreview.run();

                        updating[0] = true;
                        hexInput.setText(String.format(
                                "#%06X", (0xFFFFFF & selectedColor[0])));
                        updating[0] = false;
                    }

                    @Override
                    public void onStartTrackingTouch(android.widget.SeekBar bar) {
                    }

                    @Override
                    public void onStopTrackingTouch(android.widget.SeekBar bar) {
                    }
                });

        // hex -> wheel + brightness + preview
        hexInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (updating[0]) return;

                String hex = s.toString().trim();
                if (!hex.matches("#[0-9A-Fa-f]{6}")) return;

                try {
                    int parsed = Color.parseColor(hex);
                    selectedColor[0] = parsed;

                    wheel.setColor(parsed);
                    brightness.setProgress(Math.round(wheel.getBrightness() * 100f));
                    refreshPreview.run();
                } catch (IllegalArgumentException ignored) {
                }
            }
        });

        android.widget.ScrollView scroller = new android.widget.ScrollView(this);
        scroller.addView(layout);
        builder.setView(scroller);

        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("APPLY", (dialog, which) ->
                onApply.accept(selectedColor[0]));

        showDarkDialog(builder, false);
    }

    private final ArrayList<android.graphics.drawable.GradientDrawable> cardBackgrounds =
            new ArrayList<>();

    private int cardStrokeColor() {
        int a = getAppAccentColor();
        return Color.argb(110, Color.red(a), Color.green(a), Color.blue(a));
    }

    private android.graphics.drawable.GradientDrawable cardBg(int radiusDp) {
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{Color.rgb(22, 32, 48), Color.rgb(15, 22, 34)});
        bg.setCornerRadius(dp(radiusDp));
        bg.setStroke(dp(1), cardStrokeColor());
        cardBackgrounds.add(bg);
        return bg;
    }

    private void refreshCardStrokes() {
        for (android.graphics.drawable.GradientDrawable bg : cardBackgrounds) {
            bg.setStroke(dp(1), cardStrokeColor());
        }
    }

    private void showCustomAppColorPicker() {
        showCustomColorPicker("Custom App Color", getAppAccentColor(), color -> {
            getSharedPreferences("app_settings", MODE_PRIVATE)
                    .edit().putInt("app_accent_color", color).apply();
            updateAppAccentColor();
            updateAppColorSelection();
        });
    }


    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        isAppForeground = true;
        if (brand != null) {
            brand.startShine();
        }

        monitoringEngine.resetMonitoringModeCheck();
        updateOverlayButton();
        stopOverlaySampling();
        wakeSampler();
    }

    @Override
    protected void onPause() {
        super.onPause();

        isAppForeground = false;
        if (brand != null) {
            brand.stopShine();
        }
        if (!running) handoffOverlaySampling();
    }

    @Override
    protected void onStop() {
        super.onStop();
        finalizePendingDeletes();
    }

    private boolean isOverlayServiceRunning() {
        android.app.ActivityManager manager =
                (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);

        for (android.app.ActivityManager.RunningServiceInfo service
                : manager.getRunningServices(Integer.MAX_VALUE)) {

            if (FloatingFpsService.class.getName()
                    .equals(service.service.getClassName())) {
                return true;
            }
        }

        return false;
    }

    private void updateOverlayButton() {
        boolean enabled = isOverlayServiceRunning();

        getPrefs().edit()
                .putBoolean("overlay_enabled", enabled)
                .apply();

        overlayButton.setText(enabled
                ? "TURN OFF OVERLAY"
                : "TURN ON OVERLAY");
    }

    private android.app.AlertDialog.Builder darkDialogBuilder() {
        return new android.app.AlertDialog.Builder(
                this,
                android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK);
    }

    private void showDarkDialog(android.app.AlertDialog.Builder builder,
                                boolean destructive) {
        final android.app.AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            // Rounded dark card matching the app's panels
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.rgb(25, 33, 45));
            bg.setCornerRadius(dp(16));
            bg.setStroke(dp(1), Color.rgb(55, 70, 88));

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(bg);
            }

            // Title
            int titleId = getResources()
                    .getIdentifier("alertTitle", "id", "android");
            TextView title = dialog.findViewById(titleId);
            if (title != null) title.setTextColor(Color.WHITE);

            // Message
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) {
                message.setTextColor(Color.rgb(210, 220, 232));
            }

            // Buttons
            int accent = getAppAccentColor();
            int positiveColor = destructive
                    ? Color.rgb(240, 135, 135) : accent;

            Button positive = dialog.getButton(
                    android.app.AlertDialog.BUTTON_POSITIVE);
            Button negative = dialog.getButton(
                    android.app.AlertDialog.BUTTON_NEGATIVE);

            if (positive != null) positive.setTextColor(positiveColor);
            if (negative != null) negative.setTextColor(Color.rgb(145, 158, 175));
        });

        dialog.show();
    }

    private void toggleOverlay() {
        boolean enabled = isOverlayServiceRunning();

        if (enabled) {
            stopService(new Intent(this, FloatingFpsService.class));
            getPrefs().edit()
                    .putBoolean("overlay_enabled", false)
                    .putBoolean("overlay_engine_active", false)
                    .apply();
            overlayButton.setText("TURN ON OVERLAY");
            Toast.makeText(this, "Floating overlay disabled",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            return;
        }

        Intent serviceIntent = new Intent(this, FloatingFpsService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        getPrefs().edit().putBoolean("overlay_enabled", true).apply();
        overlayButton.setText("TURN OFF OVERLAY");
        wakeSampler();

        Toast.makeText(this, "Floating overlay enabled",
                Toast.LENGTH_SHORT).show();
    }

    // Small card for the 2x2 grid; each one takes half the row
    private TextView tile(LinearLayout row, String label, boolean last) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(8), dp(14), dp(10));
        card.setBackground(cardBg(14));
        card.setElevation(dp(3));

        TextView title = text(label, 11, true, Color.rgb(155, 174, 194));
        title.setSingleLine(true);

        TextView valueView = text("—", 22, true, Color.WHITE);
        valueView.setPadding(0, dp(1), 0, 0);
        valueView.setMaxLines(1);

        card.addView(title);
        card.addView(valueView);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (!last) lp.rightMargin = dp(7);
        row.addView(card, lp);

        return valueView;
    }


    private TextView section(String value) {
        TextView heading = text(value, 13, true, getAppAccentColor());
        heading.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_section_mark, 0, 0, 0);
        heading.setCompoundDrawablePadding(dp(8));
        heading.setCompoundDrawableTintList(
                android.content.res.ColorStateList.valueOf(getAppAccentColor()));
        appAccentTextViews.add(heading);
        return heading;
    }

    private void compactButton(Button b) {
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setSingleLine(true);
        b.setEllipsize(null);
    }

    private TextView text(String value, int size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return view;
    }

    private void add(LinearLayout parent, View view, int width, int bottom) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        width == 0 ? -1 : width, -2);

        params.bottomMargin = dp(bottom);
        parent.addView(view, params);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void startSession() {
        if (running) return;

        samples.clear();
        csv.setLength(0);

        csv.append(
                "Time,App,FPS,Current (mA),Voltage (V),Power (W)," +
                        "Battery (%),Seconds per 1% drain,Temperature (°C)," +
                        "CPU 0 (%),CPU 1 (%),CPU 2 (%),CPU 3 (%)," +
                        "CPU 4 (%),CPU 5 (%),CPU 6 (%),CPU 7 (%)," +
                        "CPU 0 Frequency (MHz),CPU 1 Frequency (MHz)," +
                        "CPU 2 Frequency (MHz),CPU 3 Frequency (MHz)," +
                        "CPU 4 Frequency (MHz),CPU 5 Frequency (MHz)," +
                        "CPU 6 Frequency (MHz),CPU 7 Frequency (MHz)," +
                        "GPU Frequency (MHz),GPU Load (%)," +
                        "Average Frame Time (ms),Frame Time Std Dev (ms)," +
                        "Stutter Count,FPS Source\n"
        );

        monitoringEngine.resetCpuBaseline();
        monitoringEngine.resetSessionClock();
        sessionId++;
        running = true;

        getSharedPreferences("session_state", MODE_PRIVATE)
                .edit()
                .putBoolean("recording", true)
                .apply();

        startButton.setText("STOP SESSION");


        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(170, 55, 65));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.rgb(205, 85, 95));
        startButton.setBackground(bg);

        summary.setText("Session in progress…");
        refreshStatusPill();

    }

    private void stopSession() {
        if (!running) return;

        running = false;
        getSharedPreferences("session_state", MODE_PRIVATE)
                .edit()
                .putBoolean("recording", false)
                .apply();
        sessionId++;

        startButton.setText("START SESSION");

        int accentColor = getAppAccentColor();
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(accentColor);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.argb(
                180,
                Color.red(accentColor),
                Color.green(accentColor),
                Color.blue(accentColor)));
        startButton.setBackground(bg);

        String result = makeSummary();

        summary.setText(result);
        refreshStatusPill();

        if (!samples.isEmpty()) {
            saveSession(result);
        }
    }

    private String formatElapsed(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void appendCsv(Sample s) {
        StringBuilder row = new StringBuilder();

        row.append(formatElapsed(s.timeMs)).append(',')
                .append("\"")
                .append(s.app.replace("\"", "\"\""))
                .append("\"").append(',')
                .append(s.fps < 0 ? "" :
                        String.format(Locale.US, "%.0f", s.fps))
                .append(',')
                .append(String.format(Locale.US,
                        "%.0f,%.3f,%.2f,%d,",
                        s.currentMa, s.voltage, s.power,
                        s.battery));

        if (s.secondsPerPercent >= 0) {
            row.append(String.format(Locale.US,
                    "%.1f", s.secondsPerPercent));
        }

        row.append(',')
                .append(String.format(Locale.US,
                        "%.1f", s.temperatureC));

        for (double value : s.cpu) {
            row.append(',');

            if (value >= 0) {
                row.append(String.format(Locale.US, "%.1f", value));
            }
        }

        for (double value : s.cpuFreqMHz) {
            row.append(',');

            if (value >= 0) {
                row.append(String.format(Locale.US, "%.1f", value));
            }
        }

        row.append(',');

        if (s.gpuFreqMHz >= 0) {
            row.append(String.format(Locale.US, "%.1f", s.gpuFreqMHz));
        }

        row.append(',');

        if (s.gpuLoadPercent >= 0) {
            row.append(String.format(Locale.US, "%.1f", s.gpuLoadPercent));
        }

        row.append(',');

        if (s.averageFrameTimeMs >= 0) {
            row.append(String.format(Locale.US, "%.2f", s.averageFrameTimeMs));
        }

        row.append(',');

        if (s.frameTimeStdDevMs >= 0) {
            row.append(String.format(Locale.US, "%.2f", s.frameTimeStdDevMs));
        }

        row.append(',');

        if (s.stutterCount >= 0) {
            row.append(s.stutterCount);
        }

        row.append(',');
        if (s.fpsSource != null && !s.fpsSource.isEmpty()) {
            row.append(s.fpsSource);
        }

        csv.append(row).append('\n');
    }

    private String deviceName() {
        String soc = "";
        if (Build.VERSION.SDK_INT >= 31
                && !"unknown".equalsIgnoreCase(Build.SOC_MODEL)) {
            soc = " (" + Build.SOC_MODEL + ")";
        }
        return Build.MANUFACTURER + " " + Build.MODEL + soc;
    }

    private String buildCsv(String result) {
        StringBuilder output = new StringBuilder();

        output.append("SESSION INFO\n");
        output.append("Device,\"")
                .append(deviceName().replace("\"", "\"\""))
                .append("\"\n");
        output.append("Android,").append(Build.VERSION.RELEASE).append('\n');
        output.append("Mode,").append(cachedMonitoringMode).append('\n');
        output.append("Summary\n");
        output.append("\"")
                .append(result.replace("\"", "\"\""))
                .append("\"\n\n");

        output.append("PERFORMANCE READINGS\n");
        output.append(csv);

        return output.toString();
    }

    private void setTextIfChanged(TextView view, String value) {
        if (!value.contentEquals(view.getText())) {
            view.setText(value);
        }
    }


    private void render(Sample s) {

        setTextIfChanged(fps, s.fps < 0
                ? "—"
                : String.format(Locale.US, "%.0f", s.fps));

        double cpuSum = 0;
        int cpuCount = 0;
        for (double v : s.cpu) {
            if (v >= 0) { cpuSum += v; cpuCount++; }
        }
        setTextIfChanged(cpuTile, cpuCount > 0
                ? String.format(Locale.US, "%.0f%%", cpuSum / cpuCount)
                : "—");

        setTextIfChanged(gpuTile, s.gpuLoadPercent < 0
                ? "—"
                : String.format(Locale.US, "%.0f%%", s.gpuLoadPercent));

        setTextIfChanged(power,
                s.power == -1
                        ? "—"
                        : String.format(Locale.US, "%.2f W", s.power));

        setTextIfChanged(powerState,
                s.charging ? "charging" : "discharging");

        setTextIfChanged(current,
                s.currentMa == -1
                        ? "—"
                        : String.format(Locale.US, "%.0f mA", s.currentMa));

        setTextIfChanged(voltage,
                s.voltage < 0
                        ? "—"
                        : String.format(Locale.US, "%.3f V", s.voltage));

        setTextIfChanged(battery, s.battery + "%");

        setTextIfChanged(temperature,
                String.format(Locale.US, "%.1f°C", s.temperatureC));
    }




    private String makeSummary() {
        if (samples.isEmpty()) return "No valid readings collected.";

        int startBattery = samples.get(0).battery;
        int endBattery = samples.get(samples.size() - 1).battery;

        long duration = samples.get(samples.size() - 1).timeMs;

        return String.format(Locale.US,
                "Run time: %.1f minutes\n" +
                        "Battery level: %d%% → %d%% (%d%% change)",
                duration / 60000.0,
                startBattery,
                endBattery,
                endBattery - startBattery);
    }


    private void saveSession(String result) {
        try {
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            String csvName = "session_" + stamp + ".csv";

            // Write CSV
            try (FileOutputStream out = openFileOutput(csvName, MODE_PRIVATE)) {
                out.write(buildCsv(result).getBytes(StandardCharsets.UTF_8));
            }

            loadHistory();
            Toast.makeText(this, "Session saved on this device", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save session", Toast.LENGTH_LONG).show();
        }
    }




    private void loadHistory() {
        loadHistory(false);
    }

    private void loadHistory(boolean scrollToTop) {
        if (sessionsAdapter == null) return;
        final File folder = getFilesDir();
        if (folder == null) return;

        // snapshot on the main thread — pendingDeletes is only mutated there
        final java.util.HashSet<String> hidden = new java.util.HashSet<>(pendingDeletes);
        final int generation = ++historyGeneration;

        fileWorker.execute(() -> {
            File[] files = folder.listFiles((dir, name) ->
                    name.startsWith("session_") && name.endsWith(".csv")
                            && !hidden.contains(name));

            java.util.List<SessionItem> items = new ArrayList<>();
            if (files != null) {
                java.util.Arrays.sort(files,
                        (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File file : files) {
                    SessionData meta = SessionData.readMeta(this, file);
                    long when = meta != null
                            ? meta.sessionDate().getTime() : file.lastModified();
                    items.add(new SessionItem(
                            file.getName(),
                            file.lastModified(),
                            when,
                            meta != null ? meta.deviceLabel() : "Unknown device",
                            meta != null && meta.imported,
                            meta != null ? meta.originalName : ""));
                }
            }

            runOnUiThread(() -> {
                if (generation != historyGeneration) return;
                if (sessionsAdapter == null) return;
                sessionsAdapter.submit(items);
                if (scrollToTop && sessionsList != null) {
                    sessionsList.scrollToPosition(0);
                }
                if (sessionsCount != null) {
                    sessionsCount.setText(items.size() + " SAVED");
                }
                boolean empty = items.isEmpty();
                sessionsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                sessionsList.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }

    private void importSession() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, 44);
    }

    private void importFromUri(android.net.Uri uri) {
        Toast.makeText(this, "Importing…", Toast.LENGTH_SHORT).show();

        fileWorker.execute(() -> {
            boolean ok = false;
            String message = "Import failed";
            File dest = null;

            try {
                String original = "unknown.csv";
                try (android.database.Cursor c = getContentResolver()
                        .query(uri, null, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        int idx = c.getColumnIndex(
                                android.provider.OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0 && !c.isNull(idx)) original = c.getString(idx);
                    }
                }

                String stamp = new java.text.SimpleDateFormat(
                        "yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                        .format(new java.util.Date());
                dest = new File(getFilesDir(),
                        "session_imported_" + stamp + ".csv");

                try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                    if (in == null) throw new java.io.IOException("Could not open file.");
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }

                if (SessionData.load(dest) == null) {
                    dest.delete();
                    message = "Not a valid ONYX session CSV";
                } else {
                    getSharedPreferences("session_meta", MODE_PRIVATE).edit()
                            .putString(dest.getName() + ".original", original)
                            .putLong(dest.getName() + ".imported_at",
                                    System.currentTimeMillis())
                            .apply();

                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})")
                            .matcher(original);
                    if (m.find()) {
                        try {
                            java.util.Date when = new java.text.SimpleDateFormat(
                                    "yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                                    .parse(m.group(1));
                            if (when != null) dest.setLastModified(when.getTime());
                        } catch (Exception ignored) {
                        }
                    }
                    ok = true;
                    message = "Session imported";
                }
            } catch (Exception e) {
                if (dest != null) dest.delete();   // don't leave a half-copied file
                message = "Import failed: " + e.getMessage();
            }

            final boolean success = ok;
            final String result = message;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (success) loadHistory();
                Toast.makeText(this, result, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void exportPastSession(String filename) {
        pendingExportFile = filename;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, filename);

        startActivityForResult(intent, 43);
    }

    // ---------- Pending deletes (undo) ----------

    private static final long UNDO_MS = 5500;
    private final java.util.LinkedHashSet<String> pendingDeletes =
            new java.util.LinkedHashSet<>();
    private boolean historyFileDeleteRequested = false;
    private LinearLayout undoBar;
    private TextView undoText;
    private TextView undoAction;
    private final Runnable finalizeRunnable = this::finalizePendingDeletes;

    /** Hides the sessions right away; files are only deleted when the undo window ends. */
    private void queueDelete(java.util.Collection<String> names, boolean alsoHistoryFile) {
        if (names.isEmpty()) return;

        pendingDeletes.addAll(names);
        if (alsoHistoryFile) historyFileDeleteRequested = true;
        if (pinnedFile != null && names.contains(pinnedFile)) {
            pinnedFile = null;
            updatePinUi(false);
        }
        loadHistory();
        showUndoBar();
    }

    private void deleteSessionWithUndo(String fileName) {
        queueDelete(java.util.Collections.singletonList(fileName), false);
    }

    private void showSessionMenu(View anchor, String fileName) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(6), dp(6), dp(6), dp(6));

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(35, 44, 59));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.rgb(47, 59, 78));
        menu.setBackground(bg);

        final android.widget.PopupWindow popup =
                new android.widget.PopupWindow(menu, dp(180),
                        LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(8));

        menu.addView(menuRow("Export CSV", Color.WHITE, () -> {
            popup.dismiss();
            exportPastSession(fileName);
        }));
        if (pinnedFile == null) {
            menu.addView(menuRow("Compare with…", Color.WHITE, () -> {
                popup.dismiss();
                setPinned(fileName);
            }));
        }
        menu.addView(menuRow("Delete", Color.rgb(240, 104, 122), () -> {
            popup.dismiss();
            deleteSessionWithUndo(fileName);
        }));

        popup.showAsDropDown(anchor, 0, 0, Gravity.END);
    }

    private void onSessionTapped(String name) {
        if (pinnedFile == null) {
            Intent intent = new Intent(this, SessionAnalysisActivity.class);
            intent.putExtra("session_filename", name);
            startActivity(intent);
        } else if (pinnedFile.equals(name)) {
            pinnedFile = null;            // tapping the pinned card unpins it
            updatePinUi(true);
        } else {
            launchPinnedCompare(name);
        }
    }

    private void setPinned(String name) {
        if (visibleSessionNames().size() < 2) {
            Toast.makeText(this, "Need at least 2 saved sessions to compare",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        pinnedFile = name;
        updatePinUi(true);
    }

    private void launchPinnedCompare(String second) {
        Intent i = new Intent(this, SessionCompareActivity.class);
        i.putExtra("a", pinnedFile);
        i.putExtra("b", second);
        pinnedFile = null;
        updatePinUi(true);
        startActivity(i);
    }

    /** Shows or hides the banner. rebind = redraw the cards (glow / badge). */
    private void updatePinUi(boolean rebind) {
        if (pinBanner == null) return;

        int accent = getAppAccentColor();
        pinBannerBg.setColor(Color.argb(30,
                Color.red(accent), Color.green(accent), Color.blue(accent)));
        pinBannerBg.setStroke(dp(1), Color.argb(100,
                Color.red(accent), Color.green(accent), Color.blue(accent)));
        pinBannerText.setTextColor(accent);
        pinBanner.setVisibility(pinnedFile != null ? View.VISIBLE : View.GONE);

        if (rebind && sessionsAdapter != null) {
            sessionsAdapter.notifyDataSetChanged();
        }
    }

    private android.widget.ImageButton headerIcon(int res, String description,
                                                  View.OnClickListener click) {
        android.widget.ImageButton b = new android.widget.ImageButton(this);
        b.setImageResource(res);
        b.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        b.setContentDescription(description);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        b.setOnClickListener(click);
        b.setTooltipText(description);
        return b;
    }


    private void showTopMenu(View anchor) {
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(6), dp(6), dp(6), dp(6));

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(35, 44, 59));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.rgb(47, 59, 78));
        menu.setBackground(bg);

        final android.widget.PopupWindow popup =
                new android.widget.PopupWindow(menu, dp(190),
                        LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(8));

        // Temporary: goes away when swipe-to-compare arrives
        menu.addView(menuRow("Export all", Color.WHITE, () -> {
            popup.dismiss();
            exportAllSessions();
        }));
        menu.addView(menuRow("Delete all", Color.rgb(240, 104, 122), () -> {
            popup.dismiss();
            clearSessionHistory();
        }));

        popup.showAsDropDown(anchor, 0, 0, Gravity.END);
    }

    private void exportAllSessions() {
        if (visibleSessionNames().isEmpty()) {
            Toast.makeText(this, "No sessions to export", Toast.LENGTH_SHORT).show();
            return;
        }

        String stamp = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "onyx-sessions-" + stamp + ".zip");
        startActivityForResult(intent, 45);
    }

    private void writeAllSessionsZip(Uri uri) {
        final java.util.List<String> names = visibleSessionNames();

        new Thread(() -> {
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
                    getContentResolver().openOutputStream(uri))) {

                byte[] buffer = new byte[8192];
                int count = 0;

                for (String name : names) {
                    File file = new File(getFilesDir(), name);
                    if (!file.exists()) continue;

                    zip.putNextEntry(new java.util.zip.ZipEntry(name));
                    try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                        int n;
                        while ((n = in.read(buffer)) != -1) zip.write(buffer, 0, n);
                    }
                    zip.closeEntry();
                    count++;
                }

                final int exported = count;
                runOnUiThread(() -> Toast.makeText(this,
                        "Exported " + exported + " sessions",
                        Toast.LENGTH_SHORT).show());

            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Export failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private TextView menuRow(String label, int color, Runnable action) {
        TextView row = text(label, 14, false, color);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setMinHeight(dp(44));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private java.util.List<String> visibleSessionNames() {
        java.util.List<String> names = new ArrayList<>();
        File[] files = getFilesDir().listFiles((dir, n) ->
                n.startsWith("session_") && n.endsWith(".csv")
                        && !pendingDeletes.contains(n));
        if (files != null) {
            for (File f : files) names.add(f.getName());
        }
        return names;
    }

    private void showUndoBar() {
        if (undoBar == null) {
            undoBar = new LinearLayout(this);
            undoBar.setOrientation(LinearLayout.HORIZONTAL);
            undoBar.setGravity(Gravity.CENTER_VERTICAL);
            undoBar.setPadding(dp(14), 0, dp(4), 0);
            undoBar.setElevation(dp(8));

            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(Color.rgb(42, 52, 68));
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(1), Color.rgb(52, 66, 90));
            undoBar.setBackground(bg);

            undoText = text("", 14, false, Color.WHITE);
            undoBar.addView(undoText, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            undoAction = text("Undo", 14, true, getAppAccentColor());
            undoAction.setPadding(dp(14), dp(12), dp(14), dp(12));
            undoAction.setOnClickListener(v -> undoDeletes());
            undoBar.addView(undoAction);
        }

        int n = pendingDeletes.size();
        undoText.setText(n == 1 ? "Session deleted" : n + " sessions deleted");
        undoAction.setTextColor(getAppAccentColor());

        if (undoBar.getParent() == null) {
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            dp(48));
            lp.gravity = Gravity.BOTTOM;
            lp.setMargins(dp(12), 0, dp(12), dp(16));
            pageContainer.addView(undoBar, lp);
        }

        // every new delete restarts the countdown
        handler.removeCallbacks(finalizeRunnable);
        handler.postDelayed(finalizeRunnable, UNDO_MS);
    }

    private void hideUndoBar() {
        if (undoBar != null && undoBar.getParent() != null) {
            pageContainer.removeView(undoBar);
        }
    }

    private void undoDeletes() {
        handler.removeCallbacks(finalizeRunnable);
        pendingDeletes.clear();
        historyFileDeleteRequested = false;
        hideUndoBar();
        // The history reload runs on a worker; scroll only after the restored
        // items have been submitted to the adapter on the main thread.
        loadHistory(true);
    }

    /** Really deletes everything that is pending. Safe to call any time. */
    private void finalizePendingDeletes() {
        handler.removeCallbacks(finalizeRunnable);
        if (!pendingDeletes.isEmpty()) {
            SharedPreferences.Editor metaEditor =
                    getSharedPreferences("session_meta", MODE_PRIVATE).edit();

            for (String name : pendingDeletes) {
                new File(getFilesDir(), name).delete();
                metaEditor.remove(name + ".original")
                        .remove(name + ".imported_at");
            }

            // Apply all metadata removals together instead of once per session.
            metaEditor.apply();
        }
        if (historyFileDeleteRequested) {
            File historyFile = new File(getFilesDir(), "session_history.txt");
            if (historyFile.exists()) historyFile.delete();
        }
        pendingDeletes.clear();
        historyFileDeleteRequested = false;
        hideUndoBar();
    }

    private void clearSessionHistory() {
        java.util.List<String> names = visibleSessionNames();

        if (names.isEmpty()) {
            Toast.makeText(this, "No sessions to clear", Toast.LENGTH_SHORT).show();
            return;
        }

        showDarkDialog(
                darkDialogBuilder()
                        .setTitle("Delete all " + names.size() + " sessions?")
                        .setMessage("They'll be removed from this device. You can undo for a few seconds.")
                        .setNegativeButton("CANCEL", null)
                        .setPositiveButton("DELETE ALL",
                                (dialog, which) -> queueDelete(names, true)),
                true);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 45 && result == RESULT_OK
                && data != null && data.getData() != null) {
            writeAllSessionsZip(data.getData());
            return;
        }

        if (request == 44 && result == RESULT_OK
                && data != null && data.getData() != null) {
            importFromUri(data.getData());
            return;
        }

        if (request == 43 && result == RESULT_OK
                && data != null && data.getData() != null) {

            try {
                File source = new File(
                        getFilesDir(),
                        pendingExportFile
                );

                try (
                        java.io.FileInputStream input =
                                new java.io.FileInputStream(source);
                        java.io.OutputStream output =
                                getContentResolver().openOutputStream(data.getData())
                ) {
                    if (output == null) {
                        throw new java.io.IOException("Could not open destination.");
                    }

                    byte[] buffer = new byte[8192];
                    int length;

                    while ((length = input.read(buffer)) != -1) {
                        output.write(buffer, 0, length);
                    }
                }

                android.widget.Toast.makeText(
                        this, "Session exported successfully!",
                        android.widget.Toast.LENGTH_SHORT
                ).show();

            } catch (Exception e) {
                android.widget.Toast.makeText(
                        this, "Export failed: " + e.getMessage(),
                        android.widget.Toast.LENGTH_LONG
                ).show();
            }

            pendingExportFile = null;
            return;
        }

        if (request == 42 && result == RESULT_OK && data != null) {
            try (OutputStream output =
                         getContentResolver().openOutputStream(data.getData())) {

                output.write(buildCsv(makeSummary()).getBytes(StandardCharsets.UTF_8));

                Toast.makeText(this, "CSV exported",
                        Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Toast.makeText(this, "Export failed",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeRequestPermissionResultListener(
                shizukuPermissionListener
        );

        running = false;
        handler.removeCallbacks(sampler);
        handoffOverlaySampling();
        worker.shutdownNow();
        fileWorker.shutdownNow();
        try {
            unregisterReceiver(overlayToggleReceiver);
        } catch (IllegalArgumentException ignored) {
        }

        super.onDestroy();
    }

}