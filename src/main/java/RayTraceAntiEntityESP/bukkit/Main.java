package RayTraceAntiEntityESP.bukkit;

import RayTraceAntiEntityESP.bukkit.commands.CommandsHandler;
import RayTraceAntiEntityESP.bukkit.config.Config;
import RayTraceAntiEntityESP.bukkit.listener.EventListener;
import RayTraceAntiEntityESP.bukkit.commands.TabCompletion;
import RayTraceAntiEntityESP.bukkit.manager.licenses.LicenseManager;
import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    public static Main plugin;

    @Override
    public void onEnable() {
        plugin = this;
        reloadConfigAll();

        if (!LicenseManager.verifyLicense(Config.licenseKey, this)) {
            getLogger().severe("License verification failed! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PacketEvents.getAPI().init();
        getServer().getPluginManager().registerEvents(new EventListener(), this);
        PacketEvents.getAPI().getEventManager().registerListener(new EventListener());
        registerCommands();
        getLogger().info("RayTraceEntityESP enabled successfully!");
    }

    @Override
    public void onDisable() {

        PacketEvents.getAPI().terminate();
        getLogger().info("RayTraceEntityESP disabled.");

    }

    @Override
    public void onLoad() {

        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();

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