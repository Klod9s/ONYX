package com.onyx.perfmonitor;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/** Owns Shizuku permission, binder, and user-service lifecycle. */
final class ShizukuController {
    private static final int REQUEST_CODE = 1001;

    private final MainActivity activity;
    private final Shizuku.UserServiceArgs serviceArgs;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != REQUEST_CODE) return;

                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(activity,
                            "Shizuku permission granted!",
                            Toast.LENGTH_SHORT).show();
                    startService();
                } else {
                    Toast.makeText(activity,
                            "Shizuku permission denied.",
                            Toast.LENGTH_SHORT).show();
                }
            };

    private final ServiceConnection serviceConnection =
            new ServiceConnection();

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        activity.cachedMonitoringMode = "";
        if (activity.monitoringEngine != null) {
            activity.monitoringEngine.resetMonitoringModeCheck();
        }

        activity.runOnUiThread(() -> {
            activity.shizukuBindRequested = false;
            startService();
        });
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        setService(null);
        activity.shizukuBindRequested = false;
        activity.runOnUiThread(() ->
                activity.setStatus("SHIZUKU · DISCONNECTED", true));
    };

    ShizukuController(MainActivity activity) {
        this.activity = activity;
        serviceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(
                        activity.getPackageName(),
                        ShizukuShellService.class.getName()
                )
        )
                .daemon(false)
                .processNameSuffix("onyx")
                .debuggable(false)
                .version(1);
    }

    void registerListeners() {
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);
    }

    void unregisterListeners() {
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
    }

    void startService() {
        if (!Shizuku.pingBinder()) {
            activity.shizukuBindRequested = false;
            Toast.makeText(activity,
                    "Shizuku is not running.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        if (Shizuku.checkSelfPermission()
                != PackageManager.PERMISSION_GRANTED) {
            activity.shizukuBindRequested = false;
            requestAccess();
            return;
        }

        try {
            if (activity.shizukuService != null
                    && activity.shizukuService.asBinder().pingBinder()) {
                activity.shizukuBindRequested = false;
                return;
            }

            activity.shizukuBindRequested = true;
            Shizuku.bindUserService(serviceArgs, serviceConnection);
        } catch (Exception e) {
            Toast.makeText(activity,
                    "Failed to start Shizuku service: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            activity.shizukuBindRequested = false;
        }
    }

    private void requestAccess() {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(activity,
                    "Shizuku is not running. Please start it first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (Shizuku.isPreV11()) {
            Toast.makeText(activity,
                    "Your Shizuku version is not supported.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (Shizuku.checkSelfPermission()
                == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity,
                    "Shizuku access already granted!",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Shizuku.requestPermission(REQUEST_CODE);
    }

    private void setService(IOnyxUserService service) {
        activity.shizukuService = service;
        if (activity.monitoringEngine != null) {
            activity.monitoringEngine.setShizukuService(service);
        }
    }

    private final class ServiceConnection
            implements android.content.ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (binder == null || !binder.pingBinder()) return;

            setService(IOnyxUserService.Stub.asInterface(binder));
            activity.shizukuBindRequested = false;
            activity.runOnUiThread(() ->
                    Toast.makeText(activity,
                            "Shizuku service connected!",
                            Toast.LENGTH_SHORT).show());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            setService(null);
            activity.shizukuBindRequested = false;
        }
    }
}
