package com.envyware.pixelmonmcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lenient readers for tool arguments.
 */
public final class ToolArgs {
    private ToolArgs() {
    }

    public static List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object element : list) {
                result.add(String.valueOf(element));
            }
        } else if (value != null) {
            result.add(String.valueOf(value));
        }
        return result;
    }

    public static boolean bool(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    public static String string(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
