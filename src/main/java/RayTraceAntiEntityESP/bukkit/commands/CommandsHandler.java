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
            case "anti_entities" -> handleListCommand(sender, args, "anti_entities", "entity type",
                    raw -> {
                        String type = raw.toLowerCase();
                        try {
                            EntityType.valueOf(type.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                        return type;
                    },
                    type -> type,
                    type -> {
                        if (!Config.antiEntities.add(type)) return false;
                        saveAndReload("anti_entities", new java.util.ArrayList<>(Config.antiEntities));
                        return true;
                    },
                    type -> {
                        if (!Config.antiEntities.remove(type)) return false;
                        saveAndReload("anti_entities", new java.util.ArrayList<>(Config.antiEntities));
                        return true;
                    },
                    () -> Config.antiEntities,
                    () -> {
                        int count = Config.antiEntities.size();
                        if (count > 0) {
                            Config.antiEntities.clear();
                            saveAndReload("anti_entities", new java.util.ArrayList<>(Config.antiEntities));
                        }
                        return count;
                    },
                    "&aAdded &e%s",
                    "&aRemoved &e%s");
            case "exclude" -> handleListCommand(sender, args, "exclude", "player name or entity uuid",
                    CommandsHandler::resolveExcludeTarget,
                    CommandsHandler::displayExcludeTarget,
                    RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager::addExclude,
                    RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager::removeExclude,
                    RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager::listExclude,
                    RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager::clearExclude,
                    "&aAll viewers can now always see &e%s",
                    "&e%s &ccan be seen by everyone again");
            case "bypass" -> handleListCommand(sender, args, "bypass", "player",
                    CommandsHandler::resolvePlayerUUID,
                    CommandsHandler::displayPlayerName,
                    RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager::addBypass,
                    RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager::removeBypass,
                    RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager::listBypass,
                    RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager::clearBypass,
                    "&e%s &acan now see all entities",
                    "&e%s &cno longer bypasses ESP checking");
            default -> sendHelp(sender);
        }
        return true;
    }

    private static <T> void handleListCommand(CommandSender sender, String[] args, String label, String itemNoun,
                                              java.util.function.Function<String, T> resolver,
                                              java.util.function.Function<T, String> display,
                                              java.util.function.Predicate<T> add,
                                              java.util.function.Predicate<T> remove,
                                              java.util.function.Supplier<? extends java.util.Collection<T>> list,
                                              java.util.function.Supplier<Integer> clear,
                                              String addedMsgFmt, String removedMsgFmt) {
        if (args.length < 2) {
            sender.sendMessage(StringFormat.formatToString(sender, "&cUsage: " + label + " <add|remove|list|clear> [" + itemNoun + "]"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add", "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&cUsage: " + label + " " + args[1].toLowerCase() + " <" + itemNoun + ">"));
                    return;
                }
                String raw = args[2];
                T value = resolver.apply(raw);
                if (value == null) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&e" + raw + " &cis not a valid " + itemNoun + "."));
                    return;
                }
                String name = display.apply(value);
                boolean isAdd = args[1].equalsIgnoreCase("add");
                boolean changed = isAdd ? add.test(value) : remove.test(value);
                if (!changed) {
                    sender.sendMessage(StringFormat.formatToString(sender,
                            isAdd ? "&e" + name + " &cis already on the " + label + " list."
                                    : "&e" + name + " &cis not on the " + label + " list."));
                } else {
                    sender.sendMessage(StringFormat.formatToString(sender,
                            String.format(isAdd ? addedMsgFmt : removedMsgFmt, name)));
                }
            }
            case "list" -> {
                java.util.Collection<T> entries = list.get();
                if (entries.isEmpty()) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&7" + label + " list is empty."));
                } else {
                    sender.sendMessage(StringFormat.formatToString(sender, "&6--- " + label + " (" + entries.size() + ") ---"));
                    java.util.List<String> names = new java.util.ArrayList<>();
                    for (T value : entries) names.add(display.apply(value));
                    sender.sendMessage(StringFormat.formatToString(sender, "&e" + String.join("&7, &e", names)));
                }
            }
            case "clear" -> {
                int count = clear.get();
                if (count == 0) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&7" + label + " list is already empty."));
                } else {
                    sender.sendMessage(StringFormat.formatToString(sender, "&aCleared &e" + count + " &aentries from " + label + "."));
                }
            }
            default -> sender.sendMessage(StringFormat.formatToString(sender,
                    "&cUnknown option: " + args[1] + ". Use add, remove, list or clear."));
        }
    }

    private static java.util.UUID tryParseUUID(String raw) {
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static java.util.UUID resolvePlayerUUID(String name) {
        java.util.UUID parsed = tryParseUUID(name);
        if (parsed != null) {
            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(parsed);
            return (offline.hasPlayedBefore() || offline.isOnline()) ? parsed : null;
        }
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) return offline.getUniqueId();
        return null;
    }

    private static String displayPlayerName(java.util.UUID uuid) {
        String name = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }

    private static java.util.UUID resolveExcludeTarget(String raw) {
        java.util.UUID parsed = tryParseUUID(raw);
        if (parsed != null) {
            if (org.bukkit.Bukkit.getEntity(parsed) != null) return parsed;
            org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(parsed);
            if (offline.hasPlayedBefore() || offline.isOnline()) return parsed;
            return null;
        }
        return resolvePlayerUUID(raw);
    }

    private static String displayExcludeTarget(java.util.UUID uuid) {
        org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(uuid);
        if (entity != null && !(entity instanceof org.bukkit.entity.Player)) {
            return entity.getType().name().toLowerCase() + " (" + uuid.toString().substring(0, 8) + ")";
        }
        return displayPlayerName(uuid);
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
                "&e/rtaee exclude <add|remove|list|clear> [player_name|entity_uuid] &7- Let everyone see this player/entity, always",
                "&e/rtaee bypass <add|remove|list|clear> [player] &7- Let a player see everyone, always",
                "&e/rtaee help &7- Show help information"
        };
        for (String msg : help) sender.sendMessage(StringFormat.formatToString(sender, msg));
    }
}