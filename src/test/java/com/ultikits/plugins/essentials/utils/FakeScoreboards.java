package com.ultikits.plugins.essentials.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Scoreboards and teams that keep their state, so a test can assert what a board holds after the
 * code under test ran, rather than only which setters it called.
 */
public final class FakeScoreboards {

    private FakeScoreboards() {
    }

    /** A scoreboard mock whose teams live in a map: register, look up, list and unregister work. */
    public static Scoreboard board() {
        Scoreboard board = mock(Scoreboard.class);
        Map<String, Team> teams = new LinkedHashMap<>();
        lenient().when(board.getTeam(anyString())).thenAnswer(inv -> teams.get(inv.<String>getArgument(0)));
        lenient().when(board.getTeams()).thenAnswer(inv -> new HashSet<>(teams.values()));
        lenient().when(board.registerNewTeam(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            if (teams.containsKey(name)) {
                throw new IllegalArgumentException("Team name '" + name + "' is already in use");
            }
            Team team = team(name, () -> teams.remove(name));
            teams.put(name, team);
            return team;
        });
        return board;
    }

    /** Registers a team on a fake board and gives it a prefix and entries. */
    public static Team addTeam(Scoreboard board, String name, String prefix, String... entries) {
        Team team = board.registerNewTeam(name);
        team.prefix(Component.text(prefix));
        for (String entry : entries) {
            team.addEntry(entry);
        }
        return team;
    }

    private static Team team(String name, Runnable onUnregister) {
        Team team = mock(Team.class);
        Map<String, Object> state = new LinkedHashMap<>();
        Set<String> entries = new HashSet<>();
        Map<Team.Option, Team.OptionStatus> options = new EnumMap<>(Team.Option.class);
        state.put("displayName", Component.text(name));
        state.put("color", ChatColor.RESET);
        state.put("friendlyFire", Boolean.TRUE);
        state.put("seeInvisibles", Boolean.TRUE);
        for (Team.Option option : Team.Option.values()) {
            options.put(option, Team.OptionStatus.ALWAYS);
        }

        lenient().when(team.getName()).thenReturn(name);
        lenient().when(team.displayName()).thenAnswer(inv -> state.get("displayName"));
        lenient().doAnswer(inv -> state.put("displayName", inv.getArgument(0))).when(team).displayName(any());
        lenient().when(team.prefix()).thenAnswer(inv -> state.get("prefix"));
        lenient().doAnswer(inv -> state.put("prefix", inv.getArgument(0))).when(team).prefix(any());
        lenient().when(team.suffix()).thenAnswer(inv -> state.get("suffix"));
        lenient().doAnswer(inv -> state.put("suffix", inv.getArgument(0))).when(team).suffix(any());
        lenient().when(team.getColor()).thenAnswer(inv -> state.get("color"));
        lenient().doAnswer(inv -> state.put("color", inv.getArgument(0))).when(team).setColor(any());
        lenient().when(team.allowFriendlyFire()).thenAnswer(inv -> state.get("friendlyFire"));
        lenient().doAnswer(inv -> state.put("friendlyFire", inv.getArgument(0))).when(team).setAllowFriendlyFire(org.mockito.ArgumentMatchers.anyBoolean());
        lenient().when(team.canSeeFriendlyInvisibles()).thenAnswer(inv -> state.get("seeInvisibles"));
        lenient().doAnswer(inv -> state.put("seeInvisibles", inv.getArgument(0))).when(team).setCanSeeFriendlyInvisibles(org.mockito.ArgumentMatchers.anyBoolean());
        lenient().when(team.getOption(any())).thenAnswer(inv -> options.get(inv.<Team.Option>getArgument(0)));
        lenient().doAnswer(inv -> options.put(inv.getArgument(0), inv.getArgument(1))).when(team).setOption(any(), any());
        lenient().when(team.getEntries()).thenAnswer(inv -> new HashSet<>(entries));
        lenient().doAnswer(inv -> entries.add(inv.getArgument(0))).when(team).addEntry(anyString());
        lenient().when(team.removeEntry(anyString())).thenAnswer(inv -> entries.remove(inv.<String>getArgument(0)));
        lenient().when(team.hasEntry(anyString())).thenAnswer(inv -> entries.contains(inv.<String>getArgument(0)));
        doAnswer(inv -> {
            onUnregister.run();
            return null;
        }).when(team).unregister();
        return team;
    }
}
