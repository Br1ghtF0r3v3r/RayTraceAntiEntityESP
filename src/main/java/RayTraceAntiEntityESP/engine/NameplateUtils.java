package RayTraceAntiEntityESP.engine;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class NameplateUtils {
    private static Team getTeam(Entity entity) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String entry = entity.getScoreboardEntryName();
        return scoreboard.getEntryTeam(entry);
    }

    private static Team.OptionStatus getTeamVisibility(Entity entity) {
        return getTeamVisibility(getTeam(entity));
    }

    private static Team.OptionStatus getTeamVisibility(Team team) {
        if (team == null) return Team.OptionStatus.ALWAYS;

        return team.getOption(Team.Option.NAME_TAG_VISIBILITY);
    }

    public static boolean isVisible(Player viewer, Entity entity) {
        Team viewerTeam = getTeam(viewer);
        Team entityTeam = getTeam(entity);
        boolean onSameTeam = (viewerTeam != null && viewerTeam.equals(entityTeam));
        Team.OptionStatus entityVisibilityStatus = getTeamVisibility(entityTeam);

        return switch (entityVisibilityStatus) {
            case ALWAYS -> true;
            case NEVER -> false;
            case FOR_OWN_TEAM -> !onSameTeam;
            case FOR_OTHER_TEAMS -> onSameTeam;
            default -> true;
        };
    }

    public static void spawnNameplate(Location location, String name) {
        TextDisplay textDisplay = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        textDisplay.text(
                Component.text(name)
        );

        textDisplay.setBillboard(TextDisplay.Billboard.CENTER); // Makes it rotate to face players
        textDisplay.setBackgroundColor(Color.fromARGB(128, 0, 0, 0)); // Transparent background
        textDisplay.setShadowed(true);
    }

    public static void spawnNameplate(Entity entity) {
        spawnNameplate(entity.getLocation().add(0, entity.getHeight() + 0.5, 0), entity.getName());
    }
}
