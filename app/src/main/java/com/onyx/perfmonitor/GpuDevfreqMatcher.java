
package com.onyx.perfmonitor;

import java.util.Locale;

final class GpuDevfreqMatcher {

    private static final String[] TOKENS = {
            "gpu",
            "mali",
            "kgsl",
            "pvr",
            "powervr",
            "rgx",
            "xclipse",
            "g3d",
            "sgpu",
            "panfrost"
    };

    private GpuDevfreqMatcher() {
        // Utility class.
    }

    static boolean matches(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        String normalized = name.toLowerCase(Locale.ROOT);

        for (String token : TOKENS) {
            if (normalized.contains(token)) {
                return true;
            }
        }

        return false;
    }

    static String shellCasePatterns() {
        StringBuilder result = new StringBuilder();

        for (String token : TOKENS) {
            if (result.length() > 0) {
                result.append("|");
            }
            result.append("*").append(token).append("*");
        }

        return result.toString();
    }
}
