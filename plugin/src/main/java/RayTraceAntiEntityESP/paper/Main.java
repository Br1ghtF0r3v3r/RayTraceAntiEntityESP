package RayTraceAntiEntityESP.paper;

import RayTraceAntiEntityESP.paper.commands.CommandsHandler;
import RayTraceAntiEntityESP.paper.commands.TabCompletion;
import RayTraceAntiEntityESP.paper.compatibility.PacketEventsBridge;
import RayTraceAntiEntityESP.paper.config.Config;
import RayTraceAntiEntityESP.paper.config.ExcludeBypassManager;
import RayTraceAntiEntityESP.paper.engine.RayTraceEngine;
import RayTraceAntiEntityESP.paper.listener.EventListener;
import RayTraceAntiEntityESP.paper.manager.EventManager;
import RayTraceAntiEntityESP.paper.nms.NmsAdapterFactory;
import RayTraceAntiEntityESP.paper.scheduler.RegionOwnershipChecker;
import RayTraceAntiEntityESP.paper.scheduler.SchedulerAdapterFactory;
import RayTraceAntiEntityESP.paper.utils.EntityIdentityCache;
import RayTraceAntiEntityESP.paper.utils.RealEntityCache;
import RayTraceAntiEntityESP.paper.utils.TeamUtils;
import RayTraceAntiEntityESP.paper.utils.VersionChecker;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    public static Main plugin;

    @Override
    public void onLoad() {
        plugin = this;

        SchedulerAdapterFactory.init(this);
        RegionOwnershipChecker.init();
        NmsAdapterFactory.init();
    }

    @Override
    public void onEnable() {
        reloadConfigAll();

        Bukkit.getPluginManager().registerEvents(new EventListener(), this);
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                RealEntityCache.add(entity.getUniqueId());
            }
        }
        PacketEventsBridge.registerIfAvailable();
        registerCommands();
        VersionChecker.check();
        getLogger().info("RayTraceEntityESP enabled on " + (SchedulerAdapterFactory.isFolia() ? "Folia" : "Paper") + ".");

        int pluginId = 32643;
        new Metrics(this, pluginId);
    }

    @Override
    public void onDisable() {
        RayTraceEngine.shutdownCleanup();

        SchedulerAdapterFactory.get().cancelAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            EventManager.uninjectPlayer(player);
        }
        EntityIdentityCache.clearAll();
        RealEntityCache.clearAll();
        TeamUtils.clearAll();
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