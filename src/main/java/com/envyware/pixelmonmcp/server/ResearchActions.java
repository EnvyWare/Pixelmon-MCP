package com.envyware.pixelmonmcp.server;

import com.envyware.pixelmonmcp.ToolArgs;
import com.envyware.pixelmonmcp.Tools;
import com.pixelmonmod.pixelmon.api.research.Research;
import com.pixelmonmod.pixelmon.api.storage.research.PlayerResearch;
import com.pixelmonmod.pixelmon.api.storage.research.PlayerResearchObjective;
import com.pixelmonmod.pixelmon.api.storage.research.PlayerResearchStage;
import com.pixelmonmod.pixelmon.api.storage.research.ResearchStorage;
import com.pixelmonmod.pixelmon.api.storage.research.ResearchStorageProxy;
import com.pixelmonmod.pixelmon.init.registry.PixelmonRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The player's research: active research with stage and objective progress, completed ids, and the registry.
 */
public final class ResearchActions {
    private ResearchActions() {
    }

    public static Map<String, Object> state(ServerPlayer player, boolean includeAvailable) {
        ResearchStorage storage = ResearchStorageProxy.getStorageNow(player);
        if (storage == null) {
            return Tools.status("not_loaded");
        }

        List<Map<String, Object>> active = new ArrayList<>();
        for (PlayerResearch research : storage.activeResearch()) {
            active.add(research(research));
        }
        List<String> completed = new ArrayList<>();
        for (ResourceLocation id : storage.completedResearch()) {
            completed.add(id.toString());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", active);
        result.put("completed", completed);
        if (includeAvailable) {
            List<String> available = new ArrayList<>();
            for (ResourceLocation id : registry(player).keySet()) {
                available.add(id.toString());
            }
            available.sort(null);
            result.put("available", available);
        }
        return result;
    }

    public static Map<String, Object> set(ServerPlayer player, Map<String, Object> args) {
        ResearchStorage storage = ResearchStorageProxy.getStorageNow(player);
        if (storage == null) {
            return Tools.status("not_loaded");
        }
        String action = ToolArgs.string(args, "action");
        if (action == null) {
            return Tools.status("rejected", "reason",
                    "action must be start, discard, complete, complete_stage, claim or clear");
        }

        if (action.equals("clear")) {
            storage.clear();
            return withState(player, "cleared");
        }

        if (action.equals("start")) {
            String id = ToolArgs.string(args, "id");
            Research research = id == null ? null : registry(player).get(ToolArgs.id(id));
            if (research == null) {
                return Tools.status("rejected", "reason", "unknown research", "id", id);
            }
            return withState(player, storage.startResearch(research) ? "started" : "not_started");
        }

        PlayerResearch research = find(storage, args);
        if (research == null) {
            return Tools.status("rejected", "reason", "no matching active research; pass id or uuid",
                    "id", ToolArgs.string(args, "id"), "uuid", ToolArgs.string(args, "uuid"));
        }

        switch (action) {
            case "discard" -> {
                return withState(player, storage.discardResearch(research) ? "discarded" : "not_discarded");
            }
            case "complete" -> {
                int stageIndex = research.type().stages().indexOf(research.currentStage().stage());
                for (int i = Math.max(0, stageIndex); i < research.type().stages().size(); i++) {
                    completeObjectives(research.currentStage());
                    research.completeStage();
                }
                research.complete();
                return withState(player, "completed");
            }
            case "complete_stage" -> {
                completeObjectives(research.currentStage());
                research.currentStage().complete();
                return withState(player, "stage_completed");
            }
            case "claim" -> {
                PlayerResearchStage stage = research.currentStage();
                int claimed = 0;
                for (PlayerResearchObjective objective : stage.currentObjectives()) {
                    if (objective.canClaimReward()) {
                        objective.claimReward();
                        claimed++;
                    }
                }
                if (stage.canClaimReward()) {
                    stage.claimReward();
                    claimed++;
                }
                Map<String, Object> result = withState(player, "claimed");
                result.put("claimed", claimed);
                return result;
            }
            default -> {
                return Tools.status("rejected", "reason", "unknown action", "action", action);
            }
        }
    }

    private static void completeObjectives(PlayerResearchStage stage) {
        if (stage == null) {
            return;
        }
        for (PlayerResearchObjective objective : stage.currentObjectives()) {
            if (!objective.isComplete()) {
                objective.complete();
            }
        }
    }

    private static PlayerResearch find(ResearchStorage storage, Map<String, Object> args) {
        String uuid = ToolArgs.string(args, "uuid");
        if (uuid != null) {
            try {
                return storage.findResearch(UUID.fromString(uuid));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        String id = ToolArgs.string(args, "id");
        if (id == null) {
            return null;
        }
        ResourceLocation location = ToolArgs.id(id);
        for (PlayerResearch research : storage.activeResearch()) {
            if (location.equals(research.type().id())) {
                return research;
            }
        }
        return null;
    }

    private static Map<String, Object> research(PlayerResearch research) {
        Research type = research.type();
        PlayerResearchStage stage = research.currentStage();
        List<Map<String, Object>> objectives = new ArrayList<>();
        if (stage != null) {
            for (PlayerResearchObjective objective : stage.currentObjectives()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", objective.objective().name().getString());
                entry.put("progress", objective.getProgress());
                entry.put("target", objective.getTarget());
                entry.put("complete", objective.isComplete());
                entry.put("claimed", objective.hasClaimedReward());
                objectives.add(entry);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", research.uuid().toString());
        result.put("id", String.valueOf(type.id()));
        result.put("name", type.name().getString());
        result.put("tier", type.tier() == null ? null : type.tier().name().getString());
        result.put("stage", stage == null ? null : type.stages().indexOf(stage.stage()));
        result.put("stageCount", type.stages().size());
        result.put("stageComplete", stage != null && stage.isComplete());
        result.put("stageClaimed", stage != null && stage.hasClaimedReward());
        result.put("complete", research.isComplete());
        result.put("favorite", research.isFavorite());
        result.put("objectives", objectives);
        return result;
    }

    private static Map<String, Object> withState(ServerPlayer player, String status) {
        Map<String, Object> result = state(player, false);
        result.put("status", status);
        return result;
    }

    private static Registry<Research> registry(ServerPlayer player) {
        return player.server.registryAccess().registryOrThrow(PixelmonRegistry.RESEARCH_REGISTRY);
    }
}
