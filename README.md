# Pixelmon MCP

A small client-side NeoForge 1.21.1 mod that adds Pixelmon tools to
[minecraft-mcp](https://github.com/EnvyWare/minecraft-mcp), the mod that exposes a running Minecraft client to AI agents
over MCP. With both installed, an agent can set up a party, start NPC, wild or horde battles, and compare what the battle
UI shows with what the server knows, without scripting menus or NPC dialogue.

The tools register through minecraft-mcp's [tool provider SPI](https://github.com/EnvyWare/minecraft-mcp/blob/main/docs/tool-provider-spi.md).
Setup tools act on the integrated server, so they work in singleplayer only.

## Requirements

- Minecraft 1.21.1, NeoForge 21.1.200+
- Pixelmon 9.4.x (built against 9.4.1)
- minecraft-mcp 0.1.0-envy.2+ (`com.envyware.minecraftmcp:minecraft_mcp-neoforge` on `https://maven.envyware.co.uk/releases`)

The mod is client-only and is meant for dev and test clients, not for players.

## Using it in another project

Either drop both jars into the game directory's `mods/` folder, or add them as dev-only runtime dependencies so they
load in your dev client but never end up in your published jar or POM. With ModDevGradle:

```groovy
repositories {
    maven { url = 'https://maven.envyware.co.uk/releases' }
}

configurations {
    runtimeClasspath.extendsFrom localRuntime
}

dependencies {
    localRuntime 'com.envyware.minecraftmcp:minecraft_mcp-neoforge:0.1.0-envy.2'
    localRuntime 'com.envyware.pixelmonmcp:pixelmon_mcp:1.0.0'
}
```

Run the client in minecraft-mcp's [background mode](https://github.com/EnvyWare/minecraft-mcp/blob/main/docs/headless.md#background-mode-windowsmacoslinux-desktop)
(`-DminecraftMcp.background=true`, and `earlyWindowControl = false` in `config/fml.toml`) so it never takes over your
screen.

If Pixelmon's internals have drifted from 9.4.1 (for example in a Pixelmon dev workspace), a tool that hits a missing
method or field returns `{"status": "unsupported", "tool": ..., "reason": ..., "pixelmonVersion": ...}` instead of
failing. `pixelmon.mcp.info` reports the versions in play.

## Connecting an agent

Agents connect through the minecraft-mcp [stdio bridge](https://github.com/EnvyWare/minecraft-mcp/blob/main/docs/bridge.md),
which stays up while the client is launched, stopped and relaunched. For Claude Code, `.mcp.json` at the project root:

```json
{
  "mcpServers": {
    "minecraft": {
      "command": "java",
      "args": ["-jar", "runs/mcp-bridge.jar", "--game-dir", "runs/client",
               "--launch-command", "gradlew.bat runClient --no-daemon", "--launch-cwd", "."]
    }
  }
}
```

Keep `--no-daemon` in the launch command: `client.stop` kills the launched process tree, so a Gradle daemon started by
the launch would be killed with it. On Linux and macOS use `./gradlew runClient --no-daemon`.

In this repository, `./gradlew prepareClientRun` (also run by `runClient`) copies the bridge to `runs/mcp-bridge.jar`,
sets `earlyWindowControl = false` in `runs/client/config/fml.toml` and writes a 1600x900, GUI scale 2 `options.txt` if
there is none. The `client` run starts in background mode with the endpoint on port 25599.

## Workflow

1. `client.launch`. It compiles first and takes a few minutes, so batch code changes before relaunching.
2. `mc.world.join` with `{"name": "agent", "preset": "flat"}` creates or opens a flat creative world with cheats. It
   returns immediately; poll `mc.client.state` until `inWorld` is true (about a minute for a new world). A fresh
   player sees the starter picker first, which keeps `inWorld` false: see the gotchas.
3. Set up state with `pixelmon.party.set` and `pixelmon.battle.start`, or chat commands through `mc.command.run`.
4. Drive the UI with `mc.screen.state`, `mc.screen.widget.click`, `mc.screen.click.at` and `mc.keyboard.press`.
5. Check with `pixelmon.*.state`, `mc.screenshot.take` (saved under `runs/client/screenshots/`) and `client.logs`.
6. `client.stop` when done.

`mc.window.show` makes the hidden window visible without taking focus; `mc.window.hide` hands it back to the agent.

## Tools

Pokémon are always given as Pokémon specs, the same format as Pixelmon commands (`"pikachu lvl:50 palette:shiny"`,
`"charizard helditem:charizardite_x"`). Resource ids default to the `pixelmon` namespace.

### General

| Tool | Arguments | What it does |
|---|---|---|
| `pixelmon.mcp.info` | | Versions of Pixelmon MCP, the loaded Pixelmon and minecraft-mcp, and the versions Pixelmon MCP was compiled against |

### Party

| Tool | Arguments | What it does |
|---|---|---|
| `pixelmon.party.state` | | The party as the client sees it: species, form, level, HP, status, moves with PP, held item, whether each Pokémon is `out`, and the `selectedSlot` |
| `pixelmon.party.set` | `pokemon` (1 to 6 specs, e.g. `["charizard lvl:50", "lapras lvl:50"]`), `heal` (default `true`) | Replaces the party. Refused in battle. Also marks the starter as picked and closes the starter picker, which otherwise stalls battles for a fresh player |
| `pixelmon.party.heal` | | Heals the party. Refused in battle |
| `pixelmon.party.select` | `slot` (0-5) | Selects a party slot, like the up and down keys |
| `pixelmon.party.send_out` | `slot` (default the selected slot) | Throws the Pokémon out, like the send-out key. It lands a moment later: poll `pixelmon.party.state` for `out: true` |
| `pixelmon.party.retrieve` | `slot` (default every Pokémon that is out) | Recalls sent-out Pokémon |
| `pixelmon.party.gimmicks.state` | | Which gimmicks are unlocked (`mega`, `dynamax`, `terastallize`), the equipped key item, the items that can be selected, Tera energy and charm states |
| `pixelmon.party.gimmicks.unlock` | `mega`, `dynamax`, `terastallize` (`true` unlocks, `false` locks), `teraEnergy`, `infiniteTeraEnergy` | Unlocks or locks gimmicks. The first unlock equips a matching key item |
| `pixelmon.party.gimmicks.select` | `item`: `BraceletORAS`, `BoostNecklace`, `MegaGlasses`, `MegaAnchor`, `MegaTiara`, `DynamaxBand`, `TeraOrb` or `None` | Equips a key item. Its gimmick must be unlocked. Mega Stones are held items: give them in the spec |
| `pixelmon.party.charms.set` | `charms`: map of charm id to `ACTIVE`, `AVAILABLE` or `DISABLED`, e.g. `{"shiny": "ACTIVE"}` | Sets charm states (`shiny`, `oval`, `mark`, `catching`, `exp`) |

### PC

| Tool | Arguments | What it does |
|---|---|---|
| `pixelmon.pc.state` | `box` (default 0) | One box (page): name, wallpaper and Pokémon with their slots, plus the name and count of every box |
| `pixelmon.pc.set` | `box`; `pokemon` (up to 30 specs, `null` or `""` for an empty slot) replaces the box, or `slot` with one `pokemon` spec (`null` clears it) sets one slot; `name` renames the box | Writes to the PC |

### Pokédex

| Tool | Arguments | What it does |
|---|---|---|
| `pixelmon.pokedex.state` | `pokemon` (specs to look up) or a page with `offset` (default 0) and `limit` (default 30, max 200); `dex` (default `national_dex`) | Seen and caught counts and percentages, and entries with status (`UNKNOWN`, `SEEN`, `CAUGHT`) and how often each was seen and caught |
| `pixelmon.pokedex.set` | `pokemon` (specs) or `all: true`; `status` (`seen` or `caught`, default `caught`); `clear: true` | Registers entries. Pixelmon cannot remove single entries: `clear` wipes the whole Pokédex first |

### Research

| Tool | Arguments | What it does |
|---|---|---|
| `pixelmon.research.state` | `available` (`true` also lists every research id) | Active research with stage and objective progress, and completed research ids |
| `pixelmon.research.set` | `action` (`start`, `discard`, `complete`, `complete_stage`, `claim` or `clear`); `id` (e.g. `pixelmon:battle/gym/fire_1`) or `uuid` of an active research | Changes research. `claim` runs the current stage's rewards |

### Battles

| Tool | Arguments | What it does |
|---|---|---|
| `pixelmon.battle.start` | One of `npc` (NPC preset id, e.g. `pixelmon:battle/leader/gym/dragon_1`), `wild` (spec) or `horde` (list of specs). `rules` (rule set id, default AG), `ai` (default `pixelmon:advanced`), `canMega`, `canDynamax`, `canTerastallize` (NPC only, default `true`), `seed` (wild only), `distance` (blocks, default 3) | Spawns the opponents in front of the player and requests the battle. Horde battles ignore `rules` and `distance` |
| `pixelmon.battle.state` | | The client battle: mode, turn, weather, pending messages and tasks, and the Pokémon on display with the HP the UI shows |
| `pixelmon.battle.server_state` | | The server battle: turn and each participant's active Pokémon with real HP |
| `pixelmon.battle.end` | | Ends the battle and removes the opponents `pixelmon.battle.start` spawned |
| `pixelmon.battle.options` | `refreshBag` | What the player can do now: `canAct` (or `blockedBy`), the choosing Pokémon and its moves, available gimmicks, `canSwitch`, `canFlee`, `canCatch`, the team and enemies with indices, bag items and pending prompts |
| `pixelmon.battle.move` | `move` (slot 0-3, name or `struggle`); `target` (enemy index) or `allyTarget` (our-side index); `gimmick` (`mega`, `ultra_burst`, `dynamax`, `terastallize` or `zmove`) | Uses a move. Targets only matter for moves that need one in multi battles |
| `pixelmon.battle.switch` | `pokemon` (team index, UUID or species) | Switches. Also answers forced switches after a faint, and Revival Blessing (pass the fainted Pokémon) |
| `pixelmon.battle.item` | `item` (e.g. `pixelmon:potion`); `pokemon` (target, default the choosing Pokémon); `move` (slot, for Ethers) | Uses a bag item |
| `pixelmon.battle.catch` | `ball` (default `pixelmon:poke_ball`) | Throws a ball at the last standing wild opponent |
| `pixelmon.battle.run` | | Runs from a wild battle, or forfeits a trainer battle |
| `pixelmon.battle.learn_move` | `replace` (slot 0-3 to forget, or -1 to skip) | Answers the next "learn a new move" prompt |
| `pixelmon.battle.dismiss_level_ups` | | Dismisses level-up screens, which hold up the next turn |

Pixelmon 9.4.1 has no Terastallization option for NPC battles: `canTerastallize` on `pixelmon.battle.start` is accepted
but ignored, and the result says so. The `terastallize` gimmick on `pixelmon.battle.move` works.

### Film

| Tool | Arguments | What it does |
|---|---|---|
| `pixelmon.film.prepare` | `stopSpawning` (default `true`); `clear` (`pokemon`, `npcs`, `wormholes` or `all`, one or a list, default none); `radius` (blocks around the player for `clear`, default the whole level); `party` (1 to 6 specs); `heal` (default `true`) | Gets the world ready for filming at runtime, without editing configs or restarting. Sets the `pixelmonSpawning` gamerule to `false`, vetoes every spawn by Pixelmon's spawners (wild Pokémon, legendaries, wormholes and the NPCs that spawn around villages), and turns off legendary spawning and the legendary broadcast. Fishing, Headbutt, Rock Smash, Sweet Scent, curry and Pixelmon grass still work. `clear` removes wild Pokémon (not owned, not in battle), NPCs and wormholes. `party` works as in `pixelmon.party.set`. Reports what it `changed` and `removed`, and anything it could not change as `unsupported` |
| `pixelmon.film.restore` | | Puts back what `pixelmon.film.prepare` changed: the gamerule, the spawner veto, the legendary settings and the party. Removed entities stay removed |

### Staging

Positions are `[x, y, z]`. With `origin` (`"spawn"`, `"player"` or `[x, y, z]`), `pos` and `lookAt` are relative to it.

| Tool | Arguments | What it does |
|---|---|---|
| `pixelmon.stage.pokemon` | `pokemon` (spec); `pos` (default `distance` blocks in front of the player, on the ground); `distance` (default 4); `ground` (`true` moves `pos` to the surface); `yaw` (degrees) or `lookAt` (default the player); `origin`; `frozen` (default `true`); `animation` (e.g. `special`) | Spawns a Pokémon for a shot. Frozen means no AI, no gravity, no despawn and invulnerable, so it stays where it was put. Returns `id`, `uuid`, `pos`, `yaw` and `species` |
| `pixelmon.stage.npc` | `npc` (NPC preset id, e.g. `pixelmon:battle/leader/gym/dragon_1`); `name`; `pos`, `distance`, `ground`, `yaw`, `lookAt`, `origin`, `frozen` as above | Spawns an NPC from a preset. Frozen NPCs are also made immovable. Returns `id`, `uuid`, `pos`, `yaw`, `npc` and `name` |
| `pixelmon.stage.clear` | `uuid` (one or a list, default every staged entity) | Removes staged Pokémon and NPCs |

Staged entities are kept by `pixelmon.film.prepare` clears.

## Playing a battle

The battle tools send the same packets as the battle screen and update its state the same way, so the UI and the server
stay in step. A turn looks like this:

1. Poll `pixelmon.battle.options` until `canAct` is true. Until then it reports `blockedBy`: messages still playing, an
   animation, or the opponent choosing.
2. Choose with `pixelmon.battle.move`, `switch`, `item`, `catch` or `run`. In doubles and triples each Pokémon chooses in
   turn: the result has `sent: false` and the `next` Pokémon until the last choice sends them all.
3. After a faint the mode is `ENFORCED_SWITCH`: answer with `pixelmon.battle.switch`.
4. Compare `pixelmon.battle.state` with `pixelmon.battle.server_state` to check what the UI shows.

The bag comes from the player's inventory (give items with `mc.command.run`, e.g. `/give @s pixelmon:potion 5`). If
`pixelmon.battle.options` shows an empty bag, pass `refreshBag: true` and read it again.

To drive the real UI instead, click with `mc.screen.click.at` in GUI coordinates, which are screenshot pixels divided by
the GUI scale. At 1600x900 and GUI scale 2, the four move buttons of a single battle are centred around (486, 386),
(640, 386), (486, 424) and (640, 424); they are already shown in `MAIN_MENU`. `turn` counts from 0, while the UI shows
"Turn 1" for the first turn.

## Filming

The film tools fit minecraft-mcp's [film tools](https://github.com/EnvyWare/minecraft-mcp/blob/main/docs/film-tools.md)
(`mc.film.run`, `mc.camera.play`, `mc.hud.set`, in minecraft-mcp 0.1.0-envy.3 and later). A scene's `setup.steps` can
call them:

```json
"setup": {
  "world": {"name": "film_pixelmon", "seed": 424242},
  "steps": [
    {"tool": "pixelmon.film.prepare", "args": {"party": ["pikachu lvl:50"], "clear": "all", "radius": 160}},
    {"tool": "pixelmon.stage.pokemon",
     "args": {"pokemon": "charizard palette:shiny", "origin": "spawn", "pos": [4, 0, 6], "ground": true, "lookAt": [0, 2, 0]}},
    {"tool": "pixelmon.stage.npc",
     "args": {"npc": "pixelmon:battle/leader/gym/dragon_1", "origin": "spawn", "pos": [-3, 0, 6], "ground": true, "lookAt": [4, 0, 6]}}
  ]
}
```

- `"origin": "spawn"` on the staging tools matches a scene's `"origin": "spawn"`, so staged entities and camera paths
  share coordinates.
- Use a staged entity's `uuid` as a shot's `target` (`{"uuid": "..."}`) to orbit or follow it.
- Hide the HUD with `mc.hud.set`; shots and recordings hide it by default. Pixelmon's party sidebar and other overlays
  are hidden with it.
- Call `pixelmon.film.restore` when done. The `pixelmonSpawning` gamerule is saved with the world, so a world left
  while prepared keeps Pixelmon spawning off until a restore or `/gamerule pixelmonSpawning true`.

## Gotchas

- A fresh player gets Pixelmon's starter picker. While it is open, `mc.client.state` reports `inWorld: false` and
  `rawInWorld: true`; poll for either, then call `pixelmon.party.set`, which closes the picker.
- `pixelmon.battle.start` returns as soon as the battle is requested; NPC battles start about half a second later. Poll
  `pixelmon.battle.state` until `battling` is true.
- After `pixelmon.battle.end`, heal only once `pixelmon.battle.state` reports the battle is over. The end-of-battle
  write-back runs later and would undo an earlier heal.
- Comparing `pixelmon.battle.state` with `pixelmon.battle.server_state` shows client/server desyncs, such as HP that
  never updates.
- Rule sets that cap levels (arena presets cap the player at 25) apply to `pixelmon.battle.start` as they do in game.
- The world is creative and peaceful. Use `mc.command.run` for anything else (`/gamemode survival`, `/time set day`).
- `pixelmon.research.set` with `claim` runs the rewards, which can open a professor dialogue screen. A battle does not
  start while another screen is open: check `mc.client.state` and close it with `mc.keyboard.press` `{"key": "escape"}`.
- A sent-out Pokémon's ball flies where the player looks and can hit something in the way, such as another sent-out
  Pokémon. Check `out` in `pixelmon.party.state` rather than assuming it landed.
- Pixelmon's client picks a Pokémon's animation from how it moves, so `animation` on `pixelmon.stage.pokemon` only
  holds for `special` on a Pokémon standing on the ground. A frozen Pokémon placed in the air plays `fly`.
- Only stop clients you launched. A developer may be running their own Minecraft clients next to the agent's.

## Building

```bash
./gradlew build                  # build/libs/pixelmon_mcp-<version>.jar
./gradlew publishToMavenLocal    # com.envyware.pixelmonmcp:pixelmon_mcp:<version>
./gradlew publish                # EnvyWare Maven, needs envywareUsername and envywarePassword
```

The published POM has no dependencies: Pixelmon and minecraft-mcp are provided by the game.

## License

MIT
