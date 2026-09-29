package com.ultikits.plugins.essentials.commands;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.BaseCommandExecutor;
import com.ultikits.ultitools.annotations.Autowired;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Base class for all UltiEssentials commands.
 * Provides convenient i18n access and online-player tab completion.
 * <p>
 * Four further helpers that no command ever called -- a feature-enabled check, a teleport-result
 * message router, and offline/all-player tab completion -- were removed together with the unused
 * {@code utils.MessageUtils} class (UltiKits/UltiEssentials#29).
 * <p>
 * Uses the new BaseCommandExecutor introduced in UltiTools-API 6.2.0.
 *
 * @author wisdomme
 * @version 1.1.0
 */
public abstract class BaseEssentialsCommand extends BaseCommandExecutor {

    @Autowired
    protected UltiToolsPlugin plugin;

    /**
     * Gets the localized string from the plugin's language file.
     *
     * @param key the translation key
     * @return the localized string
     */
    protected String i18n(String key) {
        return plugin.i18n(key);
    }

    /**
     * Suggests the names of the online players {@code viewer} may see, starting with {@code prefix}
     * (case-insensitive). A vanished player is left out for a viewer without
     * {@code ultiessentials.hide.see}, so completion does not reveal the vanish
     * (UltiKits/UltiEssentials#56).
     *
     * @param viewer the player completing, or {@code null} for the console
     * @param prefix the typed prefix, or {@code null}
     * @return the matching names
     */
    protected List<String> suggestOnlinePlayers(Player viewer, String prefix) {
        String lowerPrefix = prefix != null ? prefix.toLowerCase() : "";
        return Bukkit.getOnlinePlayers().stream()
                .filter(online -> HideCommand.canSee(viewer, online))
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }

    /**
     * Default implementation for help command.
     * Subclasses can override this to provide custom help messages.
     *
     * @param sender the command sender
     */
    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.default"));
    }
}
