package RayTraceAntiEntityESP.bukkit.commands;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.misc.StringFormat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import static RayTraceAntiEntityESP.bukkit.Main.plugin;

public class CommandsHandler implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String @NonNull [] args) {
        if (!command.getName().equalsIgnoreCase("raytrace_anti_entity_esp")) return false;
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "config_value" -> Config.printConfig(sender);
            case "reload" -> {
                plugin.reloadConfigAll();
                sender.sendMessage(StringFormat.formatToString(sender, "&aReloaded!"));
            }
            case "checking" -> {
                if (args.length < 3) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&cMissing option and value."));
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "enabled" -> set(sender, "checking.enabled", args, 2, Boolean::parseBoolean);
                    case "period_ticks" -> setWithMin(sender, "checking.period_ticks", args, 2, Long::parseLong, 1L);
                    case "stagger_groups" ->
                            setWithMin(sender, "checking.stagger_groups", args, 2, Integer::parseInt, 1);
                    case "distance_override" -> set(sender, "checking.distance_override", args, 2, Double::parseDouble);
                    case "bounding_box_extra_value" ->
                            set(sender, "checking.bounding_box_extra_value", args, 2, Double::parseDouble);
                    case "vertices_layers" ->
                            setWithMin(sender, "checking.vertices_layers", args, 2, Integer::parseInt, 2);
                    default -> sender.sendMessage(StringFormat.formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "perspective_checking" -> {
                if (args.length < 3) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&cMissing option and value."));
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "enabled" -> set(sender, "perspective_checking.enabled", args, 2, Boolean::parseBoolean);
                    case "distances_from_head" ->
                            set(sender, "perspective_checking.distances_from_head", args, 2, Double::parseDouble);
                    default -> sender.sendMessage(StringFormat.formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "display_name" -> {
                if (args.length < 3) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&cMissing option and value."));
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "enabled" -> set(sender, "display_name.enabled", args, 2, Boolean::parseBoolean);
                    case "offset_y" -> set(sender, "display_name.offset_y", args, 2, Double::parseDouble);
                    case "lookahead_ticks" -> set(sender, "display_name.lookahead_ticks", args, 2, Double::parseDouble);
                    default -> sender.sendMessage(StringFormat.formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "debug" -> {
                if (args.length < 3) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&cMissing option and value."));
                    return true;
                }
                if (args[1].equalsIgnoreCase("enabled")) {
                    set(sender, "debug.enabled", args, 2, Boolean::parseBoolean);
                } else {
                    sender.sendMessage(StringFormat.formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "anti_mode" -> {
                if (args.length < 2) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&cMissing value."));
                    return true;
                }
                String mode = args[1].toLowerCase();
                saveAndReload("anti_mode", mode);
                sender.sendMessage(StringFormat.formatToString(sender, "&aSet anti_mode to &e" + mode));
            }
            case "anti_entities" -> {
                if (args.length < 2) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&cUsage: anti_entities <add|remove|list|clear> [type]"));
                    return true;
                }

                switch (args[1].toLowerCase()) {
                    case "add", "remove" -> {
                        if (args.length < 3) {
                            sender.sendMessage(StringFormat.formatToString(sender, "&cUsage: anti_entities <add|remove> <type>"));
                            return true;
                        }
                        String type = args[2].toLowerCase();
                        try {
                            EntityType.valueOf(type.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            sender.sendMessage(StringFormat.formatToString(sender, "&e" + type + " &cis not a valid entity type."));
                            return true;
                        }
                        boolean isAdd = args[1].equalsIgnoreCase("add");
                        boolean exists = Config.antiEntities.contains(type);
                        if ((isAdd && exists) || (!isAdd && !exists)) {
                            sender.sendMessage(StringFormat.formatToString(sender, "&e" + type + " &c" + (isAdd ? "is already in the list." : "not found.")));
                        } else {
                            if (isAdd) Config.antiEntities.add(type);
                            else Config.antiEntities.remove(type);
                            saveAndReload("anti_entities", new java.util.ArrayList<>(Config.antiEntities));
                            sender.sendMessage(StringFormat.formatToString(sender, "&a" + (isAdd ? "Added" : "Removed") + " &e" + type));
                        }
                    }
                    case "list" -> {
                        if (Config.antiEntities.isEmpty()) {
                            sender.sendMessage(StringFormat.formatToString(sender, "&7Anti entities list is empty."));
                        } else {
                            sender.sendMessage(StringFormat.formatToString(sender, "&6--- Anti Entities (" + Config.antiEntities.size() + ") ---"));
                            sender.sendMessage(StringFormat.formatToString(sender, "&e" + String.join("&7, &e", Config.antiEntities)));
                        }
                    }
                    case "clear" -> {
                        if (Config.antiEntities.isEmpty()) {
                            sender.sendMessage(StringFormat.formatToString(sender, "&7Anti entities list is already empty."));
                        } else {
                            int count = Config.antiEntities.size();
                            Config.antiEntities.clear();
                            saveAndReload("anti_entities", new java.util.ArrayList<>(Config.antiEntities));
                            sender.sendMessage(StringFormat.formatToString(sender, "&aCleared &e" + count + " &aentities."));
                        }
                    }
                    default ->
                            sender.sendMessage(StringFormat.formatToString(sender, "&cUnknown option: " + args[1] + ". Use add, remove, list or clear."));
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private static void saveAndReload(String key, Object val) {
        plugin.getConfig().set(key, val);
        plugin.saveConfig();
        plugin.reloadConfigAll();
    }

    public static <T> void set(CommandSender sender, String key, String[] args, int argIndex, java.util.function.Function<String, T> parser) {
        if (args.length <= argIndex) {
            sender.sendMessage(StringFormat.formatToString(sender, "&cMissing value for " + key));
            return;
        }
        try {
            T val = parser.apply(args[argIndex]);
            saveAndReload(key, val);
            sender.sendMessage(StringFormat.formatToString(sender, "&aSet &e" + key + " &ato &e" + val));
        } catch (NumberFormatException e) {
            sender.sendMessage(StringFormat.formatToString(sender, "&cInvalid value: " + args[argIndex]));
        }
    }

    public static <T extends Comparable<T>> void setWithMin(CommandSender sender, String key, String[] args, int argIndex, java.util.function.Function<String, T> parser, T min) {
        if (args.length <= argIndex) {
            sender.sendMessage(StringFormat.formatToString(sender, "&cMissing value for " + key));
            return;
        }
        try {
            T val = parser.apply(args[argIndex]);
            if (val.compareTo(min) < 0) {
                sender.sendMessage(StringFormat.formatToString(sender, "&c" + key + " must be at least " + min + "!"));
                return;
            }
            saveAndReload(key, val);
            sender.sendMessage(StringFormat.formatToString(sender, "&aSet &e" + key + " &ato &e" + val));
        } catch (NumberFormatException e) {
            sender.sendMessage(StringFormat.formatToString(sender, "&cInvalid value: " + args[argIndex]));
        }
    }

    public static void sendHelp(CommandSender sender) {
        String[] help = {
                "&6--- RayTrace Anti Entity ESP ---",
                "&e/rtaee reload &7- Reload config from disk",
                "&e/rtaee config_value &7- Print all current config values",
                "&e/rtaee checking <enabled|period_ticks|stagger_groups|distance_override|bounding_box_extra_value|vertices_layers> <value> &7- Checking options",
                "&e/rtaee perspective_checking <enabled|distances_from_head> <value> &7- Perspective options",
                "&e/rtaee display_name <enabled|offset_y|lookahead_ticks> <value> &7- Name tag options",
                "&e/rtaee debug enabled <true|false> &7- Toggle debug mode",
                "&e/rtaee anti_mode <whitelist|blacklist> &7- Switch filter mode",
                "&e/rtaee anti_entities <add|remove|list|clear> [type] &7- Edit entity list",
                "&e/rtaee help &7- Show help information"
        };
        for (String msg : help) sender.sendMessage(StringFormat.formatToString(sender, msg));
    }
}