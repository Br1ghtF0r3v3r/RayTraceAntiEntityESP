package RayTraceAntiEntityESP;

import RayTraceAntiEntityESP.commands.CommandsHandler;
import RayTraceAntiEntityESP.manager.engine.RayTraceManager;
import RayTraceAntiEntityESP.listener.EventListener;
import RayTraceAntiEntityESP.commands.TabCompletion;
import RayTraceAntiEntityESP.utils.FakeNameDisplayUtils;
import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import static RayTraceAntiEntityESP.config.Config.setConfig;

public final class Main extends JavaPlugin {

    public static RayTraceAntiEntityESP.Main plugin;

    @Override
    public void onEnable() {

        plugin = this;

        reloadConfigAll();

        getServer().getPluginManager().registerEvents(new EventListener(), this);
        PacketEvents.getAPI().getEventManager().registerListener(new EventListener());
        registerCommands();

        PacketEvents.getAPI().init();
        RayTraceManager.startRayTraceChecking();
        FakeNameDisplayUtils.startFakeNameDisplayUpdating();

        getLogger().info("RayTraceEntityESP enabled successfully!");
    }

    @Override
    public void onDisable() {

        PacketEvents.getAPI().terminate();
        getLogger().info("RayTraceEntityESP disabled.");

    }

    public void reloadConfigAll() {
        saveDefaultConfig();
        reloadConfig();
        setConfig();
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