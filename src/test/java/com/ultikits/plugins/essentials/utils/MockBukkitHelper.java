/**
 * Migrated off the legacy `be.seeseemelk.mockbukkit` generation to
 * `org.mockbukkit.mockbukkit` (Phase 14, 14-09) - 2026-09-06.
 * Source of the original pattern: src/test/java/com/ultikits/ultitools/utils/MockBukkitHelper.java
 *
 * 如需更新，请从 UltiTools-Reborn 主项目同步此文件
 */
package com.ultikits.plugins.essentials.utils;

import java.lang.reflect.Field;

import org.bukkit.Bukkit;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * MockBukkit 测试工具类
 * 提供健壮的 MockBukkit 清理功能，解决测试之间的单例冲突问题
 */
@SuppressWarnings("PMD.AvoidAccessibilityAlteration") // Test helper requires reflection for state cleanup
public final class MockBukkitHelper {

    private MockBukkitHelper() {
        // 工具类不允许实例化
    }

    /**
     * 安全地清理 MockBukkit 和 Bukkit 的单例状态
     * 在每个测试的 @BeforeEach 开始时调用
     *
     * NOTE: This method only unmocks if MockBukkit is currently mocked.
     * It does NOT clear Bukkit.server to null, because that would break
     * subsequent MockBukkit.mock() calls that need to initialize Registry
     * and PotionEffectType classes.
     */
    public static void ensureCleanState() {
        // Only unmock if currently mocked
        try {
            if (MockBukkit.isMocked()) {
                MockBukkit.unmock();
            }
        } catch (Exception ignored) {
        }

        // Reset the static mock-holder field so MockBukkit.mock() can be called again.
        clearMockHolder();

        // DO NOT clear Bukkit.server to null here!
        // MockBukkit.unmock() already does that, and setting it to null
        // before MockBukkit.mock() breaks Registry/PotionEffectType initialization.
    }

    /**
     * 安全地卸载 MockBukkit
     * 在每个测试的 @AfterEach 结束时调用
     */
    public static void safeUnmock() {
        try {
            MockBukkit.unmock();
        } catch (Exception ignored) {
        }
    }

    /**
     * Leaves {@code Bukkit.server} null, so that a following {@code MockBukkit.mock()} can install
     * its own {@link ServerMock}. {@code MockBukkit.mock(T)} delegates to
     * {@code Bukkit.setServer(...)}, which throws {@code UnsupportedOperationException: Cannot
     * redefine singleton Server} whenever that field is already occupied, from any source
     * (verified against paper-api 1.21.11: {@code Bukkit.setServer} offsets 0-15).
     *
     * <p>The usual occupant is {@code EssentialsTestHelper.setUp()}'s raw Mockito
     * {@code mock(Server.class)}, which several test classes in this repository install via
     * reflection (bypassing {@code Bukkit.setServer()}'s own "already set" guard) and deliberately
     * never clear afterward. Call this immediately before {@code MockBukkit.mock()} in any class
     * that may run after one of those in the same forked JVM (14-09).</p>
     *
     * <p><b>The occupant is classified by its runtime type, not by {@link MockBukkit#isMocked()}.</b>
     * An earlier revision guarded on {@code !isMocked()} and argued that a false reading proved the
     * occupant "was never MockBukkit's to begin with, so no memoized registry/PotionEffectType cache
     * exists yet to corrupt". That inference does not follow. {@code isMocked()} is exactly
     * {@code mock != null} (MockBukkit 4.101.0 bytecode), and {@link #ensureCleanState()} nulls that
     * same holder field <em>unconditionally</em> while deliberately not touching
     * {@code Bukkit.server}. This repository can reach the resulting split state:
     * {@code ensureCleanState()} swallows every exception, and {@code MockBukkit.unmock()}'s leading
     * instructions -- offsets 7-22, {@code mock.getPluginManager()} and {@code disablePlugins()} --
     * sit outside its own exception table, which covers only 25-34, so a throw there escapes before
     * {@code setServerInstanceToNull()} ever runs. {@code isMocked()} then reads {@code false} while
     * {@code Bukkit.server} still holds a live {@code ServerMock}, and the old guard would have torn
     * that server down through the "foreign" path it was written to avoid.</p>
     *
     * <p><b>Postcondition: BOTH singletons are clear -- {@code Bukkit.server == null} and MockBukkit's
     * private static {@code mock} holder null.</b> An earlier revision stated only the first half, and
     * delivered only the first half, which is too weak for the sole caller. {@code MockBukkit.mock(T)}
     * tests the holder <em>first</em> (offsets 0-15, {@code IllegalStateException: "Already mocking"})
     * and reaches {@code Bukkit.setServer(...)} only at offset 45, so a run that clears
     * {@code Bukkit.server} while leaving the holder set does not recover -- it swaps one failure for
     * another. {@link #safeUnmock()} cannot be relied on to clear either field: it swallows every
     * exception, and {@code unmock()}'s own exception table covers offsets 25-34 alone, so a throw
     * from {@code getPluginManager()}/{@code disablePlugins()} (7-22) or from {@code unload()}/
     * {@code reset()} (34-49) escapes before {@code setServerInstanceToNull()} at offset 49 ever runs.
     * Measured against the previous implementation with a {@code ServerMock} whose
     * {@code getPluginManager()} throws: it left {@code Bukkit.server=null, mock!=null} and the next
     * {@code MockBukkit.mock()} threw {@code IllegalStateException: Already mocking}.</p>
     *
     * <p>So a {@link ServerMock} occupant is still handed back to MockBukkit via {@link #safeUnmock()}
     * first, letting MockBukkit's own teardown bookkeeping run; both fields are then cleared
     * unconditionally and independently -- the same pair {@code setServerInstanceToNull()} clears, with
     * the holder clear shared with {@link #ensureCleanState()} through {@link #clearMockHolder()}
     * rather than written a third time. Clearing an already-null field is a no-op, so the
     * unconditional form is also correct for a genuinely foreign occupant and for an orphaned holder
     * with no server at all -- a state the old {@code occupant == null} early return left untouched.
     * Each clear sits in its own {@code try}, so a failure of one cannot skip the other; if reflection
     * itself fails the postcondition is unmet, but the caller's immediately following
     * {@code MockBukkit.mock()} then fails loudly rather than silently.</p>
     */
    public static void clearForeignServer() {
        if (Bukkit.getServer() instanceof ServerMock) {
            safeUnmock();
        }
        clearBukkitServerField();
        clearMockHolder();
    }

