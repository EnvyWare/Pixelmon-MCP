package com.envyware.pixelmonmcp.client;

import com.envyware.pixelmonmcp.PokemonSummaries;
import com.envyware.pixelmonmcp.ToolArgs;
import com.envyware.pixelmonmcp.Tools;
import com.pixelmonmod.pixelmon.api.battles.BagSection;
import com.pixelmonmod.pixelmon.api.battles.BattleMode;
import com.pixelmonmod.pixelmon.api.battles.action.BattleAction;
import com.pixelmonmod.pixelmon.api.battles.action.type.AttackBattleAction;
import com.pixelmonmod.pixelmon.api.battles.action.type.StruggleBattleAction;
import com.pixelmonmod.pixelmon.api.battles.action.type.SwitchBattleAction;
import com.pixelmonmod.pixelmon.api.battles.action.type.UseBagItemBattleAction;
import com.pixelmonmod.pixelmon.api.util.helpers.NetworkHelper;
import com.pixelmonmod.pixelmon.battles.attacks.Attack;
import com.pixelmonmod.pixelmon.battles.status.StatusType;
import com.pixelmonmod.pixelmon.client.ClientProxy;
import com.pixelmonmod.pixelmon.client.gui.battles.AttackData;
import com.pixelmonmod.pixelmon.client.gui.battles.BattleScreen;
import com.pixelmonmod.pixelmon.client.gui.battles.ClientBattleManager;
import com.pixelmonmod.pixelmon.client.gui.battles.PixelmonClientData;
import com.pixelmonmod.pixelmon.comm.packetHandlers.ReplaceMovePacket;
import com.pixelmonmod.pixelmon.comm.packetHandlers.battles.BattleActionPacket;
import com.pixelmonmod.pixelmon.comm.packetHandlers.battles.EnforcedRevivePacket;
import com.pixelmonmod.pixelmon.comm.packetHandlers.battles.FleePacket;
import com.pixelmonmod.pixelmon.comm.packetHandlers.battles.RequestBagItemsPacket;
import com.pixelmonmod.pixelmon.items.ItemData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Battle choices made the way the battle screen makes them: the same packets, queued per Pokémon and sent once
 * every Pokémon that has to act has chosen, so the client and server stay in step.
 */
public final class BattleControls {
    private static final Set<BattleMode> CHOOSING = EnumSet.of(BattleMode.MAIN_MENU, BattleMode.CHOOSE_ATTACK,
            BattleMode.CHOOSE_TARGETS, BattleMode.CHOOSE_POKEMON, BattleMode.CHOOSE_BAG, BattleMode.USE_BAG,
            BattleMode.APPLY_TO_POKEMON, BattleMode.CHOOSE_ETHER);
    private static final Set<BattleMode> ENFORCED = EnumSet.of(BattleMode.ENFORCED_SWITCH,
            BattleMode.ENFORCED_REVIVE);

    private BattleControls() {
    }

    public static Map<String, Object> options(boolean refreshBag) {
        ClientBattleManager bm = ClientProxy.battleManager;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("battling", bm.isBattling());
        if (!bm.isBattling()) {
            return result;
        }
        if (refreshBag) {
            requestBag();
        }

        BattleMode mode = bm.getMode();
        Map<String, Object> blocked = blocked(bm, CHOOSING, ENFORCED);
        result.put("mode", mode == null ? null : mode.name());
        result.put("canAct", blocked == null);
        if (blocked != null) {
            result.put("blockedBy", blocked.get("reason"));
        }

        PixelmonClientData current = current(bm);
        if (current != null) {
            Map<String, Object> pokemon = new LinkedHashMap<>();
            pokemon.put("uuid", current.pokemonUUID.toString());
            pokemon.put("species", current.species == null ? null : current.species.getName());
            pokemon.put("position", bm.currentPokemon);
            result.put("current", pokemon);
            result.put("moves", moves(current));

            Map<String, Object> gimmicks = new LinkedHashMap<>();
            gimmicks.put("mega", bm.canMegaEvolve(current));
            gimmicks.put("ultraBurst", bm.canUltraBurst(current));
            gimmicks.put("dynamax", bm.canDynamax(current));
            gimmicks.put("terastallize", bm.canTerastallize());
            gimmicks.put("zMove", bm.canUseZMove(current));
            result.put("gimmicks", gimmicks);
        }

        List<String> waitingFor = new ArrayList<>();
        for (UUID uuid : bm.pokemonToChoose) {
            waitingFor.add(uuid.toString());
        }
        result.put("waitingFor", waitingFor);
        result.put("canSwitch", bm.canSwitch);
        result.put("canFlee", bm.canFlee);
        result.put("canForfeit", bm.canForfeit());
        result.put("canCatch", bm.canCatchOpponent());
        result.put("team", team(bm));
        result.put("enemies", field(bm.displayedEnemyPokemon));
        result.put("allies", field(bm.displayedAllyPokemon));
        result.put("bag", bag(bm));
        result.put("prompts", prompts(bm));
        return result;
    }

