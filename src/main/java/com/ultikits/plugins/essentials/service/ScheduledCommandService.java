package com.ultikits.plugins.essentials.service;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.PostConstruct;
import com.ultikits.ultitools.annotations.Service;
import lombok.extern.slf4j.Slf4j;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for executing scheduled console commands.
 * <p>
 * 定时执行控制台命令的服务。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@Slf4j
@Service
public class ScheduledCommandService {

    @Autowired
    private EssentialsConfig config;

    @Autowired
    private UltiToolsPlugin plugin;

    private Plugin bukkitPlugin;
    private final List<BukkitTask> tasks = new ArrayList<>();

    @PostConstruct
    public void init() {
        this.bukkitPlugin = Bukkit.getPluginManager().getPlugin("UltiTools");
        startTasks();
    }

    /**
     * Start all scheduled command tasks.
     */
    public void startTasks() {
        if (!config.isScheduledCommandsEnabled()) {
            return;
        }

        for (String entry : config.getScheduledCommands()) {
            int colonIndex = entry.indexOf(':');
            if (colonIndex <= 0) {
                log.warn(plugin.i18n("essentials.log.scheduled_missing_interval"), entry);
                continue;
            }

            int interval;
            try {
                interval = Integer.parseInt(entry.substring(0, colonIndex));
            } catch (NumberFormatException e) {
                log.warn(plugin.i18n("essentials.log.scheduled_invalid_interval"), entry);
                continue;
            }

            if (interval <= 0) {
                log.warn(plugin.i18n("essentials.log.scheduled_interval_not_positive"), entry);
                continue;
            }

            String command = entry.substring(colonIndex + 1).trim();
            if (command.isEmpty()) {
                log.warn(plugin.i18n("essentials.log.scheduled_empty_command"), entry);
                continue;
            }

            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }.runTaskTimer(bukkitPlugin, interval * 20L, interval * 20L);

            tasks.add(task);
            log.info(plugin.i18n("essentials.log.scheduled_started"), interval, command);
        }
    }

    /**
     * Stop all scheduled tasks.
     */
    public void shutdown() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    /**
     * Reload: stop and restart all tasks.
     */
    public void reload() {
        shutdown();
        startTasks();
    }

    /**
     * Parse a scheduled command entry. Returns interval or null if invalid.
     * Exposed for testing.
     *
     * @param entry format "interval:command"
     * @return int array with [interval] or null if invalid
     */
    public static int[] parseEntry(String entry) {
        int colonIndex = entry.indexOf(':');
        if (colonIndex <= 0) {
            return null;
        }
        try {
            int interval = Integer.parseInt(entry.substring(0, colonIndex));
            if (interval <= 0) return null;
            return new int[]{interval};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
