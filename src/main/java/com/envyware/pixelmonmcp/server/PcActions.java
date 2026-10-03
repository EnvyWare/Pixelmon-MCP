package com.envyware.pixelmonmcp.server;

import com.envyware.pixelmonmcp.PokemonSummaries;
import com.envyware.pixelmonmcp.ToolArgs;
import com.envyware.pixelmonmcp.Tools;
import com.pixelmonmod.api.parsing.ParseAttempt;
import com.pixelmonmod.api.pokemon.PokemonSpecification;
import com.pixelmonmod.api.pokemon.PokemonSpecificationProxy;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PCBox;
import com.pixelmonmod.pixelmon.api.storage.PCStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The player's PC, read and written one box (page) at a time.
 */
public final class PcActions {
    private PcActions() {
    }

    public static Map<String, Object> state(ServerPlayer player, Map<String, Object> args) {
        PCStorage pc = StorageProxy.getPCForPlayerNow(player);
        if (pc == null) {
            return Tools.status("not_loaded");
        }
        int box = ToolArgs.integer(args, "box", 0);
        if (box < 0 || box >= pc.getBoxCount()) {
            return Tools.status("rejected", "reason", "box out of range", "box", box, "boxCount", pc.getBoxCount());
        }
        return page(pc, box);
    }

    public static Map<String, Object> set(ServerPlayer player, Map<String, Object> args) {
        PCStorage pc = StorageProxy.getPCForPlayerNow(player);
        if (pc == null) {
            return Tools.status("not_loaded");
        }
        Integer box = ToolArgs.integer(args, "box");
        if (box == null || box < 0 || box >= pc.getBoxCount()) {
            return Tools.status("rejected", "reason", "box out of range", "box", box, "boxCount", pc.getBoxCount());
        }

        Integer slot = ToolArgs.integer(args, "slot");
        if (slot != null) {
            if (slot < 0 || slot >= PCBox.POKEMON_PER_BOX) {
                return Tools.status("rejected", "reason", "slot out of range", "slot", slot);
            }
            String spec = ToolArgs.string(args, "pokemon");
            Pokemon pokemon = null;
            if (spec != null) {
                ParseAttempt<PokemonSpecification> attempt = PokemonSpecificationProxy.create(spec);
                if (!attempt.wasSuccess()) {
                    return Tools.status("rejected", "spec", spec, "reason", String.valueOf(attempt.getError()));
                }
                pokemon = attempt.get().create();
            }
            pc.set(box, slot, pokemon);
        } else if (args.containsKey("pokemon")) {
            List<String> specs = ToolArgs.slots(args.get("pokemon"));
            if (specs.size() > PCBox.POKEMON_PER_BOX) {
                return Tools.status("rejected", "reason", "a box holds " + PCBox.POKEMON_PER_BOX + " Pokémon");
            }
            List<Pokemon> created = new ArrayList<>();
            for (String spec : specs) {
                if (spec == null) {
                    created.add(null);
                    continue;
                }
                ParseAttempt<PokemonSpecification> attempt = PokemonSpecificationProxy.create(spec);
                if (!attempt.wasSuccess()) {
                    return Tools.status("rejected", "spec", spec, "reason", String.valueOf(attempt.getError()));
                }
                created.add(attempt.get().create());
            }
            for (int i = 0; i < PCBox.POKEMON_PER_BOX; i++) {
                pc.set(box, i, i < created.size() ? created.get(i) : null);
            }
        }

        String name = ToolArgs.string(args, "name");
        if (name != null) {
            pc.getBox(box).setName(name);
            pc.initialize(player);
        }

        Map<String, Object> result = page(pc, box);
        result.put("status", "set");
        return result;
    }

    private static Map<String, Object> page(PCStorage pc, int box) {
        PCBox page = pc.getBox(box);
        List<Map<String, Object>> pokemon = new ArrayList<>();
        for (int slot = 0; slot < PCBox.POKEMON_PER_BOX; slot++) {
            Pokemon stored = pc.get(box, slot);
            if (stored != null) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("slot", slot);
                entry.putAll(PokemonSummaries.pokemon(stored));
                pokemon.add(entry);
            }
        }

        List<Map<String, Object>> boxes = new ArrayList<>();
        for (int i = 0; i < pc.getBoxCount(); i++) {
            int count = 0;
            for (int slot = 0; slot < PCBox.POKEMON_PER_BOX; slot++) {
                if (pc.get(i, slot) != null) {
                    count++;
                }
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("box", i);
            summary.put("name", pc.getBox(i).getName());
            summary.put("count", count);
            boxes.add(summary);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("boxCount", pc.getBoxCount());
        result.put("slotsPerBox", PCBox.POKEMON_PER_BOX);
        result.put("box", box);
        result.put("name", page.getName());
        result.put("wallpaper", page.getWallpaper());
        result.put("pokemon", pokemon);
        result.put("boxes", boxes);
        return result;
    }
}
