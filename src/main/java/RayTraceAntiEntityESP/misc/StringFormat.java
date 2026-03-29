package RayTraceAntiEntityESP.misc;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class StringFormat {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final Map<String, String> COLOR_CODES = new LinkedHashMap<>();
    static {
        COLOR_CODES.put("&0", "<black>");
        COLOR_CODES.put("&1", "<dark_blue>");
        COLOR_CODES.put("&2", "<dark_green>");
        COLOR_CODES.put("&3", "<dark_aqua>");
        COLOR_CODES.put("&4", "<dark_red>");
        COLOR_CODES.put("&5", "<dark_purple>");
        COLOR_CODES.put("&6", "<gold>");
        COLOR_CODES.put("&7", "<gray>");
        COLOR_CODES.put("&8", "<dark_gray>");
        COLOR_CODES.put("&9", "<blue>");
        COLOR_CODES.put("&a", "<green>");
        COLOR_CODES.put("&b", "<aqua>");
        COLOR_CODES.put("&c", "<red>");
        COLOR_CODES.put("&d", "<light_purple>");
        COLOR_CODES.put("&e", "<yellow>");
        COLOR_CODES.put("&f", "<white>");
        COLOR_CODES.put("&l", "<bold>");
        COLOR_CODES.put("&o", "<italic>");
        COLOR_CODES.put("&n", "<underlined>");
        COLOR_CODES.put("&m", "<strikethrough>");
        COLOR_CODES.put("&k", "<obfuscated>");
        COLOR_CODES.put("&r", "<reset>");
    }

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("§x§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])");
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("§([0-9a-fk-orA-FK-OR])");

    public static String applyColorCodes(String text) {
        text = LEGACY_HEX_PATTERN.matcher(text).replaceAll("<#$1$2$3$4$5$6>");
        text = LEGACY_CODE_PATTERN.matcher(text).replaceAll("&$1");
        text = HEX_PATTERN.matcher(text).replaceAll("<#$1>");
        for (Map.Entry<String, String> entry : COLOR_CODES.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }

    public static String formatToString(CommandSender sender, String text) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin plugin = pluginManager.getPlugin("PlaceholderAPI");
        if (plugin != null && pluginManager.isPluginEnabled("PlaceholderAPI")) {
            if (PlaceholderAPI.containsPlaceholders(text))
                text = PlaceholderAPI.setPlaceholders(sender instanceof Player p ? p : null, text);
        }
        text = applyColorCodes(text);
        return LEGACY_SERIALIZER.serialize(MINI_MESSAGE.deserialize(text));
    }

    public static Component formatToComponent(CommandSender sender, String text) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin plugin = pluginManager.getPlugin("PlaceholderAPI");
        if (plugin != null && pluginManager.isPluginEnabled("PlaceholderAPI")) {
            if (PlaceholderAPI.containsPlaceholders(text))
                text = PlaceholderAPI.setPlaceholders(sender instanceof Player p ? p : null, text);
        }
        text = applyColorCodes(text);
        return MINI_MESSAGE.deserialize(text);
    }

    public static void debug(String text) {Bukkit.broadcast(Component.text(text));}

}