package com.envyware.pixelmonmcp.server;

import com.envyware.pixelmonmcp.PokemonSummaries;
import com.envyware.pixelmonmcp.ToolArgs;
import com.envyware.pixelmonmcp.Tools;
import com.envyware.pixelmonmcp.Versions;
import com.pixelmonmod.api.parsing.ParseAttempt;
import com.pixelmonmod.api.pokemon.PokemonSpecification;
import com.pixelmonmod.api.pokemon.PokemonSpecificationProxy;
import com.pixelmonmod.pixelmon.api.battles.BattleEndCause;
import com.pixelmonmod.pixelmon.api.battles.BattleType;
import com.pixelmonmod.pixelmon.api.npc.NPCPreset;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.storage.PlayerPartyStorage;
import com.pixelmonmod.pixelmon.api.storage.StorageProxy;
import com.pixelmonmod.pixelmon.battles.BattleRegistry;
import com.pixelmonmod.pixelmon.battles.api.BattleBuilder;
import com.pixelmonmod.pixelmon.battles.api.rules.BattleRuleSet;
import com.pixelmonmod.pixelmon.battles.controller.BattleController;
import com.pixelmonmod.pixelmon.battles.controller.ai.BattleAI;
import com.pixelmonmod.pixelmon.battles.controller.participants.BattleParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
import com.pixelmonmod.pixelmon.battles.controller.participants.PlayerParticipant;
import com.pixelmonmod.pixelmon.battles.controller.participants.WildPixelmonParticipant;
import com.pixelmonmod.pixelmon.entities.npcs.NPC;
import com.pixelmonmod.pixelmon.entities.pixelmon.PixelmonEntity;
import com.pixelmonmod.pixelmon.init.registry.PixelmonRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Setup actions run on the integrated server. The client always plays singleplayer, so the player is the only one on
 * the server.
 */
public final class ServerActions {
    /** Entity tag marking opponents spawned by pixelmon.battle.start, removed again by pixelmon.battle.end. */
    public static final String SPAWNED_TAG = "pixelmon_agent_spawned";
    /** Entity tag marking entities staged by pixelmon.stage.*, kept by pixelmon.film.prepare clears. */
    public static final String STAGED_TAG = "pixelmon_mcp_staged";

    private ServerActions() {
    }

    public static Map<String, Object> onServer(Function<ServerPlayer, Map<String, Object>> action) throws Exception {
        return onServer(action, Tools.TIMEOUT_SECONDS);
    }

    public static Map<String, Object> onServer(Function<ServerPlayer, Map<String, Object>> action, long timeoutSeconds)
            throws Exception {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return Map.of("status", "not_in_singleplayer");
        }

