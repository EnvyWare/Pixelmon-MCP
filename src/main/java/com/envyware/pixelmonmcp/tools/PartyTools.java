package com.envyware.pixelmonmcp.tools;

import com.envyware.pixelmonmcp.ToolArgs;
import com.envyware.pixelmonmcp.Tools;
import com.envyware.pixelmonmcp.client.ClientSnapshots;
import com.envyware.pixelmonmcp.client.PartyControls;
import com.envyware.pixelmonmcp.server.GimmickActions;
import com.envyware.pixelmonmcp.server.ServerActions;
import io.izzel.minecraftmcp.bridge.MinecraftClientBridge;
import io.izzel.minecraftmcp.mcp.ToolRegistry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Party tools: reading and replacing the party, the selected slot, sending out and recalling, gimmicks and charms.
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

        registry.register(Tools.tool("pixelmon.party.select",
                "Select a party slot, like the up and down keys. Args: slot (0-5)",
                args -> {
                    Integer slot = ToolArgs.integer(args, "slot");
                    if (slot == null) {
                        return Tools.status("rejected", "reason", "slot is required");
                    }
                    return Tools.onClient(bridge, () -> PartyControls.select(slot));
                }));
        registry.register(Tools.tool("pixelmon.party.send_out",
                "Throw out a party Pokémon, like the send-out key. Args: slot (0-5, default the selected slot). "
                        + "It lands a moment later; pixelmon.party.state shows out: true",
                args -> Tools.onClient(bridge, () -> PartyControls.sendOut(ToolArgs.integer(args, "slot")))));
        registry.register(Tools.tool("pixelmon.party.retrieve",
                "Recall sent-out party Pokémon. Args: slot (0-5, default every Pokémon that is out)",
                args -> Tools.onClient(bridge, () -> PartyControls.retrieve(ToolArgs.integer(args, "slot")))));

        registry.register(Tools.tool("pixelmon.party.gimmicks.state",
                "Battle gimmick unlocks (mega, dynamax, terastallize), the equipped key item, the items that can be "
                        + "selected, Tera energy and charm states",
                args -> ServerActions.onServer(GimmickActions::state)));
        registry.register(Tools.tool("pixelmon.party.gimmicks.unlock",
                "Unlock or lock battle gimmicks. Args (each optional): mega, dynamax, terastallize (true unlocks, "
                        + "false locks); teraEnergy, infiniteTeraEnergy (booleans). The first unlock equips a "
                        + "matching key item",
                args -> ServerActions.onServer(player -> GimmickActions.unlock(player, args))));
        registry.register(Tools.tool("pixelmon.party.gimmicks.select",
                "Equip a key item. Args: item (BraceletORAS, BoostNecklace, MegaGlasses, MegaAnchor, MegaTiara, "
                        + "DynamaxBand, TeraOrb or None). Its gimmick must be unlocked",
                args -> ServerActions.onServer(player -> GimmickActions.select(player,
                        ToolArgs.string(args, "item")))));
        registry.register(Tools.tool("pixelmon.party.charms.set",
                "Set charm states. Args: charms (map of charm id to ACTIVE, AVAILABLE or DISABLED, e.g. "
                        + "{\"shiny\": \"ACTIVE\"})",
                args -> ServerActions.onServer(player -> GimmickActions.setCharms(player, args))));
    }
}
