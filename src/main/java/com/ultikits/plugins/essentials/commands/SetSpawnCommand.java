package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.SpawnConfig;
import com.ultikits.ultitools.annotations.command.*;
import com.ultikits.ultitools.config.ConfigWriteRefusedException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;

/**
 * Command to set the server spawn point at player's current location.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"setspawn"}, permission = "ultiessentials.spawn.set", description = "essentials.command.setspawn.description")
public class SetSpawnCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;
    private final SpawnConfig spawnConfig;

    public SetSpawnCommand(EssentialsConfig config, SpawnConfig spawnConfig) {
        this.config = config;
        this.spawnConfig = spawnConfig;
    }

    /**
     * Sets the spawn point to the player's location and writes exactly the six {@code spawn.location.*} settings.
     * <p>
     * <b>Why it cannot overwrite other operator content.</b> The command is the operator's explicit request to
     * change these six settings, so they are written with {@code saveOperatorChange} - including one the operator
     * edited by hand since the file was read (the command wins at the keys it names) - and nothing else: the
     * framework's write gate publishes the file only when every other line is byte-identical to it (maintainer
     * decision 2026-10-04, "what code may write, by file type"; UltiKits/UltiEssentials#72). When the gate refuses
     * the write, or publishing fails, the file keeps its bytes, the player is told nothing was saved (and why, for a
     * refusal), the reason is logged once, and the six settings in memory are restored to the values held before the command
     * (maintainer decision 2026-10-05).
     *
     * @param player the player whose location becomes the spawn point
     */
    @CmdMapping(format = "")
    public void setSpawn(@CmdSender Player player) {
        if (!config.isSpawnEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        // Remembered so that a write that does not happen restores the values held before the command.
        String world = spawnConfig.getWorld();
        double x = spawnConfig.getX();
        double y = spawnConfig.getY();
        double z = spawnConfig.getZ();
        double yaw = spawnConfig.getYaw();
        double pitch = spawnConfig.getPitch();
        spawnConfig.setSpawnLocation(player.getLocation());
        try {
            spawnConfig.saveOperatorChange(SpawnConfig.locationPaths());
            player.sendMessage(i18n("essentials.spawn.set"));
        } catch (ConfigWriteRefusedException e) {
            restore(world, x, y, z, yaw, pitch);
            logNotSaved(e);
            player.sendMessage(String.format(i18n("essentials.spawn.not_saved"), e.getReason()));
        } catch (IOException e) {
            restore(world, x, y, z, yaw, pitch);
            logNotSaved(e);
            player.sendMessage(i18n("essentials.spawn.save_failed"));
        }
    }

    /**
     * One WARNING with the write's own message - the file and why, never a value - so the reply's "fix the file the
     * server log names" holds even for a refusal or failure the framework does not log itself (gate-1 top-up IN-02 of
     * plan 17-72).
     */
    private void logNotSaved(IOException e) {
        plugin.getLogger().warn(String.format(i18n("essentials.log.location_not_saved"), "/setspawn", e.getMessage()));
    }

    private void restore(String world, double x, double y, double z, double yaw, double pitch) {
        spawnConfig.setWorld(world);
        spawnConfig.setX(x);
        spawnConfig.setY(y);
        spawnConfig.setZ(z);
        spawnConfig.setYaw(yaw);
        spawnConfig.setPitch(pitch);
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.setspawn"));
    }
}
