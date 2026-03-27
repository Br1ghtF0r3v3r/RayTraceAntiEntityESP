package RayTraceAntiEntityESP.misc;

import org.bukkit.entity.Player;

import java.time.Duration;

import static RayTraceAntiEntityESP.misc.StringFormat.formatToComponent;
import static net.kyori.adventure.title.Title.Times.times;
import static net.kyori.adventure.title.Title.title;

public class Title {

    public static void showTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {

        net.kyori.adventure.title.Title.Times times = times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L));
        net.kyori.adventure.title.Title titleLast = title(formatToComponent(player, title), formatToComponent(player, subtitle), times);

        player.showTitle(titleLast);
    }

}
