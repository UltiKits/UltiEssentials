package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.HomeData;
import com.ultikits.plugins.essentials.enums.TeleportResult;
import com.ultikits.plugins.essentials.service.HomeService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import com.ultikits.ultitools.annotations.I18n;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Command to teleport to a home.
 * <p>
 * 传送到家的命令。
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"home", "h"}, permission = "ultiessentials.home", description = "essentials.command.home.description")
@I18n("home.description")
public class HomeCommand extends BaseEssentialsCommand {
    
    @Autowired
    private HomeService homeService;
    
    @Autowired
    private EssentialsConfig config;
    
    /**
     * Teleport to default home (named "home") or first home.
     */
    @CmdMapping(format = "")
    public void teleportToDefaultHome(@CmdSender Player player) {
        if (!config.isHomeEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        
        // Try default home first
        TeleportResult result = homeService.teleportToHome(player, "home");
        
        if (result == TeleportResult.NOT_FOUND) {
            // Try first home
            List<HomeData> homes = homeService.getHomes(player.getUniqueId());
            if (homes.isEmpty()) {
                player.sendMessage(i18n("essentials.home.none"));
                return;
            }
            result = homeService.teleportToHome(player, homes.get(0).getName());
        }
        
        handleTeleportResult(player, result);
    }
    
    /**
     * Teleport to a specific home.
     */
    @CmdMapping(format = "<name>")
    public void teleportToHome(@CmdSender Player player, @CmdParam("name") String name) {
        if (!config.isHomeEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        
        TeleportResult result = homeService.teleportToHome(player, name);
        handleTeleportResult(player, result);
    }
    
    private void handleTeleportResult(Player player, TeleportResult result) {
        switch (result) {
            case SUCCESS:
                player.sendMessage(i18n("essentials.teleport.success"));
                break;
            case WARMUP_STARTED:
                player.sendMessage(i18n("essentials.teleport.warmup_do_not_move"));
                break;
            case NOT_FOUND:
                player.sendMessage(i18n("essentials.home.not_found"));
                break;
            case WORLD_NOT_FOUND:
                player.sendMessage(i18n("essentials.teleport.world_not_found"));
                break;
            case ALREADY_TELEPORTING:
                player.sendMessage(i18n("essentials.teleport.in_progress"));
                break;
            case DISABLED:
                player.sendMessage(i18n("essentials.error.feature_disabled"));
                break;
        }
    }
}
