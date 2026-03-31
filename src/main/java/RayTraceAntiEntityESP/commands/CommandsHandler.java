package RayTraceAntiEntityESP.commands;

import RayTraceAntiEntityESP.config.Config;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.config.Config.*;
import static RayTraceAntiEntityESP.misc.StringFormat.formatToString;

public class CommandsHandler implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String @NonNull [] args) {
        if (!command.getName().equalsIgnoreCase("raytrace_anti_entity_esp")) return false;
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "config_value" -> Config.printConfig(sender);
            case "reload" -> {
                plugin.reloadConfigAll();
                sender.sendMessage(formatToString(sender, "&aReloaded!"));
            }
            case "enabled" -> set(sender, "checking.enabled", args, 1, Boolean::parseBoolean);
            case "checking_period_ticks" -> setWithMin(sender, "checking.period_ticks", args, 1, Long::parseLong, 1L);
            case "checking_distance_override" -> set(sender, "checking.distance_override", args, 1, Double::parseDouble);
            case "bounding_box_extra_value" -> set(sender, "checking.bounding_box_extra_value", args, 1, Double::parseDouble);
            case "vertices_layers" -> setWithMin(sender, "checking.vertices_layers", args, 1, Integer::parseInt, 2);
            case "perspective_checking" -> {
                if (args.length < 3) { sender.sendMessage(formatToString(sender, "&cMissing option and value.")); return true; }
                switch (args[1].toLowerCase()) {
                    case "enabled" -> set(sender, "perspective_checking.enabled", args, 2, Boolean::parseBoolean);
                    case "distances_from_head" -> set(sender, "perspective_checking.distances_from_head", args, 2, Double::parseDouble);
                    default -> sender.sendMessage(formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "debug" -> {
                if (args.length < 3) { sender.sendMessage(formatToString(sender, "&cMissing option and value.")); return true; }
                switch (args[1].toLowerCase()) {
                    case "enabled" -> set(sender, "debug.enabled", args, 2, Boolean::parseBoolean);
                    case "period_ticks" -> setWithMin(sender, "debug.period_ticks", args, 2, Long::parseLong, 1L);
                    default -> sender.sendMessage(formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "fake_name_display" -> {
                if (args.length < 3) { sender.sendMessage(formatToString(sender, "&cMissing option and value.")); return true; }
                switch (args[1].toLowerCase()) {
                    case "enabled" -> set(sender, "fake_name_display.enabled", args, 2, Boolean::parseBoolean);
                    case "period_ticks" -> setWithMin(sender, "fake_name_display.period_ticks", args, 2, Long::parseLong, 1L);
                    case "offset_y" -> set(sender, "fake_name_display.offset_y", args, 2, Double::parseDouble);
                    default -> sender.sendMessage(formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "anti_mode" -> {
                if (args.length < 2) { sender.sendMessage(formatToString(sender, "&cMissing value.")); return true; }
                plugin.getConfig().set("anti_mode", args[1].toLowerCase());
                plugin.saveConfig();
                plugin.reloadConfigAll();
                sender.sendMessage(formatToString(sender, "&aSet anti_mode to &e" + args[1].toLowerCase()));
            }
            case "anti_entities" -> {
                if (args.length < 3) { sender.sendMessage(formatToString(sender, "&cUsage: anti_entities <add|remove> <type>")); return true; }
                String type = args[2].toLowerCase();
                try { EntityType.valueOf(type.toUpperCase()); }
                catch (IllegalArgumentException e) { sender.sendMessage(formatToString(sender, "&e" + type + " &cis not a valid entity type.")); return true; }
                switch (args[1].toLowerCase()) {
                    case "add" -> {
                        if (!antiEntities.contains(type)) {
                            antiEntities.add(type);
                            plugin.getConfig().set("anti_entities", antiEntities);
                            plugin.saveConfig();
                            plugin.reloadConfigAll();
                            sender.sendMessage(formatToString(sender, "&aAdded &e" + type));
                        } else sender.sendMessage(formatToString(sender, "&e" + type + " &cis already in the list."));
                    }
                    case "remove" -> {
                        if (antiEntities.contains(type)) {
                            antiEntities.remove(type);
                            plugin.getConfig().set("anti_entities", antiEntities);
                            plugin.saveConfig();
                            plugin.reloadConfigAll();
                            sender.sendMessage(formatToString(sender, "&aRemoved &e" + type));
                        } else sender.sendMessage(formatToString(sender, "&e" + type + " &cnot found."));
                    }
                    default -> sender.sendMessage(formatToString(sender, "&cUnknown action: " + args[1]));
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    public static <T> void set(CommandSender sender, String key, String[] args, int argIndex, java.util.function.Function<String, T> parser) {
        if (args.length <= argIndex) { sender.sendMessage(formatToString(sender, "&cMissing value for " + key)); return; }
        try {
            T val = parser.apply(args[argIndex]);
            plugin.getConfig().set(key, val);
            plugin.saveConfig();
            plugin.reloadConfigAll();
            sender.sendMessage(formatToString(sender, "&aSet &e" + key + " &ato &e" + val));
        } catch (NumberFormatException e) {
            sender.sendMessage(formatToString(sender, "&cInvalid value: " + args[argIndex]));
        }
    }

    public static <T extends Comparable<T>> void setWithMin(CommandSender sender, String key, String[] args, int argIndex, java.util.function.Function<String, T> parser, T min) {
        if (args.length <= argIndex) { sender.sendMessage(formatToString(sender, "&cMissing value for " + key)); return; }
        try {
            T val = parser.apply(args[argIndex]);
            if (val.compareTo(min) < 0) { sender.sendMessage(formatToString(sender, "&c" + key + " must be at least " + min + "!")); return; }
            plugin.getConfig().set(key, val);
            plugin.saveConfig();
            plugin.reloadConfigAll();
            sender.sendMessage(formatToString(sender, "&aSet &e" + key + " &ato &e" + val));
        } catch (NumberFormatException e) {
            sender.sendMessage(formatToString(sender, "&cInvalid value: " + args[argIndex]));
        }
    }

    public static void sendHelp(CommandSender sender) {
        sender.sendMessage(formatToString(sender, "&6--- RayTrace Anti Entity ESP ---"));
        sender.sendMessage(formatToString(sender, "&e/rtaee config_value &7- Show all config values"));
        sender.sendMessage(formatToString(sender, "&e/rtaee reload"));
        sender.sendMessage(formatToString(sender, "&e/rtaee enabled <true|false>"));
        sender.sendMessage(formatToString(sender, "&e/rtaee checking_period_ticks <value>"));
        sender.sendMessage(formatToString(sender, "&e/rtaee checking_distance_override <value>"));
        sender.sendMessage(formatToString(sender, "&e/rtaee bounding_box_extra_value <value>"));
        sender.sendMessage(formatToString(sender, "&e/rtaee vertices_layers <value>"));
        sender.sendMessage(formatToString(sender, "&e/rtaee perspective_checking <enabled|distances_from_head> <value>"));
        sender.sendMessage(formatToString(sender, "&e/rtaee debug <enabled|period_ticks> <value>"));
        sender.sendMessage(formatToString(sender, "&e/rtaee fake_name_display <enabled|period_ticks|offset_y> <value>"));
        sender.sendMessage(formatToString(sender, "&e/rtaee anti_mode <whitelist|blacklist>"));
        sender.sendMessage(formatToString(sender, "&e/rtaee anti_entities <add|remove> <type>"));
    }
}