package com.envyware.pixelmonmcp.server;

import com.envyware.pixelmonmcp.ToolArgs;
import com.envyware.pixelmonmcp.Tools;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.api.storage.charms.Charm;
import com.pixelmonmod.pixelmon.api.storage.charms.CharmState;
import com.pixelmonmod.pixelmon.enums.EnumMegaItem;
import com.pixelmonmod.pixelmon.enums.EnumMegaItemsUnlocked;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Battle gimmick unlocks, the equipped key item and charms, all stored on the server party.
 */
public final class GimmickActions {
    private GimmickActions() {
    }

    public static Map<String, Object> state(ServerPlayer player) {
        PlayerPartyStorage party = StorageProxy.getPartyNow(player);
        EnumMegaItemsUnlocked unlocked = party.getMegaItemsUnlocked();
        Map<String, Object> gimmicks = new LinkedHashMap<>();
        gimmicks.put("mega", unlocked.canMega());
        gimmicks.put("dynamax", unlocked.canDynamax());
        gimmicks.put("terastallize", unlocked.canTerastallize());

        List<String> selectable = new ArrayList<>();
        for (EnumMegaItem item : EnumMegaItem.values()) {
            if (canSelect(party, item)) {
                selectable.add(item.name());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unlocked", gimmicks);
        result.put("item", party.getMegaItem().name());
        result.put("selectableItems", selectable);
        result.put("teraEnergy", party.hasTerastalEnergy());
        result.put("infiniteTeraEnergy", party.hasInfiniteTerastalEnergy());
        result.put("charms", charms(player, party));
        return result;
    }

    public static Map<String, Object> unlock(ServerPlayer player, Map<String, Object> args) {
        PlayerPartyStorage party = StorageProxy.getPartyNow(player);
        Boolean mega = ToolArgs.optionalBool(args, "mega");
        if (mega != null && mega != party.getMegaItemsUnlocked().canMega()) {
            if (mega) {
                party.unlockMega(false);
            } else {
                party.lockMega();
            }
        }
        Boolean dynamax = ToolArgs.optionalBool(args, "dynamax");
        if (dynamax != null && dynamax != party.getMegaItemsUnlocked().canDynamax()) {
            if (dynamax) {
                party.unlockDynamax(false);
            } else {
                party.lockDynamax();
            }
        }
        Boolean terastallize = ToolArgs.optionalBool(args, "terastallize");
        if (terastallize != null && terastallize != party.getMegaItemsUnlocked().canTerastallize()) {
            if (terastallize) {
                party.unlockTerastal(false);
            } else {
                party.lockTerastal();
            }
        }
        Boolean teraEnergy = ToolArgs.optionalBool(args, "teraEnergy");
        if (teraEnergy != null) {
            party.setTerastalEnergy(teraEnergy);
        }
        Boolean infiniteTeraEnergy = ToolArgs.optionalBool(args, "infiniteTeraEnergy");
        if (infiniteTeraEnergy != null) {
            party.setInfiniteTerastalEnergy(infiniteTeraEnergy);
        }

        Map<String, Object> result = state(player);
        result.put("status", "updated");
        return result;
    }

    public static Map<String, Object> select(ServerPlayer player, String name) {
        PlayerPartyStorage party = StorageProxy.getPartyNow(player);
        EnumMegaItem item = find(name);
        if (item == null) {
            return Tools.status("rejected", "reason", "unknown item", "item", name, "items", itemNames());
        }
        if (!canSelect(party, item)) {
            return Tools.status("rejected", "reason", "unlock the matching gimmick first", "item", item.name(),
                    "selectableItems", state(player).get("selectableItems"));
        }

        party.setMegaItem(item, false);
        Map<String, Object> result = state(player);
        result.put("status", "selected");
        return result;
    }

    public static Map<String, Object> setCharms(ServerPlayer player, Map<String, Object> args) {
        PlayerPartyStorage party = StorageProxy.getPartyNow(player);
        Registry<Charm> registry = player.server.registryAccess().registryOrThrow(Charm.REGISTRY_KEY);
        Object value = args.get("charms");
        if (!(value instanceof Map<?, ?> requested) || requested.isEmpty()) {
            return Tools.status("rejected", "reason", "charms must map charm ids to ACTIVE, AVAILABLE or DISABLED",
                    "charms", charms(player, party));
        }

        for (Map.Entry<?, ?> entry : requested.entrySet()) {
            ResourceLocation id = ToolArgs.id(String.valueOf(entry.getKey()));
            ResourceKey<Charm> key = ResourceKey.create(Charm.REGISTRY_KEY, id);
            if (!registry.containsKey(key)) {
                return Tools.status("rejected", "reason", "unknown charm", "charm", id.toString(),
                        "charms", charms(player, party));
            }
            CharmState state = charmState(String.valueOf(entry.getValue()));
            if (state == null) {
                return Tools.status("rejected", "reason", "state must be ACTIVE, AVAILABLE or DISABLED",
                        "charm", id.toString(), "state", String.valueOf(entry.getValue()));
            }
            party.setCharmState(key, state);
        }

        return Tools.status("updated", "charms", charms(player, party));
    }

    private static Map<String, Object> charms(ServerPlayer player, PlayerPartyStorage party) {
        Registry<Charm> registry = player.server.registryAccess().registryOrThrow(Charm.REGISTRY_KEY);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<Charm>, Charm> entry : registry.entrySet()) {
            result.put(entry.getKey().location().toString(), party.getCharmState(entry.getKey()).name());
        }
        return result;
    }

    private static boolean canSelect(PlayerPartyStorage party, EnumMegaItem item) {
        if (item == EnumMegaItem.Disabled || !party.canEquipMegaItem()) {
            return false;
        }
        if (item == EnumMegaItem.None) {
            return true;
        }
        EnumMegaItemsUnlocked unlocked = party.getMegaItemsUnlocked();
        return switch (item.getType()) {
            case 0 -> unlocked.canMega();
            case 1 -> unlocked.canDynamax();
            case 2 -> unlocked.canTerastallize();
            default -> false;
        };
    }

    private static EnumMegaItem find(String name) {
        if (name == null) {
            return null;
        }
        for (EnumMegaItem item : EnumMegaItem.values()) {
            if (item.name().equalsIgnoreCase(name)) {
                return item;
            }
        }
        return null;
    }

    private static List<String> itemNames() {
        List<String> names = new ArrayList<>();
        for (EnumMegaItem item : EnumMegaItem.values()) {
            if (item != EnumMegaItem.Disabled) {
                names.add(item.name());
            }
        }
        return names;
    }

    private static CharmState charmState(String name) {
        for (CharmState state : CharmState.values()) {
            if (state.name().equalsIgnoreCase(name)) {
                return state;
            }
        }
        return null;
    }
}
