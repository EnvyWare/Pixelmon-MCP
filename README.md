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

| Tool | Arguments | What it does |
|---|---|---|
| `pixelmon.mcp.info` | | Versions of Pixelmon MCP, the loaded Pixelmon and minecraft-mcp, and the versions Pixelmon MCP was compiled against |
| `pixelmon.party.state` | | The party as the client sees it: species, form, level, HP, status, moves with PP, held item |
| `pixelmon.party.set` | `pokemon` (1 to 6 Pokémon specs, e.g. `["charizard lvl:50", "lapras lvl:50"]`), `heal` (default `true`) | Replaces the party. Refused in battle. Also marks the starter as picked and closes the starter picker, which otherwise stalls battles for a fresh player |
| `pixelmon.party.heal` | | Heals the party. Refused in battle |
| `pixelmon.battle.start` | One of `npc` (NPC preset id, e.g. `pixelmon:battle/leader/gym/dragon_1`), `wild` (Pokémon spec) or `horde` (list of specs). `rules` (rule set id, default AG), `ai` (default `pixelmon:advanced`), `canMega`, `canDynamax`, `canTerastallize` (NPC only, default `true`), `seed` (wild only), `distance` (blocks, default 3) | Spawns the opponents in front of the player and requests the battle. Horde battles ignore `rules` and `distance` |
| `pixelmon.battle.state` | | The client battle: mode, turn, weather, pending messages and tasks, and the Pokémon on display with the HP the UI shows |
| `pixelmon.battle.server_state` | | The server battle: turn and each participant's active Pokémon with real HP |
| `pixelmon.battle.end` | | Ends the battle and removes the opponents `pixelmon.battle.start` spawned |

Pixelmon 9.4.1 has no Terastallization option for NPC battles: `canTerastallize` is accepted but ignored, and the
result says so.

## Driving the battle UI

The battle screen draws its own controls, so `mc.screen.state` lists no widgets for it. Take a screenshot and click with
`mc.screen.click.at` in GUI coordinates, which are screenshot pixels divided by the GUI scale. At 1600x900 and GUI scale
2, the four move buttons of a single battle are centred around (486, 386), (640, 386), (486, 424) and (640, 424);
they are already shown in `MAIN_MENU`, so one click picks a move.
`pixelmon.battle.state` reports the current `mode` (`MAIN_MENU`, `CHOOSE_ATTACK`, `ENFORCED_SWITCH`, `WAITING`, ...);
check it after each click instead of assuming the click landed. `turn` counts from 0, while the UI shows
"Turn 1" for the first turn.

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
