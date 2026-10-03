package com.envyware.pixelmonmcp.client;

import com.envyware.pixelmonmcp.PokemonSummaries;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.battles.status.StatusType;
import com.pixelmonmod.pixelmon.client.ClientProxy;
import com.pixelmonmod.pixelmon.client.gui.battles.ClientBattleManager;
import com.pixelmonmod.pixelmon.client.gui.battles.PixelmonClientData;
import com.pixelmonmod.pixelmon.client.gui.starter.ChooseStarterScreen;
import com.pixelmonmod.pixelmon.client.storage.ClientStorageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-thread reads of the state the player sees.
 */
public final class ClientSnapshots {
    private ClientSnapshots() {
    }

    public static Map<String, Object> party() {
        Map<String, Object> result = new LinkedHashMap<>();
        PlayerPartyStorage party = ClientStorageManager.party();
        result.put("inWorld", Minecraft.getInstance().player != null);
        List<Map<String, Object>> slots = new ArrayList<>();
        if (party != null) {
            for (int slot = 0; slot < 6; slot++) {
                Pokemon pokemon = party.get(slot);
                if (pokemon == null) {
                    continue;
                }

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("slot", slot);
                entry.putAll(PokemonSummaries.pokemon(pokemon));
                slots.add(entry);
            }
        }
        result.put("party", slots);
        return result;
    }

    public static Map<String, Object> battle() {
        ClientBattleManager manager = ClientProxy.battleManager;
        Map<String, Object> result = new LinkedHashMap<>();
        Screen screen = Minecraft.getInstance().screen;
        result.put("screen", screen == null ? null : screen.getClass().getSimpleName());
        result.put("battling", manager.isBattling());
        result.put("spectating", manager.isSpectating);
        result.put("battleEnded", manager.battleEnded);
        if (!manager.isBattling() && !manager.isSpectating) {
            return result;
        }

        result.put("mode", manager.getMode() == null ? null : manager.getMode().name());
        result.put("battleType", manager.battleType == null ? null : String.valueOf(manager.battleType));
        result.put("turn", manager.battleTurn);
        result.put("weather", PokemonSummaries.status(manager.weather));
        result.put("terrain", PokemonSummaries.status(manager.terrain));
        result.put("waitingText", manager.waitingText);
        result.put("choosingPokemon", manager.choosingPokemon);
        result.put("currentPokemon", manager.currentPokemon);
        result.put("hasMoreMessages", manager.hasMoreMessages());
        result.put("hasMoreTasks", manager.hasMoreTasks());
        result.put("hasActiveAnimations", manager.hasActiveBattleAnimations());
        result.put("waitingDiagnostics", manager.waitingDiagnostics());
        result.put("ours", displayed(manager.displayedOurPokemon));
        result.put("allies", displayed(manager.displayedAllyPokemon));
        result.put("enemies", displayed(manager.displayedEnemyPokemon));
        return result;
    }

    public static boolean closeStarterScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ChooseStarterScreen) {
            minecraft.setScreen(null);
            return true;
        }
        return false;
    }

    private static List<Map<String, Object>> displayed(PixelmonClientData[] data) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (data == null) {
            return result;
        }

        for (PixelmonClientData pokemon : data) {
            if (pokemon == null) {
                continue;
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", pokemon.pokemonUUID == null ? null : pokemon.pokemonUUID.toString());
            entry.put("species", pokemon.species == null ? null : pokemon.species.getName());
            entry.put("form", pokemon.form);
            entry.put("name", pokemon.nickname == null ? null : pokemon.nickname.getString());
            entry.put("level", pokemon.level);
            entry.put("health", pokemon.health.get());
            entry.put("maxHealth", pokemon.maxHealth);
            entry.put("status", PokemonSummaries.status(StatusType.getEffect(pokemon.status)));
            entry.put("position", pokemon.position);
            entry.put("switchingOut", pokemon.isSwitchingOut);
            entry.put("switchingIn", pokemon.isSwitchingIn);
            entry.put("moves", PokemonSummaries.moves(pokemon.moveset));
            result.add(entry);
        }
        return result;
    }
}
