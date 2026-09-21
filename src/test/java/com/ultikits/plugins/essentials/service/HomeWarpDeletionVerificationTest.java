package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.HomeData;
import com.ultikits.plugins.essentials.entity.WarpData;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.plugins.essentials.utils.SilentlyFailingStore;
import com.ultikits.plugins.essentials.utils.TestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * {@code /delhome} and {@code /delwarp} may report a deletion only once the record is actually gone
 * from the store (UltiKits/UltiEssentials#34).
 * <p>
 * Both services previously returned {@code true} the moment {@code delById} returned, and
 * {@code delById} returns {@code void} — the framework discards the affected-row count — so a
 * delete that matched no row was indistinguishable from one that removed the record. That is what
 * made {@code /delhome farm} report success while {@code /homes} kept listing {@code farm}.
 * <p>
 * Exercised against a real framework {@link com.ultikits.ultitools.interfaces.DataOperator} backed
 * by a temp directory, not a stubbed query chain: the round trip through the operator is the thing
 * in question. The refusal half uses {@link SilentlyFailingStore}, whose delete is a silent no-op —
 * reproducing the store behaviour the issue observed — because a store that always succeeds cannot
 * tell a service that verifies apart from one that does not.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("Verified home/warp deletion (UltiKits/UltiEssentials#34)")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class HomeWarpDeletionVerificationTest {

    @TempDir
    Path tempDir;

    private EssentialsConfig config;

    @BeforeEach
    void setUp() {
        MockBukkitHelper.clearForeignServer();
        MockBukkit.mock();
        TestHelper.mockUltiToolsInstance();

        config = mock(EssentialsConfig.class);
        lenient().when(config.isHomeEnabled()).thenReturn(true);
        lenient().when(config.isWarpEnabled()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    @Nested
    @DisplayName("Homes")
    class HomeTests {

        @Test
        @DisplayName("a home that really is removed is reported as deleted, and is gone from the store")
        void aRemovedHomeIsReportedDeleted() throws Exception {
            SilentlyFailingStore<HomeData> store = homeStore();
            HomeService service = homeService(store);
            UUID player = UUID.randomUUID();
            store.insert(home(player, "farm"));

            boolean deleted = service.deleteHome(player, "farm");

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(deleted).isTrue();
            assertThat(service.getHome(player, "farm")).as("home re-read from the store").isNull();
            assertThat(service.getHomes(player)).isEmpty();
        }

        @Test
        @DisplayName("a home the store silently kept is NOT reported as deleted")
        void aKeptHomeIsNotReportedDeleted() throws Exception {
            SilentlyFailingStore<HomeData> store = homeStore();
            HomeService service = homeService(store);
            UUID player = UUID.randomUUID();
            store.insert(home(player, "farm"));
            store.ignoreDeletes();

            boolean deleted = service.deleteHome(player, "farm");

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(deleted).as("reported outcome while the record survives").isFalse();
            assertThat(service.getHome(player, "farm")).as("the record the caller was told about").isNotNull();
        }

        @Test
        @DisplayName("a home nobody has is still reported as not found, without touching the store")
        void anAbsentHomeIsReportedNotFound() throws Exception {
            SilentlyFailingStore<HomeData> store = homeStore();
            HomeService service = homeService(store);

            boolean deleted = service.deleteHome(UUID.randomUUID(), "never-existed");

            assertThat(deleted).isFalse();
            assertThat(store.deleteAttempts()).as("no delete attempted for a record that is absent").isZero();
        }

        @Test
        @DisplayName("one player's home is not reported deleted by another player's /delhome")
        void anotherPlayersHomeIsNotDeleted() throws Exception {
            SilentlyFailingStore<HomeData> store = homeStore();
            HomeService service = homeService(store);
            UUID owner = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            store.insert(home(owner, "farm"));

            boolean deleted = service.deleteHome(other, "farm");

            assertThat(deleted).isFalse();
            assertThat(service.getHome(owner, "farm")).as("the owner's home").isNotNull();
        }
    }

    @Nested
    @DisplayName("Warps")
    class WarpTests {

        @Test
        @DisplayName("a warp that really is removed is reported as deleted, and is gone from the store")
        void aRemovedWarpIsReportedDeleted() throws Exception {
            SilentlyFailingStore<WarpData> store = warpStore();
            WarpService service = warpService(store);
            store.insert(warp("shop"));

            boolean deleted = service.deleteWarp("shop");

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(deleted).isTrue();
            assertThat(service.getWarp("shop")).as("warp re-read from the store").isNull();
            assertThat(service.getAllWarps()).isEmpty();
        }

        @Test
        @DisplayName("a warp the store silently kept is NOT reported as deleted")
        void aKeptWarpIsNotReportedDeleted() throws Exception {
            SilentlyFailingStore<WarpData> store = warpStore();
            WarpService service = warpService(store);
            store.insert(warp("shop"));
            store.ignoreDeletes();

            boolean deleted = service.deleteWarp("shop");

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(deleted).as("reported outcome while the record survives").isFalse();
            assertThat(service.getWarp("shop")).as("the record the caller was told about").isNotNull();
        }

        @Test
        @DisplayName("a warp nobody created is still reported as not found, without touching the store")
        void anAbsentWarpIsReportedNotFound() throws Exception {
            SilentlyFailingStore<WarpData> store = warpStore();
            WarpService service = warpService(store);

            boolean deleted = service.deleteWarp("never-existed");

            assertThat(deleted).isFalse();
            assertThat(store.deleteAttempts()).as("no delete attempted for a record that is absent").isZero();
        }
    }

    // === fixtures ===

    private SilentlyFailingStore<HomeData> homeStore() {
        return new SilentlyFailingStore<>(tempDir.resolve("homes").toFile().getAbsolutePath(), HomeData.class);
    }

    private SilentlyFailingStore<WarpData> warpStore() {
        return new SilentlyFailingStore<>(tempDir.resolve("warps").toFile().getAbsolutePath(), WarpData.class);
    }

    private HomeService homeService(SilentlyFailingStore<HomeData> store) throws Exception {
        HomeService service = new HomeService();
        setField(HomeService.class, service, "config", config);
        setField(HomeService.class, service, "homeOperator", store);
        return service;
    }

    private WarpService warpService(SilentlyFailingStore<WarpData> store) throws Exception {
        WarpService service = new WarpService();
        setField(WarpService.class, service, "config", config);
        setField(WarpService.class, service, "warpOperator", store);
        return service;
    }

    private static HomeData home(UUID player, String name) {
        return HomeData.builder()
            .uuid(UUID.randomUUID())
            .playerUuid(player.toString())
            .name(name)
            .world("world")
            .x(1).y(2).z(3)
            .createdAt(System.currentTimeMillis())
            .build();
    }

    private static WarpData warp(String name) {
        return WarpData.builder()
            .uuid(UUID.randomUUID())
            .name(name)
            .world("world")
            .x(1).y(2).z(3)
            .createdBy(UUID.randomUUID().toString())
            .createdAt(System.currentTimeMillis())
            .build();
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
