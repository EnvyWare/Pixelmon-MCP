package com.envyware.pixelmonmcp;

import net.minecraft.resources.ResourceLocation;

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

    /**
     * Reads a list that may contain nulls or blanks, which mark empty slots.
     */
    public static List<String> slots(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object element : list) {
                String text = element == null ? "" : String.valueOf(element).trim();
                result.add(text.isEmpty() ? null : text);
            }
        }
        return result;
    }

    public static boolean bool(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    public static Boolean optionalBool(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : Boolean.parseBoolean(String.valueOf(value));
    }

    public static String string(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public static Integer integer(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int integer(Map<String, Object> args, String key, int fallback) {
        Integer value = integer(args, key);
        return value == null ? fallback : value;
    }

    /**
     * Parses a resource id, defaulting to the {@code pixelmon} namespace.
     */
    public static ResourceLocation id(String text) {
        return text.indexOf(':') < 0 ? ResourceLocation.fromNamespaceAndPath("pixelmon", text)
                : ResourceLocation.parse(text);
    }
}
