package com.envyware.pixelmonmcp.server;

import com.envyware.pixelmonmcp.ToolArgs;
import com.mojang.datafixers.util.Pair;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.entities.npcs.NPC;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Finds things to point a camera at: loaded Pixelmon entities and NPCs near a point, and the nearest structures.
 * Positions are absolute, or relative to {@code origin} when one is given, so they can go straight into a film scene.
 */
public final class LocateActions {
    private LocateActions() {
    }

    public static Map<String, Object> pokemon(ServerPlayer player, Map<String, Object> args) {
        Area area = area(player, args);
        if (area.error != null) {
            return area.error;
        }

        List<String> species = new ArrayList<>();
        for (String name : ToolArgs.strings(args.get("species"))) {
            species.add(name.toLowerCase(Locale.ROOT));
        }
        Boolean wild = ToolArgs.optionalBool(args, "wild");
        List<PixelmonEntity> found = new ArrayList<>();
        for (PixelmonEntity entity : area.entities(player, PixelmonEntity.class)) {
            Pokemon pokemon = entity.getPokemon();
            if (pokemon == null || pokemon.getSpecies() == null) {
                continue;
            }
            if (!species.isEmpty() && !species.contains(pokemon.getSpecies().getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (wild != null && wild != (entity.getOwner() == null)) {
                continue;
            }
            found.add(entity);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (PixelmonEntity entity : area.nearest(found)) {
            Pokemon pokemon = entity.getPokemon();
            Map<String, Object> entry = area.entry(entity);
            entry.put("species", pokemon.getSpecies().getName());
            if (pokemon.getForm() != null && !pokemon.getForm().isDefault()) {
                entry.put("form", pokemon.getForm().getName());
            }
            if (pokemon.getPalette() != null && pokemon.getPalette().isShiny()) {
                entry.put("shiny", true);
            }
            entry.put("level", pokemon.getPokemonLevel());
            LivingEntity owner = entity.getOwner();
            if (owner != null) {
                entry.put("owner", owner.getName().getString());
            }
            if (entity.battleController != null) {
                entry.put("inBattle", true);
            }
            results.add(entry);
        }
        return area.result(found.size(), results);
    }

    public static Map<String, Object> npcs(ServerPlayer player, Map<String, Object> args) {
        Area area = area(player, args);
        if (area.error != null) {
            return area.error;
        }

        List<NPC> found = area.entities(player, NPC.class);
        List<Map<String, Object>> results = new ArrayList<>();
        for (NPC npc : area.nearest(found)) {
            Map<String, Object> entry = area.entry(npc);
            entry.put("name", npc.getName().getString());
            if (npc.isLinkedToPreset()) {
                entry.put("preset", npc.getLinkedPresetKey().toString());
            }
            results.add(entry);
        }
        return area.result(found.size(), results);
    }

    public static Map<String, Object> structures(ServerPlayer player, Map<String, Object> args) {
        ServerLevel level = player.serverLevel();
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<String> queries = ToolArgs.strings(args.get("structure"));
        if (queries.isEmpty()) {
            queries.add("pixelmon:");
        }

        List<Holder<Structure>> matched = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (String query : queries) {
            for (Holder<Structure> holder : match(registry, query)) {
                String id = id(holder);
                if (!ids.contains(id)) {
                    ids.add(id);
                    matched.add(holder);
                }
            }
        }
        if (matched.isEmpty()) {
            return Map.of("status", "rejected", "reason", "no structure matches", "structure", queries);
        }
        if (ToolArgs.bool(args.get("list"), false) || args.get("structure") == null) {
            ids.sort(Comparator.naturalOrder());
            return Map.of("status", "listed", "count", ids.size(), "structures", ids);
        }

        Area area = area(player, args);
        if (area.error != null) {
            return area.error;
        }
        BlockPos center = BlockPos.containing(area.center);
        double radius = ToolArgs.number(args, "radius", 1600);
        int radiusChunks = Math.max(1, (int) Math.ceil(radius / 16.0));
        int limit = Math.max(1, ToolArgs.integer(args, "limit", 5));

        List<Map<String, Object>> results = new ArrayList<>();
        List<Holder<Structure>> remaining = new ArrayList<>(matched);
        while (results.size() < limit && !remaining.isEmpty()) {
            Pair<BlockPos, Holder<Structure>> nearest = level.getChunkSource().getGenerator()
                    .findNearestMapStructure(level, HolderSet.direct(remaining), center, radiusChunks, false);
            if (nearest == null) {
                break;
            }
            String id = id(nearest.getSecond());
            List<Holder<Structure>> next = new ArrayList<>();
            for (Holder<Structure> holder : remaining) {
                if (!id(holder).equals(id)) {
                    next.add(holder);
                }
            }
            remaining = next;

            BlockPos found = nearest.getFirst();
            double distance = Math.sqrt(Math.pow(found.getX() - area.center.x, 2)
                    + Math.pow(found.getZ() - area.center.z, 2));
            if (distance > radius) {
                break;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("structure", id);
            entry.put("x", Placement.round(found.getX() - area.origin.x));
            entry.put("z", Placement.round(found.getZ() - area.origin.z));
            entry.put("distance", Placement.round(distance));
            results.add(entry);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", results.isEmpty() ? "not_found" : "found");
        result.put("searched", matched.size());
        if (!player.server.getWorldData().worldGenOptions().generateStructures()) {
            result.put("warning", "this world was created without structures");
        }
        result.put("structures", results);
        return result;
    }

    private static List<Holder<Structure>> match(Registry<Structure> registry, String query) {
        List<Holder<Structure>> result = new ArrayList<>();
        if (query.startsWith("#")) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, ToolArgs.id(query.substring(1)));
            Optional<HolderSet.Named<Structure>> set = registry.getTag(tag);
            if (set.isPresent()) {
                for (Holder<Structure> holder : set.get()) {
                    result.add(holder);
                }
            }
            return result;
        }

        String prefix = query;
        if (!query.endsWith(":")) {
            ResourceLocation id = ToolArgs.id(query);
            Optional<Holder.Reference<Structure>> exact = registry.getHolder(
                    ResourceKey.create(Registries.STRUCTURE, id));
            if (exact.isPresent()) {
                result.add(exact.get());
                return result;
            }
            prefix = id.toString();
        }
        for (ResourceKey<Structure> key : registry.registryKeySet()) {
            if (key.location().toString().startsWith(prefix)) {
                registry.getHolder(key).ifPresent(result::add);
            }
        }
        return result;
    }

    private static String id(Holder<Structure> holder) {
        return holder.unwrapKey().map(key -> key.location().toString()).orElse("?");
    }

    private static Area area(ServerPlayer player, Map<String, Object> args) {
        Vec3 origin = Placement.origin(player, args.get("origin"));
        if (origin == null) {
            return Area.error("origin must be \"spawn\", \"player\" or [x, y, z]");
        }
        Vec3 center = player.position();
        if (args.get("pos") != null) {
            center = Placement.point(args, "pos", origin);
            if (center == null) {
                return Area.error("pos must be [x, y, z]");
            }
        }
        return new Area(origin, center, ToolArgs.number(args, "radius", 64),
                Math.max(1, ToolArgs.integer(args, "limit", 10)), null);
    }

    private record Area(Vec3 origin, Vec3 center, double radius, int limit, Map<String, Object> error) {
        static Area error(String reason) {
            return new Area(null, null, 0, 0, Map.of("status", "rejected", "reason", reason));
        }

        <T extends Entity> List<T> entities(ServerPlayer player, Class<T> type) {
            double radiusSquared = radius * radius;
            return player.serverLevel().getEntitiesOfClass(type, new AABB(center, center).inflate(radius),
                    entity -> entity.isAlive() && entity.distanceToSqr(center) <= radiusSquared);
        }

        <T extends Entity> List<T> nearest(List<T> entities) {
            List<T> sorted = new ArrayList<>(entities);
            sorted.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(center)));
            return sorted.size() > limit ? sorted.subList(0, limit) : sorted;
        }

        Map<String, Object> entry(Entity entity) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", entity.getId());
            entry.put("uuid", entity.getStringUUID());
            entry.put("pos", Placement.rounded(entity.position().subtract(origin)));
            entry.put("distance", Placement.round(Math.sqrt(entity.distanceToSqr(center))));
            if (entity.getTags().contains(ServerActions.STAGED_TAG)) {
                entry.put("staged", true);
            }
            return entry;
        }

        Map<String, Object> result(int total, List<Map<String, Object>> results) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "found");
            result.put("total", total);
            result.put("results", results);
            return result;
        }
    }
}
