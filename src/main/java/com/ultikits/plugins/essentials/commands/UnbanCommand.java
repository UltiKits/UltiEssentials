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
    description = "essentials.command.unban.description"
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
            sender.sendMessage(i18n("essentials.unban.failed") + " (" + playerName + ")");
            return;
        }

        boolean success = outcome == BanService.UnbanResult.REMOVED;

        if (success && banService.isBannedInServerBanList(playerName)) {
            // The plugin's own ban record was removed, but the same name is also banned in
            // the server's own ban list (e.g. an additional vanilla /ban) -- reporting this
            // as a full unban, and broadcasting it, would tell everyone the player can
            // rejoin when the server will still reject them. See
            // BanService#isBannedInServerBanList.
            sender.sendMessage(i18n("essentials.unban.plugin_only_prefix") + playerName +
                i18n("essentials.unban.plugin_only_suffix"));
        } else if (success) {
            sender.sendMessage(i18n("essentials.unban.success_prefix") + playerName +
                i18n("essentials.unban.success_suffix"));
            // features.ban.broadcast-unban (UltiKits/UltiEssentials#27). The issuer has just been
            // told above, so with the switch off nothing is lost but the server-wide line.
            if (config.isUnbanBroadcast()) {
                Bukkit.broadcastMessage(i18n("essentials.unban.broadcast_prefix") +
                    playerName + " §7的封禁已被解除");
            }
        } else if (banService.isBannedInServerBanList(playerName)) {
            // banPlayer() never writes to the server's own ban list, so a name absent from this
            // plugin's own records may still be banned there (e.g. a vanilla /ban). Reporting
            // that the same way as "not banned anywhere" would be a false "not banned" for a
            // player who is demonstrably banned -- see BanService#isBannedInServerBanList.
            sender.sendMessage(i18n("essentials.unban.server_list_only") + playerName);
        } else {
            sender.sendMessage(i18n("essentials.unban.not_banned") + playerName);
        }
    }
    
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.unban.usage"));
        sender.sendMessage(i18n("essentials.help.unban"));
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
