package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command to manage server whitelist.
 */
@CmdTarget(CmdTarget.CmdTargetType.BOTH)
@CmdExecutor(alias = {"wl"}, permission = "ultiessentials.whitelist.manage", description = "白名单管理")
public class WhitelistCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public WhitelistCommand(EssentialsConfig config) {
        this.config = config;
    }

    /**
     * The platform's own player-name length limit. {@code @CmdParam("player") OfflinePlayer}
     * would run the platform's offline-player resolution inside the framework's parameter
     * conversion, before either handler body below ever executes -- which is why {@code add}/
     * {@code remove} declare a raw {@code String} instead and resolve it here, after checking it.
     */
    private static final int MAX_PLAYER_NAME_LENGTH = 16;

    @CmdMapping(format = "add <player>")
    public void add(@CmdSender CommandSender sender, @CmdParam("player") String playerName) {
        if (!config.isWhitelistEnabled()) {
            sender.sendMessage(i18n("该功能已禁用"));
            return;
        }

        String trimmedName = playerName == null ? null : playerName.trim();
        String rejection = rejectInvalidPlayerName(trimmedName);
        if (rejection != null) {
            sender.sendMessage(rejection);
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(trimmedName);

        if (target == null) {
            sender.sendMessage(i18n("玩家不存在"));
            return;
        }

        target.setWhitelisted(true);
        sender.sendMessage(String.format(i18n("已将 %s 添加到白名单"), target.getName()));
    }

    @CmdMapping(format = "remove <player>")
    public void remove(@CmdSender CommandSender sender, @CmdParam("player") String playerName) {
        if (!config.isWhitelistEnabled()) {
            sender.sendMessage(i18n("该功能已禁用"));
            return;
        }

        // Deliberately not guarded by rejectInvalidPlayerName's length check, and deliberately not
        // resolved via Bukkit.getOfflinePlayer either: that resolver is the platform call
        // UltiEssentials#17 measured throwing IllegalArgumentException for a name over 16
        // characters on a real Paper server. add()'s guard exists to keep such a name away from it
        // before *creating* a whitelist entry -- but a prior fix here removed remove()'s guard on
        // the premise that "remove has no resolution step to protect," while leaving this method
        // still calling that same resolver unconditionally, which reopened the identical crash for
        // remove(). Matching against Bukkit.getWhitelistedPlayers()'s existing entries instead lets
        // an administrator clear a malformed/legacy whitelist.json entry (manual edit, historical
        // offline-mode account, file corruption) of any length or blankness, without ever asking
        // the platform to resolve a name it could crash on.
        OfflinePlayer target = Bukkit.getWhitelistedPlayers().stream()
                .filter(whitelisted -> playerName.equalsIgnoreCase(whitelisted.getName()))
                .findFirst()
                .orElse(null);

        if (target == null) {
            sender.sendMessage(i18n("玩家不存在"));
            return;
        }

        target.setWhitelisted(false);
        sender.sendMessage(String.format(i18n("已将 %s 从白名单移除"), target.getName()));
    }

    /**
     * Checks a whitelist name before it reaches the platform's offline-player resolution.
     * Deliberately checked ahead of resolution rather than by intercepting a failure from it
     * afterward: reacting to whichever exception type a platform release happens to throw would
     * bind this module's correctness to that platform detail, and the check itself is two
     * conditions.
     * <p>
     * Expects an already-trimmed argument -- callers must trim incidental leading/trailing
     * whitespace before both this check and the resolution call that follows it, so the two
     * agree on which string they are validating and resolving.
     *
     * @param playerName the argument, already trimmed
     * @return an i18n-ready refusal message if the name is empty, blank, or longer than the
     *         platform allows; {@code null} if the name is fine to resolve
     */
    private String rejectInvalidPlayerName(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return i18n("玩家名不能为空");
        }
        if (playerName.length() > MAX_PLAYER_NAME_LENGTH) {
            return i18n("玩家名过长，最多 16 个字符");
        }
        return null;
    }

    @CmdMapping(format = "list")
    public void list(@CmdSender CommandSender sender) {
        if (!config.isWhitelistEnabled()) {
            sender.sendMessage(i18n("该功能已禁用"));
            return;
        }

        Set<OfflinePlayer> whitelisted = Bukkit.getWhitelistedPlayers();
        if (whitelisted.isEmpty()) {
            sender.sendMessage(i18n("白名单为空"));
            return;
        }

        String names = whitelisted.stream()
                .map(OfflinePlayer::getName)
                .collect(Collectors.joining(", "));

        sender.sendMessage(String.format(i18n("白名单 (%d): %s"), whitelisted.size(), names));
    }

    @CmdMapping(format = "on")
    public void enable(@CmdSender CommandSender sender) {
        if (!config.isWhitelistEnabled()) {
            sender.sendMessage(i18n("该功能已禁用"));
            return;
        }

        Bukkit.setWhitelist(true);
        sender.sendMessage(i18n("白名单已启用"));
    }

    @CmdMapping(format = "off")
    public void disable(@CmdSender CommandSender sender) {
        if (!config.isWhitelistEnabled()) {
            sender.sendMessage(i18n("该功能已禁用"));
            return;
        }

        Bukkit.setWhitelist(false);
        sender.sendMessage(i18n("白名单已禁用"));
    }

    @CmdMapping(format = "status")
    public void status(@CmdSender CommandSender sender) {
        if (!config.isWhitelistEnabled()) {
            sender.sendMessage(i18n("该功能已禁用"));
            return;
        }

        boolean enabled = Bukkit.hasWhitelist();
        int count = Bukkit.getWhitelistedPlayers().size();

        sender.sendMessage(String.format(i18n("白名单状态: %s, 人数: %d"),
                enabled ? i18n("已启用") : i18n("已禁用"), count));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("白名单命令帮助:"));
        sender.sendMessage(i18n("/wl add <玩家> - 添加玩家到白名单"));
        sender.sendMessage(i18n("/wl remove <玩家> - 从白名单移除玩家"));
        sender.sendMessage(i18n("/wl list - 查看白名单列表"));
        sender.sendMessage(i18n("/wl on/off - 启用/禁用白名单"));
        sender.sendMessage(i18n("/wl status - 查看白名单状态"));
    }
}
