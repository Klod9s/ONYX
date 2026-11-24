package com.onyx.perfmonitor;

public class SessionItem {
    public final String fileName;
    public final long lastModified;
    public final long sessionTimeMs;
    public final String deviceLabel;
    public final boolean imported;
    public final String originalName;

    public SessionItem(String fileName, long lastModified, long sessionTimeMs,
                       String deviceLabel, boolean imported, String originalName) {
        this.fileName = fileName;
        this.lastModified = lastModified;
        this.sessionTimeMs = sessionTimeMs;
        this.deviceLabel = deviceLabel;
        this.imported = imported;
        this.originalName = originalName;
    }
}