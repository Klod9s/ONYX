package com.onyx.perfmonitor;

import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** File-system operations for saved session history. */
final class SessionFileManager {

    private SessionFileManager() {
    }

    static List<String> listVisibleSessionNames(
            File filesDir, Collection<String> pendingDeletes) {
        List<String> names = new ArrayList<>();
        File[] files = filesDir.listFiles((dir, name) ->
                name.startsWith("session_") && name.endsWith(".csv")
                        && !pendingDeletes.contains(name));
        if (files != null) {
            for (File file : files) {
                names.add(file.getName());
            }
        }
        return names;
    }


    static List<SessionItem> loadHistoryItems(
            android.content.Context context,
            File folder,
            Collection<String> hiddenNames) {
        File[] files = folder.listFiles((dir, name) ->
                name.startsWith("session_") && name.endsWith(".csv")
                        && !hiddenNames.contains(name));

        List<SessionItem> items = new ArrayList<>();
        if (files != null) {
            java.util.Arrays.sort(files,
                    (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File file : files) {
                SessionData meta = SessionData.readMeta(context, file);
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
        return items;
    }


    static ImportResult importSession(
            android.content.ContentResolver resolver,
            android.net.Uri uri,
            File filesDir,
            SharedPreferences metadata) {
        File dest = null;
        try {
            String original = "unknown.csv";
            try (android.database.Cursor cursor = resolver.query(
                    uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME);
                    if (index >= 0 && !cursor.isNull(index)) {
                        original = cursor.getString(index);
                    }
                }
            }

            String stamp = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                    .format(new java.util.Date());
            dest = new File(filesDir, "session_imported_" + stamp + ".csv");

            try (java.io.InputStream input = resolver.openInputStream(uri);
                 java.io.FileOutputStream output =
                         new java.io.FileOutputStream(dest)) {
                if (input == null) {
                    throw new java.io.IOException("Could not open file.");
                }
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }

            if (SessionData.load(dest) == null) {
                dest.delete();
                return new ImportResult(
                        false, "Not a valid ONYX session CSV");
            }

            metadata.edit()
                    .putString(dest.getName() + ".original", original)
                    .putLong(dest.getName() + ".imported_at",
                            System.currentTimeMillis())
                    .apply();

            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})")
                    .matcher(original);
            if (matcher.find()) {
                try {
                    java.util.Date when = new java.text.SimpleDateFormat(
                            "yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                            .parse(matcher.group(1));
                    if (when != null) {
                        dest.setLastModified(when.getTime());
                    }
                } catch (Exception ignored) {
                }
            }

            return new ImportResult(true, "Session imported");
        } catch (Exception e) {
            if (dest != null) {
                dest.delete();
            }
            return new ImportResult(false, "Import failed: " + e.getMessage());
        }
    }

    static final class ImportResult {
        final boolean success;
        final String message;

        ImportResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    static void finalizePendingDeletes(
            File filesDir,
            SharedPreferences metadata,
            Collection<String> pendingDeletes,
            boolean deleteHistoryFile) {
        if (!pendingDeletes.isEmpty()) {
            SharedPreferences.Editor editor = metadata.edit();

            for (String name : pendingDeletes) {
                new File(filesDir, name).delete();
                editor.remove(name + ".original")
                        .remove(name + ".imported_at");
            }

            editor.apply();
        }

        if (deleteHistoryFile) {
            File historyFile = new File(filesDir, "session_history.txt");
            if (historyFile.exists()) {
                historyFile.delete();
            }
        }
    }
}
