package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.plugins.essentials.utils.TestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves UltiEssentials#15 (13-CONTEXT.md, re-confirmed on Paper 1.21.11 in
 * {@code 13-RECONFIRMATION.md}): {@link ScoreboardListener} and {@link NamePrefixListener} both
 * looked the framework up under its artifact name ({@code "UltiTools-API"}), which is never a
 * registered plugin name, so every {@code getPlugin(...)} call returned {@code null} and every
 * player join threw {@code IllegalArgumentException: Plugin cannot be null} inside
 * {@code Bukkit.getScheduler().runTaskLater(...)}.
 * <p>
 * The chosen fix resolves the plugin once, under the module's own already-correct registered
 * name (the same string {@code ScoreboardService}, {@code NamePrefixService},
 * {@code TeleportService}, {@code TpaService} and {@code HideCommand} already use), into a field
 * with a null check performed at construction ({@code @PostConstruct init()}) rather than per
 * join -- so the next occurrence of this class of defect fails where an operator is looking
 * (startup) instead of throwing once per player forever.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("ScoreboardListener / NamePrefixListener plugin-lookup Tests")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ScoreboardListenerPluginLookupTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        MockBukkitHelper.clearForeignServer();
        server = MockBukkit.mock();
        TestHelper.mockUltiToolsInstance();
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    @Test
    @DisplayName("theListenerResolvesTheFrameworkPlugin: both listeners resolve a non-null plugin handle when the framework is registered under its actual plugin name, \"UltiTools\"")
    void theListenerResolvesTheFrameworkPlugin() throws Exception {
        MockBukkit.createMockPlugin("UltiTools");

        ScoreboardListener scoreboardListener = new ScoreboardListener();
        scoreboardListener.init();
        assertThat(resolvedPlugin(scoreboardListener)).isNotNull();

        NamePrefixListener namePrefixListener = new NamePrefixListener();
        namePrefixListener.init();
        assertThat(resolvedPlugin(namePrefixListener)).isNotNull();
    }

    @Test
    @DisplayName("anUnresolvableFrameworkFailsAtStartupRatherThanOnJoin: when the lookup cannot resolve, both listeners fail at construction rather than on the first join")
    void anUnresolvableFrameworkFailsAtStartupRatherThanOnJoin() {
        // No plugin is registered under any name -- resolution must fail inside init(),
        // never silently, and never deferred to the first PlayerJoinEvent.
        assertThatThrownBy(() -> new ScoreboardListener().init())
            .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new NamePrefixListener().init())
            .isInstanceOf(IllegalStateException.class);
    }

    private static Object resolvedPlugin(Object listener) throws Exception {
        Field field = listener.getClass().getDeclaredField("bukkitPlugin");
        field.setAccessible(true);
        return field.get(listener);
    }
}
