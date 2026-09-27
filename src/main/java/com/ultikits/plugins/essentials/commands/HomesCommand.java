package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.HomeData;
import com.ultikits.plugins.essentials.service.HomeService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import com.ultikits.ultitools.annotations.I18n;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Command to list all homes.
 * <p>
 * 列出所有家的命令。
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"homes", "homelist"}, permission = "ultiessentials.homes", description = "essentials.command.homes.description")
@I18n("homes.description")
public class HomesCommand extends BaseEssentialsCommand {
    
    @Autowired
    private HomeService homeService;
    
    @Autowired
    private EssentialsConfig config;
    
    /**
     * List all homes.
     */
    @CmdMapping(format = "")
    public void listHomes(@CmdSender Player player) {
        if (!config.isHomeEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        
        List<HomeData> homes = homeService.getHomes(player.getUniqueId());
        int maxHomes = homeService.getMaxHomes(player);
        
        player.sendMessage("§6========== " + i18n("essentials.homes.header") + " §7(" + homes.size() + "/" + maxHomes + ") §6==========");
        
        if (homes.isEmpty()) {
            player.sendMessage("§7" + i18n("essentials.home.none"));
            player.sendMessage("§7" + i18n("essentials.homes.hint_sethome"));
        } else {
            for (HomeData home : homes) {
                String worldName = home.getWorld();
                int x = (int) home.getX();
                int y = (int) home.getY();
                int z = (int) home.getZ();
                
                player.sendMessage(String.format(
                    "§e%s §7- §f%s §7(§f%d§7, §f%d§7, §f%d§7)",
                    home.getName(),
                    worldName,
                    x, y, z
                ));
            }
        }
        
        player.sendMessage("§6" + "=".repeat(40));
    }
}
