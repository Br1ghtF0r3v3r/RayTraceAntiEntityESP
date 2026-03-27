package RayTraceAntiEntityESP.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class TabCompletion implements TabCompleter {

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, Command command, @NonNull String alias, String @NonNull [] args) {

        if (command.getName().equalsIgnoreCase("rtaee")) {
            if (args.length == 1) {
                return List.of("reload");
            }
        }
        return null;
    }
}