    public static Map<String, Object> move(Map<String, Object> args) {
        ClientBattleManager bm = ClientProxy.battleManager;
        Map<String, Object> blocked = blocked(bm, CHOOSING);
        if (blocked != null) {
            return blocked;
        }
        PixelmonClientData pokemon = current(bm);
        BattleScreen screen = (BattleScreen) Minecraft.getInstance().screen;
        String requested = ToolArgs.string(args, "move");
        if (requested == null) {
            return Tools.status("rejected", "reason", "move must be a move slot (0-3), a move name or struggle",
                    "moves", moves(pokemon));
        }

        if (requested.equalsIgnoreCase("struggle")) {
            queue(bm, new StruggleBattleAction(pokemon.pokemonUUID, copy(bm.targetted)));
            bm.selectedMove();
            return chosen(bm, "Struggle", -1, null);
        }

        int slot = moveSlot(pokemon, requested);
        if (slot < 0) {
            return Tools.status("rejected", "reason", "unknown move", "move", requested, "moves", moves(pokemon));
        }
        Attack attack = pokemon.moveset.get(slot);
        String gimmick = ToolArgs.string(args, "gimmick");
        if (gimmick == null && (attack.getPP() <= 0 || attack.getDisabled())) {
            return Tools.status("rejected", "reason", "move has no PP or is disabled; use struggle if nothing is "
                    + "usable", "move", attack.getMove().getAttackName());
        }

        bm.megaEvolving = false;
        bm.dynamaxing = false;
        bm.terastallizing = false;
        bm.showZMoves = false;
        if (gimmick != null) {
            String refused = applyGimmick(bm, pokemon, gimmick);
            if (refused != null) {
                return Tools.status("rejected", "reason", refused, "gimmick", gimmick);
            }
        }

        if (screen.canSelectTarget(attack)) {
            Integer ally = ToolArgs.integer(args, "allyTarget");
            Integer target = ToolArgs.integer(args, "target");
            boolean valid;
            if (ally != null) {
                valid = ally >= 0 && ally < bm.targetted[0].length;
                if (valid) {
                    screen.setTargeting(pokemon, attack, -1, ally);
                    valid = bm.targetted[0][ally];
                }
            } else {
                int enemy = target == null ? firstStanding(bm.displayedEnemyPokemon) : target;
                valid = enemy >= 0 && enemy < bm.targetted[1].length;
                if (valid) {
                    screen.setTargeting(pokemon, attack, enemy, -1);
                    valid = bm.targetted[1][enemy];
                }
            }
            if (!valid) {
                clearGimmicks(bm);
                return Tools.status("rejected", "reason", "that target cannot be hit by this move",
                        "target", target, "allyTarget", ally, "enemies", field(bm.displayedEnemyPokemon));
            }
        } else {
            screen.setTargeting(pokemon, attack, -1, -1);
        }

        int buttonId = slot + (bm.showZMoves ? 4 : 0);
        bm.selectedAttack = buttonId;
        queue(bm, new AttackBattleAction(pokemon.pokemonUUID, copy(bm.targetted), buttonId, bm.megaEvolving,
                bm.dynamaxing, bm.terastallizing));
        if (!bm.usedZMove) {
            bm.usedZMove = bm.showZMoves;
        }
        bm.selectedMove();
        return chosen(bm, attack.getMove().getAttackName(), slot, gimmick);
    }

