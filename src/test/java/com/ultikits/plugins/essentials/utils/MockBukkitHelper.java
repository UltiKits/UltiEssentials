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
        // NOTE (14-09): on the 1.21 generation this field is named `mock`
        // (a private static ServerMock), not the legacy `mocked` boolean flag --
        // confirmed via javap, `mocked` does not exist on this generation at all.
        try {
            Field mockField = MockBukkit.class.getDeclaredField("mock");
            mockField.setAccessible(true);
            mockField.set(null, null);
        } catch (Exception ignored) {
        }

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
     * Clears {@code Bukkit.server} when it is occupied by something MockBukkit
     * did not install -- most commonly {@code EssentialsTestHelper.setUp()}'s own
     * raw Mockito {@code mock(Server.class)}, which several test classes in this
     * repository set via reflection (bypassing {@code Bukkit.setServer()}'s own
     * "already set" guard) and deliberately never clear afterward.
     *
     * <p>Call this immediately before {@code MockBukkit.mock()} in a test class
     * whose fixture (or a fixture that ran earlier in the same forked JVM) may
     * have left a non-MockBukkit occupant behind -- {@code MockBukkit.mock()}
     * itself throws {@code UnsupportedOperationException: Cannot redefine
     * singleton Server} if {@code Bukkit.server} is already non-null, from any
     * source (14-09).</p>
     *
     * <p><b>Deliberately distinct from {@link #ensureCleanState()}'s own "do not
     * clear Bukkit.server" rule.</b> That rule protects against double-clearing a
     * server MockBukkit itself owns, which can leave already-memoized
     * registry/PotionEffectType static caches pointing at a torn-down instance.
     * This method only acts when {@link MockBukkit#isMocked()} is already
     * {@code false} -- i.e. the occupant, if any, was never MockBukkit's to begin
     * with, so no such cache exists yet to corrupt.</p>
     */
    public static void clearForeignServer() {
        try {
            if (!MockBukkit.isMocked() && Bukkit.getServer() != null) {
                Field serverField = Bukkit.class.getDeclaredField("server");
                serverField.setAccessible(true);
                serverField.set(null, null);
            }
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
