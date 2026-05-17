package RayTraceAntiEntityESP.bukkit;

import RayTraceAntiEntityESP.bukkit.commands.CommandsHandler;
import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.listener.EventListener;
import RayTraceAntiEntityESP.bukkit.commands.TabCompletion;
import RayTraceAntiEntityESP.bukkit.manager.licenses.LicenseValidator;
import RayTraceAntiEntityESP.bukkit.manager.licenses.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;


public final class Main extends JavaPlugin {

    public static Main plugin;

    @Override
    public void onEnable() {
        plugin = this;

        reloadConfigAll();

        if (!LicenseValidator.verifyLicense(this)) {
            getLogger().severe("License verification failed! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (!SessionManager.startSession(LicenseValidator.MEMBER_ID, this)) {
            getLogger().severe("License session failed! Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(new EventListener(), this);
        registerCommands();
        getLogger().info("RayTraceEntityESP enabled successfully!");

    }

    @Override
    public void onDisable() {
        SessionManager.endSession();

        getLogger().info("RayTraceEntityESP disabled.");
    }

    public void reloadConfigAll() {
        saveDefaultConfig();
        reloadConfig();
        Config.setConfig();
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