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

    private final Shizuku.OnRequestPermissionResultListener permissionListener;

    private final ServiceConnection serviceConnection =
            new ServiceConnection();

    private final Shizuku.OnBinderReceivedListener binderReceivedListener;

    private final Shizuku.OnBinderDeadListener binderDeadListener;

    ShizukuController(MainActivity activity) {
        this.activity = activity;

        permissionListener = (requestCode, grantResult) -> {
            if (requestCode != REQUEST_CODE) return;

            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this.activity,
                        "Shizuku permission granted!",
                        Toast.LENGTH_SHORT).show();
                startService();
            } else {
                Toast.makeText(this.activity,
                        "Shizuku permission denied.",
                        Toast.LENGTH_SHORT).show();
            }
        };

        binderReceivedListener = () -> {
            this.activity.cachedMonitoringMode = "";
            if (this.activity.monitoringEngine != null) {
                this.activity.monitoringEngine.resetMonitoringModeCheck();
            }

            this.activity.runOnUiThread(() -> {
                this.activity.shizukuBindRequested = false;
                startService();
            });
        };

        binderDeadListener = () -> {
            setService(null);
            this.activity.shizukuBindRequested = false;
            this.activity.runOnUiThread(() ->
                    this.activity.setStatus("SHIZUKU · DISCONNECTED", true));
        };

        serviceArgs = new Shizuku.UserServiceArgs(
                new ComponentName(
                        activity.getPackageName(),
                        ShizukuShellService.class.getName()
                )
        )
                .daemon(false)
                .processNameSuffix("onyx")
                .debuggable(false)
                .version(2);
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

    void unbindService() {
        activity.shizukuBindRequested = false;
        setService(null);
        try {
            Shizuku.unbindUserService(serviceArgs, serviceConnection, true);
        } catch (Exception ignored) {
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
