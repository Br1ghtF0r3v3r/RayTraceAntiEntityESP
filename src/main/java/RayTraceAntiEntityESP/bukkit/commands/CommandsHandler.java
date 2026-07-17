package RayTraceAntiEntityESP.bukkit.commands;

import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager;
import RayTraceAntiEntityESP.bukkit.misc.StringFormat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
                if (requireArgs(sender, args, 3, "&cMissing option and value.")) return true;
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
                if (requireArgs(sender, args, 3, "&cMissing option and value.")) return true;
                switch (args[1].toLowerCase()) {
                    case "enabled" -> set(sender, "perspective_checking.enabled", args, 2, Boolean::parseBoolean);
                    case "distances_from_head" ->
                            set(sender, "perspective_checking.distances_from_head", args, 2, Double::parseDouble);
                    default -> sender.sendMessage(StringFormat.formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "display_name" -> {
                if (requireArgs(sender, args, 3, "&cMissing option and value.")) return true;
                switch (args[1].toLowerCase()) {
                    case "enabled" -> set(sender, "display_name.enabled", args, 2, Boolean::parseBoolean);
                    case "offset_y" -> set(sender, "display_name.offset_y", args, 2, Double::parseDouble);
                    case "lookahead_ticks" -> set(sender, "display_name.lookahead_ticks", args, 2, Double::parseDouble);
                    default -> sender.sendMessage(StringFormat.formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "debug" -> {
                if (requireArgs(sender, args, 3, "&cMissing option and value.")) return true;
                if (args[1].equalsIgnoreCase("enabled")) {
                    set(sender, "debug.enabled", args, 2, Boolean::parseBoolean);
                } else {
                    sender.sendMessage(StringFormat.formatToString(sender, "&cUnknown: " + args[1]));
                }
            }
            case "anti_mode" -> {
                if (requireArgs(sender, args, 2, "&cMissing value.")) return true;
                String mode = args[1].toLowerCase();
                saveAndReload("anti_mode", mode);
                sender.sendMessage(StringFormat.formatToString(sender, "&aSet anti_mode to &e" + mode));
            }
            case "anti_entities" -> handleListCommand(sender, args, 1, "anti_entities", "entity type",
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
                        saveAndReload("anti_entities", new ArrayList<>(Config.antiEntities));
                        return true;
                    },
                    type -> {
                        if (!Config.antiEntities.remove(type)) return false;
                        saveAndReload("anti_entities", new ArrayList<>(Config.antiEntities));
                        return true;
                    },
                    () -> Config.antiEntities,
                    () -> {
                        int count = Config.antiEntities.size();
                        if (count > 0) {
                            Config.antiEntities.clear();
                            saveAndReload("anti_entities", new ArrayList<>(Config.antiEntities));
                        }
                        return count;
                    },
                    "&aAdded &e%s",
                    "&aRemoved &e%s");
            case "exclude" -> handleListCommand(sender, args, 1, "exclude", "player name or entity uuid",
                    CommandsHandler::resolveExcludeTarget,
                    CommandsHandler::displayExcludeTarget,
                    ExcludeBypassManager::addExclude,
                    ExcludeBypassManager::removeExclude,
                    ExcludeBypassManager::listExclude,
                    ExcludeBypassManager::clearExclude,
                    "&aAll viewers can now always see &e%s",
                    "&e%s &ccan be seen by everyone again");
            case "bypass" -> handleListCommand(sender, args, 1, "bypass", "player",
                    CommandsHandler::resolvePlayerUUID,
                    CommandsHandler::displayPlayerName,
                    ExcludeBypassManager::addBypass,
                    ExcludeBypassManager::removeBypass,
                    ExcludeBypassManager::listBypass,
                    ExcludeBypassManager::clearBypass,
                    "&e%s &acan now see all entities",
                    "&e%s &cno longer bypasses ESP checking");
            case "blacklisted_world" -> handleListCommand(sender, args, 1, "blacklisted_world", "world name",
                    raw -> Bukkit.getWorld(raw) != null ? raw.toLowerCase() : null,
                    name -> name,
                    name -> {
                        if (!Config.blacklistedWorlds.add(name)) return false;
                        saveAndReload("blacklisted_world", new ArrayList<>(Config.blacklistedWorlds));
                        return true;
                    },
                    name -> {
                        if (!Config.blacklistedWorlds.remove(name)) return false;
                        saveAndReload("blacklisted_world", new ArrayList<>(Config.blacklistedWorlds));
                        return true;
                    },
                    () -> Config.blacklistedWorlds,
                    () -> {
                        int count = Config.blacklistedWorlds.size();
                        if (count > 0) {
                            Config.blacklistedWorlds.clear();
                            saveAndReload("blacklisted_world", new ArrayList<>(Config.blacklistedWorlds));
                        }
                        return count;
                    },
                    "&aAdded &e%s",
                    "&aRemoved &e%s");
            default -> sendHelp(sender);
        }
        return true;
    }

    private static boolean requireArgs(CommandSender sender, String[] args, int min, String message) {
        if (args.length < min) {
            sender.sendMessage(StringFormat.formatToString(sender, message));
            return true;
        }
        return false;
    }

    private static <T> void handleListCommand(CommandSender sender, String[] args, int argIndex, String label, String itemNoun,
                                              Function<String, T> resolver,
                                              Function<T, String> display,
                                              Predicate<T> add,
                                              Predicate<T> remove,
                                              Supplier<? extends Collection<T>> list,
                                              Supplier<Integer> clear,
                                              String addedMsgFmt, String removedMsgFmt) {
        if (args.length <= argIndex) {
            sender.sendMessage(StringFormat.formatToString(sender, "&cUsage: " + label + " <add|remove|list|clear> [" + itemNoun + "]"));
            return;
        }

        switch (args[argIndex].toLowerCase()) {
            case "add", "remove" -> {
                if (args.length <= argIndex + 1) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&cUsage: " + label + " " + args[argIndex].toLowerCase() + " <" + itemNoun + ">"));
                    return;
                }
                String raw = args[argIndex + 1];
                T value = resolver.apply(raw);
                if (value == null) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&e" + raw + " &cis not a valid " + itemNoun + "."));
                    return;
                }
                String name = display.apply(value);
                boolean isAdd = args[argIndex].equalsIgnoreCase("add");
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
                Collection<T> entries = list.get();
                if (entries.isEmpty()) {
                    sender.sendMessage(StringFormat.formatToString(sender, "&7" + label + " list is empty."));
                } else {
                    sender.sendMessage(StringFormat.formatToString(sender, "&6" + label + " (" + entries.size() + "):"));
                    List<String> names = new ArrayList<>();
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
                    "&cUnknown option: " + args[argIndex] + ". Use add, remove, list or clear."));
        }
    }

    private static UUID tryParseUUID(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID resolvePlayerUUID(String name) {
        UUID parsed = tryParseUUID(name);
        if (parsed != null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(parsed);
            return (offline.hasPlayedBefore() || offline.isOnline()) ? parsed : null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) return offline.getUniqueId();
        return null;
    }

    private static String displayPlayerName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }

    private static UUID resolveExcludeTarget(String raw) {
        UUID parsed = tryParseUUID(raw);
        if (parsed != null) {
            if (Bukkit.getEntity(parsed) != null) return parsed;
            OfflinePlayer offline = Bukkit.getOfflinePlayer(parsed);
            if (offline.hasPlayedBefore() || offline.isOnline()) return parsed;
            return null;
        }
        return resolvePlayerUUID(raw);
    }

    private static String displayExcludeTarget(UUID uuid) {
        Entity entity = Bukkit.getEntity(uuid);
        if (entity != null && !(entity instanceof Player)) {
            return entity.getType().name().toLowerCase() + " (" + uuid.toString().substring(0, 8) + ")";
        }
        return displayPlayerName(uuid);
    }

    private static void saveAndReload(String key, Object val) {
        plugin.getConfig().set(key, val);
        plugin.saveConfig();
        plugin.reloadConfigAll();
    }

    public static <T> void set(CommandSender sender, String key, String[] args, int argIndex, Function<String, T> parser) {
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

    public static <T extends Comparable<T>> void setWithMin(CommandSender sender, String key, String[] args, int argIndex, Function<String, T> parser, T min) {
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
                "&6RayTrace Anti Entity ESP",
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
                "&e/rtaee blacklisted_world <add|remove|list|clear> [world] &7- Worlds where ESP checking never runs",
                "&e/rtaee help &7- Show help information"
        };
        for (String msg : help) sender.sendMessage(StringFormat.formatToString(sender, msg));
    }
}