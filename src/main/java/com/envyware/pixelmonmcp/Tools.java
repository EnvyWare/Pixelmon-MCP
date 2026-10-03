package com.envyware.pixelmonmcp;

import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.McpTool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Tool construction shared by every tool group.
 */
public final class Tools {
    public static final long TIMEOUT_SECONDS = 10;

    private Tools() {
    }

    /**
     * Creates a tool whose failures from Pixelmon API drift are reported as an {@code unsupported} result.
     */
    public static McpTool tool(String name, String description, McpTool.Handler handler) {
        return McpTool.of(name, description, args -> {
            try {
                return handler.call(args);
            } catch (LinkageError e) {
                return unsupported(name, e);
            } catch (Exception e) {
                LinkageError cause = linkageCause(e);
                if (cause == null) {
                    throw e;
                }
                return unsupported(name, cause);
            }
        });
    }

    public static Map<String, Object> onClient(MinecraftClientBridge bridge, Supplier<Map<String, Object>> action)
            throws Exception {
        return bridge.submit(action).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public static Map<String, Object> status(String status, Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return result;
    }

    private static LinkageError linkageCause(Throwable error) {
        for (Throwable current = error.getCause(); current != null; current = current.getCause()) {
            if (current instanceof LinkageError linkage) {
                return linkage;
            }
        }
        return null;
    }

    private static Map<String, Object> unsupported(String tool, LinkageError error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "unsupported");
        result.put("tool", tool);
        result.put("reason", error.getClass().getSimpleName() + ": " + error.getMessage());
        result.put("pixelmonVersion", Versions.pixelmon());
        return result;
    }
}
