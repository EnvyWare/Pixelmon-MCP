package com.envyware.pixelmonmcp.tools;

import com.envyware.pixelmonmcp.ToolArgs;
import com.envyware.pixelmonmcp.Tools;
import com.envyware.pixelmonmcp.client.ClientSnapshots;
import com.envyware.pixelmonmcp.server.FilmActions;
import com.envyware.pixelmonmcp.server.ServerActions;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Film tools: putting a world into a quiet state for filming and back again.
 */
public final class FilmTools {
    private FilmTools() {
    }

    public static void register(ToolRegistry registry, MinecraftClientBridge bridge) {
        registry.register(Tools.tool("pixelmon.film.prepare",
                "Prepare the world for filming, at runtime and without editing configs: stop wild, legendary, "
                        + "wormhole and NPC spawning and legendary broadcasts. Args: stopSpawning (default true); "
                        + "clear (list of pokemon, npcs, wormholes or all: removes wild Pokémon, NPCs, wormholes; "
                        + "default none); radius (blocks around the player for clear, default the whole level); "
                        + "party (list of Pokémon specs, also closes the starter picker); heal (default true). "
                        + "pixelmon.film.restore puts back what this changed. Hide the HUD with mc.hud.set",
                args -> {
                    Map<String, Object> result = ServerActions.onServer(player -> FilmActions.prepare(player, args));
                    if (!ToolArgs.strings(args.get("party")).isEmpty()) {
                        bridge.submit(ClientSnapshots::closeStarterScreen).get(Tools.TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    }
                    return result;
                }));
        registry.register(Tools.tool("pixelmon.film.restore",
                "Undo pixelmon.film.prepare: spawning, legendary settings and the party go back to what they were. "
                        + "Removed entities stay removed",
                args -> ServerActions.onServer(FilmActions::restore)));
    }
}
