package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Command for banning players.
 * <p>
 * Usage: /ban <player> [reason]
 *        /tempban <player> <duration> [reason]
 *        /banip <player|ip> [reason]
 *
 * @author wisdomme
 * @version 1.0.0
 */
@CmdTarget(CmdTarget.CmdTargetType.BOTH)
@CmdExecutor(
    alias = {"ban", "eban"},
    permission = "ultiessentials.ban",
    description = "essentials.command.ban.description"
)
public class BanCommand extends BaseEssentialsCommand {
    
    @Autowired
    private BanService banService;
    
    /** Read on every ban for {@code features.ban.broadcast-ban} (UltiKits/UltiEssentials#27). */
    @Autowired
    private EssentialsConfig config;
    
    @CmdMapping(format = "<player>")
    public void ban(@CmdSender CommandSender sender, @CmdParam("player") String playerName) {
        banWithReason(sender, playerName, i18n("essentials.ban.no_reason"));
    }
    
    @CmdMapping(format = "<player> <reason>")
    public void banWithReason(
        @CmdSender CommandSender sender,
        @CmdParam("player") String playerName,
        @CmdParam("reason") String reason
    ) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null && !target.hasPlayedBefore()) {
            sender.sendMessage(i18n("essentials.ban.player_not_found") + playerName);
            return;
        }
        
        UUID operatorUuid = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
        String operatorName = sender instanceof Player ? sender.getName() : i18n("essentials.ban.console");
        
        BanService.BanResult result = banService.banPlayer(
            target.getUniqueId(),
            target.getName() != null ? target.getName() : playerName,
            reason,
            operatorUuid,
            operatorName
        );
        
        switch (result) {
            case SUCCESS:
                announce(sender, String.format(i18n("essentials.ban.broadcast"), target.getName(), operatorName),
                    i18n("essentials.ban.reason") + reason);
                break;
            case ALREADY_BANNED:
                sender.sendMessage(i18n("essentials.ban.already_banned"));
                break;
            case DISABLED:
                sender.sendMessage(i18n("essentials.ban.disabled"));
                break;
            default:
                // Handle unexpected result types
                break;
        }
    }
    
    /**
     * Announces a ban: to everyone when {@code features.ban.broadcast-ban} is on (the declared
     * default, and this command's behaviour before the switch was read), otherwise to the issuer
     * alone. The broadcast is the issuer's only confirmation, so with the switch off the same lines
     * still reach them rather than the ban succeeding in silence (UltiKits/UltiEssentials#27).
     *
     * @param sender the command's issuer
     * @param lines  the announcement, in order
     */
    private void announce(CommandSender sender, String... lines) {
        boolean broadcast = config.isBanBroadcast();
        for (String line : lines) {
            if (broadcast) {
                Bukkit.broadcastMessage(line);
            } else {
                sender.sendMessage(line);
            }
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.ban.usage"));
        sender.sendMessage(i18n("essentials.help.ban"));
    }
    
    @Override
    protected List<String> suggest(Player player, Command command, String[] args) {
        if (args.length == 1) {
            return suggestOnlinePlayers(args[0]);
        }
        return super.suggest(player, command, args);
    }
}
