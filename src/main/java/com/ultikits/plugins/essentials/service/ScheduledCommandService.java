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
     * <p>
     * Each task dispatches by re-reading {@link #commandAt(int)} on every run rather than closing over
     * the command text parsed here: {@code init()} runs from the IoC container's {@code @PostConstruct}
     * pass, which completes before {@code registerSelf()} even starts (the framework assembles and
     * refreshes the whole container first), so a text captured here would still be the pre-materialize
     * value -- the one {@code UltiEssentials#writeConfigTextInServerLanguage()} is about to replace --
     * for as long as this task keeps running. Reading it fresh at dispatch time means the very first
     * run, which cannot fire before the plugin has finished loading, already sees the materialized text
     * (maintainer decision 2026-09-25, UltiKits/UltiEssentials#26).
     */
    public void startTasks() {
        if (!config.isScheduledCommandsEnabled()) {
            return;
        }

        List<String> entries = config.getScheduledCommands();
        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index);
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

            final int scheduledIndex = index;
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    String current = commandAt(scheduledIndex);
                    if (current != null) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), current);
                    }
                }
            }.runTaskTimer(bukkitPlugin, interval * 20L, interval * 20L);

            tasks.add(task);
            log.info(plugin.i18n("essentials.log.scheduled_started"), interval, command);
        }
    }

    /**
     * The command text of the scheduled entry at {@code index} in the configuration's current
     * {@code features.scheduled-commands.commands}, re-parsed fresh on every call so a task dispatches
     * whatever the file holds now -- including a value {@code writeConfigTextInServerLanguage()}
     * rewrote after this task was scheduled. {@code null} when the entry no longer exists or no longer
     * parses (the list changed since this task was scheduled; the next {@code /ul reload} rebuilds the
     * tasks from the new list).
     *
     * @param index the entry's position in {@link EssentialsConfig#getScheduledCommands()} when this
     *              task was scheduled
     * @return the command text, or {@code null}
     */
    private String commandAt(int index) {
        List<String> entries = config.getScheduledCommands();
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        String entry = entries.get(index);
        int colonIndex = entry.indexOf(':');
        if (colonIndex <= 0) {
            return null;
        }
        String command = entry.substring(colonIndex + 1).trim();
        return command.isEmpty() ? null : command;
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
