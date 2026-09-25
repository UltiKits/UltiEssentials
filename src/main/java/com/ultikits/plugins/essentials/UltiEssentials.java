package com.ultikits.plugins.essentials;

import com.ultikits.plugins.essentials.config.ConfigTextDefaults;
import com.ultikits.plugins.essentials.config.MotdConfig;
import com.ultikits.plugins.essentials.config.TabBarConfig;
import com.ultikits.plugins.essentials.commands.HideCommand;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.RemovedConfigKeys;
import com.ultikits.plugins.essentials.service.EntityIdBackfillService;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScheduledCommandService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.service.TeleportService;
import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * UltiEssentials - Essential commands and features for Minecraft servers.
 * <p>
 * This plugin provides common essential commands like teleportation, player status,
 * and server management features built on the UltiTools-API framework.
 * </p>
 * <p>
 * All services are automatically initialized by the IoC container via {@code @PostConstruct}.
 * </p>
 * <p>
 * Reload and unload are performed by the framework's final {@code reloadSelf()} and
 * {@code unregisterSelf()}. {@code /ul reload UltiEssentials} re-reads this module's configuration
 * files (for example {@code config/essentials.yml}) into the running configuration beans, then
 * {@link #onReload()} restarts the scheduled-command, scoreboard and name-prefix services against
 * the re-read values, so their enable flags, intervals and command list apply without a restart
 * (UltiKits/UltiEssentials#28). Start-up and every reload also warn about any setting this module
 * has removed that is still in the operator's file (UltiKits/UltiEssentials#27). Unloading it, for
 * example with {@code /upm uninstall UltiEssentials}, runs {@link #onUnregister()} first, which
 * stops every repeating task this module started (UltiKits/UltiEssentials#43), and then the
 * framework's command and listener unregistration.
 * </p>
 *
 * @author wisdommen
 * @author UltiKits Team
 * @version 1.0.0
 */
@UltiToolsModule(scanBasePackages = {"com.ultikits.plugins.essentials"})
public class UltiEssentials extends UltiToolsPlugin {

    @Override
    public boolean registerSelf() {
        // All services are automatically initialized by IoC container via @PostConstruct
        repairStoredPrimaryKeys();
        warnAboutRemovedSettings();
        if (writeConfigTextInServerLanguage()) {
            // The container's @PostConstruct pass scheduled the commands before this point, each task
            // holding the text it was scheduled with; restart them so they send the text just written.
            reloadService(ScheduledCommandService.class, ScheduledCommandService::reload);
        }
        getLogger().info(i18n("essentials.log.enabled"));
        return true;
    }

    /**
     * Gives records written before UltiKits/UltiEssentials#34 was fixed the primary key they were
     * saved without, so deleting a home or warp, lifting a ban, or removing a container lock works
     * on data this module wrote earlier.
     * <p>
     * Runs here rather than from a service's own {@code @PostConstruct} so that it happens after
     * every service has its data operator, in one place an operator can find in the log. A failure
     * is logged and enabling continues: refusing to load the module would be a worse outcome than
     * leaving the records as they are, and the repair is idempotent, so the next start-up retries.
     * <p>
     * 为 #34 修复之前写入的记录补上缺失的主键；失败只记录日志，不阻止模块启用，下次启动会重试。
     */
    private void repairStoredPrimaryKeys() {
        EntityIdBackfillService repair = getContext().getBean(EntityIdBackfillService.class);
        if (repair == null) {
            getLogger().warn(i18n("essentials.log.repair_unavailable"));
            return;
        }
        try {
            repair.run();
        } catch (RuntimeException e) {
            getLogger().error(e, i18n("essentials.log.repair_failed"));
        }
    }

    /**
     * Writes the scoreboard title and lines, the tab-list header and footer, the MOTD lines, the
     * scheduled-command text and the death-punishment command text in the server's language, when each
     * is still built-in text -- a default an earlier version shipped, or this jar's own text for it in
     * any language -- and saves the file(s) that changed; any other value is the operator's and is kept
     * (maintainer decision 2026-09-25, UltiKits/UltiEssentials#26). Runs at start-up and on every
     * {@code /ul reload}, after the framework has re-read the configuration and rebuilt the language;
     * idempotent, so a second call writes nothing.
     * <p>
     * 计分板标题/内容、Tab 栏头尾、MOTD、定时命令与死亡惩罚命令中的内置文本按服务器语言写入并保存；运维自定义的值保留。
     *
     * @return whether {@code config/essentials.yml}'s values changed, which includes the scheduled-command
     *         text the running tasks were scheduled with
     */
    private boolean writeConfigTextInServerLanguage() {
        EssentialsConfig essentials = getContext().getBean(EssentialsConfig.class);
        boolean essentialsChanged = essentials != null
                && essentials.materializeText(ConfigTextDefaults.jarLanguage(EssentialsConfig.class, getLanguageCode())::getLocalizedText);
        if (essentialsChanged) {
            saveMaterialized(essentials);
        }
        TabBarConfig tabBar = getContext().getBean(TabBarConfig.class);
        if (tabBar != null
                && tabBar.materializeText(ConfigTextDefaults.jarLanguage(TabBarConfig.class, getLanguageCode())::getLocalizedText)) {
            saveMaterialized(tabBar);
        }
        MotdConfig motd = getContext().getBean(MotdConfig.class);
        if (motd != null
                && motd.materializeText(ConfigTextDefaults.jarLanguage(MotdConfig.class, getLanguageCode())::getLocalizedText)) {
            saveMaterialized(motd);
        }
        return essentialsChanged;
    }

    private void saveMaterialized(AbstractConfigEntity config) {
        try {
            config.save();
        } catch (IOException e) {
            getLogger().warn(String.format(i18n("essentials.log.config_default_save_failed"), config.getConfigFilePath(),
                    e.getMessage()));
        }
    }

    /**
     * Warns once for each setting this module has removed that is still in the operator's
     * {@code config/essentials.yml}, naming the key and where its job went (UltiKits/UltiEssentials#27).
     * <p>
     * Runs at start-up and again on every {@code /ul reload}, after the framework has re-read the
     * file, so a removed key copied back in from an old backup is reported without a restart.
     * <p>
     * 启动与每次重载时，对运维文件中仍残留的已删除配置项各报一条警告。
     */
    private void warnAboutRemovedSettings() {
        for (String warning : RemovedConfigKeys.warningsFor(getContext().getBean(EssentialsConfig.class), this)) {
            getLogger().warn(warning);
        }
    }

    /**
     * Restarts the three task-owning services against the configuration the framework has just
     * re-read. Each service's {@code reload()} cancels its running tasks and starts them again only
     * if its feature is still enabled, so turning a feature on, off, or changing its interval or
     * command list all take effect (UltiKits/UltiEssentials#28).
     * <p>
     * Each service is reloaded on its own: a service whose {@code reload()} throws is logged at
     * SEVERE with its name and the other services are still reloaded. The framework's
     * {@code reloadSelf()} does not isolate this hook (UltiKits/UltiTools-Reborn#509).
     * <p>
     * 按重新读取的配置重启定时命令、计分板与头顶称号服务；任一服务重载失败只记录日志，不影响其他服务。
     */
    @Override
    protected void onReload() {
        warnAboutRemovedSettings();
        writeConfigTextInServerLanguage();
        reloadService(ScheduledCommandService.class, ScheduledCommandService::reload);
        reloadService(ScoreboardService.class, ScoreboardService::reload);
        reloadService(NamePrefixService.class, NamePrefixService::reload);
    }

    /**
     * Stops every repeating Bukkit task this module started, so unloading it really stops them
     * (UltiKits/UltiEssentials#43).
     * <p>
     * These tasks are owned by the {@code UltiTools} Bukkit plugin rather than by this module, so
     * Bukkit does not cancel them when this module is unloaded, and the framework's own unload path
     * can only cancel the tasks it created itself from {@code @Scheduled} methods. Without this
     * hook, {@code /upm uninstall UltiEssentials} reported the module uninstalled while its
     * configured console commands kept being dispatched, its sidebar and name-prefix timers kept
     * writing scoreboards and teams, and a teleport warmup already counting down still teleported
     * the player.
     * <p>
     * The hook is the extension point rather than {@code @PreDestroy} for three reasons: it runs
     * while this module's beans, commands and listeners are all still alive; its failure is reported
     * to whoever asked for the <em>uninstall</em>, whereas a {@code @PreDestroy} failure is logged by
     * the container and swallowed; and {@code PluginManager#unregisterSupersededVersions} calls
     * {@code unregisterSelf()} without closing the container, so {@code @PreDestroy} would not run
     * there at all.
     * <p>
     * That third path is also the one the second reason does <em>not</em> hold on, and the limit is
     * named here rather than left to be discovered. On the version-supersede path a failure thrown
     * from here is caught by {@code PluginManager#attemptPluginRegistration} and reported as the
     * <em>incoming</em> version failing to load, while this older version stays listed as loaded with
     * its commands, listeners and tasks already gone -- see
     * UltiKits/UltiTools-Reborn#528, and UltiKits/UltiTools-Reborn#506 for that path's other losses.
     * The rethrow is kept anyway: swallowing would let {@code /upm uninstall} report a clean removal
     * while a service's tasks are still running, which is the defect this hook exists to remove, and
     * every service is shut down before the throw either way. That path is not operator-reachable
     * today (#506 records the measurement).
     * <p>
     * Before the services, every vanished player is shown to everyone again and the vanish state is
     * forgotten ({@link #revealVanishedPlayers()}): the hides belong to the {@code UltiTools} Bukkit
     * plugin, so they would otherwise outlive the {@code /hide} command that lifts them.
     * <p>
     * Each service is shut down on its own, and every one is shut down even when an earlier one
     * fails -- a service left running is exactly the defect this hook exists to remove. The first
     * failure is then rethrown with any later one attached to it, so the unload is reported as
     * incomplete instead of reporting a success it did not perform. This mirrors what the
     * framework's own {@code unregisterSelf()} does with its three steps.
     * <p>
     * 卸载模块时停止本模块启动的全部重复任务；任一服务失败不影响其他服务，但失败会上报给调用方。
     */
    @Override
    protected void onUnregister() {
        Throwable failure = revealVanishedPlayers();
        failure = shutdownService(failure, ScheduledCommandService.class, ScheduledCommandService::shutdown);
        failure = shutdownService(failure, ScoreboardService.class, ScoreboardService::shutdown);
        failure = shutdownService(failure, NamePrefixService.class, NamePrefixService::shutdown);
        failure = shutdownService(failure, TeleportService.class, TeleportService::shutdown);
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        } else if (failure instanceof Error) {
            throw (Error) failure;
        }
    }

    /**
     * Shows every vanished player to everyone again and forgets the vanish state, so the unload does
     * not leave players hidden by a module that is no longer there to un-hide them (gate-1 WR-01 on
     * UltiKits/UltiEssentials#32). It runs first and inside the same barrier as the service
     * shutdowns: a failure here is returned to be rethrown once every service has been shut down, and
     * a service failure cannot stop the reveal.
     *
     * @return the failure raised while revealing, or {@code null}
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException") // deliberate cleanup barrier -- see shutdownService
    private static Throwable revealVanishedPlayers() {
        try {
            HideCommand.revealAllVanished();
        } catch (RuntimeException | Error e) {
            return e;
        }
        return null;
    }

    /**
     * Shuts one service down, collecting its failure onto {@code previousFailure} rather than
     * letting it replace, or be replaced by, another service's failure.
     * <p>
     * {@code shutdown} declares no checked exception, so catching {@code RuntimeException | Error}
     * covers every real case without the width of catching {@link Throwable}.
     * <p>
     * A service the container cannot resolve is a <em>collected failure</em>, not a skip. It is the
     * same shape as the defect this hook exists to remove: a service that is missing here is one
     * whose repeating tasks this unload did not stop, so returning quietly would let the unload
     * report a clean removal while those tasks go on running. It is reachable without anyone
     * noticing -- renaming a service, moving it out of {@code @UltiToolsModule}'s
     * {@code scanBasePackages}, or registering it under an interface type all leave the module
     * loading and its tasks starting from {@code @PostConstruct} while this hook silently stops
     * covering it. On a stock install it cannot fire: all four services are unconditional
     * {@code @Service} beans and this module declares no {@code @ConditionalOnConfig}, so this is a
     * guard against a future source change rather than a state an operator can configure into.
     * <p>
     * No container at all is different, and is not a failure: a module that never went through
     * {@code PluginManager#register} has no container, so no service bean was ever built and no
     * task was ever started. That case is reachable with a directly constructed instance
     * (UltiKits/UltiTools-Reborn#338), which is why {@code PluginManager#unregister} guards the
     * same way before closing the context.
     * <p>
     * Nothing is logged here. The collected failure is rethrown by {@link #onUnregister()}, and
     * whoever asked for the uninstall already reports it -- {@code /upm uninstall} logs it at SEVERE
     * naming this module and carries on, with any suppressed failure printed alongside it, and the
     * method reference that failed puts the service's own class in the frame that reaches that log.
     * A log call here would not only repeat that; it would give this barrier a way to fail, and a
     * failure raised while reporting another one replaces it and abandons the services not yet shut
     * down.
     *
     * @param previousFailure the failure collected from an earlier service, or {@code null}
     * @param type            the service's bean type
     * @param shutdown        the service's own shutdown
     * @return the failure to carry on with
     */
    @SuppressWarnings("PMD.AvoidCatchingGenericException") // deliberate cleanup barrier -- see javadoc above
    private <T> Throwable shutdownService(Throwable previousFailure, Class<T> type, Consumer<T> shutdown) {
        if (getContext() == null) {
            return previousFailure;
        }
        T service = getContext().getBean(type);
        if (service == null) {
            return collect(previousFailure, new IllegalStateException("The unload could not reach "
                    + type.getSimpleName() + "; any repeating task it started is still running"));
        }
        try {
            shutdown.accept(service);
        } catch (RuntimeException | Error e) {
            return collect(previousFailure, e);
        }
        return previousFailure;
    }

    /**
     * Attaches {@code next} to the failure collected so far, or makes it the collected failure when
     * there is none yet.
     * <p>
     * Attaching a failure to itself is skipped: {@link Throwable#addSuppressed} rejects that, and
     * the {@link IllegalArgumentException} it would raise would escape this hook and abandon the
     * services not yet shut down -- which two services throwing one shared static exception instance
     * would otherwise produce.
     *
     * @param previousFailure the failure collected so far, or {@code null}
     * @param next            the failure to attach
     * @return the failure to carry on with
     */
    private static Throwable collect(Throwable previousFailure, Throwable next) {
        if (previousFailure == null) {
            return next;
        }
        if (previousFailure != next) {
            previousFailure.addSuppressed(next);
        }
        return previousFailure;
    }

    /**
     * Reloads one service, leaving the others to be reloaded whatever this one does.
     * <p>
     * A service the container cannot resolve is reported as a warning rather than skipped in
     * silence -- the same defect class {@link #shutdownService} was corrected for (gate 1 WR-02),
     * found by sweeping this repository for it. It is reported rather than thrown because
     * {@code reloadSelf()} does not isolate {@link #onReload()}
     * (UltiKits/UltiTools-Reborn#509), so throwing here would stop the services after it from
     * reloading at all -- and, because {@code PluginManager#reload()} loops the modules with no
     * per-module guard either, it would stop every module <em>after</em> this one from reloading
     * too. A warning that names the service is what this hook can give without that cost, and it
     * matches {@link #repairStoredPrimaryKeys()}'s precedent in this same class. Note that
     * {@code /ul reload <name>} replies success unconditionally, so this warning reaches the console
     * and not the sender (UltiKits/UltiTools-Reborn#529).
     * <p>
     * No {@code getContext() == null} guard here, unlike {@link #shutdownService}, and the asymmetry
     * is deliberate: {@code pluginList.add} has one call site, inside
     * {@code PluginManager#onPluginRegistered}, reached only after the container is assembled, so
     * every instance the framework reloads has one. {@code unregisterSelf()} is different -- it is
     * also reachable with a directly constructed instance (UltiKits/UltiTools-Reborn#338), which is
     * why only the unload twin guards.
     * <p>
     * On a stock install this warning cannot fire: all four services are unconditional
     * {@code @Service} beans and this module declares no {@code @ConditionalOnConfig}, so
     * {@code getBean} returns null only after a source change -- a renamed service, one moved out of
     * {@code scanBasePackages}, or one registered under an interface type. It is a guard against
     * that, not a state an operator can configure into.
     *
     * @param type   the service's bean type
     * @param reload the service's own reload
     */
    private <T> void reloadService(Class<T> type, Consumer<T> reload) {
        T service = getContext().getBean(type);
        if (service == null) {
            getLogger().warn(String.format(i18n("essentials.log.reload_unreachable"), type.getSimpleName()));
            return;
        }
        try {
            reload.accept(service);
        } catch (RuntimeException e) {
            getLogger().error(e, String.format(i18n("essentials.log.reload_failed"), type.getSimpleName()));
        }
    }
}
