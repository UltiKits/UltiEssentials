package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.HomeService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import com.ultikits.ultitools.annotations.I18n;
import org.bukkit.entity.Player;

/**
 * Command to delete a home.
 * <p>
 * 删除家的命令。
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"delhome", "deletehome", "rmhome"}, permission = "ultiessentials.delhome", description = "essentials.command.delhome.description")
@I18n("delhome.description")
public class DelHomeCommand extends BaseEssentialsCommand {
    
    @Autowired
    private HomeService homeService;
    
    @Autowired
    private EssentialsConfig config;
    
    /**
     * Delete a home by name.
     */
    @CmdMapping(format = "<name>")
    public void deleteHome(@CmdSender Player player, @CmdParam("name") String name) {
        if (!config.isHomeEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        
        switch (homeService.deleteHome(player.getUniqueId(), name)) {
            case REMOVED:
                player.sendMessage(i18n("essentials.home.deleted") + " (" + name.toLowerCase() + ")");
                break;
            case NOT_FOUND:
                player.sendMessage(i18n("essentials.home.not_found"));
                break;
            case FAILED:
                // Distinct from NOT_FOUND on purpose: the home is still there, so telling the
                // player it does not exist would send them away while /homes still lists it.
                player.sendMessage(i18n("essentials.home.delete_failed"));
                break;
        }
    }
}
