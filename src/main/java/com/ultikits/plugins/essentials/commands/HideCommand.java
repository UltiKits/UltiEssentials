package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Command to toggle vanish/invisible mode.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"hide", "vanish"}, permission = "ultiessentials.hide", description = "切换隐身模式")
public class HideCommand extends BaseEssentialsCommand {

    /**
     * Stores currently hidden player UUIDs.
     */
    private static final Set<UUID> HIDDEN_PLAYERS = new HashSet<>();

    /**
     * Lets its holder keep seeing vanished players, both when someone vanishes and when the holder
     * joins later.
     */
    private static final String SEE_VANISHED_PERMISSION = "ultiessentials.hide.see";

    private final EssentialsConfig config;

    public HideCommand(EssentialsConfig config) {
        this.config = config;
    }

    /**
     * The Bukkit plugin every hide and show is recorded against. Bukkit keys visibility by plugin,
     * so the join-time re-hide and the un-vanish in {@link #toggleHide} must use the same one for
     * the un-vanish to undo it.
     */
    private static Plugin getBukkitPlugin() {
        return Bukkit.getPluginManager().getPlugin("UltiTools");
    }

    @CmdMapping(format = "")
    public void toggleHide(@CmdSender Player player) {
        if (!config.isHideEnabled()) {
            player.sendMessage(i18n("该功能已禁用"));
            return;
        }

        if (HIDDEN_PLAYERS.contains(player.getUniqueId())) {
            // Disable vanish
            HIDDEN_PLAYERS.remove(player.getUniqueId());
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(getBukkitPlugin(), player);
            }
            player.sendMessage(i18n("隐身模式已关闭"));
        } else {
            // Enable vanish
            HIDDEN_PLAYERS.add(player.getUniqueId());
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.hasPermission(SEE_VANISHED_PERMISSION)) {
                    online.hidePlayer(getBukkitPlugin(), player);
                }
            }
            player.sendMessage(i18n("隐身模式已开启"));
        }
    }

    /**
     * Checks if a player is currently hidden.
     *
     * @param player the player to check
     * @return true if hidden, false otherwise
     */
    public static boolean isHidden(Player player) {
        return HIDDEN_PLAYERS.contains(player.getUniqueId());
    }

    /**
     * Hides every currently vanished player from a player who has just joined, unless the joiner
     * holds {@code ultiessentials.hide.see} -- the same exemption {@link #toggleHide} applies to
     * the players online when someone vanishes. Without this, {@code /hide} held only against the
     * players online at that moment, and anyone who joined later saw the vanished player
     * (UltiKits/UltiEssentials#32).
     * <p>
     * It does not consult {@code features.hide.enabled}: a player already vanished stays hidden
     * from the players who were online when they vanished whatever that switch now says, so a later
     * joiner is treated the same way.
     * <p>
     * 对刚加入的玩家隐藏所有当前隐身的玩家；持有 ultiessentials.hide.see 的玩家除外。
     *
     * @param joiner the player who has just joined
     */
    public static void hideVanishedPlayersFrom(Player joiner) {
        if (HIDDEN_PLAYERS.isEmpty() || joiner.hasPermission(SEE_VANISHED_PERMISSION)) {
            return;
        }
        Plugin plugin = getBukkitPlugin();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(joiner) && isHidden(online)) {
                joiner.hidePlayer(plugin, online);
            }
        }
    }

    /**
     * Shows every vanished player to every other online player and forgets all vanish state. Called
     * when the module is unloaded: every hide is recorded against the {@code UltiTools} Bukkit
     * plugin, which stays enabled, so without this the hides would outlive the module while the
     * {@code /hide} command that lifts them and the join listener that keeps later joiners consistent
     * are gone -- leaving a vanished player hidden from some players, visible to others and unable
     * to un-vanish.
     * <p>
     * Uses the same plugin reference as {@link #toggleHide}, which is what makes each
     * {@code showPlayer} undo the matching {@code hidePlayer}. The set is emptied even of players who
     * already left, so a later reinstall starts from a state that matches what Bukkit shows.
     * <p>
     * 卸载模块时让所有隐身玩家对所有人重新可见，并清空隐身状态。
     */
    public static void revealAllVanished() {
        if (HIDDEN_PLAYERS.isEmpty()) {
            return;
        }
        Plugin plugin = getBukkitPlugin();
        for (Player vanished : Bukkit.getOnlinePlayers()) {
            if (!isHidden(vanished)) {
                continue;
            }
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.equals(vanished)) {
                    viewer.showPlayer(plugin, vanished);
                }
            }
        }
        HIDDEN_PLAYERS.clear();
    }

    /**
     * Removes a player from the hidden set.
     * Should be called when a player disconnects.
     *
     * @param uuid the player's UUID
     */
    public static void removePlayer(UUID uuid) {
        HIDDEN_PLAYERS.remove(uuid);
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("使用 /hide 切换隐身模式"));
    }
}
