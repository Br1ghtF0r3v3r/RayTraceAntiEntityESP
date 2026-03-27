package RayTraceAntiEntityESP;

import RayTraceAntiEntityESP.commands.CommandsHandler;
import RayTraceAntiEntityESP.listener.EventListener;
import RayTraceAntiEntityESP.commands.TabCompletion;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class Main extends JavaPlugin {

    public static RayTraceAntiEntityESP.Main plugin;
    public static FileConfiguration messagesConfig;

    @Override
    public void onEnable() {

        plugin = this;

        reloadConfigAll();

        getServer().getPluginManager().registerEvents(new EventListener(), this);
        registerCommands();

        getLogger().info("RayTraceEntityESP enabled successfully!");
    }

    @Override
    public void onDisable() {

        getLogger().info("RayTraceEntityESP disabled.");
    }

    public void reloadConfigAll() {
        saveDefaultConfig();
        reloadConfig();
        reloadMessage();
    }

    public void reloadMessage() {
        File messageFile = new File(plugin.getDataFolder(), "message.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messageFile);
        final InputStream defConfigStream = getResource("message.yml");
        if (defConfigStream == null) {
            return;
        }
        messagesConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8)));
    }

    public void registerCommands() {
        CommandsHandler handler = new CommandsHandler();
        TabCompletion tabCompleter = new TabCompletion();

        var commands = getDescription().getCommands();

        for (String cmdName : commands.keySet()) {
            PluginCommand command = getCommand(cmdName);
            if (command != null) {
                command.setExecutor(handler);
                command.setTabCompleter(tabCompleter);
            } else {
                getLogger().severe("Command '" + cmdName + "' is missing in plugin.yml!");
            }
        }
    }
}