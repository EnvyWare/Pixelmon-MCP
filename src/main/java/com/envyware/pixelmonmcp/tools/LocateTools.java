package com.envyware.pixelmonmcp.tools;

import com.envyware.pixelmonmcp.Tools;
import com.envyware.pixelmonmcp.server.LocateActions;
import com.envyware.pixelmonmcp.server.ServerActions;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

/**
 * Locate tools: compact positions of Pokémon, NPCs and structures, for pointing a camera at them.
 */
public final class LocateTools {
    private static final long STRUCTURE_TIMEOUT_SECONDS = 60;

    private LocateTools() {
    }

    public static void register(ToolRegistry registry) {
        registry.register(Tools.tool("pixelmon.locate.pokemon",
                "Loaded Pokémon near a point, nearest first: id, uuid, pos, distance, species, level, and form, "
                        + "shiny, owner, inBattle and staged when they apply. Args: pos ([x, y, z], default the "
                        + "player); origin (\"spawn\", \"player\" or [x, y, z]: pos and the results are relative to "
                        + "it); radius (default 64); limit (default 10); species (one or a list); wild (true only "
                        + "wild, false only owned)",
                args -> ServerActions.onServer(player -> LocateActions.pokemon(player, args))));
        registry.register(Tools.tool("pixelmon.locate.npcs",
                "Loaded Pixelmon NPCs near a point, nearest first: id, uuid, pos, distance, name, preset and staged. "
                        + "Args: pos, origin, radius, limit as for pixelmon.locate.pokemon",
                args -> ServerActions.onServer(player -> LocateActions.npcs(player, args))));
        registry.register(Tools.tool("pixelmon.locate.structure",
                "The nearest structures matching ids, nearest first, one per structure id: structure, x, z, "
                        + "distance. Args: structure (one or a list of ids, id prefixes such as gyms or den, or "
                        + "#tags; the pixelmon namespace is the default; omit it to list Pixelmon structures); list "
                        + "(true lists the matching ids without searching); pos, origin as for "
                        + "pixelmon.locate.pokemon; radius (blocks, default 1600); limit (default 5)",
                args -> ServerActions.onServer(player -> LocateActions.structures(player, args),
                        STRUCTURE_TIMEOUT_SECONDS)));
    }
}
