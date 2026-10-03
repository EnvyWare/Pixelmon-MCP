package com.envyware.pixelmonmcp.tools;

import com.envyware.pixelmonmcp.ToolArgs;
import com.envyware.pixelmonmcp.Tools;
import com.envyware.pixelmonmcp.client.BattleControls;
import com.envyware.pixelmonmcp.client.ClientSnapshots;
import com.envyware.pixelmonmcp.server.ServerActions;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

/**
 * Battle tools: starting and ending battles, reading both sides' state, and every choice the battle screen offers.
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

        registry.register(Tools.tool("pixelmon.battle.options",
                "What the player can do right now: whether a choice is expected (canAct), the choosing Pokémon's moves "
                        + "with PP, available gimmicks, team (index, HP, fainted, active), enemies (index), bag items "
                        + "and pending prompts. Args: refreshBag (true requests the bag from the server)",
                args -> Tools.onClient(bridge, () -> BattleControls.options(
                        ToolArgs.bool(args.get("refreshBag"), false)))));
        registry.register(Tools.tool("pixelmon.battle.move",
                "Use a move. Args: move (slot 0-3, move name, or struggle); target (enemy index, for moves that need "
                        + "one in doubles and triples) or allyTarget (our-side index); gimmick (mega, ultra_burst, "
                        + "dynamax, terastallize or zmove). In multi battles the choice is queued until every Pokémon "
                        + "has chosen; the result reports sent and the next Pokémon",
                args -> Tools.onClient(bridge, () -> BattleControls.move(args))));
        registry.register(Tools.tool("pixelmon.battle.switch",
                "Switch in a team member. Also answers forced switches after a faint and Revival Blessing (pass the "
                        + "fainted Pokémon). Args: pokemon (team index from pixelmon.battle.options, UUID or species)",
                args -> Tools.onClient(bridge, () -> BattleControls.switchPokemon(args))));
        registry.register(Tools.tool("pixelmon.battle.item",
                "Use a bag item. Args: item (item id, e.g. pixelmon:potion); pokemon (target team index, UUID or "
                        + "species, default the choosing Pokémon); move (move slot, for Ethers and Leppa Berries)",
                args -> Tools.onClient(bridge, () -> BattleControls.useItem(args))));
        registry.register(Tools.tool("pixelmon.battle.catch",
                "Throw a Poké Ball at the last standing wild opponent. Args: ball (item id, default "
                        + "pixelmon:poke_ball)",
                args -> Tools.onClient(bridge, () -> BattleControls.catchPokemon(args))));
        registry.register(Tools.tool("pixelmon.battle.run",
                "Run from a wild battle, or forfeit a trainer battle",
                args -> Tools.onClient(bridge, BattleControls::run)));
        registry.register(Tools.tool("pixelmon.battle.learn_move",
                "Answer the next \"learn a new move\" prompt. Args: replace (move slot 0-3 to forget, or -1 to not "
                        + "learn it). pixelmon.battle.options lists pending prompts",
                args -> Tools.onClient(bridge, () -> BattleControls.learnMove(args))));
        registry.register(Tools.tool("pixelmon.battle.dismiss_level_ups",
                "Dismiss pending level-up screens, which otherwise hold up the next turn",
                args -> Tools.onClient(bridge, BattleControls::dismissLevelUps)));
    }
}
