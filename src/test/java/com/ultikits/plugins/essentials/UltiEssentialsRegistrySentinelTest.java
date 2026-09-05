package com.ultikits.plugins.essentials;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import com.ultikits.plugins.essentials.utils.MockBukkitHelper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reopen guard for the live test-time server bootstrap (mockbukkit-v1.21).
 *
 * <p>Every assertion here depends on a live server, never a bare registry constant --
 * mockbukkit-v1.21 registers its {@code RegistryAccess} mock via {@code ServiceLoader}, so a
 * registry constant alone would resolve merely from the dependency being on the classpath, even
 * with every {@code MockBukkit.mock()} call deleted. This class must go red the moment the
 * bootstrap is removed, and green the moment it is restored.</p>
 *
 * <p>{@link MockBukkitHelper#clearForeignServer()} guards against a real, measured hazard in this
 * repository: several test classes (e.g. {@code WildCommandTest} via {@code EssentialsTestHelper})
 * install a raw Mockito {@code mock(Server.class)} into {@code Bukkit.server} via reflection and
 * never clear it. Without this call, this sentinel's own {@code MockBukkit.mock()} throws
 * {@code UnsupportedOperationException: Cannot redefine singleton Server} whenever it happens to
 * run after one of those classes in the same forked JVM -- a reopen guard must not itself be
 * fragile to unrelated test ordering (14-09).</p>
 */
public class UltiEssentialsRegistrySentinelTest {

    @BeforeEach
    void setUp() {
        MockBukkitHelper.clearForeignServer();
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void liveServerIsBootstrapped() {
        assertNotNull(Bukkit.getServer(), "live server bootstrap must be present");
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

    @Test
    void itemStackConstructionResolvesRegistry() {
        ItemStack stack = new ItemStack(Material.DIAMOND);
        assertNotNull(stack);
        assertEquals(Material.DIAMOND, stack.getType());
    }
}
