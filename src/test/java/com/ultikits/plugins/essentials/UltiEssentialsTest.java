package com.ultikits.plugins.essentials;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.plugins.essentials.utils.TestHelper;
import com.ultikits.ultitools.UltiTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UltiEssentials main plugin class.
 * <p>
 * 测试 UltiEssentials 主插件类的生命周期。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("UltiEssentials Tests")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
// 13-04 re-measurement: the stale reason was never true for this class -- it constructs a REAL
// UltiEssentials via `new UltiEssentials()` and calls registerSelf() directly, which runs
// UltiToolsPlugin's no-arg constructor. That constructor's getInputStream() (:439-441)
// unconditionally builds a "jar:file:" + CodeSource-location + "!/plugin.yml" URL, assuming the
// running classpath entry is a packaged jar. In a unit test the CodeSource is target/classes/ (a
// directory), so the jar: URL never resolves, loadPluginConfiguration() falls back to an empty
// YamlConfiguration, and the constructor throws PluginModuleException ("no 'name:' key in its
// plugin.yml") -- confirmed after fixing this class's OWN prior, narrower cause (getLogger()
// left unstubbed by TestHelper.mockUltiToolsInstance(), fixed in setUp() below). Testing a real
// UltiToolsPlugin's construction needs a packaged jar on the test classpath, which is out of
// scope for this plan (13-04's job is the test harness, not a new build step); filed as an issue
// rather than routed to "needs the bootstrap" -- MockBukkit.load() does not apply either, since
// UltiToolsPlugin is not a Bukkit Plugin (implements IPlugin directly, per the framework's own
// architecture notes).
@Disabled("new UltiEssentials() runs UltiToolsPlugin's no-arg constructor, whose getInputStream()"
        + " assumes a packaged jar (\"jar:file:\" + CodeSource + \"!/plugin.yml\"); the test"
        + " classpath is a directory (target/classes/), so plugin.yml never resolves and the"
        + " constructor throws PluginModuleException for a missing 'name:' key -- see UltiKits/"
        + "UltiEssentials#21")
class UltiEssentialsTest {

    private ServerMock server;
    private UltiEssentials plugin;

    @BeforeEach
    void setUp() {
        MockBukkitHelper.ensureCleanState();
        server = MockBukkit.mock();
        TestHelper.mockUltiToolsInstance();
        // UltiToolsPlugin.getLogger() wraps UltiTools.getInstance().getLogger() in a
        // PluginLogger; TestHelper.mockUltiToolsInstance() leaves getLogger() unstubbed
        // (returns null), and PluginLogger.log's own delegation methods NPE on that --
        // the actual cause behind this class's stale "MockBukkit Registry/PotionEffectType"
        // reason (13-04 re-measurement). UltiTools.getInstance() already returns the mock
        // that method installed, so it is stubbed further here rather than in TestHelper.java,
        // which is a file synced verbatim from the framework and not meant to diverge per test.
        lenient().when(UltiTools.getInstance().getLogger())
                .thenReturn(Logger.getLogger("UltiEssentialsTest"));
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    @Nested
    @DisplayName("Plugin Lifecycle Tests")
    class PluginLifecycleTests {

        @Test
        @DisplayName("Should initialize plugin successfully")
        void shouldInitializePlugin() {
            plugin = new UltiEssentials();

            boolean result = plugin.registerSelf();

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should unregister plugin successfully")
        void shouldUnregisterPlugin() {
            plugin = new UltiEssentials();
            plugin.registerSelf();

            Assertions.assertDoesNotThrow(() -> plugin.unregisterSelf());
        }

        @Test
        @DisplayName("Should reload plugin successfully")
        void shouldReloadPlugin() {
            plugin = new UltiEssentials();
            plugin.registerSelf();

            Assertions.assertDoesNotThrow(() -> plugin.reloadSelf());
        }
    }
}
