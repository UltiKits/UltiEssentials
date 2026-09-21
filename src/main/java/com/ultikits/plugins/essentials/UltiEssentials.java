package com.ultikits.plugins.essentials;

import com.ultikits.plugins.essentials.service.EntityIdBackfillService;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScheduledCommandService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.service.TeleportService;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

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
 * (UltiKits/UltiEssentials#28). Unloading it, for example with
 * {@code /upm uninstall UltiEssentials}, runs {@link #onUnregister()} first, which stops every
 * repeating task this module started (UltiKits/UltiEssentials#43), and then the framework's command
 * and listener unregistration.
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
        getLogger().info(i18n("UltiEssentials 已启用！"));
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
            getLogger().warn("The stored-primary-key repair is unavailable; records written before "
                    + "UltiKits/UltiEssentials#34 was fixed were left as they are");
            return;
        }
        try {
            repair.run();
        } catch (RuntimeException e) {
            getLogger().error(e, "The stored-primary-key repair failed; records written before "
                    + "UltiKits/UltiEssentials#34 was fixed were left as they are, and the repair "
                    + "will run again on the next start-up");
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
        Throwable failure = null;
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
     * covering it.
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

    private <T> void reloadService(Class<T> type, Consumer<T> reload) {
        T service = getContext().getBean(type);
        if (service == null) {
            return;
        }
        try {
            reload.accept(service);
        } catch (RuntimeException e) {
            getLogger().error(e, "Reload of " + type.getSimpleName()
                    + " failed; the other services were still reloaded");
        }
    }
}
