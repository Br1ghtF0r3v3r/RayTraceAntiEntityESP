package RayTraceAntiEntityESP.bukkit;

import RayTraceAntiEntityESP.bukkit.commands.CommandsHandler;
import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.listener.EventListener;
import RayTraceAntiEntityESP.bukkit.commands.TabCompletion;
import RayTraceAntiEntityESP.bukkit.utils.VersionChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    public static Main plugin;

    @Override
    public void onEnable() {
        plugin = this;

        reloadConfigAll();

        Bukkit.getPluginManager().registerEvents(new EventListener(), this);
        registerCommands();
        VersionChecker.check();
        getLogger().info("RayTraceEntityESP enabled.");
    }

    @Override
    public void onDisable() {

        getLogger().info("RayTraceEntityESP disabled.");
    }

    public void reloadConfigAll() {
        saveDefaultConfig();
        Config.migrateConfigIfNeeded();
        reloadConfig();
        Config.setConfig();
        RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager.load();
    }

    @SuppressWarnings("deprecation")
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