package com.ultikits.plugins.essentials;

import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScheduledCommandService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
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
 * (UltiKits/UltiEssentials#28). This module adds no {@code onUnregister()} work: unload does not yet
 * cancel those services' tasks (UltiKits/UltiEssentials#43).
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
        getLogger().info(i18n("UltiEssentials 已启用！"));
        return true;
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
