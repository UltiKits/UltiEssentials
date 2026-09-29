package com.ultikits.plugins.essentials.service;

import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Copies the server's main scoreboard teams onto a private scoreboard.
 * <p>
 * A player sees team prefixes, suffixes, colours and name-tag options only from the scoreboard they
 * are viewing. Moving a player onto a private sidebar board would therefore hide every main-board
 * team from them: this module's own name prefixes, vanilla {@code /team} teams and other plugins'
 * teams. Each module that gives a player a private board carries the main board's teams onto it
 * (maintainer decision 2026-09-27, UltiKits/UltiEssentials#40). The main board stays the single owner
 * of teams: the copy follows it and never writes back.
 * <p>
 * The copy is a diff: a team or attribute that already matches is not written again, so an
 * unchanged team costs no scoreboard writes. Teams on the private board that no longer exist on the
 * main board are unregistered; the private board holds no team of its own.
 */
final class MainTeamMirror {

    private MainTeamMirror() {
    }

    /**
     * Makes the teams of {@code target} match the teams of {@code main}: every main-scoreboard team is
     * copied, and a copied team that has left the main scoreboard is removed. Only teams this mirror
     * copied are ever changed or removed: {@code copied} holds the team each earlier call copied under
     * each name, and a team on {@code target} that is not that team - one another plugin put there,
     * even under a copied team's name after removing the copy - is left as that plugin set it. Teams
     * are compared with {@code equals}, because the server hands out a new wrapper for the same team on
     * every lookup.
     *
     * @param main   the server's main scoreboard, the source of truth
     * @param target a private scoreboard owned by the caller
     * @param copied the teams copied onto {@code target} so far, kept by the caller for as long as it
     *               keeps {@code target}, and updated here
     */
    static void mirror(Scoreboard main, Scoreboard target, Map<String, Team> copied) {
        if (main == null || target == null || copied == null || main.equals(target)) {
            return;
        }
        Map<String, Team> nowCopied = new HashMap<>();
        for (Team source : main.getTeams()) {
            String name = source.getName();
            Team copy = target.getTeam(name);
            if (copy == null) {
                copy = target.registerNewTeam(name);
            } else if (!copy.equals(copied.get(name))) {
                // Another plugin's team under this name: not this mirror's to change.
                continue;
            }
            copyTeam(source, copy);
            nowCopied.put(name, copy);
        }
        for (Map.Entry<String, Team> earlier : copied.entrySet()) {
            if (nowCopied.containsKey(earlier.getKey())) {
                continue;
            }
            Team current = target.getTeam(earlier.getKey());
            if (current != null && current.equals(earlier.getValue())) {
                current.unregister();
            }
        }
        copied.clear();
        copied.putAll(nowCopied);
    }

    private static void copyTeam(Team source, Team copy) {
        if (!Objects.equals(source.displayName(), copy.displayName())) {
            copy.displayName(source.displayName());
        }
        if (!Objects.equals(source.prefix(), copy.prefix())) {
            copy.prefix(source.prefix());
        }
        if (!Objects.equals(source.suffix(), copy.suffix())) {
            copy.suffix(source.suffix());
        }
        ChatColor color = source.getColor();
        if (color != null && color != copy.getColor()) {
            copy.setColor(color);
        }
        if (source.allowFriendlyFire() != copy.allowFriendlyFire()) {
            copy.setAllowFriendlyFire(source.allowFriendlyFire());
        }
        if (source.canSeeFriendlyInvisibles() != copy.canSeeFriendlyInvisibles()) {
            copy.setCanSeeFriendlyInvisibles(source.canSeeFriendlyInvisibles());
        }
        for (Team.Option option : Team.Option.values()) {
            Team.OptionStatus status = source.getOption(option);
            if (status != null && status != copy.getOption(option)) {
                copy.setOption(option, status);
            }
        }
        Set<String> wanted = source.getEntries();
        Set<String> present = new HashSet<>(copy.getEntries());
        for (String entry : present) {
            if (!wanted.contains(entry)) {
                copy.removeEntry(entry);
            }
        }
        for (String entry : wanted) {
            if (!present.contains(entry)) {
                copy.addEntry(entry);
            }
        }
    }
}
