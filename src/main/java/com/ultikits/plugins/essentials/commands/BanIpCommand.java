package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Permanently bans a numeric IP in this module's store, not in the vanilla ban list. */
@CmdTarget(CmdTarget.CmdTargetType.BOTH)
@CmdExecutor(alias = {"banip"}, permission = "ultiessentials.banip",
        description = "essentials.command.banip.description")
public class BanIpCommand extends BaseEssentialsCommand {
    @Autowired
    private BanService banService;
    @Autowired
    private EssentialsConfig config;

    @CmdMapping(format = "<ip>")
    public void ban(@CmdSender CommandSender sender, @CmdParam("ip") String ip) {
        banWithReason(sender, ip, i18n("essentials.ban.no_reason"));
    }

    @CmdMapping(format = "<ip> <reason>")
    public void banWithReason(@CmdSender CommandSender sender, @CmdParam("ip") String address,
                              @CmdParam("reason") String reason) {
        String ip = BanService.normalizeIpAddress(address);
        if (ip == null) {
            sender.sendMessage(i18n("essentials.ipban.invalid"));
            return;
        }
        UUID issuer = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        String name = sender instanceof Player ? sender.getName() : i18n("essentials.ban.console");
        BanService.IpBanResult result = banService.banIp(ip, reason, issuer, name);
        switch (result) {
            case SUCCESS:
                sender.sendMessage(String.format(i18n("essentials.ipban.success"), ip));
                if (config.isBanBroadcast()) {
                    Bukkit.broadcastMessage(String.format(i18n("essentials.ipban.broadcast"), ip, name, reason));
                }
                break;
            case ALREADY_BANNED:
                sender.sendMessage(String.format(i18n("essentials.ipban.already_banned"), ip));
                break;
            case DISABLED:
                sender.sendMessage(i18n("essentials.ban.disabled"));
                break;
            case INVALID_ADDRESS:
                sender.sendMessage(i18n("essentials.ipban.invalid"));
                break;
            case FAILED:
                sender.sendMessage(String.format(i18n("essentials.ipban.failed"), ip));
                break;
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.banip.usage"));
    }
}