    public static Map<String, Object> switchPokemon(Map<String, Object> args) {
        ClientBattleManager bm = ClientProxy.battleManager;
        Map<String, Object> blocked = blocked(bm, CHOOSING, ENFORCED);
        if (blocked != null) {
            return blocked;
        }
        PixelmonClientData target = teamMember(bm, args, null);
        if (target == null) {
            return Tools.status("rejected", "reason", "pass pokemon as a team index, UUID or species",
                    "team", team(bm));
        }
        boolean fainted = target.health.get() <= 0;
        boolean active = isActive(bm, target.pokemonUUID);
        BattleMode mode = bm.getMode();

        if (mode == BattleMode.ENFORCED_REVIVE) {
            if (!fainted) {
                return Tools.status("rejected", "reason", "Revival Blessing needs a fainted Pokémon");
            }
            NetworkHelper.sendToServer(new EnforcedRevivePacket(target.pokemonUUID));
            bm.selectedMove();
            return Tools.status("reviving", "pokemon", name(target));
        }
        if (fainted || active || target.isSwitchingIn) {
            return Tools.status("rejected", "reason", fainted ? "fainted" : "already in battle",
                    "pokemon", name(target));
        }
        if (mode == BattleMode.ENFORCED_SWITCH) {
            queue(bm, new SwitchBattleAction(target.pokemonUUID, bm.teamPokemon[bm.currentPokemon], true));
            bm.selectedMove(true);
            return Tools.status("switching", "pokemon", name(target), "sent", true);
        }
        if (!bm.canSwitch) {
            return Tools.status("rejected", "reason", "the current Pokémon cannot switch out");
        }
        PixelmonClientData pokemon = current(bm);
        target.isSwitchingIn = true;
        queue(bm, new SwitchBattleAction(target.pokemonUUID, pokemon.pokemonUUID, false));
        bm.selectedMove();
        return chosen(bm, "switch to " + name(target), -1, null);
    }

    public static Map<String, Object> useItem(Map<String, Object> args) {
        ClientBattleManager bm = ClientProxy.battleManager;
        Map<String, Object> blocked = blocked(bm, CHOOSING);
        if (blocked != null) {
            return blocked;
        }
        String id = ToolArgs.string(args, "item");
        if (id == null) {
            return Tools.status("rejected", "reason", "pass item, e.g. pixelmon:potion", "bag", bag(bm));
        }
        ItemStack stack = findItem(bm, ToolArgs.id(id), false);
        if (stack == null) {
            return missingItem(bm, id);
        }

        PixelmonClientData pokemon = current(bm);
        Integer moveIndex = ToolArgs.integer(args, "move");
        if (moveIndex != null) {
            queue(bm, new UseBagItemBattleAction(pokemon.pokemonUUID, stack.copy(), moveIndex));
            bm.selectedMove();
            return chosen(bm, "use " + id + " on move " + moveIndex, -1, null);
        }
        PixelmonClientData target = teamMember(bm, args, pokemon);
        if (target == null) {
            return Tools.status("rejected", "reason", "unknown target Pokémon", "team", team(bm));
        }
        queue(bm, new UseBagItemBattleAction(pokemon.pokemonUUID, target.pokemonUUID, stack.copy()));
        bm.selectedMove();
        return chosen(bm, "use " + id + " on " + name(target), -1, null);
    }

    public static Map<String, Object> catchPokemon(Map<String, Object> args) {
        ClientBattleManager bm = ClientProxy.battleManager;
        Map<String, Object> blocked = blocked(bm, CHOOSING);
        if (blocked != null) {
            return blocked;
        }
        if (!bm.canCatchOpponent()) {
            return Tools.status("rejected", "reason", "the opponent cannot be caught");
        }
        int standing = 0;
        if (bm.displayedEnemyPokemon != null) {
            for (PixelmonClientData enemy : bm.displayedEnemyPokemon) {
                if (enemy != null && enemy.health.get() > 0) {
                    standing++;
                }
            }
        }
        if (standing != 1) {
            return Tools.status("rejected", "reason", "a ball can only be thrown when exactly one opponent is left",
                    "standing", standing);
        }

        String id = ToolArgs.string(args, "ball");
        String ball = id == null ? "pixelmon:poke_ball" : id;
        ItemStack stack = findItem(bm, ToolArgs.id(ball), true);
        if (stack == null) {
            return missingItem(bm, ball);
        }
        bm.selectedActions.clear();
        NetworkHelper.sendToServer(new BattleActionPacket(bm.battleControllerIndex,
                new UseBagItemBattleAction(current(bm).pokemonUUID, stack.copy(), 0)));
        bm.setMode(BattleMode.WAITING);
        return Tools.status("throwing", "ball", ball);
    }

