package com.ultikits.plugins.essentials.service;

import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

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
     * The names of the teams copied onto each private board, so a team that leaves the main scoreboard
     * is removed from the copy while a team another plugin put on the board itself stays. Weak keys:
     * a board this module no longer holds is forgotten with it. Main thread only, like every scoreboard
     * call; synchronised anyway, as it is shared by every player's board.
     */
    private static final Map<Scoreboard, Set<String>> COPIED =
            Collections.synchronizedMap(new WeakHashMap<Scoreboard, Set<String>>());

    /**
     * Makes the teams of {@code target} match the teams of {@code main}: every main-scoreboard team is
     * copied, and a copied team that has left the main scoreboard is removed. A team on {@code target}
     * that was never copied from the main scoreboard is left alone.
     *
     * @param main   the server's main scoreboard, the source of truth
     * @param target a private scoreboard owned by this module
     */
    static void mirror(Scoreboard main, Scoreboard target) {
        if (main == null || target == null || main.equals(target)) {
            return;
        }
        Set<String> copied = COPIED.computeIfAbsent(target, board -> new HashSet<>());
        Set<String> mainTeamNames = new HashSet<>();
        for (Team source : main.getTeams()) {
            mainTeamNames.add(source.getName());
            Team copy = target.getTeam(source.getName());
            if (copy == null) {
                copy = target.registerNewTeam(source.getName());
            }
            copyTeam(source, copy);
        }
        for (Team stale : target.getTeams()) {
            if (copied.contains(stale.getName()) && !mainTeamNames.contains(stale.getName())) {
                stale.unregister();
            }
        }
        copied.clear();
        copied.addAll(mainTeamNames);
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
