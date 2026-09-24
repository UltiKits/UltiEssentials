package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for unbanning players.
 * <p>
 * Usage: /unban <player>
 *        /unbanip <ip>
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.BOTH)
@CmdExecutor(
    alias = {"unban", "pardon"},
    permission = "ultiessentials.unban",
    description = "解除封禁"
)
public class UnbanCommand extends BaseEssentialsCommand {
    
    @Autowired
    private BanService banService;
    
    /** Read on every unban for {@code features.ban.broadcast-unban} (UltiKits/UltiEssentials#27). */
    @Autowired
    private EssentialsConfig config;
    
    @CmdMapping(format = "<player>")
    public void unban(@CmdSender CommandSender sender, @CmdParam("player") String playerName) {
        BanService.UnbanResult outcome = banService.unbanPlayerByName(playerName);

        if (outcome == BanService.UnbanResult.FAILED) {
            // Never the "not banned" branch below: the ban record is still active, so the player is
            // still rejected at login and /banlist still lists them. Reporting "not banned" would
            // stop the operator looking just as effectively as the old false success did
            // (gate 1 MAJOR-03).
            sender.sendMessage(i18n("§c解禁失败，该玩家的封禁记录无法更新，该玩家仍处于封禁状态，请联系管理员") + " (" + playerName + ")");
            return;
        }

        boolean success = outcome == BanService.UnbanResult.REMOVED;

        if (success && banService.isBannedInServerBanList(playerName)) {
            // The plugin's own ban record was removed, but the same name is also banned in
            // the server's own ban list (e.g. an additional vanilla /ban) -- reporting this
            // as a full unban, and broadcasting it, would tell everyone the player can
            // rejoin when the server will still reject them. See
            // BanService#isBannedInServerBanList.
            sender.sendMessage(i18n("§e已解除本插件对 ") + playerName +
                i18n(" 的封禁，但服务器封禁名单仍封禁该玩家"));
        } else if (success) {
            sender.sendMessage(i18n("§a已解除 ") + playerName +
                i18n(" 的封禁"));
            // features.ban.broadcast-unban (UltiKits/UltiEssentials#27). The issuer has just been
            // told above, so with the switch off nothing is lost but the server-wide line.
            if (config.isUnbanBroadcast()) {
                Bukkit.broadcastMessage(i18n("§a[解禁] §f") +
                    playerName + " §7的封禁已被解除");
            }
        } else if (banService.isBannedInServerBanList(playerName)) {
            // banPlayer() never writes to the server's own ban list, so a name absent from this
            // plugin's own records may still be banned there (e.g. a vanilla /ban). Reporting
            // that the same way as "not banned anywhere" would be a false "not banned" for a
            // player who is demonstrably banned -- see BanService#isBannedInServerBanList.
            sender.sendMessage(i18n("§c该玩家未被本插件封禁，但已被服务器封禁名单封禁: ") + playerName);
        } else {
            sender.sendMessage(i18n("§c该玩家未被封禁: ") + playerName);
        }
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("用法: /unban <玩家>"));
        sender.sendMessage(i18n("解除玩家的封禁"));
    }
    
    @Override
    protected List<String> suggest(Player player, Command command, String[] args) {
        if (args.length == 1) {
            return banService.getActiveBans().stream()
                .map(BanData::getPlayerName)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .distinct()
                .collect(Collectors.toList());
        }
        return super.suggest(player, command, args);
    }
}
