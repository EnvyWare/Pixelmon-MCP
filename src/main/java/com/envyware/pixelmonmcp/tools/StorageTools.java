package com.envyware.pixelmonmcp.tools;

import com.envyware.pixelmonmcp.ToolArgs;
import com.envyware.pixelmonmcp.Tools;
import com.envyware.pixelmonmcp.server.PcActions;
import com.envyware.pixelmonmcp.server.PokedexActions;
import com.envyware.pixelmonmcp.server.ResearchActions;
import com.envyware.pixelmonmcp.server.ServerActions;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

/**
 * PC, Pokédex and research tools. All of them act on the integrated server's player data.
 */
public final class StorageTools {
    private StorageTools() {
    }

    public static void register(ToolRegistry registry) {
        registry.register(Tools.tool("pixelmon.pc.state",
                "One PC box: its name, wallpaper and Pokémon, plus the name and count of every box. Args: box "
                        + "(default 0)",
                args -> ServerActions.onServer(player -> PcActions.state(player, args))));
        registry.register(Tools.tool("pixelmon.pc.set",
                "Write to a PC box. Args: box; pokemon (list of up to 30 Pokémon specs, null or \"\" for an empty "
                        + "slot) replaces the whole box, or slot plus pokemon (one spec, or null to clear) sets one "
                        + "slot; name (optional) renames the box",
                args -> ServerActions.onServer(player -> PcActions.set(player, args))));

        registry.register(Tools.tool("pixelmon.pokedex.state",
                "Pokédex counts and entries. Args: pokemon (list of Pokémon specs to look up) or a page of the dex "
                        + "with offset (default 0) and limit (default 30, max 200); dex (default national_dex)",
                args -> ServerActions.onServer(player -> PokedexActions.state(player, args))));
        registry.register(Tools.tool("pixelmon.pokedex.set",
                "Register Pokédex entries. Args: pokemon (list of Pokémon specs) or all: true; status (seen or "
                        + "caught, default caught); clear: true wipes the whole Pokédex first, the only way to "
                        + "remove entries",
                args -> ServerActions.onServer(player -> PokedexActions.set(player, args))));

        registry.register(Tools.tool("pixelmon.research.state",
                "Active research with stage and objective progress, and completed research ids. Args: available "
                        + "(true also lists every research id)",
                args -> ServerActions.onServer(player -> ResearchActions.state(player,
                        ToolArgs.bool(args.get("available"), false)))));
        registry.register(Tools.tool("pixelmon.research.set",
                "Change research. Args: action (start, discard, complete, complete_stage, claim or clear); id "
                        + "(research id, e.g. pixelmon:pick_starter) or uuid (an active research). claim gives the "
                        + "current stage's rewards",
                args -> ServerActions.onServer(player -> ResearchActions.set(player, args))));
    }
}