    public static Map<String, Object> run() {
        ClientBattleManager bm = ClientProxy.battleManager;
        Map<String, Object> blocked = blocked(bm, CHOOSING, EnumSet.of(BattleMode.ENFORCED_SWITCH));
        if (blocked != null) {
            return blocked;
        }
        if (bm.getMode() == BattleMode.ENFORCED_SWITCH && (bm.enforcedFleeFailed || bm.preventFleeOnSwitch)) {
            return Tools.status("rejected", "reason", "running is not allowed during this switch");
        }
        UUID fleeing = bm.getMode() == BattleMode.ENFORCED_SWITCH ? bm.teamPokemon[bm.currentPokemon]
                : current(bm).pokemonUUID;
        boolean forfeit = bm.canForfeit();
        bm.selectedActions.clear();
        NetworkHelper.sendToServer(new FleePacket(fleeing));
        bm.setMode(BattleMode.WAITING);
        return Tools.status(forfeit ? "forfeiting" : "fleeing");
    }

    public static Map<String, Object> learnMove(Map<String, Object> args) {
        ClientBattleManager bm = ClientProxy.battleManager;
        if (bm.newAttackList.isEmpty()) {
            return Tools.status("rejected", "reason", "no move is waiting to be learned");
        }
        Integer replace = ToolArgs.integer(args, "replace");
        if (replace == null || replace < -1 || replace > 3) {
            return Tools.status("rejected", "reason", "replace must be the move slot to forget (0-3) or -1 to "
                    + "not learn it", "prompt", prompts(bm));
        }

        AttackData pending = bm.newAttackList.remove(0);
        String move = pending.attack.getMove().getAttackName();
        NetworkHelper.sendToServer(new ReplaceMovePacket(pending.pokemonUUID, move, replace, pending.checkEvo));
        if (Minecraft.getInstance().screen instanceof BattleScreen) {
            if (bm.hasNewAttacks() || bm.hasLevelUps()) {
                if (bm.yesNoOrigin != null) {
                    bm.setMode(bm.yesNoOrigin);
                }
            } else if (bm.battleControllerIndex != -1 && !bm.battleEnded) {
                bm.setMode(BattleMode.MAIN_MENU);
                bm.checkClearedMessages();
            }
        }
        return Tools.status(replace == -1 ? "skipped" : "learned", "move", move, "replaced", replace,
                "remaining", bm.newAttackList.size());
    }

    public static Map<String, Object> dismissLevelUps() {
        ClientBattleManager bm = ClientProxy.battleManager;
        int dismissed = bm.levelUpList.size();
        bm.levelUpList.clear();
        if (bm.getMode() == BattleMode.LEVEL_UP && Minecraft.getInstance().screen instanceof BattleScreen) {
            bm.setMode(bm.getOldMode());
            bm.checkClearedMessages();
        }
        return Tools.status("dismissed", "count", dismissed);
    }

    private static Map<String, Object> blocked(ClientBattleManager bm, Set<BattleMode> allowed) {
        return blocked(bm, allowed, EnumSet.noneOf(BattleMode.class));
    }

    private static Map<String, Object> blocked(ClientBattleManager bm, Set<BattleMode> choosing,
                                               Set<BattleMode> enforced) {
        if (!bm.isBattling()) {
            return Tools.status("not_in_battle");
        }
        BattleMode mode = bm.getMode();
        String modeName = mode == null ? null : mode.name();
        if (!(Minecraft.getInstance().screen instanceof BattleScreen)) {
            return Tools.status("not_ready", "reason", "the battle screen is not open", "mode", modeName);
        }
        if (bm.hasMoreMessages()) {
            return Tools.status("not_ready", "reason", "battle messages are still playing", "mode", modeName);
        }
        if (enforced.contains(mode)) {
            return null;
        }
        if (!choosing.contains(mode)) {
            return Tools.status("not_ready", "reason", "not waiting for a choice", "mode", modeName);
        }
        PixelmonClientData pokemon = current(bm);
        if (pokemon == null || !bm.pokemonToChoose.contains(pokemon.pokemonUUID)) {
            return Tools.status("not_ready", "reason", "no Pokémon is waiting for a choice", "mode", modeName);
        }
        return null;
    }

    private static PixelmonClientData current(ClientBattleManager bm) {
        if (bm.teamPokemon == null || bm.currentPokemon < 0 || bm.currentPokemon >= bm.teamPokemon.length) {
            return null;
        }
        return bm.getCurrentPokemon();
    }

    private static void queue(ClientBattleManager bm, BattleAction action) {
        bm.selectedActions.add(new BattleActionPacket(bm.battleControllerIndex, action));
    }

