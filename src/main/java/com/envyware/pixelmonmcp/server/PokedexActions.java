package com.envyware.pixelmonmcp.server;

import com.envyware.pixelmonmcp.ToolArgs;
import com.envyware.pixelmonmcp.Tools;
import com.pixelmonmod.api.parsing.ParseAttempt;
import com.pixelmonmod.api.pokemon.PokemonSpecification;
import com.pixelmonmod.api.pokemon.PokemonSpecificationProxy;
import com.pixelmonmod.pixelmon.api.pokedex.PokeDexStorageProxy;
import com.pixelmonmod.pixelmon.api.pokedex.Pokedex;
import com.pixelmonmod.pixelmon.api.pokedex.PokedexStorage;
import com.pixelmonmod.pixelmon.api.pokedex.event.PokedexEvent;
import com.pixelmonmod.pixelmon.api.pokedex.status.PokedexRegistrationStatus;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's Pokédex. Entries are addressed with Pokémon specs, so forms and palettes work as in party.set.
 */
public final class PokedexActions {
    private static final int MAX_PAGE = 200;

    private PokedexActions() {
    }

    public static Map<String, Object> state(ServerPlayer player, Map<String, Object> args) {
        PokedexStorage storage = PokeDexStorageProxy.getStorageNow(player);
        if (storage == null) {
            return Tools.status("not_loaded");
        }

        Map<String, Object> result = summary(storage);
        List<String> specs = ToolArgs.strings(args.get("pokemon"));
        List<Map<String, Object>> entries = new ArrayList<>();
        if (!specs.isEmpty()) {
            for (String spec : specs) {
                ParseAttempt<PokemonSpecification> attempt = PokemonSpecificationProxy.create(spec);
                if (!attempt.wasSuccess()) {
                    return Tools.status("rejected", "spec", spec, "reason", String.valueOf(attempt.getError()));
                }
                entries.add(entry(storage, attempt.get().create()));
            }
            result.put("entries", entries);
            return result;
        }

        List<Pokemon> dex = dexPokemon(player, args);
        if (dex == null) {
            return Tools.status("rejected", "reason", "unknown pokedex", "dex", ToolArgs.string(args, "dex"));
        }
        int offset = Math.max(0, ToolArgs.integer(args, "offset", 0));
        int limit = Math.min(MAX_PAGE, Math.max(1, ToolArgs.integer(args, "limit", 30)));
        for (int i = offset; i < dex.size() && i < offset + limit; i++) {
            entries.add(entry(storage, dex.get(i)));
        }
        result.put("total", dex.size());
        result.put("offset", offset);
        result.put("entries", entries);
        return result;
    }

    public static Map<String, Object> set(ServerPlayer player, Map<String, Object> args) {
        PokedexStorage storage = PokeDexStorageProxy.getStorageNow(player);
        if (storage == null) {
            return Tools.status("not_loaded");
        }

        String status = ToolArgs.string(args, "status");
        boolean caught = status == null || status.equalsIgnoreCase("caught");
        if (!caught && !status.equalsIgnoreCase("seen")) {
            return Tools.status("rejected", "reason", "status must be seen or caught; use clear to reset");
        }
        if (ToolArgs.bool(args.get("clear"), false)) {
            storage.clear();
        }

        List<Pokemon> targets = new ArrayList<>();
        if (ToolArgs.bool(args.get("all"), false)) {
            List<Pokemon> dex = dexPokemon(player, args);
            if (dex == null) {
                return Tools.status("rejected", "reason", "unknown pokedex", "dex", ToolArgs.string(args, "dex"));
            }
            targets.addAll(dex);
        } else {
            for (String spec : ToolArgs.strings(args.get("pokemon"))) {
                ParseAttempt<PokemonSpecification> attempt = PokemonSpecificationProxy.create(spec);
                if (!attempt.wasSuccess()) {
                    return Tools.status("rejected", "spec", spec, "reason", String.valueOf(attempt.getError()));
                }
                targets.add(attempt.get().create());
            }
        }

        for (Pokemon pokemon : targets) {
            storage.setSeen(pokemon, PokedexEvent.Constants.COMMAND_GIVEN);
            if (caught) {
                storage.setCaught(pokemon, PokedexEvent.Constants.COMMAND_GIVEN);
            }
        }

        Map<String, Object> result = summary(storage);
        result.put("status", "set");
        result.put("updated", targets.size());
        if (targets.size() <= MAX_PAGE) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Pokemon pokemon : targets) {
                entries.add(entry(storage, pokemon));
            }
            result.put("entries", entries);
        }
        return result;
    }

    private static Map<String, Object> summary(PokedexStorage storage) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("seen", storage.countSeen());
        result.put("caught", storage.countCaught());
        result.put("percentSeen", storage.getPercentage(PokedexRegistrationStatus.SEEN));
        result.put("percentCaught", storage.getPercentage(PokedexRegistrationStatus.CAUGHT));
        return result;
    }

    private static Map<String, Object> entry(PokedexStorage storage, Pokemon pokemon) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("dex", pokemon.getSpecies() == null ? null : pokemon.getSpecies().getDex());
        entry.put("species", pokemon.getSpecies() == null ? null : pokemon.getSpecies().getName());
        entry.put("form", pokemon.getForm() == null ? null : pokemon.getForm().getName());
        entry.put("palette", pokemon.getPalette() == null ? null : pokemon.getPalette().getName());
        entry.put("status", storage.getStatus(pokemon).name());
        entry.put("timesSeen", storage.getSeenCount(pokemon));
        entry.put("timesCaught", storage.getCaughtCount(pokemon));
        return entry;
    }

    private static List<Pokemon> dexPokemon(ServerPlayer player, Map<String, Object> args) {
        String dexId = ToolArgs.string(args, "dex");
        ResourceKey<Pokedex> key = dexId == null ? Pokedex.NATIONAL_DEX
                : ResourceKey.create(Pokedex.REGISTRY, ToolArgs.id(dexId));
        Pokedex dex = player.server.registryAccess().registryOrThrow(Pokedex.REGISTRY).get(key);
        return dex == null ? null : dex.pokemon().getPokemon();
    }
}
