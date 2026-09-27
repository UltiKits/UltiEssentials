package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to adjust player movement speed.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"speed"}, permission = "ultiessentials.speed", description = "essentials.command.speed.description")
public class SpeedCommand extends BaseEssentialsCommand {

    private static final float DEFAULT_WALK_SPEED = 0.2f;
    private static final float DEFAULT_FLY_SPEED = 0.1f;

    private final EssentialsConfig config;

    public SpeedCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "<speed>")
    public void setSpeed(@CmdSender Player player, @CmdParam("speed") int speed) {
        if (!config.isSpeedEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        int maxSpeed = config.getSpeedMaxSpeed();
        if (speed < 0 || speed > maxSpeed) {
            player.sendMessage(String.format(i18n("essentials.speed.out_of_range"), maxSpeed));
            return;
        }

        float speedValue;
        if (speed == 0) {
            // Reset to default speed
            player.setWalkSpeed(DEFAULT_WALK_SPEED);
            player.setFlySpeed(DEFAULT_FLY_SPEED);
            player.sendMessage(i18n("essentials.speed.reset"));
        } else {
            // Set speed (1-10 mapped to 0.2-1.0)
            speedValue = Math.min(1.0f, DEFAULT_WALK_SPEED * speed);
            player.setWalkSpeed(speedValue);
            player.setFlySpeed(Math.min(1.0f, DEFAULT_FLY_SPEED * speed));
            player.sendMessage(String.format(i18n("essentials.speed.set"), speed));
        }
    }

    @CmdMapping(format = "reset")
    public void resetSpeed(@CmdSender Player player) {
        if (!config.isSpeedEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        player.setWalkSpeed(DEFAULT_WALK_SPEED);
        player.setFlySpeed(DEFAULT_FLY_SPEED);
        player.sendMessage(i18n("essentials.speed.reset"));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.speed"));
    }
}
