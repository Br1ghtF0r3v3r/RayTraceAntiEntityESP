package RayTraceAntiEntityESP.bukkit.commands;

import RayTraceAntiEntityESP.bukkit.config.Config;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class TabCompletion implements TabCompleter {
    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, Command command, @NonNull String alias, String @NonNull [] args) {
        if (!command.getName().equalsIgnoreCase("raytrace_anti_entity_esp")) return null;
        if (args.length == 1) {
            return filter(args[0], List.of("config_value", "reload", "checking", "perspective_checking", "debug", "display_name", "anti_mode", "anti_entities", "help"));
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "checking" -> filter(args[1], List.of("enabled", "period_ticks", "stagger_groups", "distance_override", "bounding_box_extra_value", "vertices_layers"));
                case "perspective_checking" -> filter(args[1], List.of("enabled", "distances_from_head"));
                case "debug" -> filter(args[1], List.of("enabled"));
                case "display_name" -> filter(args[1], List.of("enabled", "offset_y", "lookahead_ticks"));
                case "anti_mode" -> filter(args[1], List.of("whitelist", "blacklist"));
                case "anti_entities" -> filter(args[1], List.of("add", "remove", "list", "clear"));
                default -> null;
            };
        }
        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "checking" -> switch (args[1].toLowerCase()) {
                    case "enabled" -> filter(args[2], List.of("true", "false"));
                    case "period_ticks", "stagger_groups", "distance_override", "bounding_box_extra_value", "vertices_layers" -> List.of("<value>");
                    default -> null;
                };
                case "perspective_checking" -> switch (args[1].toLowerCase()) {
                    case "enabled" -> filter(args[2], List.of("true", "false"));
                    case "distances_from_head" -> List.of("<value>");
                    default -> null;
                };
                case "debug" -> switch (args[1].toLowerCase()) {
                    case "enabled" -> filter(args[2], List.of("true", "false"));
                    default -> null;
                };
                case "display_name" -> switch (args[1].toLowerCase()) {
                    case "enabled" -> filter(args[2], List.of("true", "false"));
                    case "offset_y", "lookahead_ticks" -> List.of("<value>");
                    default -> null;
                };
                case "anti_entities" -> switch (args[1].toLowerCase()) {
                    case "add" -> {
                        List<String> types = new ArrayList<>();
                        for (EntityType type : EntityType.values()) {
                            String name = type.name().toLowerCase();
                            if (!Config.antiEntities.contains(name) && name.startsWith(args[2].toLowerCase())) {
                                types.add(name);
                            }
                        }
                        yield types;
                    }
                    case "remove" -> filter(args[2], new ArrayList<>(Config.antiEntities));
                    default -> null;
                };
                default -> null;
            };
        }
        return null;
    }

    public List<String> filter(String input, List<String> options) {
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(input.toLowerCase())) result.add(o);
        }
        return result;
    }
}