    private static Map<String, Object> chosen(ClientBattleManager bm, String choice, int slot, String gimmick) {
        Map<String, Object> result = Tools.status("chosen", "choice", choice);
        if (slot >= 0) {
            result.put("slot", slot);
        }
        if (gimmick != null) {
            result.put("gimmick", gimmick);
        }
        boolean sent = bm.getMode() == BattleMode.WAITING;
        result.put("sent", sent);
        if (!sent) {
            PixelmonClientData next = current(bm);
            result.put("next", next == null ? null : name(next));
        }
        return result;
    }

    private static String applyGimmick(ClientBattleManager bm, PixelmonClientData pokemon, String gimmick) {
        switch (gimmick.toLowerCase()) {
            case "mega" -> {
                if (!bm.canMegaEvolve(pokemon)) {
                    return "this Pokémon cannot Mega Evolve now";
                }
                bm.megaEvolving = true;
            }
            case "ultra_burst", "ultraburst" -> {
                if (!bm.canUltraBurst(pokemon)) {
                    return "this Pokémon cannot Ultra Burst now";
                }
                bm.megaEvolving = true;
            }
            case "dynamax" -> {
                if (!bm.canDynamax(pokemon)) {
                    return "this Pokémon cannot Dynamax now";
                }
                bm.dynamaxing = true;
            }
            case "terastallize", "tera" -> {
                if (!bm.canTerastallize()) {
                    return "Terastallization is not available now";
                }
                bm.terastallizing = true;
            }
            case "zmove", "z_move", "z" -> {
                if (!bm.canUseZMove(pokemon)) {
                    return "no Z-Move is available now";
                }
                bm.showZMoves = true;
            }
            default -> {
                return "gimmick must be mega, ultra_burst, dynamax, terastallize or zmove";
            }
        }
        return null;
    }

    private static void clearGimmicks(ClientBattleManager bm) {
        bm.megaEvolving = false;
        bm.dynamaxing = false;
        bm.terastallizing = false;
        bm.showZMoves = false;
    }

    private static int moveSlot(PixelmonClientData pokemon, String requested) {
        try {
            int slot = Integer.parseInt(requested);
            return slot >= 0 && slot < pokemon.moveset.size() && pokemon.moveset.get(slot) != null ? slot : -1;
        } catch (NumberFormatException ignored) {
        }
        String wanted = normalise(requested);
        for (int i = 0; i < pokemon.moveset.size(); i++) {
            Attack attack = pokemon.moveset.get(i);
            if (attack != null && normalise(attack.getMove().getAttackName()).equals(wanted)) {
                return i;
            }
        }
        return -1;
    }

    private static String normalise(String text) {
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

    private static boolean[][] copy(boolean[][] targets) {
        if (targets == null) {
            return new boolean[0][0];
        }
        boolean[][] result = new boolean[targets.length][];
        for (int i = 0; i < targets.length; i++) {
            result[i] = targets[i] == null ? new boolean[0] : targets[i].clone();
        }
        return result;
    }

    private static int firstStanding(PixelmonClientData[] pokemon) {
        if (pokemon == null) {
            return -1;
        }
        for (int i = 0; i < pokemon.length; i++) {
            if (pokemon[i] != null && pokemon[i].health.get() > 0) {
                return i;
            }
        }
        return pokemon.length > 0 ? 0 : -1;
    }

    private static boolean isActive(ClientBattleManager bm, UUID uuid) {
        if (bm.teamPokemon == null) {
            return false;
        }
        for (UUID active : bm.teamPokemon) {
            if (uuid.equals(active)) {
                return true;
            }
        }
        return false;
    }

    private static PixelmonClientData teamMember(ClientBattleManager bm, Map<String, Object> args,
                                                 PixelmonClientData fallback) {
        Object value = args.get("pokemon");
        if (value == null) {
            return fallback;
        }
        List<PixelmonClientData> team = bm.fullOurPokemon;
        if (value instanceof Number number) {
            int index = number.intValue();
            return index >= 0 && index < team.size() ? team.get(index) : null;
        }
        String text = String.valueOf(value).trim();
        try {
            int index = Integer.parseInt(text);
            return index >= 0 && index < team.size() ? team.get(index) : null;
        } catch (NumberFormatException ignored) {
        }
        String wanted = normalise(text);
        for (PixelmonClientData member : team) {
            if (member.pokemonUUID.toString().equalsIgnoreCase(text)
                    || (member.species != null && normalise(member.species.getName()).equals(wanted))) {
                return member;
            }
        }
        return null;
    }

    private static String name(PixelmonClientData pokemon) {
        if (pokemon.nickname != null) {
            return pokemon.nickname.getString();
        }
        return pokemon.species == null ? String.valueOf(pokemon.pokemonUUID) : pokemon.species.getName();
    }

    private static List<Map<String, Object>> moves(PixelmonClientData pokemon) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (pokemon == null || pokemon.moveset == null) {
            return result;
        }
        for (int i = 0; i < pokemon.moveset.size(); i++) {
            Attack attack = pokemon.moveset.get(i);
            if (attack == null || attack.getMove() == null) {
                continue;
            }
            Map<String, Object> move = new LinkedHashMap<>();
            move.put("slot", i);
            move.put("name", attack.getMove().getAttackName());
            move.put("pp", attack.getPP());
            move.put("maxPp", attack.getMaxPP());
            move.put("disabled", attack.getDisabled());
            result.add(move);
        }
        return result;
    }

