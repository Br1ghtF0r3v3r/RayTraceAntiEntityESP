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
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "config_value" -> Config.printConfig(sender);
            case "reload" -> { plugin.reloadConfigAll(); sender.sendMessage(StringFormat.formatToString(sender, "&aReloaded!")); }
            case "enabled" -> set(sender, "checking.enabled", args, 1, Boolean::parseBoolean);
            case "checking_period_ticks" -> setWithMin(sender, "checking.period_ticks", args, 1, Long::parseLong, 1L);
            case "checking_distance_override" -> set(sender, "checking.distance_override", args, 1, Double::parseDouble);
            case "bounding_box_extra_value" -> set(sender, "checking.bounding_box_extra_value", args, 1, Double::parseDouble);
            case "vertices_layers" -> setWithMin(sender, "checking.vertices_layers", args, 1, Integer::parseInt, 2);
            case "perspective_checking" -> {
                if (args.length < 3) { sender.sendMessage(StringFormat.formatToString(sender, "&cMissing option and value.")); return true; }
                switch (args[1].toLowerCase()) {
                    case "enabled" -> set(sender, "perspective_checking.enabled", args, 2, Boolean::parseBoolean);
                    case "distances_from_head" -> set(sender, "perspective_checking.distances_from_head", args, 2, Double::parseDouble);
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
                    default -> sender.sendMessage(StringFormat.formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "debug" -> {
                if (args.length < 3) { sender.sendMessage(StringFormat.formatToString(sender, "&cMissing option and value.")); return true; }
                if (args[1].equalsIgnoreCase("enabled")) {
                    set(sender, "debug.enabled", args, 2, Boolean::parseBoolean);
                } else {
                    sender.sendMessage(StringFormat.formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "anti_mode" -> {
                if (args.length < 2) { sender.sendMessage(StringFormat.formatToString(sender, "&cMissing value.")); return true; }
                String mode = args[1].toLowerCase();
                saveAndReload("anti_mode", mode);
                sender.sendMessage(StringFormat.formatToString(sender, "&aSet anti_mode to &e" + mode));
            }
            case "anti_entities" -> {
                if (args.length < 3) { sender.sendMessage(StringFormat.formatToString(sender, "&cUsage: anti_entities <add|remove> <type>")); return true; }
                String type = args[2].toLowerCase();
                try { EntityType.valueOf(type.toUpperCase()); }
                catch (IllegalArgumentException e) { sender.sendMessage(StringFormat.formatToString(sender, "&e" + type + " &cis not a valid entity type.")); return true; }
                boolean isAdd = args[1].equalsIgnoreCase("add");
                boolean exists = Config.antiEntities.contains(type);
                if ((isAdd && exists) || (!isAdd && !exists)) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&e" + type + " &c" + (isAdd ? "is already in the list." : "not found.")));
                } else {
                    if (isAdd) Config.antiEntities.add(type);
                    else Config.antiEntities.remove(type);
                    saveAndReload("anti_entities", Config.antiEntities);
                    sender.sendMessage(StringFormat.formatToString(sender, "&a" + (isAdd ? "Added" : "Removed") + " &e" + type));
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
        if (args.length <= argIndex) { sender.sendMessage(StringFormat.formatToString(sender, "&cMissing value for " + key)); return; }
        try {
            T val = parser.apply(args[argIndex]);
            saveAndReload(key, val);
            sender.sendMessage(StringFormat.formatToString(sender, "&aSet &e" + key + " &ato &e" + val));
        } catch (NumberFormatException e) {
            sender.sendMessage(StringFormat.formatToString(sender, "&cInvalid value: " + args[argIndex]));
        }
    }

    public static <T extends Comparable<T>> void setWithMin(CommandSender sender, String key, String[] args, int argIndex, java.util.function.Function<String, T> parser, T min) {
        if (args.length <= argIndex) { sender.sendMessage(StringFormat.formatToString(sender, "&cMissing value for " + key)); return; }
        try {
            T val = parser.apply(args[argIndex]);
            if (val.compareTo(min) < 0) { sender.sendMessage(StringFormat.formatToString(sender, "&c" + key + " must be at least " + min + "!")); return; }
            saveAndReload(key, val);
            sender.sendMessage(StringFormat.formatToString(sender, "&aSet &e" + key + " &ato &e" + val));
        } catch (NumberFormatException e) {
            sender.sendMessage(StringFormat.formatToString(sender, "&cInvalid value: " + args[argIndex]));
        }
    }

    public static void sendHelp(CommandSender sender) {
        String[] help = {
            "&6--- RayTrace Anti Entity ESP ---",
            "&e/rtaee config_value &7- Show all config values",
            "&e/rtaee reload",
            "&e/rtaee enabled <true|false>",
            "&e/rtaee checking_period_ticks <value>",
            "&e/rtaee checking_distance_override <value>",
            "&e/rtaee bounding_box_extra_value <value>",
            "&e/rtaee vertices_layers <value>",
            "&e/rtaee perspective_checking <enabled|distances_from_head> <value>",
            "&e/rtaee debug <enabled|period_ticks> <value>",
            "&e/rtaee fake_name_display <enabled|period_ticks|offset_y> <value>",
            "&e/rtaee anti_mode <whitelist|blacklist>",
            "&e/rtaee anti_entities <add|remove> <type>",
            "&e/rtaee help"
        };
        for (String msg : help) sender.sendMessage(StringFormat.formatToString(sender, msg));
    }
}