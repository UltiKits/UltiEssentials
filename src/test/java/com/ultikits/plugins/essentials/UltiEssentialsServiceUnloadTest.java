package com.ultikits.plugins.essentials;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScheduledCommandService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.service.TeleportService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.manager.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;
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
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * UltiKits/UltiEssentials#43: unloading this module at runtime must stop every repeating Bukkit
 * task it started.
 * <p>
 * The module's repeating tasks are owned by the {@code UltiTools} Bukkit plugin, not by the module,
 * so Bukkit does not cancel them when the module is unloaded, and the framework's unload path can
 * only cancel the tasks it created itself from {@code @Scheduled} methods. Until this hook existed,
 * {@code /upm uninstall UltiEssentials} reported the module uninstalled while its scheduled console
 * commands kept executing, its sidebar timer kept rebuilding scoreboards, its name-prefix timer kept
 * writing teams, and a teleport warmup already counting down still teleported the player.
 * <p>
 * Each test boots the four task-owning services the way the container does ({@code @PostConstruct
 * init()} against an {@link EssentialsConfig} registered with a real {@link ConfigManager}), then
 * invokes the module-visible step of the framework's final {@code unregisterSelf()}: the
 * {@code onUnregister()} hook. The framework's own two steps afterwards (command and listener
 * unregistration) need a running UltiTools instance and do not touch these services.
 * <p>
 * Every assertion is on observable state -- cancelled scheduler tasks, the scoreboard a player
 * holds, team entries on the main scoreboard, and whether a warmup is still pending -- and every
 * test asserts the starting state first, so a run in which no task was ever started cannot pass.
 * <p>
 * 验证 /upm uninstall 卸载模块时会取消本模块启动的全部重复任务（#43）。
 */
@DisplayName("Unloading the module stops its repeating tasks (UltiKits/UltiEssentials#43)")
class UltiEssentialsServiceUnloadTest {

    /** Period in ticks of the name-prefix update task, from `features.nameprefix.update-interval: 5`. */
    private static final long NAME_PREFIX_PERIOD = 100L;
    /** Period in ticks of the sidebar update task, from `features.scoreboard.update-interval: 3`. */
    private static final long SCOREBOARD_PERIOD = 60L;
    /** Period in ticks of the one scheduled console command, from the `60:say hello` entry. */
    private static final long SCHEDULED_COMMAND_PERIOD = 1200L;
    /** Period in ticks of a teleport warmup countdown; {@code TeleportService} fixes it at one second. */
    private static final long TELEPORT_WARMUP_PERIOD = 20L;

    /** One {@code runTaskTimer} call observed on the mocked scheduler. */
    private static final class ScheduledTimer {
        private final long period;
        private final BukkitTask task;
        private boolean cancelled;

        private ScheduledTimer(long period, BukkitTask task) {
            this.period = period;
            this.task = task;
        }
    }

    @TempDir
    Path moduleFolder;

    private File configFile;
    private UltiEssentials plugin;
    private EssentialsConfig config;
    private NamePrefixService namePrefixService;
    private ScoreboardService scoreboardService;
    private ScheduledCommandService scheduledCommandService;
    private TeleportService teleportService;