        return server.submit(() -> {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                return Map.<String, Object>of("status", "no_player");
            }
            return action.apply(players.get(0));
        }).get(timeoutSeconds, TimeUnit.SECONDS);
    }

    public static Map<String, Object> setParty(ServerPlayer player, List<String> specs, boolean heal) {
        if (BattleRegistry.getBattle(player) != null) {
            return Map.of("status", "in_battle");
        }
        if (specs.isEmpty() || specs.size() > 6) {
            return Map.of("status", "rejected", "reason", "pokemon must list 1 to 6 specs");
        }

        List<Pokemon> created = new ArrayList<>();
        for (String spec : specs) {
            ParseAttempt<PokemonSpecification> attempt = PokemonSpecificationProxy.create(spec);
            if (!attempt.wasSuccess()) {
                return Map.of("status", "rejected", "spec", spec, "reason", String.valueOf(attempt.getError()));
            }
            created.add(attempt.get().create());
        }

        PlayerPartyStorage party = StorageProxy.getPartyNow(player);
        party.starterPicked = true;
        for (int slot = 0; slot < 6; slot++) {
            party.set(slot, slot < created.size() ? created.get(slot) : null);
        }
        if (heal) {
            party.heal();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "set");
        result.put("party", summarise(party));
        return result;
    }

    public static Map<String, Object> healParty(ServerPlayer player) {
        if (BattleRegistry.getBattle(player) != null) {
            return Map.of("status", "in_battle");
        }

        PlayerPartyStorage party = StorageProxy.getPartyNow(player);
        party.heal();
        return Map.of("status", "healed", "party", summarise(party));
    }

    public static Map<String, Object> startBattle(ServerPlayer player, Map<String, Object> args) {
        if (BattleRegistry.getBattle(player) != null) {
            return Map.of("status", "in_battle");
        }

        String npcId = ToolArgs.string(args, "npc");
        String wildSpec = ToolArgs.string(args, "wild");
        List<String> hordeSpecs = ToolArgs.strings(args.get("horde"));
        if (!hordeSpecs.isEmpty() && npcId == null && wildSpec == null) {
            return startHorde(player, hordeSpecs);
        }
        if ((npcId == null) == (wildSpec == null)) {
            return Map.of("status", "rejected", "reason", "pass exactly one of npc or wild");
        }

        RegistryAccess registryAccess = player.server.registryAccess();
        String rulesId = ToolArgs.string(args, "rules");
        Holder<BattleRuleSet> rules;
        if (rulesId == null) {
            rules = BattleRuleSet.getAG(registryAccess);
        } else {
            Optional<Holder.Reference<BattleRuleSet>> found = registryAccess
                    .registryOrThrow(PixelmonRegistry.BATTLE_RULE_SET_REGISTRY)
                    .getHolder(ResourceLocation.parse(rulesId));
            if (found.isEmpty()) {
                return Map.of("status", "rejected", "reason", "unknown battle rule set", "rules", rulesId);
            }
            rules = found.get();
        }

        double distance = args.get("distance") instanceof Number number ? number.doubleValue() : 3.0;
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(0, 0, 1);
        }
        Vec3 position = player.position().add(flat.normalize().scale(distance));
        ServerLevel level = player.serverLevel();

        if (npcId != null) {
            Optional<Holder.Reference<NPCPreset>> preset = registryAccess
                    .registryOrThrow(PixelmonRegistry.NPC_PRESET_REGISTRY)
                    .getHolder(ResourceLocation.parse(npcId));
            if (preset.isEmpty()) {
                return Map.of("status", "rejected", "reason", "unknown NPC preset", "npc", npcId);
            }

            String aiId = ToolArgs.string(args, "ai");
            ResourceKey<BattleAI> ai = aiId == null ? BattleAI.ADVANCED
                    : ResourceKey.create(BattleAI.REGISTRY, ResourceLocation.parse(aiId));
            NPC npc = preset.get().value().createBuilder().position(position).buildAndSpawn(level);
            npc.addTag(SPAWNED_TAG);
            npc.battle(player, rules, ai, ToolArgs.bool(args.get("canMega"), true),
                    ToolArgs.bool(args.get("canDynamax"), true));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "starting");
            result.put("npc", npcId);
            result.put("entityId", npc.getId());
            result.put("opponentParty", summarise(npc.getParty().getTeam()));
            if (args.containsKey("canTerastallize")) {
                result.put("canTerastallize", "unsupported by Pixelmon " + Versions.pixelmon() + ", ignored");
            }
            return result;
        }

        ParseAttempt<PokemonSpecification> attempt = PokemonSpecificationProxy.create(wildSpec);
        if (!attempt.wasSuccess()) {
            return Map.of("status", "rejected", "spec", wildSpec, "reason", String.valueOf(attempt.getError()));
        }

        PlayerPartyStorage party = StorageProxy.getPartyNow(player);
        PixelmonEntity starter = party.getAndSendOutFirstAblePokemon(player);
        if (starter == null) {
            return Map.of("status", "rejected", "reason", "the party has no Pokémon able to battle");
        }

        PixelmonEntity wild = attempt.get().create(level);
        wild.setPos(position.x, position.y, position.z);
        wild.addTag(SPAWNED_TAG);
        level.addFreshEntity(wild);

        BattleBuilder builder = BattleBuilder.builder()
                .rules(rules)
                .teamOne(new PlayerParticipant(player, starter.getPokemon()))
                .teamTwo(new WildPixelmonParticipant(false, wild));
        if (args.get("seed") instanceof Number seed) {
            builder.seed(seed.longValue());
        }
        builder.start(registryAccess);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "starting");
        result.put("wild", wildSpec);
        result.put("entityId", wild.getId());
        result.put("opponent", PokemonSummaries.pokemon(wild.getPokemon()));
        return result;
    }

    public static Map<String, Object> startHorde(ServerPlayer player, List<String> specs) {
        PlayerPartyStorage party = StorageProxy.getPartyNow(player);
        PixelmonEntity starter = party.getAndSendOutFirstAblePokemon(player);
        if (starter == null) {
            return Map.of("status", "rejected", "reason", "the party has no Pokémon able to battle");
        }

        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z);
        if (forward.lengthSqr() < 1.0E-4) {
            forward = new Vec3(0, 0, 1);
        }
        forward = forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        List<BattleParticipant> horde = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            ParseAttempt<PokemonSpecification> attempt = PokemonSpecificationProxy.create(specs.get(i));
            if (!attempt.wasSuccess()) {
                return Map.of("status", "rejected", "spec", specs.get(i), "reason",
                        String.valueOf(attempt.getError()));
            }
            Vec3 position = player.position().add(forward.scale(6))
                    .add(side.scale((i - (specs.size() - 1) / 2.0) * 2.5));
            PixelmonEntity wild = attempt.get().create(level);
            wild.setPos(position.x, position.y, position.z);
            wild.addTag(SPAWNED_TAG);
            level.addFreshEntity(wild);
            horde.add(new WildPixelmonParticipant(wild));
        }

        BattleBuilder.builder()
                .rules(BattleRuleSet.getAG(player.server.registryAccess()))
                .teamOne(horde)
                .teamTwo(new PlayerParticipant(player, starter.getPokemon()))
                .setBattleType(BattleType.HORDE)
                .start(player.server.registryAccess());
        return Map.of("status", "starting", "horde", specs);
    }

    public static Map<String, Object> endBattle(ServerPlayer player) {
        BattleController battle = BattleRegistry.getBattle(player);
        boolean ended = false;
        if (battle != null) {
            battle.endBattle(BattleEndCause.COMMAND);
            BattleRegistry.deRegisterBattle(battle);
            ended = true;
        }

        List<Entity> spawned = new ArrayList<>();
        for (Entity entity : player.serverLevel().getAllEntities()) {
            if (entity.getTags().contains(SPAWNED_TAG)) {
                spawned.add(entity);
            }
        }
        for (Entity entity : spawned) {
            entity.discard();
        }

        return Map.of("status", ended ? "ended" : "not_in_battle", "removedEntities", spawned.size());
    }

    public static Map<String, Object> battleState(ServerPlayer player) {
        BattleController battle = BattleRegistry.getBattle(player);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inBattle", battle != null);
        if (battle == null) {
            return result;
        }

        result.put("turn", battle.battleTurn());
        result.put("ended", battle.battleEnded());
        List<Map<String, Object>> participants = new ArrayList<>();
        for (BattleParticipant participant : battle.participants()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", participant.getName() == null ? null : participant.getName().getString());
            entry.put("kind", participant.getClass().getSimpleName());
            entry.put("isPlayer", participant.getEntity() == player);
            List<Map<String, Object>> active = new ArrayList<>();
            for (PixelmonWrapper wrapper : participant.controlledPokemon) {
                Map<String, Object> pokemon = new LinkedHashMap<>();
                pokemon.put("uuid", wrapper.getPokemonUUID() == null ? null : wrapper.getPokemonUUID().toString());
                pokemon.put("species", wrapper.getSpecies() == null ? null : wrapper.getSpecies().getName());
                pokemon.put("health", wrapper.getHealth());
                pokemon.put("maxHealth", wrapper.getMaxHealth());
                pokemon.put("status", PokemonSummaries.status(
                        wrapper.getPrimaryStatus() == null ? null : wrapper.getPrimaryStatus().type));
                active.add(pokemon);
            }
            entry.put("active", active);
            participants.add(entry);
        }
        result.put("participants", participants);
        return result;
    }

    private static List<Map<String, Object>> summarise(PlayerPartyStorage party) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int slot = 0; slot < 6; slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon != null) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("slot", slot);
                entry.putAll(PokemonSummaries.pokemon(pokemon));
                result.add(entry);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> summarise(List<Pokemon> team) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Pokemon pokemon : team) {
            result.add(PokemonSummaries.pokemon(pokemon));
        }
        return result;
    }
}
