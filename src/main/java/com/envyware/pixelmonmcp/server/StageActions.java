package com.envyware.pixelmonmcp.server;

import com.envyware.pixelmonmcp.ToolArgs;
import com.pixelmonmod.api.parsing.ParseAttempt;
import com.pixelmonmod.api.pokemon.PokemonSpecification;
import com.pixelmonmod.api.pokemon.PokemonSpecificationProxy;
import com.pixelmonmod.pixelmon.api.npc.NPCPreset;
import com.pixelmonmod.pixelmon.entities.npcs.NPC;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.init.registry.PixelmonRegistry;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Posed Pokémon and NPCs for shots. Staged entities carry {@link ServerActions#STAGED_TAG}, so they survive
 * {@code pixelmon.film.prepare} clears and {@code pixelmon.stage.clear} can find them.
 */
public final class StageActions {
    private StageActions() {
    }

    public static Map<String, Object> stagePokemon(ServerPlayer player, Map<String, Object> args) {
        String spec = ToolArgs.string(args, "pokemon");
        if (spec == null) {
            return Map.of("status", "rejected", "reason", "pokemon (a Pokémon spec) is required");
        }
        ParseAttempt<PokemonSpecification> attempt = PokemonSpecificationProxy.create(spec);
        if (!attempt.wasSuccess()) {
            return Map.of("status", "rejected", "spec", spec, "reason", String.valueOf(attempt.getError()));
        }
        Pose pose = pose(player, args);
        if (pose.error != null) {
            return pose.error;
        }

        ServerLevel level = player.serverLevel();
        PixelmonEntity pokemon = attempt.get().create(level);
        pokemon.canDespawn = false;
        place(pokemon, pose, ToolArgs.bool(args.get("frozen"), true));
        level.addFreshEntity(pokemon);
        String animation = ToolArgs.string(args, "animation");
        if (animation != null) {
            pokemon.setAnimation(animation);
        }

        Map<String, Object> result = staged(pokemon, pose);
        result.put("species", pokemon.getSpecies() == null ? null : pokemon.getSpecies().getName());
        if (pokemon.getPokemon().getForm() != null && !pokemon.getPokemon().getForm().isDefault()) {
            result.put("form", pokemon.getPokemon().getForm().getName());
        }
        if (pokemon.getPokemon().getPalette() != null && pokemon.getPokemon().getPalette().isShiny()) {
            result.put("shiny", true);
        }
        if (animation != null) {
            result.put("animation", animation);
        }
        return result;
    }

    public static Map<String, Object> stageNpc(ServerPlayer player, Map<String, Object> args) {
        String presetId = ToolArgs.string(args, "npc");
        if (presetId == null) {
            return Map.of("status", "rejected", "reason", "npc (an NPC preset id) is required");
        }
        Optional<Holder.Reference<NPCPreset>> preset = player.server.registryAccess()
                .registryOrThrow(PixelmonRegistry.NPC_PRESET_REGISTRY)
                .getHolder(ToolArgs.id(presetId));
        if (preset.isEmpty()) {
            return Map.of("status", "rejected", "reason", "unknown NPC preset", "npc", presetId);
        }
        Pose pose = pose(player, args);
        if (pose.error != null) {
            return pose.error;
        }

        boolean frozen = ToolArgs.bool(args.get("frozen"), true);
        NPC npc = preset.get().value().createBuilder().position(pose.position).buildAndSpawn(player.serverLevel());
        place(npc, pose, frozen);
        if (frozen) {
            npc.makeImmovable();
        }
        String name = ToolArgs.string(args, "name");
        if (name != null) {
            npc.setCustomName(Component.literal(name));
        }

        Map<String, Object> result = staged(npc, pose);
        result.put("npc", preset.get().key().location().toString());
        result.put("name", npc.getName().getString());
        return result;
    }

    public static Map<String, Object> clear(ServerPlayer player, Map<String, Object> args) {
        List<String> uuids = ToolArgs.strings(args.get("uuid"));
        List<Entity> found = new ArrayList<>();
        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (entity.getTags().contains(ServerActions.STAGED_TAG)
                    && (uuids.isEmpty() || uuids.contains(entity.getStringUUID()))) {
                found.add(entity);
            }
        }
        List<String> removed = new ArrayList<>();
        for (Entity entity : found) {
            removed.add(entity.getStringUUID());
            entity.discard();
        }
        return Map.of("status", "cleared", "removed", removed.size(), "uuids", removed);
    }

    private static void place(Mob mob, Pose pose, boolean frozen) {
        mob.moveTo(pose.position.x, pose.position.y, pose.position.z, pose.yaw, 0);
        mob.setYHeadRot(pose.yaw);
        mob.setYBodyRot(pose.yaw);
        mob.addTag(ServerActions.STAGED_TAG);
        mob.setPersistenceRequired();
        if (frozen) {
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setInvulnerable(true);
            mob.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static Map<String, Object> staged(Entity entity, Pose pose) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "staged");
        result.put("id", entity.getId());
        result.put("uuid", entity.getStringUUID());
        result.put("pos", Placement.rounded(pose.position));
        result.put("yaw", Placement.round(pose.yaw));
        return result;
    }

    private static Pose pose(ServerPlayer player, Map<String, Object> args) {
        Vec3 origin = Placement.origin(player, args.get("origin"));
        if (origin == null) {
            return Pose.error("origin must be \"spawn\", \"player\" or [x, y, z]");
        }
        Vec3 position;
        if (args.get("pos") == null) {
            position = Placement.inFront(player, ToolArgs.number(args, "distance", 4));
        } else {
            position = Placement.point(args, "pos", origin);
            if (position == null) {
                return Pose.error("pos must be [x, y, z]");
            }
            if (ToolArgs.bool(args.get("ground"), false)) {
                position = Placement.ground(player, position);
            }
        }

        Double yaw = ToolArgs.number(args, "yaw");
        if (yaw != null) {
            return new Pose(position, yaw.floatValue(), null);
        }
        Vec3 lookAt = args.get("lookAt") == null ? player.position() : Placement.point(args, "lookAt", origin);
        if (lookAt == null) {
            return Pose.error("lookAt must be [x, y, z]");
        }
        return new Pose(position, Placement.yawTowards(position, lookAt), null);
    }

    private record Pose(Vec3 position, float yaw, Map<String, Object> error) {
        static Pose error(String reason) {
            return new Pose(null, 0, Map.of("status", "rejected", "reason", reason));
        }
    }
}
