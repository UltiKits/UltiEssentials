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
import org.bukkit.Server;

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
     * <p>So a {@link ServerMock} occupant is handed back to MockBukkit via {@link #safeUnmock()}
     * first, letting MockBukkit's own teardown bookkeeping run, and is cleared reflectively only if
     * that leaves the field still occupied -- which is what an orphaned {@code ServerMock} (holder
     * already nulled, so {@code unmock()} returns immediately) looks like, and is the same clear
     * MockBukkit's own {@code setServerInstanceToNull()} performs. Anything else is genuinely
     * foreign and is cleared directly. Either way the postcondition is the one callers need:
     * {@code Bukkit.server == null}.</p>
     */
    public static void clearForeignServer() {
        try {
            Server occupant = Bukkit.getServer();
            if (occupant == null) {
                return;
            }
            if (occupant instanceof ServerMock) {
                safeUnmock();
                if (Bukkit.getServer() == null) {
                    return;
                }
            }
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
