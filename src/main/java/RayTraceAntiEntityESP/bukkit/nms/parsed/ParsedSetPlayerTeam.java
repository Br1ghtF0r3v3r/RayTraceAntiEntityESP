package RayTraceAntiEntityESP.bukkit.nms.parsed;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scoreboard.Team;

import java.util.List;

public record ParsedSetPlayerTeam(
        String teamName,
        String teamAction,
        String playerAction,
        List<String> players,
        NamedTextColor color,
        Component prefix,
        Component suffix,
        Team.OptionStatus nametagVisibility
) {}
