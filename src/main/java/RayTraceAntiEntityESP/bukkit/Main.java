package RayTraceAntiEntityESP.bukkit;

import RayTraceAntiEntityESP.bukkit.commands.CommandsHandler;
import RayTraceAntiEntityESP.bukkit.compatibility.PacketEventsBridge;
import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.config.ExcludeBypassManager;
import RayTraceAntiEntityESP.bukkit.engine.RayTraceEngine;
import RayTraceAntiEntityESP.bukkit.listener.EventListener;
import RayTraceAntiEntityESP.bukkit.commands.TabCompletion;
import RayTraceAntiEntityESP.bukkit.manager.events.EventManager;
import RayTraceAntiEntityESP.bukkit.utils.VersionChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    public static Main plugin;

    @Override
    public void onLoad() {
        plugin = this;
    }

    @Override
    public void onEnable() {
        reloadConfigAll();

        Bukkit.getPluginManager().registerEvents(new EventListener(), this);
        PacketEventsBridge.registerIfAvailable();
        registerCommands();
        VersionChecker.check();
        getLogger().info("RayTraceEntityESP enabled.");
    }

    @Override
    public void onDisable() {
        RayTraceEngine.killTask();
        for (Player player : Bukkit.getOnlinePlayers()) {
            EventManager.uninjectPlayer(player);
        }
        getLogger().info("RayTraceEntityESP disabled.");
    }

    public void reloadConfigAll() {
        saveDefaultConfig();
        Config.migrateConfigIfNeeded();
        reloadConfig();
        Config.setConfig();
        ExcludeBypassManager.load();
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