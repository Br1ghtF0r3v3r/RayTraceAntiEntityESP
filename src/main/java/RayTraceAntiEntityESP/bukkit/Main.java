package RayTraceAntiEntityESP.bukkit;

import RayTraceAntiEntityESP.bukkit.commands.CommandsHandler;
import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.listener.EventListener;
import RayTraceAntiEntityESP.bukkit.commands.TabCompletion;
import RayTraceAntiEntityESP.bukkit.manager.licenses.LicenseManager;
import RayTraceAntiEntityESP.bukkit.manager.licenses.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Main extends JavaPlugin {

    public static Main plugin;
    public static ExecutorService executor;

    @Override
    public void onEnable() {
        plugin = this;

        reloadConfigAll();

        if (!SessionManager.startSession(Config.licenseKey, this)) {
            plugin.getLogger().severe("License session failed! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (!LicenseManager.verifyLicense(Config.licenseKey, this)) {
            getLogger().severe("License verification failed! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(new EventListener(), this);
        registerCommands();
        getLogger().info("RayTraceEntityESP enabled successfully!");

        executor = Executors.newFixedThreadPool(Config.asyncThreads, r -> {
            Thread t = new Thread(r, "RTAEE-Worker");
            t.setDaemon(true);
            return t;
        });

    }

    @Override
    public void onDisable() {

        SessionManager.endSession();

        if (executor != null) {
            executor.shutdownNow();
        }

        getLogger().info("RayTraceEntityESP disabled.");

    }

    public void reloadConfigAll() {
        saveDefaultConfig();
        reloadConfig();
        Config.setConfig();
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