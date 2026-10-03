package com.envyware.pixelmonmcp;

import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.stats.Moveset;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.status.StatusType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON shapes for Pokémon, moves, statuses and items shared by the client and server tools.
 */
public final class PokemonSummaries {
    private PokemonSummaries() {
    }

    public static Map<String, Object> pokemon(Pokemon pokemon) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", pokemon.getUUID().toString());
        result.put("species", pokemon.getSpecies() == null ? null : pokemon.getSpecies().getName());
        result.put("form", pokemon.getForm() == null ? null : pokemon.getForm().getName());
        result.put("name", pokemon.getDisplayName().getString());
        result.put("level", pokemon.getPokemonLevel());
        result.put("health", pokemon.getHealth());
        result.put("maxHealth", pokemon.getMaxHealth());
        result.put("fainted", pokemon.isFainted());
        result.put("egg", pokemon.isEgg());
        result.put("status", status(pokemon.getPrimaryStatus() == null ? null : pokemon.getPrimaryStatus().type));
        result.put("heldItem", item(pokemon.getHeldItem()));
        result.put("moves", moves(pokemon.getMoveset()));
        return result;
    }

    public static List<Map<String, Object>> moves(Moveset moveset) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (moveset == null) {
            return result;
        }

        for (Attack attack : moveset) {
            if (attack == null || attack.getActualMove() == null) {
                continue;
            }

            Map<String, Object> move = new LinkedHashMap<>();
            move.put("name", attack.getActualMove().getAttackName());
            move.put("pp", attack.getPP());
            move.put("maxPp", attack.getMaxPP());
            result.add(move);
        }
        return result;
    }

    public static String status(StatusType type) {
        return type == null || type == StatusType.None ? null : type.name();
    }

    public static String item(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
