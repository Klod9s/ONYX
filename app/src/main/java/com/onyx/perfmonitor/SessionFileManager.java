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
