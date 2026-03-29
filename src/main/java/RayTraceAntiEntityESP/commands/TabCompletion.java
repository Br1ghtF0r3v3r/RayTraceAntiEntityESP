package RayTraceAntiEntityESP.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public class TabCompletion implements TabCompleter {

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, Command command, @NonNull String alias, String @NonNull [] args) {

        if (command.getName().equalsIgnoreCase("raytrace_anti_entity_esp")) {
            if (args.length == 1) {
                return List.of("reload", "enabled");
            }
            if (args.length == 2) {
                if (args[0].equalsIgnoreCase("enabled")) {
                    return List.of("true", "false");
                }
            }
        }
        return null;
    }
}