    /**
     * Nulls MockBukkit's private static {@code mock} holder -- the field {@link MockBukkit#isMocked()}
     * reads and the field {@code MockBukkit.mock(T)} guards on before installing a new server.
     *
     * <p>NOTE (14-09): on the 1.21 generation this field is named {@code mock} (a private static
     * {@link ServerMock}), not the legacy {@code mocked} boolean flag -- confirmed via javap,
     * {@code mocked} does not exist on this generation at all.</p>
     */
    private static void clearMockHolder() {
        try {
            Field mockField = MockBukkit.class.getDeclaredField("mock");
            mockField.setAccessible(true);
            mockField.set(null, null);
        } catch (Exception ignored) {
        }
    }

    /**
     * Nulls {@code Bukkit.server} reflectively, which is what MockBukkit's own
     * {@code setServerInstanceToNull()} does. {@code Bukkit} exposes no public setter that can
     * displace an existing occupant -- {@code Bukkit.setServer(...)} throws
     * {@code UnsupportedOperationException} whenever the field is already populated.
     */
    private static void clearBukkitServerField() {
        try {
            Field serverField = Bukkit.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(null, null);
        } catch (Exception ignored) {
        }
    }

    /**
     * The module's single, shared entry point for bootstrapping a live MockBukkit server in an
     * active (non-{@code @Disabled}) test class.
     *
     * <p>Before this method existed, {@code UltiEssentialsRegistrySentinelTest} and
     * {@code BanListenerMockitoTest} -- the only two currently-enabled test classes in this module
     * that need a genuinely live server rather than a raw Mockito {@code mock(Server.class)} --
     * each inlined the identical {@link #clearForeignServer()} + {@code MockBukkit.mock()} pair
     * independently. That duplication defeated the sentinel's own purpose: deleting or breaking
     * this bootstrap sequence in the sentinel's own {@code @BeforeEach} left every other class's
     * live-server wiring untouched, so the sentinel could go red while the module's actual
     * production-path test ({@code BanListenerMockitoTest}, which exercises
     * {@code BanListener.onPlayerLogin} against a real {@code Bukkit.createProfile(...)}) never
     * noticed. Routing both classes through this one method means breaking it breaks both --
     * the sentinel is now watching the same wiring the production-path test actually depends on,
     * not merely its own private copy of it (14-13).</p>
     *
     * @return the freshly-mocked {@link ServerMock}, for callers that need direct access to it
     *         (e.g. to add players or worlds) without a second lookup via {@link MockBukkit#getMock()}
     */
    public static ServerMock bootstrapLiveServer() {
        clearForeignServer();
        return MockBukkit.mock();
    }
}
