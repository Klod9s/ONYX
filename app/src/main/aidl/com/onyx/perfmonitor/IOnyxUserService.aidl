package com.onyx.perfmonitor;

interface IOnyxUserService {
    void destroy();
    String runCommand(String command);
}