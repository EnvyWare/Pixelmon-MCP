package com.envyware.pixelmonmcp.tools;

import com.envyware.pixelmonmcp.Tools;
import com.envyware.pixelmonmcp.client.ClientSnapshots;
import com.envyware.pixelmonmcp.server.ServerActions;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

/**
 * Battle tools: starting and ending battles and reading both sides' state.
 */
public final class BattleTools {
    private BattleTools() {
    }

    public static void register(ToolRegistry registry, MinecraftClientBridge bridge) {
        registry.register(Tools.tool("pixelmon.battle.state",
                "The client battle state: mode, turn, weather, pending tasks and the Pokémon on display with the HP "
                        + "the battle UI shows. Compare with pixelmon.battle.server_state to catch desyncs",
                args -> Tools.onClient(bridge, ClientSnapshots::battle)));
        registry.register(Tools.tool("pixelmon.battle.start",
                "Start a battle in front of the player. Args: npc (NPC preset id, e.g. "
                        + "\"pixelmon:battle/leader/gym/dragon_1\"), wild (Pokémon spec) or horde (list of Pokémon "
                        + "specs); rules (battle rule set id, default AG); ai (NPC battle AI id, default "
                        + "pixelmon:advanced); canMega, canDynamax, canTerastallize (NPC only, default true); seed "
                        + "(wild only); distance (blocks, default 3). Returns once the battle is requested; wait for "
                        + "pixelmon.battle.state",
                args -> ServerActions.onServer(player -> ServerActions.startBattle(player, args))));
        registry.register(Tools.tool("pixelmon.battle.end",
                "End the player's battle and remove the opponents spawned by pixelmon.battle.start",
                args -> ServerActions.onServer(ServerActions::endBattle)));
        registry.register(Tools.tool("pixelmon.battle.server_state",
                "The server's view of the player's battle: turn and every participant's active Pokémon with real HP",
                args -> ServerActions.onServer(ServerActions::battleState)));

    }
}
