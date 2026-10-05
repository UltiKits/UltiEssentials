package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.LobbyConfig;
import com.ultikits.ultitools.annotations.command.*;
import com.ultikits.ultitools.config.ConfigWriteRefusedException;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;

/**
 * Command to set the server lobby/hub at player's current location.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"setlobby", "sethub"}, permission = "ultiessentials.lobby.set", description = "essentials.command.setlobby.description")
public class SetLobbyCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;
    private final LobbyConfig lobbyConfig;

    public SetLobbyCommand(EssentialsConfig config, LobbyConfig lobbyConfig) {
        this.config = config;
        this.lobbyConfig = lobbyConfig;
    }

    /**
     * Sets the lobby to the player's location and writes exactly the six {@code lobby.location.*} settings.
     * <p>
     * <b>Why it cannot overwrite other operator content.</b> The command is the operator's explicit request to
     * change these six settings, so they are written with {@code saveOperatorChange} - including one the operator
     * edited by hand since the file was read (the command wins at the keys it names) - and nothing else: the
     * framework's write gate publishes the file only when every other line is byte-identical to it (maintainer
     * decision 2026-10-04, "what code may write, by file type"; UltiKits/UltiEssentials#72). When the gate refuses
     * the write, or publishing fails, the file keeps its bytes, the player is told nothing was saved (and why, for a
     * refusal), and the six settings in memory go back to what they were, so the running lobby matches the file
     * (maintainer decision 2026-10-05).
     *
     * @param player the player whose location becomes the lobby
     */
    @CmdMapping(format = "")
    public void setLobby(@CmdSender Player player) {
        if (!config.isLobbyEnabled()) {
            player.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        // Remembered so that a write that does not happen leaves the running lobby as the file holds it.
        String world = lobbyConfig.getWorld();
        double x = lobbyConfig.getX();
        double y = lobbyConfig.getY();
        double z = lobbyConfig.getZ();
        double yaw = lobbyConfig.getYaw();
        double pitch = lobbyConfig.getPitch();
        lobbyConfig.setLobbyLocation(player.getLocation());
        try {
            lobbyConfig.saveOperatorChange(LobbyConfig.locationPaths());
            player.sendMessage(i18n("essentials.lobby.set"));
        } catch (ConfigWriteRefusedException e) {
            restore(world, x, y, z, yaw, pitch);
            player.sendMessage(String.format(i18n("essentials.lobby.not_saved"), e.getReason()));
        } catch (IOException e) {
            restore(world, x, y, z, yaw, pitch);
            player.sendMessage(i18n("essentials.lobby.save_failed"));
        }
    }

    private void restore(String world, double x, double y, double z, double yaw, double pitch) {
        lobbyConfig.setWorld(world);
        lobbyConfig.setX(x);
        lobbyConfig.setY(y);
        lobbyConfig.setZ(z);
        lobbyConfig.setYaw(yaw);
        lobbyConfig.setPitch(pitch);
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.setlobby"));
    }
}
