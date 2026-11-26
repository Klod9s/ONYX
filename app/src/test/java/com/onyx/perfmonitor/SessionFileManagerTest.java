package com.onyx.perfmonitor;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionFileManagerTest {

    @Test
    public void acceptsImportSizesAtAndBelowLimit() {
        assertTrue(SessionFileManager.isImportSizeAllowed(0));
        assertTrue(SessionFileManager.isImportSizeAllowed(1));
        assertTrue(SessionFileManager.isImportSizeAllowed(
                SessionFileManager.MAX_IMPORT_BYTES));
        assertTrue(SessionFileManager.isImportSizeAllowed(
                SessionFileManager.MAX_IMPORT_BYTES - 1));
    }

    @Test
    public void rejectsOversizedImportAndAllowsUnknownProviderSize() {
        assertFalse(SessionFileManager.isImportSizeAllowed(
                SessionFileManager.MAX_IMPORT_BYTES + 1));
        assertTrue(SessionFileManager.isImportSizeAllowed(-1));
    }

    @Test
    public void acceptsOnyxSessionFilenames() {
        assertTrue(SessionFileManager.isSafeSessionFilename(
                "session_2026-11-27_01-02-03.csv"));
        assertTrue(SessionFileManager.isSafeSessionFilename(
                "session_2026-11-27_01-02-03-1.csv"));
        assertTrue(SessionFileManager.isSafeSessionFilename(
                "session_imported_2026-11-27_01-02-03.csv"));
        assertTrue(SessionFileManager.isSafeSessionFilename(
                "session_imported_2026-11-27_01-02-03-12.csv"));
    }

    @Test
    public void rejectsTraversalAndUnexpectedNames() {
        assertFalse(SessionFileManager.isSafeSessionFilename("../session_2026-11-27_01-02-03.csv"));
        assertFalse(SessionFileManager.isSafeSessionFilename("session_../../outside.csv"));
        assertFalse(SessionFileManager.isSafeSessionFilename("session_2026-11-27_01-02-03.csv/../outside"));
        assertFalse(SessionFileManager.isSafeSessionFilename("/session_2026-11-27_01-02-03.csv"));
        assertFalse(SessionFileManager.isSafeSessionFilename("session_2026-11-27_01-02-03.txt"));
        assertFalse(SessionFileManager.isSafeSessionFilename("anything.csv"));
        assertFalse(SessionFileManager.isSafeSessionFilename(""));
        assertFalse(SessionFileManager.isSafeSessionFilename(null));
    }
}
