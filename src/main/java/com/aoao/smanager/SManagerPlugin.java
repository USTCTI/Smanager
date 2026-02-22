package com.aoao.smanager;

import com.aoao.smanager.monitor.MetricsCollector;
import com.aoao.smanager.monitor.MetricsSnapshot;
import com.aoao.smanager.web.WebServer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SManagerPlugin extends JavaPlugin {
    private final AtomicReference<MetricsSnapshot> snapshot = new AtomicReference<>();
    private MetricsCollector collector;
    private ScheduledTask task;
    private WebServer webServer;
    private long intervalMs;
    private int port;
    private String apiToken;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();
        collector = new MetricsCollector();
        snapshot.set(collector.sample());
        task = getServer().getAsyncScheduler().runAtFixedRate(this, scheduledTask -> snapshot.set(collector.sample()), intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        webServer = new WebServer(this::currentSnapshotJson, port, apiToken, this.getSLF4JLogger());
        webServer.start();
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        if (webServer != null) webServer.stop();
    }

    private void loadConfigValues() {
        intervalMs = getConfig().getLong("monitor.intervalMillis", 1000);
        port = getConfig().getInt("web.port", 25566);
        apiToken = getConfig().getString("auth.token", "");
    }

    private String currentSnapshotJson() {
        MetricsSnapshot s = snapshot.get();
        return MetricsSnapshot.toJson(s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("smanager")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("smanager.admin")) {
                    sender.sendMessage("No permission");
                    return true;
                }
                reloadConfig();
                loadConfigValues();
                if (task != null) task.cancel();
                task = getServer().getAsyncScheduler().runAtFixedRate(this, scheduledTask -> snapshot.set(collector.sample()), intervalMs, intervalMs, TimeUnit.MILLISECONDS);
                if (webServer != null) {
                    webServer.stop();
                    webServer = new WebServer(this::currentSnapshotJson, port, apiToken, this.getSLF4JLogger());
                    webServer.start();
                }
                sender.sendMessage("SManager reloaded");
                return true;
            }
            sender.sendMessage("Usage: /smanager reload");
            return true;
        }
        return false;
    }
}