    private final List<ScheduledTimer> timers = new ArrayList<>();
    private Scoreboard mainScoreboard;
    private Team team;
    private final Set<String> teamEntries = new HashSet<>();
    private ScoreboardManager scoreboardManager;
    private Player player;
    private World world;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        BukkitScheduler scheduler = EssentialsTestHelper.getMockServer().getScheduler();
        lenient().when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    BukkitTask task = mock(BukkitTask.class);
                    ScheduledTimer timer = new ScheduledTimer(inv.getArgument(3), task);
                    lenient().doAnswer(cancel -> {
                        timer.cancelled = true;
                        return null;
                    }).when(task).cancel();
                    timers.add(timer);
                    return task;
                });
        lenient().when(EssentialsTestHelper.getMockServer().getPluginManager().getPlugin("UltiTools"))
                .thenReturn(mock(Plugin.class));

        scoreboardManager = mock(ScoreboardManager.class);
        mainScoreboard = mock(Scoreboard.class);
        team = mock(Team.class);
        lenient().when(scoreboardManager.getMainScoreboard()).thenReturn(mainScoreboard);
        Map<String, Team> teams = new HashMap<>();
        lenient().when(mainScoreboard.getTeam(anyString())).thenAnswer(inv -> teams.get(inv.<String>getArgument(0)));
        lenient().when(mainScoreboard.registerNewTeam(anyString())).thenAnswer(inv -> {
            teams.put(inv.getArgument(0), team);
            return team;
        });
        lenient().doAnswer(inv -> teamEntries.add(inv.getArgument(0))).when(team).addEntry(anyString());
        lenient().when(team.removeEntry(anyString())).thenAnswer(inv -> teamEntries.remove(inv.<String>getArgument(0)));
        lenient().when(team.hasEntry(anyString())).thenAnswer(inv -> teamEntries.contains(inv.<String>getArgument(0)));
        lenient().when(team.getEntries()).thenAnswer(inv -> new HashSet<>(teamEntries));
        lenient().when(scoreboardManager.getNewScoreboard()).thenAnswer(inv -> newSidebarScoreboard());
        lenient().when(EssentialsTestHelper.getMockServer().getScoreboardManager()).thenReturn(scoreboardManager);

        world = EssentialsTestHelper.createMockWorld("world");
        player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        lenient().when(player.isOnline()).thenReturn(true);
        lenient().when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
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
    @DisplayName("every repeating task the module started is cancelled, and none is started again")
    void unloadCancelsEveryRepeatingTask() throws Exception {
        bootWithEverythingRunning();

        invokeOnUnregister(plugin);

        assertThat(periods())
                .as("no task is started by the unload itself")
                .containsExactly(NAME_PREFIX_PERIOD, SCOREBOARD_PERIOD, SCHEDULED_COMMAND_PERIOD,
                        TELEPORT_WARMUP_PERIOD);
        for (ScheduledTimer timer : timers) {
            assertThat(timer.cancelled).as("the task with period %d is cancelled", timer.period).isTrue();
            verify(timer.task).cancel();
        }
    }

    @Test
    @DisplayName("the sidebar is taken down and the player is returned to the main scoreboard")
    void unloadReturnsThePlayerToTheMainScoreboard() throws Exception {
        bootWithEverythingRunning();

        invokeOnUnregister(plugin);

        assertThat(scoreboardService.isEnabled(player)).isFalse();
        assertThat(lastScoreboard(player)).isSameAs(mainScoreboard);
    }

    @Test
    @DisplayName("the name-prefix team entries are removed from the main scoreboard")
    void unloadClearsTheNamePrefixTeams() throws Exception {
        bootWithEverythingRunning();

        invokeOnUnregister(plugin);

        assertThat(teamEntries).isEmpty();
    }

    @Test
    @DisplayName("a teleport warmup already counting down is cancelled rather than completed")
    void unloadCancelsAPendingTeleportWarmup() throws Exception {
        bootWithEverythingRunning();

        invokeOnUnregister(plugin);

        assertThat(teleportService.isTeleporting(player.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("no per-player teleport state is left behind after the warmups are cancelled")
    void unloadForgetsThePerPlayerTeleportState() throws Exception {
        bootWithEverythingRunning();

        invokeOnUnregister(plugin);

        assertThat((Map<?, ?>) EssentialsTestHelper.getField(teleportService, "pendingTeleports"))
                .as("the cancelled warmup tasks")
                .isEmpty();
        assertThat((Map<?, ?>) EssentialsTestHelper.getField(teleportService, "teleportStartLocations"))
                .as("the movement-detection start locations, which pin a Location and through it a World")
                .isEmpty();
    }

    @Test
    @DisplayName("a service whose shutdown throws does not stop the others, and the failure is reported to the caller")
    void throwingServiceDoesNotStopTheOthersAndIsReported() throws Exception {
        bootWithEverythingRunning();
        IllegalStateException scoreboardFailure = new IllegalStateException("scoreboard boom");
        IllegalStateException namePrefixFailure = new IllegalStateException("name prefix boom");
        ScoreboardService throwingScoreboard = spy(scoreboardService);
        NamePrefixService throwingNamePrefix = spy(namePrefixService);
        doThrow(scoreboardFailure).when(throwingScoreboard).shutdown();
        doThrow(namePrefixFailure).when(throwingNamePrefix).shutdown();
        registerServices(scheduledCommandService, throwingScoreboard, throwingNamePrefix, teleportService);

        assertThatThrownBy(() -> invokeOnUnregister(plugin))
                .as("the first failure, in the order the hook shuts the services down, is the one that propagates")
                .isSameAs(scoreboardFailure)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(namePrefixFailure));

        assertThat(cancelled(SCHEDULED_COMMAND_PERIOD))
                .as("the service shut down before the throwing one")
                .isTrue();
        assertThat(cancelled(TELEPORT_WARMUP_PERIOD))
                .as("the service shut down after both throwing ones")
                .isTrue();
    }

    @Test
    @DisplayName("a module whose services are not in the container unloads without throwing")
    void unloadWithoutServicesDoesNotThrow() throws Exception {
        bootWithEverythingRunning();
        plugin.setContext(new SimpleContainer());

        assertThatCode(() -> invokeOnUnregister(plugin)).doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Boots the four task-owning services with every feature on, puts the player on a sidebar and in
     * a name-prefix team, starts a teleport warmup, and asserts that starting state -- so a later
     * assertion that everything is cancelled cannot pass because nothing was ever running.
     */
    private void bootWithEverythingRunning() throws Exception {
        boot();
        scoreboardService.enableScoreboard(player);
        namePrefixService.updatePlayer(player);
        teleportService.teleport(player, new Location(world, 10, 64, 10), 5, false);

        assertThat(periods())
                .as("the four repeating tasks this module starts")
                .containsExactly(NAME_PREFIX_PERIOD, SCOREBOARD_PERIOD, SCHEDULED_COMMAND_PERIOD,
                        TELEPORT_WARMUP_PERIOD);
        for (ScheduledTimer timer : timers) {
            assertThat(timer.cancelled).as("the task with period %d is running", timer.period).isFalse();
        }
        assertThat(scoreboardService.isEnabled(player)).isTrue();
        assertThat(lastScoreboard(player)).isNotSameAs(mainScoreboard);
        assertThat(teamEntries).containsExactly("Steve");
        assertThat(teleportService.isTeleporting(player.getUniqueId())).isTrue();
    }

    private void boot() throws Exception {
        write(yaml());
        plugin = mock(UltiEssentials.class, CALLS_REAL_METHODS);
        setResourceFolderPath(plugin, moduleFolder.toString());

        config = new EssentialsConfig();
        new ConfigManager().register(plugin, config);

        namePrefixService = new NamePrefixService();
        scoreboardService = new ScoreboardService();
        scheduledCommandService = new ScheduledCommandService();
        teleportService = new TeleportService();
        EssentialsTestHelper.setField(namePrefixService, "config", config);
        EssentialsTestHelper.setField(scoreboardService, "config", config);
        EssentialsTestHelper.setField(scheduledCommandService, "config", config);
        EssentialsTestHelper.setField(teleportService, "plugin", plugin);
        namePrefixService.init();
        scoreboardService.init();
        scheduledCommandService.init();
        teleportService.init();

        registerServices(scheduledCommandService, scoreboardService, namePrefixService, teleportService);
    }

    private void registerServices(ScheduledCommandService scheduled, ScoreboardService scoreboard,
                                  NamePrefixService namePrefix, TeleportService teleport) {
        SimpleContainer container = new SimpleContainer();
        container.registerType(ScheduledCommandService.class, scheduled);
        container.registerType(ScoreboardService.class, scoreboard);
        container.registerType(NamePrefixService.class, namePrefix);
        container.registerType(TeleportService.class, teleport);
        plugin.setContext(container);
    }

    /** Whether the single task started with {@code period} has been cancelled. */
    private boolean cancelled(long period) {
        List<ScheduledTimer> matching = new ArrayList<>();
        for (ScheduledTimer timer : timers) {
            if (timer.period == period) {
                matching.add(timer);
            }
        }
        assertThat(matching).as("timers with period %d", period).hasSize(1);
        return matching.get(0).cancelled;
    }

    private List<Long> periods() {
        List<Long> periods = new ArrayList<>();
        for (ScheduledTimer timer : timers) {
            periods.add(timer.period);
        }
        return periods;
    }

    private static Scoreboard lastScoreboard(Player target) {
        ArgumentCaptor<Scoreboard> assigned = ArgumentCaptor.forClass(Scoreboard.class);
        verify(target, atLeastOnce()).setScoreboard(assigned.capture());
        return assigned.getValue();
    }

    private Scoreboard newSidebarScoreboard() {
        Scoreboard scoreboard = mock(Scoreboard.class);
        Objective objective = mock(Objective.class);
        lenient().when(scoreboard.getEntries()).thenReturn(Collections.<String>emptySet());
        lenient().when(scoreboard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(objective);
        lenient().when(objective.getScore(anyString())).thenReturn(mock(Score.class));
        return scoreboard;
    }

    // onUnregister() is protected in the framework's package; unregisterSelf() calls it virtually,
    // and so does this reflective call, so it reaches whatever this module declares. The hook's own
    // failure is rethrown as unregisterSelf() rethrows it, rather than wrapped, so a test can assert
    // which failure reaches the caller.
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // invokes the protected framework hook as unregisterSelf() does
    private static void invokeOnUnregister(UltiToolsPlugin plugin) {
        try {
            Method hook = UltiToolsPlugin.class.getDeclaredMethod("onUnregister");
            hook.setAccessible(true);
            hook.invoke(plugin);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException(cause);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String yaml() {
        return "features:\n"
                + "  nameprefix:\n"
                + "    enabled: true\n"
                + "    update-interval: 5\n"
                + "    prefix-format: '[P] '\n"
                + "    suffix-format: ''\n"
                + "  scoreboard:\n"
                + "    enabled: true\n"
                + "    auto-enable: true\n"
                + "    update-interval: 3\n"
                + "    title: 'Title'\n"
                + "    lines:\n"
                + "    - 'line'\n"
                + "  scheduled-commands:\n"
                + "    enabled: true\n"
                + "    commands:\n"
                + "    - '60:say hello'\n";
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
