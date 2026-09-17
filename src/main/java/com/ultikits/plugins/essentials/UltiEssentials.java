package com.ultikits.plugins.essentials;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.UltiToolsModule;

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
 * {@code unregisterSelf()}; this module adds no {@code onReload()} or {@code onUnregister()} work,
 * so {@code /ul reload UltiEssentials} re-reads this module's configuration files (for example
 * {@code config/essentials.yml}) into the running configuration beans, but does not yet start,
 * stop or reschedule the scheduled-command, scoreboard or name-prefix tasks
 * (UltiKits/UltiEssentials#28). Changing those services' enable flags, intervals or command list
 * therefore needs a server restart, not a reload: a reload that turns
 * {@code features.nameprefix.enabled} on leaves {@code NamePrefixService} without a scoreboard, so
 * every later player join throws a {@code NullPointerException} from
 * {@code NamePrefixService#updatePlayer}. Unload does not yet cancel those tasks either
 * (UltiKits/UltiEssentials#43).
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
}

