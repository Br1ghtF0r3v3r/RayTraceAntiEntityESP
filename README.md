# RayTrace Anti Entity ESP

A Paper plugin that prevents players from seeing entities through walls.

It uses a DDA raytrace algorithm to check each entity's visibility per player every tick — entities hidden behind solid
blocks are concealed at the packet level, meaning clients never receive data for entities they shouldn't see.

---

## Demo

<details>
<summary>View</summary>

### Without RayTrace Anti Entity ESP

https://github.com/user-attachments/assets/68cc56f7-f8e2-4c7c-95db-995082286535

### With RayTrace Anti Entity ESP

https://github.com/user-attachments/assets/c9be734c-eb48-4401-9587-965c3b0977f3
</details>

---

## Installation

<details>
<summary>View</summary>

1. **Download** the latest `.jar` from [Download Link]().
2. **Drop** the `.jar` into your server's `/plugins` folder.
3. **Restart** your server (do not use `/reload`).
4. **Edit** the generated [config.yml](src/main/resources/config.yml) in `plugins/RayTraceAntiEntityESP/config.yml` to
   your liking.
5. **Run** `/rtaee reload` in-game to apply config changes without restarting.

> ⚠️ **Requirements:** Paper **1.21.x**.

> ⚠️ Do not enable, disable or reload this plugin using plugin managers or `/reload`. It will not work properly and may
cause issues.
</details>

---

## Configuration

<details>
<summary>View</summary>

```yaml
checking:
  enabled: true
  period_ticks: 1        # How often to run the check loop in ticks (recommended: 1 when using stagger_groups)
  stagger_groups: 3      # Divide entities into N groups, checking 1 group per tick for smooth CPU usage (1 = disabled)
  distance_override: 5   # Entities within this many blocks are always shown (0 to disable)
  bounding_box_extra_value: 0  # Expand entity bounding box for more lenient detection
  vertices_layers: 4     # Sample points per entity (min 2, higher = more accurate)
perspective_checking:
  enabled: true
  distances_from_head: 4 # Simulated third-person camera distance in blocks
display_name:
  enabled: true          # Show a fake name tag above hidden entities
  offset_y: 0            # Vertical offset of the name tag above the entity's head
debug:
  enabled: false         # Show block markers at raytrace vertices (testing only)
anti_entities:
  - player
anti_mode: whitelist     # whitelist = only listed types | blacklist = all except listed
exclude_entity_tag: raytrace_anti_entity_esp_excluded  # Tag an entity with this to always show it
```

</details>

---

## Commands

<details>
<summary>View</summary>

All commands require OP or the `raytrace_anti_entity_esp.admin` permission. Alias: `/rtaee`

| Command                                                              | Description                     |
|----------------------------------------------------------------------|---------------------------------|
| `/rtaee reload`                                                      | Reload config from disk         |
| `/rtaee config_value`                                                | Print all current config values |
| `/rtaee enabled <true\|false>`                                       | Enable or disable the plugin    |
| `/rtaee checking_period_ticks <value>`                               | Set check frequency             |
| `/rtaee checking_stagger_groups <value>`                             | Set entity check stagger groups |
| `/rtaee checking_distance_override <value>`                          | Set always-show range           |
| `/rtaee bounding_box_extra_value <value>`                            | Set bounding box expansion      |
| `/rtaee vertices_layers <value>`                                     | Set vertex sample count         |
| `/rtaee perspective_checking <enabled\|distances_from_head> <value>` | Perspective options             |
| `/rtaee display_name <enabled\|offset_y> <value>`                   | Name tag options                |
| `/rtaee debug enabled <true\|false>`                                 | Toggle debug mode               |
| `/rtaee anti_entities <add\|remove\|list\|clear> [type]`            | Edit entity list                |
| `/rtaee anti_mode <whitelist\|blacklist>`                            | Switch filter mode              |
| `/rtaee help`                                                        | Show help information           |

</details>

---

## License

<details>
<summary>View</summary>

Copyright (c) 2026 [Br1ghtF0r3v3r](https://github.com/Br1ghtF0r3v3r)

All rights reserved. Redistribution, resale, or decompilation of this plugin is strictly prohibited. One license per
server instance.
</details>