package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for toggling scoreboard display.
 * <p>
 * Usage: /scoreboard (toggle)
 *        /sb on/off
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(
    alias = {"scoreboard", "sb"},
    permission = "ultiessentials.scoreboard",
    description = "essentials.command.scoreboard.description"
)
public class ScoreboardCommand extends BaseEssentialsCommand {
    
    @Autowired
    private ScoreboardService scoreboardService;
    
    @CmdMapping(format = "")
    public void toggle(@CmdSender Player player) {
        boolean enabled = scoreboardService.toggleScoreboard(player);
        
        if (enabled) {
            player.sendMessage(i18n("essentials.scoreboard.enabled"));
        } else {
            player.sendMessage(i18n("essentials.scoreboard.disabled"));
        }
    }
    
    @CmdMapping(format = "on")
    public void enable(@CmdSender Player player) {
        if (scoreboardService.isEnabled(player)) {
            player.sendMessage(i18n("essentials.scoreboard.already_enabled"));
            return;
        }
        
        scoreboardService.enableScoreboard(player);
        player.sendMessage(i18n("essentials.scoreboard.enabled"));
    }
    
    @CmdMapping(format = "off")
    public void disable(@CmdSender Player player) {
        if (!scoreboardService.isEnabled(player)) {
            player.sendMessage(i18n("essentials.scoreboard.already_disabled"));
            return;
        }
        
        scoreboardService.disableScoreboard(player);
        player.sendMessage(i18n("essentials.scoreboard.disabled"));
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.scoreboard.usage"));
        sender.sendMessage(i18n("essentials.help.scoreboard"));
    }
}
