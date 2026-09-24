package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to change player game mode.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"gm"}, permission = "ultiessentials.gamemode.self", description = "essentials.command.gamemode.description")
public class GameModeCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public GameModeCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "<mode>")
    public void setGameMode(@CmdSender Player player, @CmdParam("mode") String mode) {
        if (!config.isGamemodeEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        GameMode gameMode = parseGameMode(mode);
        if (gameMode == null) {
            player.sendMessage(i18n("essentials.gamemode.invalid_with_options"));
            return;
        }

        player.setGameMode(gameMode);
        player.sendMessage(String.format(i18n("essentials.gamemode.changed"), gameMode.name()));
    }

    @CmdMapping(format = "<mode> <player>", permission = "ultiessentials.gamemode.other")
    public void setGameModeOther(
            @CmdSender Player sender,
            @CmdParam("mode") String mode,
            @CmdParam("player") Player target) {

        if (!config.isGamemodeEnabled()) {
            sender.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        if (target == null) {
            sender.sendMessage(i18n("essentials.error.player_not_found_or_offline"));
            return;
        }

        GameMode gameMode = parseGameMode(mode);
        if (gameMode == null) {
            sender.sendMessage(i18n("essentials.gamemode.invalid"));
            return;
        }

        target.setGameMode(gameMode);
        sender.sendMessage(String.format(i18n("essentials.gamemode.changed_other"), target.getName(), gameMode.name()));
        target.sendMessage(String.format(i18n("essentials.gamemode.changed_target"), gameMode.name()));
    }

    /**
     * Parses a game mode string into a GameMode enum.
     *
     * @param mode the mode string
     * @return the GameMode or null if invalid
     */
    private GameMode parseGameMode(String mode) {
        switch (mode.toLowerCase()) {
            case "0":
            case "s":
            case "survival":
                return GameMode.SURVIVAL;
            case "1":
            case "c":
            case "creative":
                return GameMode.CREATIVE;
            case "2":
            case "a":
            case "adventure":
                return GameMode.ADVENTURE;
            case "3":
            case "sp":
            case "spectator":
                return GameMode.SPECTATOR;
            default:
                return null;
        }
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.gamemode"));
    }
}
