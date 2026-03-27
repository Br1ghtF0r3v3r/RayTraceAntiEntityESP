package RayTraceAntiEntityESP.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.Mics.StringFormat.formatToString;

public class CommandsHandler implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String @NonNull [] args) {

        if (command.getName().equalsIgnoreCase("rtaee")) {

            if (args[0].equalsIgnoreCase("reload")) {
                sender.sendMessage(formatToString(sender, "&aReloaded all configurations!"));
                plugin.reloadConfigAll();
            }
            return true;
        }
        return false;
    }
}