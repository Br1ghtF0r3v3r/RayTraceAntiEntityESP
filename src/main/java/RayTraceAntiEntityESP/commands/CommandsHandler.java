package RayTraceAntiEntityESP.commands;

import RayTraceAntiEntityESP.engine.RaycastUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static RayTraceAntiEntityESP.Main.plugin;
import static RayTraceAntiEntityESP.misc.StringFormat.formatToString;

public class CommandsHandler implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String @NonNull [] args) {

        if (command.getName().equalsIgnoreCase("rtaee")) {

            if (args[0].equalsIgnoreCase("reload")) {
                sender.sendMessage(formatToString(sender, "&aReloaded all configurations!"));
                plugin.reloadConfigAll();
            }

            if (args[0].equalsIgnoreCase("nigger")) {
                Collection<? extends Player> players = Bukkit.getOnlinePlayers();

                sender.sendMessage(
                        String.valueOf(
                                RaycastUtils.isEntityVisible((Player) sender, (LivingEntity) ((Player) sender).getNearbyEntities(2d, 2d, 2d).getFirst())
                        )
                );
            }
            return true;
        }
        return false;
    }
}