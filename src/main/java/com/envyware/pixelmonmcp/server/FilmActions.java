package com.envyware.pixelmonmcp.server;

import com.envyware.pixelmonmcp.ToolArgs;
import com.pixelmonmod.pixelmon.Pixelmon;
import com.pixelmonmod.pixelmon.api.config.PixelmonConfigProxy;
import com.pixelmonmod.pixelmon.api.events.spawning.SpawnEvent;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.rules.PixelmonGameRules;
import com.pixelmonmod.pixelmon.api.spawning.archetypes.spawners.TriggerSpawner;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.entities.WormholeEntity;
import com.pixelmonmod.pixelmon.entities.npcs.NPC;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts a world into a state for filming (no Pixelmon spawning, optionally a cleared area and a set party) and puts back
 * exactly what was changed. Runs on the server thread.
 */
public final class FilmActions {
    private static final String[] LEGENDARY_SETTINGS = {"allowLegendarySpawn", "displayLegendaryGlobalMessage"};

    private static volatile Prepared prepared;
    private static volatile int vetoedSpawns;
    private static boolean listening;

    private FilmActions() {
    }

    public static Map<String, Object> prepare(ServerPlayer player, Map<String, Object> args) {
        Prepared current = current(player.server);
        boolean already = current != null;
        if (current == null) {
            current = new Prepared(player.server);
        }

        Map<String, Object> changed = new LinkedHashMap<>();
        List<Map<String, Object>> unsupported = new ArrayList<>();

        List<String> party = ToolArgs.strings(args.get("party"));
        if (!party.isEmpty()) {
            List<Pokemon> previous = new ArrayList<>();
            PlayerPartyStorage storage = StorageProxy.getPartyNow(player);
            for (int slot = 0; slot < 6; slot++) {
                previous.add(storage.get(slot));
            }
            boolean starterPicked = storage.starterPicked;
            Map<String, Object> result = ServerActions.setParty(player, party, ToolArgs.bool(args.get("heal"), true));
            if (!"set".equals(result.get("status"))) {
                Map<String, Object> rejected = new LinkedHashMap<>(result);
                rejected.put("step", "party");
                return rejected;
            }
            if (current.party == null) {
                current.party = previous;
                current.starterPicked = starterPicked;
            }
            changed.put("party", species(storage));
        }

        if (ToolArgs.bool(args.get("stopSpawning"), true)) {
            try {
                listen();
                current.vetoSpawns = true;
                ServerLevel overworld = player.server.overworld();
                boolean spawning = overworld.getGameRules().getBoolean(PixelmonGameRules.SPAWNING);
                if (current.spawningRule == null) {
                    current.spawningRule = spawning;
                }
                overworld.getGameRules().getRule(PixelmonGameRules.SPAWNING).set(false, player.server);
                changed.put("pixelmonSpawning", false);
                changed.put("spawnerVeto", true);
            } catch (LinkageError e) {
                unsupported.add(unsupported("pixelmonSpawning", e));
            }

            for (String setting : LEGENDARY_SETTINGS) {
                try {
                    Object config = PixelmonConfigProxy.getSpawningLegendary();
                    Object previous = swap(config, setting, false);
                    if (!current.legendary.containsKey(setting)) {
                        current.legendary.put(setting, previous);
                    }
                    changed.put(setting, false);
                } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
                    unsupported.add(unsupported(setting, e));
                }
            }
        }

