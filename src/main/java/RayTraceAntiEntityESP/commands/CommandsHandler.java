package RayTraceAntiEntityESP.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.config.Config.isCheckingEnabled;
import static RayTraceAntiEntityESP.misc.StringFormat.formatToString;

public class CommandsHandler implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String @NonNull [] args) {

        if (command.getName().equalsIgnoreCase("raytrace_anti_entity_esp")) {

            if (args.length > 0) {

                if (args[0].equalsIgnoreCase("reload")) {
                    sender.sendMessage(formatToString(sender, "&aReloaded all configurations!"));
                    plugin.reloadConfigAll();
                }
                else if (args[0].equalsIgnoreCase("enabled")) {
                    if (args.length > 1) {
                        isCheckingEnabled = Boolean.parseBoolean(args[1]);
                        plugin.getConfig().set("enabled", isCheckingEnabled);
                        plugin.saveConfig();
                    }
                }

                if (args[0].equalsIgnoreCase("1")) {
                }

            }
            return true;
        }
        return false;
    }
}