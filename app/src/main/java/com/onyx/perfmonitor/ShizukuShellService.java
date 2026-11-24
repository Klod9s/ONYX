package com.onyx.perfmonitor;

import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class ShizukuShellService extends IOnyxUserService.Stub {

    public ShizukuShellService() {
    }

    @Override
    public String runCommand(String command) throws RemoteException {
        // --- LOOPHOLE #1 FIX: Prevent NPE if command is null or empty ---
        if (command == null || command.trim().isEmpty()) {
            return "ERROR: Empty command";
        }

        Process process = null;
        Thread readerThread = null;

        try {
            process = new ProcessBuilder(
                    "sh", "-c", command
            ).redirectErrorStream(true).start();

            final Process activeProcess = process;
            final StringBuilder output = new StringBuilder();

            readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                activeProcess.getInputStream()
                        )
                )) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (output) {
                            if (output.length() < 2_000_000) {
                                output.append(line).append("\n");
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            });

            readerThread.setDaemon(true);
            readerThread.start();

            // --- LOOPHOLE #2 FIX: Bumped timeout from 3s to 5s for heavy dumpsys calls ---
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor(500, TimeUnit.MILLISECONDS);

                try {
                    process.getInputStream().close();
                } catch (Exception ignored) {}

                readerThread.join(500);
                return "ERROR: Command timed out";
            }

            readerThread.join(500);

            synchronized (output) {
                return output.toString();
            }

        } catch (Exception e) {
            return "ERROR: " + e.getMessage();

        } finally {
            if (process != null) {
                try {
                    process.getInputStream().close();
                } catch (Exception ignored) {}
                process.destroy();
            }
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}