package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.HomeData;
import com.ultikits.plugins.essentials.service.HomeService.DeleteResult;
import com.ultikits.plugins.essentials.entity.WarpData;
import com.ultikits.plugins.essentials.service.HomeService.SetHomeResult;
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
import org.bukkit.Location;
import org.bukkit.World;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

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
    private ServerMock server;

    @BeforeEach
    void setUp() {
        MockBukkitHelper.clearForeignServer();
        server = MockBukkit.mock();
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

            DeleteResult deleted = service.deleteHome(player, "farm");

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(deleted).isEqualTo(DeleteResult.REMOVED);
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

            DeleteResult deleted = service.deleteHome(player, "farm");

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(deleted)
                .as("a surviving record must not be reported as an absent one -- the operator would "
                    + "stop looking while /homes still lists it (gate 1 MAJOR-03)")
                .isEqualTo(DeleteResult.FAILED);
            assertThat(service.getHome(player, "farm")).as("the record the caller was told about").isNotNull();
        }

        @Test
        @DisplayName("a home nobody has is still reported as not found, without touching the store")
        void anAbsentHomeIsReportedNotFound() throws Exception {
            SilentlyFailingStore<HomeData> store = homeStore();
            HomeService service = homeService(store);

            DeleteResult deleted = service.deleteHome(UUID.randomUUID(), "never-existed");

            assertThat(deleted).isEqualTo(DeleteResult.NOT_FOUND);
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

            DeleteResult deleted = service.deleteHome(other, "farm");

            assertThat(deleted).isEqualTo(DeleteResult.NOT_FOUND);
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

            WarpService.DeleteResult deleted = service.deleteWarp("shop");

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(deleted).isEqualTo(WarpService.DeleteResult.REMOVED);
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

            WarpService.DeleteResult deleted = service.deleteWarp("shop");

            assertThat(store.deleteAttempts()).as("the service really asked the store to delete").isEqualTo(1);
            assertThat(deleted)
                .as("a surviving record must not be reported as an absent one (gate 1 MAJOR-03)")
                .isEqualTo(WarpService.DeleteResult.FAILED);
            assertThat(service.getWarp("shop")).as("the record the caller was told about").isNotNull();
        }

        @Test
        @DisplayName("a warp nobody created is still reported as not found, without touching the store")
        void anAbsentWarpIsReportedNotFound() throws Exception {
            SilentlyFailingStore<WarpData> store = warpStore();
            WarpService service = warpService(store);

            WarpService.DeleteResult deleted = service.deleteWarp("never-existed");

            assertThat(deleted).isEqualTo(WarpService.DeleteResult.NOT_FOUND);
            assertThat(store.deleteAttempts()).as("no delete attempted for a record that is absent").isZero();
        }
    }

    @Nested
    @DisplayName("/sethome on an existing home (gate 1 MAJOR-01)")
    class SetHomeUpdateTests {

        @Test
        @DisplayName("a move that really is stored is reported as updated, and the stored coordinates change")
        void aStoredMoveIsReportedUpdated() throws Exception {
            SilentlyFailingStore<HomeData> store = homeStore();
            HomeService service = homeService(store);
            PlayerMock player = server.addPlayer("HomeOwner");
            store.insert(home(player.getUniqueId(), "farm"));
            World world = server.addSimpleWorld("world");
            player.teleport(new Location(world, 40, 70, 50));

            SetHomeResult result = service.setHome(player, "farm");

            assertThat(store.updateAttempts()).as("the service really asked the store to update").isEqualTo(1);
            assertThat(result).isEqualTo(SetHomeResult.UPDATED);
            HomeData stored = service.getHome(player.getUniqueId(), "farm");
            assertThat(stored).isNotNull();
            assertThat(stored.getX()).isEqualTo(40);
            assertThat(stored.getY()).isEqualTo(70);
            assertThat(stored.getZ()).isEqualTo(50);
        }

        @Test
        @DisplayName("a facing-only change the store ignored is NOT reported as updated either")
        void anIgnoredFacingChangeIsNotReportedUpdated() throws Exception {
            // The same coordinates, a different direction. /home reads yaw and pitch back through
            // toLocation(), so an ignored write leaves the player facing the old way -- and a
            // comparison of world and XYZ alone reported that as UPDATED (gate 2 P2). The check now
            // compares the constructed location, so every field toLocation() reads is covered and the
            // next field added is covered without anyone remembering.
            SilentlyFailingStore<HomeData> store = homeStore();
            HomeService service = homeService(store);
            PlayerMock player = server.addPlayer("HomeOwner");
            World world = server.addSimpleWorld("world");
            store.insert(home(player.getUniqueId(), "farm"));
            Location sameSpotFacingElsewhere = new Location(world, 1, 2, 3, 90f, 45f);
            player.teleport(sameSpotFacingElsewhere);
            store.ignoreUpdates();

            SetHomeResult result = service.setHome(player, "farm");

            assertThat(store.updateAttempts()).as("the service really asked the store to update").isEqualTo(1);
            assertThat(result).isEqualTo(SetHomeResult.FAILED);
            HomeData stored = service.getHome(player.getUniqueId(), "farm");
            assertThat(stored).isNotNull();
            assertThat(stored.getYaw()).as("the direction /home would still face").isEqualTo(0f);
        }

        @Test
        @DisplayName("a move the store silently ignored is NOT reported as updated")
        void anIgnoredMoveIsNotReportedUpdated() throws Exception {
            SilentlyFailingStore<HomeData> store = homeStore();
            HomeService service = homeService(store);
            PlayerMock player = server.addPlayer("HomeOwner");
            store.insert(home(player.getUniqueId(), "farm"));
            World world = server.addSimpleWorld("world");
            player.teleport(new Location(world, 40, 70, 50));
            store.ignoreUpdates();

            SetHomeResult result = service.setHome(player, "farm");

            assertThat(store.updateAttempts()).as("the service really asked the store to update").isEqualTo(1);
            assertThat(result)
                .as("telling the player the home moved while the stored coordinates did not is #34's "
                    + "symptom on the trigger the CHANGELOG claims fixed (gate 1 MAJOR-01)")
                .isEqualTo(SetHomeResult.FAILED);
            HomeData stored = service.getHome(player.getUniqueId(), "farm");
            assertThat(stored).isNotNull();
            assertThat(stored.getX()).as("the coordinates the player would be sent to").isEqualTo(1);
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
