package com.ultikits.plugins.essentials;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockbukkit.mockbukkit.ServerMock;

import com.ultikits.plugins.essentials.utils.MockBukkitHelper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reopen guard for the module's shared test-time server bootstrap (mockbukkit-v1.21).
 *
 * <p>Every assertion here depends on a live server, never a bare registry constant --
 * mockbukkit-v1.21 registers its {@code RegistryAccess} mock via {@code ServiceLoader}, so a
 * registry constant alone would resolve merely from the dependency being on the classpath, even
 * with every {@code MockBukkit.mock()} call deleted. This class must go red the moment the
 * bootstrap is removed, and green the moment it is restored.</p>
 *
 * <p><b>Bootstraps through {@link MockBukkitHelper#bootstrapLiveServer()}, deliberately not its
 * own {@code MockBukkit.mock()} call.</b> An earlier revision of this class called
 * {@code MockBukkit.mock()} directly, which meant this sentinel only proved that MockBukkit's own
 * API resolves a live server -- it did not notice a regression in the module's shared bootstrap at
 * all, because it never depended on it. {@code MockBukkitHelper.bootstrapLiveServer()} is this
 * module's one shared entry point: it is also the exact sequence {@code BanListenerMockitoTest}
 * (a real production-path test, not a sentinel) depends on to make
 * {@code Bukkit.createProfile(...)} resolve for {@code BanListener.onPlayerLogin}. Breaking or
 * removing {@code bootstrapLiveServer()} now fails both classes, not just this one (14-13).</p>
 *
 * <p>{@code bootstrapLiveServer()} itself opens with {@link MockBukkitHelper#clearForeignServer()},
 * which guards against a real, measured hazard in this repository: several test classes (e.g.
 * {@code WildCommandTest} via {@code EssentialsTestHelper}) install a raw Mockito
 * {@code mock(Server.class)} into {@code Bukkit.server} via reflection and never clear it. Without
 * that pre-step, {@code MockBukkit.mock()} throws {@code UnsupportedOperationException: Cannot
 * redefine singleton Server} whenever this sentinel happens to run after one of those classes in
 * the same forked JVM -- a reopen guard must not itself be fragile to unrelated test ordering
 * (14-09). Kept here unchanged; only the call site moved.</p>
 */
public class UltiEssentialsRegistrySentinelTest {

    @BeforeEach
    void setUp() {
        MockBukkitHelper.bootstrapLiveServer();
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    /**
     * Asserts the installed server's <em>type</em>, not merely its presence.
     *
     * <p>A bare {@code assertNotNull(Bukkit.getServer())} does not establish that this class's
     * bootstrap ran: it is equally satisfied by the raw Mockito {@code mock(Server.class)} that
     * {@code EssentialsTestHelper.setUp()} installs and never clears. Measured with the bootstrap
     * removed and such a mock installed in its place, this class's other three tests failed while
     * the {@code assertNotNull} form of this one still passed -- a reopen guard that a leaked mock
     * can satisfy is not guarding the bootstrap.</p>
     */
    @Test
    void liveServerIsBootstrapped() {
        assertInstanceOf(ServerMock.class, Bukkit.getServer(),
            "live server bootstrap must install a MockBukkit ServerMock; a raw Mockito "
                + "mock(Server.class) leaked by another test class satisfies a bare assertNotNull "
                + "but is not a live server");
    }

    @Test
    void unsafeValuesResolves() {
        assertNotNull(Bukkit.getUnsafe(), "UnsafeValues must resolve on a live server");
    }

    @Test
    void createProfileDoesNotSilentlyReturnNull() {
        Object profile = Bukkit.createProfile(UUID.randomUUID(), "SentinelPlayer");
        assertNotNull(profile, "createProfile must not silently return null");
    }

    /**
     * Guards the registry path, and names the failure it produces.
     *
     * <p>Without the bootstrap, {@code new ItemStack(...)} throws MockBukkit's
     * {@code IncompatiblePaperVersionException}, whose text reads "Version Mismatch!" and advises
     * recompiling against a matching Paper API. <b>That advice is a dead end here.</b> The versions
     * already match: MockBukkit 4.101.0 reports itself built against Paper API
     * 1.21.11-R0.1-SNAPSHOT, and that is exactly what {@code pom.xml} depends on.
     * {@code RegistryMock.loadIfEmpty} catches {@code ExceptionInInitializerError} and reports
     * every instance as a version mismatch, so the message describes the catch site rather than the
     * cause. Both absent-bootstrap shapes were measured, and both bottom out in
     * {@code Tag.<clinit>} calling {@code Bukkit.getTag(...)} without a live server behind it:
     * a {@code NullPointerException} on a null {@code Bukkit.server}, or the same on a raw Mockito
     * {@code mock(Server.class)} returning null from the unstubbed call. Neither is a Paper version
     * problem.</p>
     *
     * <p>The assertion message below carries that so the next maintainer reads it at the point of
     * failure instead of rediscovering it.</p>
     */
    @Test
    void itemStackConstructionResolvesRegistry() {
        ItemStack stack = assertDoesNotThrow(
            () -> new ItemStack(Material.DIAMOND),
            "ItemStack construction must resolve MockBukkit's registry. If this reports "
                + "IncompatiblePaperVersionException \"Version Mismatch!\", do NOT go looking for a "
                + "version to correct -- MockBukkit 4.101.0 and this pom both target Paper "
                + "1.21.11-R0.1-SNAPSHOT. RegistryMock.loadIfEmpty reports any "
                + "ExceptionInInitializerError under that message; the real cause is Tag.<clinit> "
                + "calling Bukkit.getTag(...) without a live server behind it (Bukkit.server null, "
                + "or a raw Mockito mock returning null), i.e. the live server bootstrap is missing");
        assertNotNull(stack);
        assertEquals(Material.DIAMOND, stack.getType());
    }
}
