# RayTrace Anti Entity ESP

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/raytraceantientityesp?logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/plugin/raytraceantientityesp)
[![Git Release](https://img.shields.io/gitea/v/release/BrightForever/RayTraceAntiEntityESP?gitea_url=https://codeberg.org&logo=codeberg&label=Release)](https://codeberg.org/BrightForever/RayTraceAntiEntityESP/releases)
[![License](https://img.shields.io/badge/license-GPL--3.0-orange?logo=codeberg&label=License)](https://codeberg.org/BrightForever/RayTraceAntiEntityESP/src/branch/main/LICENSE)
[![Last Commit](https://img.shields.io/gitea/last-commit/BrightForever/RayTraceAntiEntityESP?gitea_url=https://codeberg.org&logo=codeberg&label=Last%20Commit)](https://codeberg.org/BrightForever/RayTraceAntiEntityESP/commits/branch/main)
[![Stars](https://img.shields.io/gitea/stars/BrightForever/RayTraceAntiEntityESP?gitea_url=https://codeberg.org&logo=codeberg&label=Stars)](https://codeberg.org/BrightForever/RayTraceAntiEntityESP)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-brightgreen?label=Minecraft%20Version)]()
[![Paper](https://img.shields.io/badge/Paper-Supported-informational)](https://papermc.io/downloads/paper)
[![Purpur](https://img.shields.io/badge/Purpur-Supported-blueviolet)](https://purpurmc.org/download/purpur)
[![Folia](https://img.shields.io/badge/Folia-Supported-2ECC71)](https://papermc.io/downloads/folia)

A plugin that prevents HACKERS from seeing entities (e.g. Players) through walls.

---

## Demo

<details>
<summary>View</summary>

<img width="854" height="480" alt="demo" src="https://github.com/user-attachments/assets/8075d138-6952-4d2d-b9a0-969b742fed95"/>

</details>

---

## Installation

<details>
<summary>View</summary>

1. **Download** the latest `.jar` from [Download Link](https://modrinth.com/plugin/raytraceantientityesp/versions).
2. **Drop** the `.jar` into your server's `/plugins` folder.
3. **Restart** your server (do not use `/reload`).
4. **Edit** the generated [config.yml](https://codeberg.org/BrightForever/RayTraceAntiEntityESP/src/branch/main/plugin/src/main/resources/config.yml) in `plugins/RayTraceAntiEntityESP/config.yml` to your liking.
5. **Run** `/rtaee reload` in-game to apply config changes without restarting.

>**Requirements:** Paper, Purpur, or Folia ver **1.21.x** or **26.x**.
>Do not enable, disable or reload this plugin using plugin managers or `/reload`. It will not work properly and may cause issues.

</details>

---

## Configuration

<details>
<summary>View</summary>

```yaml
config_version: 6
checking:
  enabled: true
  period_ticks: 1
  stagger_groups: 6
  distance_override: 10
  bounding_box_extra_value: 0
  vertices_layers: 3
async:
  enabled: true
  threads: 2
  chunk_snapshot_ttl_ticks: 200
perspective_checking:
  enabled: true
  distances_from_head: 4
display_name:
  enabled: true
  period_ticks: 1
  lookahead_ticks: 3
  offset_y: 0
update_checker:
  enabled: true
debug:
  enabled: false
anti_entities:
  - player
anti_mode: whitelist
blacklisted_world: []
```

### checking

| Option | Range | Description |
|---|---|---|
| `enabled` | true / false | Turns the whole ESP check on or off |
| `period_ticks` | 1 - 1200 | Ticks between checks (1 tick = 50ms). Keep it at 1 when using `stagger_groups` |
| `stagger_groups` | 1 - 64 | Splits entities into N groups and checks one group per tick. Higher means less CPU, but hidden entities take longer to update |
| `distance_override` | 0 - 256 | Entities closer than this many blocks are always shown. 0 disables it |
| `bounding_box_extra_value` | 0 - 4 | Extra size added to the hitbox. Currently only used by the debug markers |
| `vertices_layers` | 2 - 7 | Sample layers per entity. Currently only used by the debug markers |

### async

| Option | Range | Description |
|---|---|---|
| `enabled` | true / false | Runs the raycasts off the main thread using chunk snapshots |
| `threads` | 1 - 16 | Worker threads for the raycasts |
| `chunk_snapshot_ttl_ticks` | 1 - 72000 | Ticks a chunk snapshot stays valid. Changing a block in the chunk refreshes it early |

### perspective_checking

| Option | Range | Description |
|---|---|---|
| `enabled` | true / false | Also checks from where a third person camera would be. Costs about 3x the raytraces. If disabled, a player in third person can see through walls the first person check would block |
| `distances_from_head` | 0 - 32 | How far behind the player the fake camera sits, in blocks |

### display_name

| Option | Range | Description |
|---|---|---|
| `enabled` | true / false | Shows a fake nametag over hidden entities so they do not just vanish |
| `period_ticks` | 1 - 200 | Ticks between nametag updates. Higher means less work per tick |
| `lookahead_ticks` | 0 - 20 | How many ticks ahead the nametag is moved, based on the entity's speed |
| `offset_y` | -10 - 10 | Moves the nametag up or down, in blocks |

### update_checker

| Option | Range | Description |
|---|---|---|
| `enabled` | true / false | Checks for a newer release once on startup and notifies admins when they join. Set to false if you do not want the plugin to touch the internet |

### debug

| Option | Range | Description |
|---|---|---|
| `enabled` | true / false | Draws markers at the raytrace points and forces every check to run on the main thread. Leave it off on a live server |

### Entity filtering

| Option | Description |
|---|---|
| `anti_entities` | Entity types that get protected, using names from the [EntityType](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/entity/EntityType.html) list. A misspelled name is ignored with a warning. Keep the list short, every extra type costs more raytraces |
| `anti_mode` | `whitelist` checks only the listed types, `blacklist` checks everything except the listed types. Anything else falls back to `whitelist` |
| `blacklisted_world` | Worlds where checking is skipped, based on the viewing player's current world. Empty means it runs everywhere |

`config_version` is managed by the plugin. When keys change it bumps the version and merges your old file into the new one, keeping a backup.

</details>

---

## Commands

<details>
<summary>View</summary>

All commands require OP or the `raytrace_anti_entity_esp.admin` permission. Alias: `/rtaee`

| Command | Description |
|---|---|
| `/rtaee reload` | Reload config from disk |
| `/rtaee config_value` | Print all current config values |
| `/rtaee checking <enabled\|period_ticks\|stagger_groups\|distance_override\|bounding_box_extra_value\|vertices_layers> <value>` | Checking options |
| `/rtaee async <enabled\|threads\|chunk_snapshot_ttl_ticks> <value>` | Async raycast options |
| `/rtaee perspective_checking <enabled\|distances_from_head> <value>` | Perspective options |
| `/rtaee display_name <enabled\|period_ticks\|offset_y\|lookahead_ticks> <value>` | Name tag options |
| `/rtaee debug enabled <true\|false>` | Toggle debug mode |
| `/rtaee anti_mode <whitelist\|blacklist>` | Switch filter mode |
| `/rtaee anti_entities <add\|remove\|list\|clear> [type]` | Edit entity list |
| `/rtaee exclude <add\|remove\|list\|clear> [player_name\|entity_uuid]` | Let everyone always see this player/entity, ESP-proof or not |
| `/rtaee bypass <add\|remove\|list\|clear> [player]` | Let a player always see everyone, walls or not |
| `/rtaee blacklisted_world <add\|remove\|list\|clear> [world]` | Worlds where ESP checking never runs |
| `/rtaee help` | Show help information |

Values are validated against the ranges listed in the configuration section. Changes made through commands are saved to `config.yml` and applied immediately.

</details>

---

## License

This project is licensed under the **GPL-3.0 License**. See the [LICENSE](https://codeberg.org/BrightForever/RayTraceAntiEntityESP/src/branch/main/LICENSE) file for details.

---

## Support

If you have any questions, issues, or suggestions, please open an issue on [ISSUES](https://codeberg.org/BrightForever/RayTraceAntiEntityESP/issues).

---

[![bStats](https://bstats.org/signatures/bukkit/RayTraceAntiEntityESP.svg)](https://bstats.org/plugin/bukkit/RayTraceAntiEntityESP/32643)