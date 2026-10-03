package com.envyware.pixelmonmcp;

import com.envyware.pixelmonmcp.client.ClientSnapshots;
import com.envyware.pixelmonmcp.server.ServerActions;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.McpTool;
import io.izzel.minecraftmcp.mcp.ToolRegistry;
import io.izzel.minecraftmcp.tools.ToolProvider;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Pixelmon tools for agents driving a client through minecraft-mcp. Client tools read what the player sees; setup tools
 * act on the integrated server so agents can reach a test state without scripting menus and NPC dialogue.
 */
public class PixelmonMcpTools implements ToolProvider {
    public static final long TIMEOUT_SECONDS = 10;

    @Override
    public void registerClient(ToolRegistry registry, MinecraftClientBridge bridge) {
        registry.register(McpTool.of("pixelmon.party.state",
                "The player's party as the client sees it: species, form, level, HP, moves with PP, held item",
                args -> bridge.submit(ClientSnapshots::party).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)));
        registry.register(McpTool.of("pixelmon.battle.state",
                "The client battle state: mode, turn, weather, pending tasks and the Pokémon on display with the HP the "
                        + "battle UI shows. Compare with pixelmon.battle.server_state to catch desyncs",
                args -> bridge.submit(ClientSnapshots::battle).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)));

        registry.register(McpTool.of("pixelmon.party.set",
                "Replace the player's party. Args: pokemon (list of up to 6 Pokémon specs, e.g. \"pikachu lvl:50\"), "
                        + "heal (default true). Refused while in battle",
                args -> {
                    Map<String, Object> result = ServerActions.onServer(player -> ServerActions.setParty(player,
                            ToolArgs.strings(args.get("pokemon")), ToolArgs.bool(args.get("heal"), true)));
                    bridge.submit(ClientSnapshots::closeStarterScreen).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return result;
                }));
        registry.register(McpTool.of("pixelmon.party.heal",
                "Heal the player's party. Call after pixelmon.battle.state reports the battle is over, not straight "
                        + "after pixelmon.battle.end",
                args -> ServerActions.onServer(ServerActions::healParty)));
        registry.register(McpTool.of("pixelmon.battle.start",
                "Start a battle in front of the player. Args: npc (NPC preset id, e.g. "
                        + "\"pixelmon:battle/leader/gym/dragon_1\"), wild (Pokémon spec) or horde (list of Pokémon "
                        + "specs); rules (battle rule set id, default AG); ai (NPC battle AI id, default "
                        + "pixelmon:advanced); canMega, canDynamax, canTerastallize (NPC only, default true); seed "
                        + "(wild only); distance (blocks, default 3). Returns once the battle is requested; wait for "
                        + "pixelmon.battle.state",
                args -> ServerActions.onServer(player -> ServerActions.startBattle(player, args))));
        registry.register(McpTool.of("pixelmon.battle.end",
                "End the player's battle and remove the opponents spawned by pixelmon.battle.start",
                args -> ServerActions.onServer(ServerActions::endBattle)));
        registry.register(McpTool.of("pixelmon.battle.server_state",
                "The server's view of the player's battle: turn and every participant's active Pokémon with real HP",
                args -> ServerActions.onServer(ServerActions::battleState)));
    }
}
