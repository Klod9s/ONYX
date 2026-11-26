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

    static final long MAX_IMPORT_BYTES = 20L * 1024L * 1024L;

    static boolean isImportSizeAllowed(long sizeBytes) {
        return sizeBytes >= 0 && sizeBytes <= MAX_IMPORT_BYTES;
    }

    /**
     * Accept only ONYX-owned session filenames. Never allow a caller-provided
     * path, separator, hidden filename, or arbitrary extension into filesDir.
     */
    static boolean isSafeSessionFilename(String filename) {
        return filename != null
                && filename.length() <= 96
                && filename.matches("session_(?:imported_)?[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{2}-[0-9]{2}-[0-9]{2}(?:-[0-9]{1,6})?\\.csv");
    }

    private static File resolveSessionFile(File filesDir, String filename)
            throws java.io.IOException {
        if (filesDir == null || !isSafeSessionFilename(filename)) {
            throw new java.io.IOException("Invalid session filename.");
        }
        File root = filesDir.getCanonicalFile();
        File file = new File(root, filename).getCanonicalFile();
        if (!root.equals(file.getParentFile())) {
            throw new java.io.IOException("Invalid session path.");
        }
        return file;
    }

    static List<String> listVisibleSessionNames(
            File filesDir, Collection<String> pendingDeletes) {
        List<String> names = new ArrayList<>();
        File[] files = filesDir.listFiles((dir, name) ->
                isSafeSessionFilename(name) && !pendingDeletes.contains(name));
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
                isSafeSessionFilename(name) && !hiddenNames.contains(name));

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

            // Reject providers that report an oversized file before copying it.
            try (android.database.Cursor cursor = resolver.query(
                    uri, new String[]{android.provider.OpenableColumns.SIZE},
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(
                            android.provider.OpenableColumns.SIZE);
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        long reportedSize = cursor.getLong(sizeIndex);
                        if (!isImportSizeAllowed(reportedSize)) {
                            return new ImportResult(false,
                                    "Import rejected: CSV exceeds the 20 MB limit.");
                        }
                    }
                }
            }

            String stamp = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss", java.util.Locale.US)
                    .format(new java.util.Date());
            String baseName = "session_imported_" + stamp;
            dest = resolveSessionFile(filesDir, baseName + ".csv");
            int suffix = 1;
            while (!dest.createNewFile()) {
                if (!dest.exists() || suffix > 999999) {
                    throw new java.io.IOException(
                            "Could not reserve an import filename.");
                }
                dest = resolveSessionFile(
                        filesDir, baseName + "-" + suffix++ + ".csv");
            }

            try (java.io.InputStream input = resolver.openInputStream(uri);
                 java.io.FileOutputStream output =
                         new java.io.FileOutputStream(dest)) {
                if (input == null) {
                    throw new java.io.IOException("Could not open file.");
                }
                byte[] buffer = new byte[8192];
                int count;
                long totalBytes = 0;
                while ((count = input.read(buffer)) != -1) {
                    if (count > MAX_IMPORT_BYTES - totalBytes) {
                        throw new ImportTooLargeException();
                    }
                    totalBytes += count;
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
        } catch (ImportTooLargeException e) {
            if (dest != null) {
                dest.delete();
            }
            return new ImportResult(false,
                    "Import rejected: CSV exceeds the 20 MB limit.");
        } catch (Exception e) {
            if (dest != null) {
                dest.delete();
            }
            return new ImportResult(false, "Import failed: " + e.getMessage());
        }
    }

    private static final class ImportTooLargeException extends java.io.IOException {
    }

    static final class ImportResult {
        final boolean success;
        final String message;

        ImportResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }


    static void copySessionToUri(
            android.content.ContentResolver resolver,
            android.net.Uri uri,
            File filesDir,
            String filename) throws java.io.IOException {
        File source = resolveSessionFile(filesDir, filename);
        if (!source.isFile()) {
            throw new java.io.IOException("Session file not found.");
        }
        try (java.io.FileInputStream input =
                     new java.io.FileInputStream(source);
             java.io.OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) {
                throw new java.io.IOException("Could not open destination.");
            }

            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
        }
    }

    static int writeSessionsZip(
            android.content.ContentResolver resolver,
            android.net.Uri uri,
            File filesDir,
            Collection<String> names) throws java.io.IOException {
        java.io.OutputStream output = resolver.openOutputStream(uri);
        if (output == null) {
            throw new java.io.IOException("Could not open destination.");
        }

        int count = 0;
        try (java.util.zip.ZipOutputStream zip =
                     new java.util.zip.ZipOutputStream(output)) {
            byte[] buffer = new byte[8192];
            for (String name : names) {
                File file = resolveSessionFile(filesDir, name);
                if (!file.isFile()) {
                    continue;
                }

                zip.putNextEntry(new java.util.zip.ZipEntry(name));
                try (java.io.FileInputStream input =
                             new java.io.FileInputStream(file)) {
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        zip.write(buffer, 0, length);
                    }
                }
                zip.closeEntry();
                count++;
            }
        }
        return count;
    }


    static void finalizePendingDeletes(
            File filesDir,
            SharedPreferences metadata,
            Collection<String> pendingDeletes,
            boolean deleteHistoryFile) {
        if (!pendingDeletes.isEmpty()) {
            SharedPreferences.Editor editor = metadata.edit();

            for (String name : pendingDeletes) {
                if (!isSafeSessionFilename(name)) {
                    continue;
                }
                try {
                    resolveSessionFile(filesDir, name).delete();
                    editor.remove(name + ".original")
                            .remove(name + ".imported_at");
                } catch (java.io.IOException ignored) {
                    // Ignore invalid or non-canonical paths.
                }
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
