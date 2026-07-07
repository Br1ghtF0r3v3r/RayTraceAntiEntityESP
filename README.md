# RayTrace Anti Entity ESP

A Paper plugin that prevents players from seeing entities through walls.

It uses a DDA raytrace algorithm to check each entity's visibility per player every tick — entities hidden behind solid
blocks are concealed at the packet level, meaning clients never receive data for entities they shouldn't see.

---

## Demo

<details>
<summary>View</summary>

### Without Xray
<img width="720" height="405" alt="=Without Xray" src="https://github.com/user-attachments/assets/f8c1979e-fa92-4a03-91ba-458b5f6a2f8d"/>

### With Xray
<img width="720" height="405" alt="With Xray" src="https://github.com/user-attachments/assets/3061f724-cf3d-49a5-a8ad-ae32cccea742"/>

</details>

---

## Installation

<details>
<summary>View</summary>

1. **Download** the latest `.jar` from [Download Link](https://modrinth.com/plugin/raytraceantientityesp/versions).
2. **Drop** the `.jar` into your server's `/plugins` folder.
3. **Restart** your server (do not use `/reload`).
4. **Edit** the generated [config.yml](src/main/resources/config.yml) in `plugins/RayTraceAntiEntityESP/config.yml` to your liking.
5. **Run** `/rtaee reload` in-game to apply config changes without restarting.

> ⚠️ **Requirements:** Paper **1.21.x**.
>
> ⚠️ Do not enable, disable or reload this plugin using plugin managers or `/reload`. It will not work properly and may cause issues.

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
  distance_override: 10  # Entities within this many blocks are always shown (0 to disable)
  bounding_box_extra_value: 0  # Expand entity bounding box for more lenient detection
  vertices_layers: 4     # Sample points per entity (min 2, higher = more accurate)
perspective_checking:
  enabled: true
  distances_from_head: 4 # Simulated third-person camera distance in blocks
display_name:
  enabled: true          # Show a fake name tag above hidden entities
  lookahead_ticks: 3     # Extrapolate the fake nametag's position this many ticks ahead to compensate
  # for vanilla's client-side smoothing (0 disables extrapolation)
  offset_y: 0            # Vertical offset of the name tag above the entity's head
debug:
  enabled: false         # Show block markers at raytrace vertices (testing only)
anti_entities:
  - player
anti_mode: whitelist     # whitelist = only listed types | blacklist = all except listed
```

</details>

---

## Commands

<details>
<summary>View</summary>

All commands require OP or the `raytrace_anti_entity_esp.admin` permission. Alias: `/rtaee`

| Command                                                                                                                         | Description                                        |
|---------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|
| `/rtaee reload`                                                                                                                 | Reload config from disk                            |
| `/rtaee config_value`                                                                                                           | Print all current config values                    |
| `/rtaee checking <enabled\|period_ticks\|stagger_groups\|distance_override\|bounding_box_extra_value\|vertices_layers> <value>` | Checking options                |
| `/rtaee perspective_checking <enabled\|distances_from_head> <value>`                                                            | Perspective options                                |
| `/rtaee display_name <enabled\|offset_y\|lookahead_ticks> <value>`                                                              | Name tag options                                   |
| `/rtaee debug enabled <true\|false>`                                                                                            | Toggle debug mode                                  |
| `/rtaee anti_mode <whitelist\|blacklist>`                                                                                       | Switch filter mode                                 |
| `/rtaee anti_entities <add\|remove\|list\|clear> [type]`                                                                        | Edit entity list                                   |
| `/rtaee exclude <add\|remove\|list\|clear> [player_name\|entity_uuid]`                                                          | Let everyone always see this player/entity, ESP-proof or not |
| `/rtaee bypass <add\|remove\|list\|clear> [player]`                                                                             | Let a player always see everyone, walls or not     |
| `/rtaee help`                                                                                                                   | Show help information                              |

</details>

---

## License

This project is licensed under the **GPL-3.0 License**. See the [LICENSE](LICENSE) file for details.

---

## Support

If you have any questions, issues, or suggestions, please open an issue on [Issues](https://github.com/Br1ghtF0r3v3r/RayTraceAntiEntityESP/issues).

Or join the [Discord](https://discord.gg/x8KkZPXXHR) for support and discussion.

---
