package com.ultikits.plugins.essentials;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScheduledCommandService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.manager.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiEssentials#28: {@code /ul reload UltiEssentials} must restart the scheduled-command,
 * scoreboard and name-prefix services against the re-read configuration.
 * <p>
 * Each test boots the three services the way the container does ({@code @PostConstruct init()}
 * against an {@link EssentialsConfig} registered with a real {@link ConfigManager}), rewrites
 * {@code config/essentials.yml}, then performs the two module-visible steps of the framework's
 * final {@code reloadSelf()} in its order: {@link ConfigManager#reloadConfigs} re-reads the file
 * into the same config instance, and {@code onReload()} is invoked on the plugin. The assertions
 * are on observable state: team entries, scheduled task periods, cancelled tasks, dispatched
 * commands and assigned scoreboards.
 * <p>
 * The remaining {@code reloadSelf()} steps (language refresh, drift report, framework reload line)
 * need a running UltiTools instance and do not touch these services.
 * <p>
 * 验证重载会按新配置重启定时命令、计分板与头顶称号服务。
 */
@DisplayName("/ul reload restarts the three background services (UltiKits/UltiEssentials#28)")
class UltiEssentialsServiceReloadTest {

    /** One {@code runTaskTimer} call observed on the mocked scheduler. */
    private static final class ScheduledTimer {
        private final Runnable runnable;
        private final long period;
        private final BukkitTask task;

        private ScheduledTimer(Runnable runnable, long period, BukkitTask task) {
            this.runnable = runnable;
            this.period = period;
            this.task = task;
        }
    }

    @TempDir
    Path moduleFolder;

    private File configFile;
    private UltiEssentials plugin;
    private ConfigManager configManager;
    private EssentialsConfig config;
    private NamePrefixService namePrefixService;
    private ScoreboardService scoreboardService;
    private ScheduledCommandService scheduledCommandService;

    private final List<ScheduledTimer> timers = new ArrayList<>();
    private Scoreboard mainScoreboard;
    private Team team;
    private ScoreboardManager scoreboardManager;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        BukkitScheduler scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        lenient().when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    BukkitTask task = mock(BukkitTask.class);
                    timers.add(new ScheduledTimer(inv.getArgument(1), inv.getArgument(3), task));
                    return task;
                });
        lenient().when(EssentialsTestHelper.getMockServer().getPluginManager().getPlugin("UltiTools"))
                .thenReturn(mock(Plugin.class));

        scoreboardManager = mock(ScoreboardManager.class);
        mainScoreboard = mock(Scoreboard.class);
        team = mock(Team.class);
        lenient().when(scoreboardManager.getMainScoreboard()).thenReturn(mainScoreboard);
        lenient().when(mainScoreboard.getTeam(anyString())).thenReturn(null);
        lenient().when(mainScoreboard.registerNewTeam(anyString())).thenReturn(team);
        lenient().when(scoreboardManager.getNewScoreboard()).thenAnswer(inv -> newSidebarScoreboard());
        lenient().when(EssentialsTestHelper.getMockServer().getScoreboardManager()).thenReturn(scoreboardManager);

        player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        lenient().when(player.isOnline()).thenReturn(true);
        doReturn(Collections.singletonList(player)).when(EssentialsTestHelper.getMockServer()).getOnlinePlayers();
        lenient().when(EssentialsTestHelper.getMockServer().getPlayer(player.getUniqueId())).thenReturn(player);

        configFile = moduleFolder.resolve("config").resolve("essentials.yml").toFile();
        assertThat(configFile.getParentFile().mkdirs()).isTrue();
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Test
    @DisplayName("name prefixes turned on by reload: updatePlayer does not throw and applies the team")
    void namePrefixTurnedOnByReloadAppliesTeam() throws Exception {
        boot(yaml(false, 5, false, 1, false, Collections.<String>emptyList()));
        assertThat(config.isNamePrefixEnabled()).isFalse();
        assertThat(periods()).isEmpty();

        rewriteAndReload(yaml(true, 5, false, 1, false, Collections.<String>emptyList()));
        assertThat(config.isNamePrefixEnabled()).isTrue();

        assertThatCode(() -> namePrefixService.updatePlayer(player)).doesNotThrowAnyException();
        verify(team).addEntry("Steve");
        assertThat(periods()).containsExactly(100L);
    }

    @Test
    @DisplayName("name prefixes turned off by reload: the update task is cancelled and the player leaves the team")
    void namePrefixTurnedOffByReloadCancelsTaskAndClearsTeam() throws Exception {
        boot(yaml(true, 5, false, 1, false, Collections.<String>emptyList()));
        namePrefixService.updatePlayer(player);
        lenient().when(team.getEntries()).thenReturn(Collections.singleton("Steve"));
        ScheduledTimer bootTimer = onlyTimer(100L);

        rewriteAndReload(yaml(false, 5, false, 1, false, Collections.<String>emptyList()));

        verify(bootTimer.task).cancel();
        verify(team).removeEntry("Steve");
        assertThat(timers).hasSize(1);
    }

    @Test
    @DisplayName("scheduled command list changed by reload: the old task is cancelled and the new command is scheduled")
    void scheduledCommandChangeIsRescheduled() throws Exception {
        boot(yaml(false, 5, false, 1, true, Collections.singletonList("60:say old")));
        ScheduledTimer oldTimer = onlyTimer(1200L);

        rewriteAndReload(yaml(false, 5, false, 1, true, Collections.singletonList("30:say new")));

        verify(oldTimer.task).cancel();
        ScheduledTimer newTimer = onlyTimer(600L);
        newTimer.runnable.run();
        verify(EssentialsTestHelper.getMockServer()).dispatchCommand(any(), org.mockito.ArgumentMatchers.eq("say new"));
        verify(EssentialsTestHelper.getMockServer(), never())
                .dispatchCommand(any(), org.mockito.ArgumentMatchers.eq("say old"));
    }

    @Test
    @DisplayName("scoreboard turned on by reload: the update task starts and online players get a sidebar")
    void scoreboardTurnedOnByReloadStartsAndShowsSidebar() throws Exception {
        boot(yaml(false, 5, false, 1, false, Collections.<String>emptyList()));
        assertThat(periods()).isEmpty();
        verify(player, never()).setScoreboard(any(Scoreboard.class));

        rewriteAndReload(yaml(false, 5, true, 2, false, Collections.<String>emptyList()));

        assertThat(periods()).containsExactly(40L);
        assertThat(scoreboardService.isEnabled(player)).isTrue();
        verify(player).setScoreboard(any(Scoreboard.class));
    }

    @Test
    @DisplayName("scoreboard interval changed by reload: the old task is cancelled and a task with the new period starts")
    void scoreboardIntervalChangeIsRescheduled() throws Exception {
        boot(yaml(false, 5, true, 1, false, Collections.<String>emptyList()));
        ScheduledTimer oldTimer = onlyTimer(20L);

        rewriteAndReload(yaml(false, 5, true, 3, false, Collections.<String>emptyList()));

        verify(oldTimer.task).cancel();
        assertThat(periods()).containsExactly(20L, 60L);
    }

    @Test
    @DisplayName("a player who hid the sidebar keeps it hidden across a reload that changes an unrelated key")
    void hiddenSidebarChoiceSurvivesUnrelatedReload() throws Exception {
        boot(yaml(false, 5, true, true, 1, false, Collections.<String>emptyList()));
        scoreboardService.enableScoreboard(player); // what ScoreboardListener does on join with auto-enable
        assertThat(scoreboardService.toggleScoreboard(player)).isFalse(); // the player runs /scoreboard
        clearInvocations(player);

        rewriteAndReload(yaml(false, 6, true, true, 1, false, Collections.<String>emptyList()));

        assertThat(scoreboardService.isEnabled(player)).isFalse();
        verify(player, never()).setScoreboard(any(Scoreboard.class));
    }

    @Test
    @DisplayName("with auto-enable off, a player who showed the sidebar keeps it shown across a reload that changes an unrelated key")
    void shownSidebarChoiceSurvivesUnrelatedReloadWithoutAutoEnable() throws Exception {
        boot(yaml(false, 5, true, false, 1, false, Collections.<String>emptyList()));
        assertThat(scoreboardService.toggleScoreboard(player)).isTrue(); // the player runs /scoreboard
        clearInvocations(player);

        rewriteAndReload(yaml(false, 6, true, false, 1, false, Collections.<String>emptyList()));

        assertThat(scoreboardService.isEnabled(player)).isTrue();
        verify(player, atLeastOnce()).setScoreboard(any(Scoreboard.class));
    }

    // ---------------------------------------------------------------------------------------------

    private void boot(String yaml) throws Exception {
        write(yaml);
        plugin = mock(UltiEssentials.class, CALLS_REAL_METHODS);
        setResourceFolderPath(plugin, moduleFolder.toString());

        config = new EssentialsConfig();
        configManager = new ConfigManager();
        configManager.register(plugin, config);

        namePrefixService = new NamePrefixService();
        scoreboardService = new ScoreboardService();
        scheduledCommandService = new ScheduledCommandService();
        EssentialsTestHelper.setField(namePrefixService, "config", config);
        EssentialsTestHelper.setField(scoreboardService, "config", config);
        EssentialsTestHelper.setField(scheduledCommandService, "config", config);
        namePrefixService.init();
        scoreboardService.init();
        scheduledCommandService.init();

        SimpleContainer container = new SimpleContainer();
        container.registerType(NamePrefixService.class, namePrefixService);
        container.registerType(ScoreboardService.class, scoreboardService);
        container.registerType(ScheduledCommandService.class, scheduledCommandService);
        plugin.setContext(container);
    }

    private void rewriteAndReload(String yaml) throws Exception {
        write(yaml);
        configManager.reloadConfigs(plugin);
        invokeOnReload(plugin);
    }

    // onReload() is protected in the framework's package; reloadSelf() calls it virtually, and so
    // does this reflective call, so it reaches whatever this module declares.
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // invokes the protected framework hook as reloadSelf() does
    private static void invokeOnReload(UltiToolsPlugin plugin) throws Exception {
        Method hook = UltiToolsPlugin.class.getDeclaredMethod("onReload");
        hook.setAccessible(true);
        hook.invoke(plugin);
    }

    private ScheduledTimer onlyTimer(long period) {
        List<ScheduledTimer> matching = new ArrayList<>();
        for (ScheduledTimer timer : timers) {
            if (timer.period == period) {
                matching.add(timer);
            }
        }
        assertThat(matching).as("timers with period %d", period).hasSize(1);
        return matching.get(0);
    }

    private List<Long> periods() {
        List<Long> periods = new ArrayList<>();
        for (ScheduledTimer timer : timers) {
            periods.add(timer.period);
        }
        return periods;
    }

    private Scoreboard newSidebarScoreboard() {
        Scoreboard scoreboard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        lenient().when(scoreboard.getEntries()).thenReturn(Collections.<String>emptySet());
        lenient().when(scoreboard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(objective);
        lenient().when(objective.getScore(anyString())).thenReturn(mock(Score.class));
        return scoreboard;
    }

    private static String yaml(boolean namePrefix, int namePrefixInterval, boolean scoreboard,
                               int scoreboardInterval, boolean scheduled, List<String> commands) {
        return yaml(namePrefix, namePrefixInterval, scoreboard, true, scoreboardInterval, scheduled, commands);
    }

    private static String yaml(boolean namePrefix, int namePrefixInterval, boolean scoreboard, boolean autoEnable,
                               int scoreboardInterval, boolean scheduled, List<String> commands) {
        StringBuilder sb = new StringBuilder()
                .append("features:\n")
                .append("  nameprefix:\n")
                .append("    enabled: ").append(namePrefix).append('\n')
                .append("    update-interval: ").append(namePrefixInterval).append('\n')
                .append("    prefix-format: '[P] '\n")
                .append("    suffix-format: ''\n")
                .append("  scoreboard:\n")
                .append("    enabled: ").append(scoreboard).append('\n')
                .append("    auto-enable: ").append(autoEnable).append('\n')
                .append("    update-interval: ").append(scoreboardInterval).append('\n')
                .append("    title: 'Title'\n")
                .append("    lines:\n")
                .append("    - 'line'\n")
                .append("  scheduled-commands:\n")
                .append("    enabled: ").append(scheduled).append('\n');
        if (commands.isEmpty()) {
            sb.append("    commands: []\n");
        } else {
            sb.append("    commands:\n");
            for (String command : commands) {
                sb.append("    - '").append(command).append("'\n");
            }
        }
        return sb.toString();
    }

    private void write(String content) throws Exception {
        Files.write(configFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    // No supported setter exists: the field is private, its accessors are protected final, and the
    // only public constructor that takes the path also builds the language catalogue from disk.
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    private static void setResourceFolderPath(UltiToolsPlugin plugin, String path) throws Exception {
        Field field = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        field.setAccessible(true);
        field.set(plugin, path);
    }
}
