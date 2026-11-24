package com.onyx.perfmonitor;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class SessionIndex {
    private static final String INDEX_FILE = "session_index.json";

    public static class Entry {
        public String fileName;
        public long timestamp;
        public String device;
        public String summaryPreview;
        public boolean isImported;
        public String originalName;

        public Entry(String fileName, long timestamp, String device, String summary, boolean isImported, String originalName) {
            this.fileName = fileName;
            this.timestamp = timestamp;
            this.device = device;
            this.summaryPreview = summary.length() > 50 ? summary.substring(0, 50) + "..." : summary;
            this.isImported = isImported;
            this.originalName = originalName;
        }
    }

    public static List<Entry> loadIndex(File filesDir) {
        File indexFile = new File(filesDir, INDEX_FILE);
        if (!indexFile.exists()) return new ArrayList<>();

        try (FileReader reader = new FileReader(indexFile)) {
            Type listType = new TypeToken<ArrayList<Entry>>(){}.getType();
            return new Gson().fromJson(reader, listType);
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public static void saveIndex(File filesDir, List<Entry> entries) {
        File indexFile = new File(filesDir, INDEX_FILE);
        try (FileWriter writer = new FileWriter(indexFile)) {
            new Gson().toJson(entries, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addEntry(File filesDir, Entry entry) {
        List<Entry> entries = loadIndex(filesDir);
        entries.add(entry);
        saveIndex(filesDir, entries);
    }

    public static void removeEntry(File filesDir, String fileName) {
        List<Entry> entries = loadIndex(filesDir);
        entries.removeIf(e -> e.fileName.equals(fileName));
        saveIndex(filesDir, entries);
    }
}