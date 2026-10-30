package com.bookwell.common.util;

public final class QueryUtils {

    private QueryUtils() {
    }

    public static String escapeLike(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > 100) {
            trimmed = trimmed.substring(0, 100);
        }
        return trimmed.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