        prepared = current;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "prepared");
        result.put("alreadyPrepared", already);
        result.put("changed", changed);
        Map<String, Object> removed = clear(player, args, unsupported);
        if (!removed.isEmpty()) {
            result.put("removed", removed);
        }
        if (!unsupported.isEmpty()) {
            result.put("unsupported", unsupported);
        }
        return result;
    }

    public static Map<String, Object> restore(ServerPlayer player) {
        Prepared current = prepared;
        if (current == null) {
            return Map.of("status", "not_prepared");
        }
        prepared = null;
        if (current.server != player.server) {
            return Map.of("status", "stale", "reason", "prepared in a world that is no longer loaded; nothing restored");
        }

        Map<String, Object> restored = new LinkedHashMap<>();
        List<Map<String, Object>> unsupported = new ArrayList<>();
        if (current.vetoSpawns) {
            restored.put("spawnerVeto", false);
            restored.put("vetoedSpawns", vetoedSpawns);
            vetoedSpawns = 0;
        }
        if (current.spawningRule != null) {
            try {
                player.server.overworld().getGameRules().getRule(PixelmonGameRules.SPAWNING)
                        .set(current.spawningRule, player.server);
                restored.put("pixelmonSpawning", current.spawningRule);
            } catch (LinkageError e) {
                unsupported.add(unsupported("pixelmonSpawning", e));
            }
        }
        for (Map.Entry<String, Object> entry : current.legendary.entrySet()) {
            try {
                swap(PixelmonConfigProxy.getSpawningLegendary(), entry.getKey(), entry.getValue());
                restored.put(entry.getKey(), entry.getValue());
            } catch (LinkageError | ReflectiveOperationException | RuntimeException e) {
                unsupported.add(unsupported(entry.getKey(), e));
            }
        }
        if (current.party != null) {
            PlayerPartyStorage storage = StorageProxy.getPartyNow(player);
            for (int slot = 0; slot < 6; slot++) {
                storage.set(slot, current.party.get(slot));
            }
            storage.starterPicked = current.starterPicked;
            restored.put("party", species(storage));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "restored");
        result.put("restored", restored);
        result.put("notRestored", "removed entities stay removed");
        if (!unsupported.isEmpty()) {
            result.put("unsupported", unsupported);
        }
        return result;
    }

    private static Map<String, Object> clear(ServerPlayer player, Map<String, Object> args,
                                             List<Map<String, Object>> unsupported) {
        List<String> kinds = ToolArgs.strings(args.get("clear"));
        boolean pokemon = kinds.contains("pokemon") || kinds.contains("all");
        boolean npcs = kinds.contains("npcs") || kinds.contains("all");
        boolean wormholes = kinds.contains("wormholes") || kinds.contains("all");
        Map<String, Object> removed = new LinkedHashMap<>();
        if (!pokemon && !npcs && !wormholes) {
            return removed;
        }

        Double radius = ToolArgs.number(args, "radius");
        double radiusSquared = radius == null ? Double.MAX_VALUE : radius * radius;
        List<Entity> pokemonEntities = new ArrayList<>();
        List<Entity> npcEntities = new ArrayList<>();
        List<Entity> wormholeEntities = new ArrayList<>();
        try {
            for (Entity entity : player.serverLevel().getAllEntities()) {
                if (entity.getTags().contains(ServerActions.STAGED_TAG)
                        || entity.distanceToSqr(player) > radiusSquared) {
                    continue;
                }
                if (pokemon && entity instanceof PixelmonEntity pixelmon && pixelmon.getOwner() == null
                        && pixelmon.battleController == null) {
                    pokemonEntities.add(entity);
                } else if (npcs && entity instanceof NPC) {
                    npcEntities.add(entity);
                } else if (wormholes && entity instanceof WormholeEntity) {
                    wormholeEntities.add(entity);
                }
            }
        } catch (LinkageError e) {
            unsupported.add(unsupported("clear", e));
            return removed;
        }

        if (pokemon) {
            removed.put("pokemon", discard(pokemonEntities));
        }
        if (npcs) {
            removed.put("npcs", discard(npcEntities));
        }
        if (wormholes) {
            removed.put("wormholes", discard(wormholeEntities));
        }
        removed.put("radius", radius == null ? "level" : radius);
        return removed;
    }

    private static int discard(List<Entity> entities) {
        for (Entity entity : entities) {
            entity.discard();
        }
        return entities.size();
    }

    private static Prepared current(MinecraftServer server) {
        Prepared current = prepared;
        return current != null && current.server == server ? current : null;
    }

    private static void listen() {
        if (!listening) {
            Pixelmon.EVENT_BUS.addListener(EventPriority.HIGHEST, false, SpawnEvent.class, FilmActions::onSpawn);
            listening = true;
        }
    }

    private static void onSpawn(SpawnEvent event) {
        Prepared current = prepared;
        if (current != null && current.vetoSpawns && !(event.spawner instanceof TriggerSpawner)) {
            event.setCanceled(true);
            vetoedSpawns++;
        }
    }

    private static Object swap(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(target);
        field.set(target, value);
        return previous;
    }

    private static List<String> species(PlayerPartyStorage storage) {
        List<String> result = new ArrayList<>();
        for (int slot = 0; slot < 6; slot++) {
            Pokemon pokemon = storage.get(slot);
            if (pokemon != null) {
                result.add(pokemon.getSpecies() == null ? null : pokemon.getSpecies().getName());
            }
        }
        return result;
    }

    private static Map<String, Object> unsupported(String setting, Throwable error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("setting", setting);
        result.put("reason", error.getClass().getSimpleName() + ": " + error.getMessage());
        return result;
    }

    private static final class Prepared {
        final MinecraftServer server;
        final Map<String, Object> legendary = new LinkedHashMap<>();
        boolean vetoSpawns;
        Boolean spawningRule;
        List<Pokemon> party;
        boolean starterPicked;

        Prepared(MinecraftServer server) {
            this.server = server;
        }
    }
}
