package com.envyware.pixelmonmcp;

import com.envyware.pixelmonmcp.tools.BattleTools;
import com.envyware.pixelmonmcp.tools.FilmTools;
import com.envyware.pixelmonmcp.tools.PartyTools;
import com.envyware.pixelmonmcp.tools.StorageTools;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.tools.ToolProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pixelmon tools for agents driving a client through minecraft-mcp. Client tools read what the player sees and act
 * through the same packets as the GUI; setup tools act on the integrated server so agents can reach a test state
 * without scripting menus and NPC dialogue.
 */
public class PixelmonMcpTools implements ToolProvider {
    @Override
    public void registerClient(ToolRegistry registry, MinecraftClientBridge bridge) {
        registry.register(Tools.tool("pixelmon.mcp.info",
                "Versions of Pixelmon MCP, the loaded Pixelmon and minecraft-mcp, and the Pixelmon version Pixelmon "
                        + "MCP was compiled against",
                args -> info()));
        PartyTools.register(registry, bridge);
        StorageTools.register(registry);
        BattleTools.register(registry, bridge);
        FilmTools.register(registry, bridge);
    }

    private static Map<String, Object> info() {
        Map<String, Object> compiledAgainst = new LinkedHashMap<>();
        compiledAgainst.put("pixelmon", Versions.compiledPixelmon());
        compiledAgainst.put("minecraftMcp", Versions.compiledMinecraftMcp());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pixelmonMcpVersion", Versions.mod());
        result.put("pixelmonVersion", Versions.pixelmon());
        result.put("minecraftMcpVersion", Versions.minecraftMcp());
        result.put("compiledAgainst", compiledAgainst);
        return result;
    }
}
