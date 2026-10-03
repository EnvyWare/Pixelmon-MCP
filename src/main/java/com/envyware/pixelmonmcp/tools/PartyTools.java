package com.envyware.pixelmonmcp.tools;

import com.envyware.pixelmonmcp.ToolArgs;
import com.envyware.pixelmonmcp.Tools;
import com.envyware.pixelmonmcp.client.ClientSnapshots;
import com.envyware.pixelmonmcp.server.ServerActions;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Party tools: reading, replacing and healing the party.
 */
public final class PartyTools {
    private PartyTools() {
    }

    public static void register(ToolRegistry registry, MinecraftClientBridge bridge) {
        registry.register(Tools.tool("pixelmon.party.state",
                "The player's party as the client sees it: species, form, level, HP, moves with PP, held item",
                args -> Tools.onClient(bridge, ClientSnapshots::party)));
        registry.register(Tools.tool("pixelmon.party.set",
                "Replace the player's party. Args: pokemon (list of up to 6 Pokémon specs, e.g. \"pikachu lvl:50\"), "
                        + "heal (default true). Refused while in battle",
                args -> {
                    Map<String, Object> result = ServerActions.onServer(player -> ServerActions.setParty(player,
                            ToolArgs.strings(args.get("pokemon")), ToolArgs.bool(args.get("heal"), true)));
                    bridge.submit(ClientSnapshots::closeStarterScreen).get(Tools.TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return result;
                }));
        registry.register(Tools.tool("pixelmon.party.heal",
                "Heal the player's party. Call after pixelmon.battle.state reports the battle is over, not straight "
                        + "after pixelmon.battle.end",
                args -> ServerActions.onServer(ServerActions::healParty)));

    }
}
