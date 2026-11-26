package com.onyx.perfmonitor;

interface IOnyxUserService {
    void destroy();
    String readMetrics(String cachedGpuFreqPath, String cachedGpuLoadPath,
                       String cachedMaliPath, boolean searchMali);
    String getResumedActivityDump();
    String getWindowDump();
    String getSurfaceFlingerDump();
    String getSurfaceFlingerLayers();
    String getSurfaceFlingerLatency(String layerName);
    String getSurfaceFlingerTimeStatsDump();
}
