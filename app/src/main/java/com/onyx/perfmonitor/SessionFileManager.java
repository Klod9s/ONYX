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
