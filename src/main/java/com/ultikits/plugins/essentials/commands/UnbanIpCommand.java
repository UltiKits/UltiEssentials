package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/** Lifts all matching module records, confirming the store before announcing success. */
@CmdTarget(CmdTarget.CmdTargetType.BOTH)
@CmdExecutor(alias = {"unbanip"}, permission = "ultiessentials.unbanip",
        description = "essentials.command.unbanip.description")
public class UnbanIpCommand extends BaseEssentialsCommand {
    @Autowired
    private BanService banService;
    @Autowired
    private EssentialsConfig config;

    @CmdMapping(format = "<ip>")
    public void unban(@CmdSender CommandSender sender, @CmdParam("ip") String address) {
        String ip = BanService.normalizeIpAddress(address);
        if (ip == null) {
            sender.sendMessage(i18n("essentials.ipban.invalid"));
            return;
        }
        BanService.UnbanResult result = banService.unbanIp(ip);
        if (result == BanService.UnbanResult.FAILED) {
            sender.sendMessage(String.format(i18n("essentials.ipunban.failed"), ip));
            return;
        }
        boolean removed = result == BanService.UnbanResult.REMOVED;
        if (banService.isIpBannedInServerBanList(ip)) {
            if (removed) {
                sender.sendMessage(String.format(i18n("essentials.ipunban.plugin_only"), ip));
            } else {
                sender.sendMessage(String.format(i18n("essentials.ipunban.server_only"), ip));
            }
        } else if (removed) {
            sender.sendMessage(String.format(i18n("essentials.ipunban.success"), ip));
            if (config.isUnbanBroadcast()) {
                Bukkit.broadcastMessage(String.format(i18n("essentials.ipunban.broadcast"), ip));
            }
        } else {
            sender.sendMessage(String.format(i18n("essentials.ipunban.not_banned"), ip));
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.unbanip.usage"));
    }
}