    private static List<Map<String, Object>> team(ClientBattleManager bm) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (bm.fullOurPokemon == null) {
            return result;
        }
        for (int i = 0; i < bm.fullOurPokemon.size(); i++) {
            PixelmonClientData member = bm.fullOurPokemon.get(i);
            Map<String, Object> entry = summary(member);
            entry.put("index", i);
            entry.put("active", isActive(bm, member.pokemonUUID));
            entry.put("fainted", member.health.get() <= 0);
            result.add(entry);
        }
        return result;
    }

    private static List<Map<String, Object>> field(PixelmonClientData[] pokemon) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (pokemon == null) {
            return result;
        }
        for (int i = 0; i < pokemon.length; i++) {
            if (pokemon[i] == null) {
                continue;
            }
            Map<String, Object> entry = summary(pokemon[i]);
            entry.put("index", i);
            result.add(entry);
        }
        return result;
    }

    private static Map<String, Object> summary(PixelmonClientData pokemon) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("uuid", pokemon.pokemonUUID == null ? null : pokemon.pokemonUUID.toString());
        entry.put("species", pokemon.species == null ? null : pokemon.species.getName());
        entry.put("name", name(pokemon));
        entry.put("level", pokemon.level);
        entry.put("health", pokemon.health.get());
        entry.put("maxHealth", pokemon.maxHealth);
        entry.put("status", PokemonSummaries.status(StatusType.getEffect(pokemon.status)));
        return entry;
    }

    private static Map<String, Object> bag(ClientBattleManager bm) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (BagSection section : BagSection.values()) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (ItemData data : bm.getBagItems(section)) {
                ItemStack stack = data.itemStack();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                item.put("count", stack.getCount());
                items.add(item);
            }
            result.put(section.name(), items);
        }
        return result;
    }

    private static Map<String, Object> prompts(ClientBattleManager bm) {
        List<Map<String, Object>> newMoves = new ArrayList<>();
        for (AttackData data : bm.newAttackList) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("uuid", data.pokemonUUID.toString());
            entry.put("move", data.attack.getMove().getAttackName());
            newMoves.add(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("levelUps", bm.levelUpList.size());
        result.put("newMoves", newMoves);
        result.put("evolutions", bm.evolveList.size());
        return result;
    }

    private static ItemStack findItem(ClientBattleManager bm, ResourceLocation id, boolean balls) {
        for (BagSection section : BagSection.values()) {
            if ((section == BagSection.POKEBALLS) != balls) {
                continue;
            }
            for (ItemData data : bm.getBagItems(section)) {
                if (id.equals(BuiltInRegistries.ITEM.getKey(data.itemStack().getItem()))) {
                    return data.itemStack();
                }
            }
        }
        return null;
    }

    private static Map<String, Object> missingItem(ClientBattleManager bm, String id) {
        boolean empty = true;
        for (BagSection section : BagSection.values()) {
            if (!bm.getBagItems(section).isEmpty()) {
                empty = false;
            }
        }
        if (empty) {
            requestBag();
            return Tools.status("bag_loading", "reason", "the bag was not loaded yet; it has been requested, retry");
        }
        return Tools.status("rejected", "reason", "not in the bag", "item", id, "bag", bag(bm));
    }

    private static void requestBag() {
        for (BagSection section : BagSection.values()) {
            NetworkHelper.sendToServer(new RequestBagItemsPacket(section));
        }
    }
}
