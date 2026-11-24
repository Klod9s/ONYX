package com.onyx.perfmonitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SessionIndexTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadIndexReturnsEmptyListWhenIndexDoesNotExist() {
        List<SessionIndex.Entry> entries =
                SessionIndex.loadIndex(temporaryFolder.getRoot());

        assertTrue(entries.isEmpty());
    }

    @Test
    public void addEntryPersistsSessionMetadata() {
        File filesDir = temporaryFolder.getRoot();
        SessionIndex.Entry expected = new SessionIndex.Entry(
                "session_123.csv",
                123456789L,
                "Test device",
                "A sample session summary",
                false,
                null
        );

        SessionIndex.addEntry(filesDir, expected);

        List<SessionIndex.Entry> entries = SessionIndex.loadIndex(filesDir);
        assertEquals(1, entries.size());
        assertEquals("session_123.csv", entries.get(0).fileName);
        assertEquals(123456789L, entries.get(0).timestamp);
        assertEquals("Test device", entries.get(0).device);
        assertEquals("A sample session summary", entries.get(0).summaryPreview);
        assertFalse(entries.get(0).isImported);
    }

    @Test
    public void removeEntryRemovesOnlyMatchingSession() {
        File filesDir = temporaryFolder.getRoot();
        SessionIndex.addEntry(filesDir, new SessionIndex.Entry(
                "first.csv", 1L, "Device", "First", false, null));
        SessionIndex.addEntry(filesDir, new SessionIndex.Entry(
                "second.csv", 2L, "Device", "Second", false, null));

        SessionIndex.removeEntry(filesDir, "first.csv");

        List<SessionIndex.Entry> entries = SessionIndex.loadIndex(filesDir);
        assertEquals(1, entries.size());
        assertEquals("second.csv", entries.get(0).fileName);
    }

    @Test
    public void loadIndexReturnsEmptyListForMalformedJson() throws Exception {
        File indexFile = new File(temporaryFolder.getRoot(), "session_index.json");
        try (FileWriter writer = new FileWriter(indexFile)) {
            writer.write("{ this is not valid JSON");
        }

        List<SessionIndex.Entry> entries =
                SessionIndex.loadIndex(temporaryFolder.getRoot());

        assertTrue(entries.isEmpty());
    }

    @Test
    public void removeEntryHandlesNullEntriesInIndex() throws Exception {
        File indexFile = new File(temporaryFolder.getRoot(), "session_index.json");
        try (FileWriter writer = new FileWriter(indexFile)) {
            writer.write("[null,{\"fileName\":\"keep.csv\",\"timestamp\":2,"
                    + "\"device\":\"Device\",\"summaryPreview\":\"Keep\"}]");
        }

        SessionIndex.removeEntry(temporaryFolder.getRoot(), "remove.csv");

        List<SessionIndex.Entry> entries =
                SessionIndex.loadIndex(temporaryFolder.getRoot());
        assertEquals(1, entries.size());
        assertEquals("keep.csv", entries.get(0).fileName);
    }
}
