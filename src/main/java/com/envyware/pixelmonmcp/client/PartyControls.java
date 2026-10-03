package com.envyware.pixelmonmcp.client;

import com.envyware.pixelmonmcp.Tools;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.util.helpers.NetworkHelper;
import com.pixelmonmod.pixelmon.client.ClientProxy;
import com.pixelmonmod.pixelmon.client.listener.SendoutListener;
import com.pixelmonmod.pixelmon.client.storage.ClientStorageManager;
import com.pixelmonmod.pixelmon.comm.packetHandlers.KeyPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Party actions done the way the player's keybinds do them: select a slot, send it out, recall it.
 */
public final class PartyControls {
    private static final int PARTY_SIZE = 6;

    private PartyControls() {
    }

    public static boolean isOut(Pokemon pokemon) {
        Level level = Minecraft.getInstance().level;
        return level != null && pokemon != null && SendoutListener.isInWorld(pokemon.getUUID(), level);
    }

    public static Map<String, Object> select(int slot) {
        PlayerPartyStorage party = ClientStorageManager.party();
        Map<String, Object> rejected = validate(party, slot);
        if (rejected != null) {
            return rejected;
        }
        party.setSelectedSlot(slot, false);
        party.sendClientUpdatePacket();
        return Tools.status("selected", "selectedSlot", party.getSelectedSlot());
    }

    public static Map<String, Object> sendOut(Integer requestedSlot) {
        PlayerPartyStorage party = ClientStorageManager.party();
        if (Minecraft.getInstance().level == null || party == null) {
            return Tools.status("not_in_world");
        }
        if (ClientProxy.battleManager.isBattling()) {
            return Tools.status("in_battle");
        }
        int slot = requestedSlot == null ? party.getSelectedSlot() : requestedSlot;
        Map<String, Object> rejected = validate(party, slot);
        if (rejected != null) {
            return rejected;
        }
        Pokemon pokemon = party.get(slot);
        if (pokemon.isFainted()) {
            return Tools.status("rejected", "reason", "fainted", "slot", slot);
        }
        if (isOut(pokemon)) {
            return Tools.status("already_out", "slot", slot);
        }

        party.setSelectedSlot(slot, false);
        party.sendClientUpdatePacket();
        NetworkHelper.sendToServer(new KeyPacket(slot));
        return Tools.status("sending_out", "slot", slot, "species", pokemon.getSpecies().getName());
    }

    public static Map<String, Object> retrieve(Integer requestedSlot) {
        PlayerPartyStorage party = ClientStorageManager.party();
        if (Minecraft.getInstance().level == null || party == null) {
            return Tools.status("not_in_world");
        }
        if (ClientProxy.battleManager.isBattling()) {
            return Tools.status("in_battle");
        }

        List<Integer> recalled = new ArrayList<>();
        for (int slot = 0; slot < PARTY_SIZE; slot++) {
            if (requestedSlot != null && requestedSlot != slot) {
                continue;
            }
            if (isOut(party.get(slot))) {
                NetworkHelper.sendToServer(new KeyPacket(slot));
                recalled.add(slot);
            }
        }
        return Tools.status(recalled.isEmpty() ? "none_out" : "retrieving", "slots", recalled);
    }

    private static Map<String, Object> validate(PlayerPartyStorage party, int slot) {
        if (party == null) {
            return Tools.status("not_in_world");
        }
        if (slot < 0 || slot >= PARTY_SIZE) {
            return Tools.status("rejected", "reason", "slot must be 0 to 5", "slot", slot);
        }
        Pokemon pokemon = party.get(slot);
        if (pokemon == null) {
            return Tools.status("rejected", "reason", "empty slot", "slot", slot);
        }
        if (pokemon.isEgg()) {
            return Tools.status("rejected", "reason", "egg", "slot", slot);
        }
        return null;
    }
}
