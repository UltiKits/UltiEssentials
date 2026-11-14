package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.OwnTeleports;
import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.annotations.command.CmdMapping;
import com.ultikits.ultitools.annotations.command.CmdParam;
import com.ultikits.ultitools.annotations.command.CmdSender;
import com.ultikits.ultitools.annotations.command.CmdTarget;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Immediate staff summon, not a return-location or consent-request command.
 * Uses exact online names and respects Bukkit teleport cancellation; the summoned player can
 * return with the separately permitted /back command. No cooldown, warmup or cross-world limit.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"recall"}, permission = "ultiessentials.recall.other", description = "essentials.command.recall.description")
public class RecallCommand extends BaseEssentialsCommand {
    private final EssentialsConfig config;

    public RecallCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "<player>")
    public void recall(@CmdSender Player sender, @CmdParam("player") String playerName) {
        if (!config.isRecallEnabled()) {
            sender.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }
        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null || !target.isOnline() || !HideCommand.canSee(sender, target)) {
            sender.sendMessage(i18n("essentials.error.player_not_found_or_offline"));
            return;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(i18n("essentials.recall.self"));
            return;
        }
        if (!OwnTeleports.teleport(target, sender.getLocation())) {
            sender.sendMessage(i18n("essentials.recall.failed"));
            return;
        }
        sender.sendMessage(String.format(i18n("essentials.recall.success"), target.getName()));
        target.sendMessage(String.format(i18n("essentials.recall.target"), sender.getName()));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.recall"));
    }

    @Override
    protected List<String> suggest(Player player, Command command, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        return suggestOnlinePlayers(player, args[0]).stream()
                .filter(name -> !name.equalsIgnoreCase(player.getName()))
                .collect(Collectors.toList());
    }
}
