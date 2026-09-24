package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.HomeService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import com.ultikits.ultitools.annotations.I18n;
import org.bukkit.entity.Player;

/**
 * Command to set a home location.
 * <p>
 * 设置家位置的命令。
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"sethome", "sh"}, permission = "ultiessentials.sethome", description = "essentials.command.sethome.description")
@I18n("sethome.description")
public class SetHomeCommand extends BaseEssentialsCommand {
    
    @Autowired
    private HomeService homeService;
    
    @Autowired
    private EssentialsConfig config;
    
    /**
     * Set default home (named "home").
     */
    @CmdMapping(format = "")
    public void setDefaultHome(@CmdSender Player player) {
        setHome(player, "home");
    }
    
    /**
     * Set a named home.
     */
    @CmdMapping(format = "<name>")
    public void setHome(@CmdSender Player player, @CmdParam("name") String name) {
        if (!config.isHomeEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        
        HomeService.SetHomeResult result = homeService.setHome(player, name);
        
        switch (result) {
            case CREATED:
                player.sendMessage(i18n("essentials.home.set") + " (" + name.toLowerCase() + ")");
                int current = homeService.getHomeCount(player.getUniqueId());
                int max = homeService.getMaxHomes(player);
                player.sendMessage(i18n("essentials.home.count") + ": " + current + "/" + max);
                break;
            case UPDATED:
                player.sendMessage(i18n("essentials.home.updated") + " (" + name.toLowerCase() + ")");
                break;
            case FAILED:
                // The home exists and the move did not reach storage, so /home would still teleport
                // to the old location. Reporting it as updated is #34's symptom (gate 1 MAJOR-01).
                player.sendMessage(i18n("essentials.home.update_failed") + " (" + name.toLowerCase() + ")");
                break;
            case LIMIT_REACHED:
                player.sendMessage(i18n("essentials.home.limit_reached"));
                player.sendMessage(i18n("essentials.home.hint_delhome"));
                break;
            case INVALID_NAME:
                player.sendMessage(i18n("essentials.home.invalid_name"));
                break;
            case DISABLED:
                player.sendMessage(i18n("essentials.error.feature_disabled"));
                break;
        }
    }
}
