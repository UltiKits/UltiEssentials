package com.ultikits.plugins.essentials.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The placeholders this module fills itself in scoreboard lines and name prefixes, such as
 * {@code %online_players%} or {@code %player_food%}.
 * <p>
 * They are filled whether or not PlaceholderAPI is installed, and before PlaceholderAPI runs, so the
 * shipped default lines show values on every server (UltiKits/UltiEssentials#59). All of a line's
 * placeholders are filled in one pass over the text as written: a value filled in (a world name,
 * for example) is never scanned again, so a value that happens to look like a placeholder stays
 * literal.
 */
final class BuiltInPlaceholders {

    private BuiltInPlaceholders() {
    }

    /**
     * Fills the scoreboard's built-in placeholders.
     *
     * @param player the player the line is for
     * @param text   the line as configured
     * @return the line with every built-in placeholder filled
     */
    static String forScoreboard(Player player, String text) {
        Map<String, Supplier<String>> values = new HashMap<>();
        values.put("player_name", player::getName);
        values.put("player_health", () -> String.valueOf((int) player.getHealth()));
        values.put("player_food", () -> String.valueOf(player.getFoodLevel()));
        values.put("player_level", () -> String.valueOf(player.getLevel()));
        values.put("player_world", () -> player.getWorld().getName());
        values.put("online_players", () -> String.valueOf(Bukkit.getOnlinePlayers().size()));
        values.put("max_players", () -> String.valueOf(Bukkit.getMaxPlayers()));
        return fill(text, values);
    }

    /**
     * Fills the name prefix's built-in placeholder, {@code %player_name%}.
     *
     * @param player the player the prefix is for
     * @param text   the prefix or suffix format as configured
     * @return the format with the built-in placeholder filled
     */
    static String forNamePrefix(Player player, String text) {
        Map<String, Supplier<String>> values = new HashMap<>();
        values.put("player_name", player::getName);
        return fill(text, values);
    }

    /**
     * Replaces each {@code %name%} whose name is in {@code values}, in one left-to-right pass;
     * anything else, including a lone {@code %}, is copied unchanged. A value is computed only when
     * its placeholder occurs.
     */
    static String fill(String text, Map<String, Supplier<String>> values) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '%') {
                int end = text.indexOf('%', i + 1);
                if (end > i) {
                    Supplier<String> value = values.get(text.substring(i + 1, end));
                    if (value != null) {
                        out.append(value.get());
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
