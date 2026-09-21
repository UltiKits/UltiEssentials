package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.service.BanService.UnbanResult;
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
 * {@code /unban} may report — and broadcast — an unban only once this module's own ban record is
 * confirmed inactive (UltiKits/UltiEssentials#35).
 * <p>
 * All three unban paths set {@code active = false} on each matched record and then called
 * {@code update(T)}, which addresses its row with {@code WHERE id = ?} and returns {@code void}.
 * With the primary key never written, that matched nothing and the {@code active} column stayed
 * {@code 1} — so the command reported success, broadcast it to the whole server, and the target was
 * still rejected at login by this module's own ban message.
 * <p>
 * A false unban is worse than a failed one: the operator is told the player may rejoin and stops
 * looking, and everyone online is told the same. That is why the refusal is asserted against a
 * real framework operator whose update is a silent no-op ({@link SilentlyFailingStore}) rather
 * than against a mock's recorded calls: what matters is the state of the store afterwards, not that
 * a method was invoked.
 * <p>
 * All three paths are covered even though only {@link BanService#unbanPlayerByName(String)} is
 * reachable from a command today: {@link BanService#unbanPlayer(java.util.UUID)} and
 * {@link BanService#unbanIp(String)} have no production caller in this repository (measured;
 * {@code UnbanCommand}'s javadoc names a {@code /unbanip} command that does not exist), and leaving
 * two copies of the same defect behind for whoever wires them up is how this class of bug returns.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("Verified unban (UltiKits/UltiEssentials#35)")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class BanUnbanVerificationTest {

    @TempDir
    Path tempDir;

    private SilentlyFailingStore<BanData> store;
    private BanService banService;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkitHelper.clearForeignServer();
        MockBukkit.mock();
        TestHelper.mockUltiToolsInstance();

        EssentialsConfig config = mock(EssentialsConfig.class);
        lenient().when(config.isBanEnabled()).thenReturn(true);

        store = new SilentlyFailingStore<>(tempDir.toFile().getAbsolutePath(), BanData.class);
        banService = new BanService();
        setField(banService, "config", config);
        setField(banService, "banOperator", store);
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    @Nested
    @DisplayName("By name — the path /unban uses")
    class ByNameTests {

        @Test
        @DisplayName("an unban that really deactivates the record is reported as done")
        void aDeactivatedBanIsReportedUnbanned() {
            UUID target = UUID.randomUUID();
            store.insert(activeBan(target, "BannedPlayer", null));

            UnbanResult unbanned = banService.unbanPlayerByName("BannedPlayer");

            assertThat(store.updateAttempts()).as("the service really asked the store to update").isEqualTo(1);
            assertThat(unbanned).isEqualTo(UnbanResult.REMOVED);
            assertThat(banService.getActiveBan(target)).as("active ban re-read from the store").isNull();
        }

        @Test
        @DisplayName("an unban the store silently ignored is NOT reported as done")
        void aSurvivingBanIsNotReportedUnbanned() {
            UUID target = UUID.randomUUID();
            store.insert(activeBan(target, "BannedPlayer", null));
            store.ignoreUpdates();

            UnbanResult unbanned = banService.unbanPlayerByName("BannedPlayer");

            assertThat(store.updateAttempts()).as("the service really asked the store to update").isEqualTo(1);
            assertThat(unbanned)
                .as("a ban that is still active must not be reported as no ban at all -- the operator "
                    + "stops looking while /banlist still lists them (gate 1 MAJOR-03)")
                .isEqualTo(UnbanResult.FAILED);
            assertThat(banService.getActiveBan(target)).as("the ban the caller was told about").isNotNull();
        }

        @Test
        @DisplayName("a name with no active ban is still reported as not banned, without an update")
        void anUnbannedNameIsReportedNotBanned() {
            UnbanResult unbanned = banService.unbanPlayerByName("NeverBanned");

            assertThat(unbanned).isEqualTo(UnbanResult.NOT_BANNED);
            assertThat(store.updateAttempts()).as("no update attempted for a name with no active ban").isZero();
        }

        @Test
        @DisplayName("two active bans on one name are not reported unbanned while either survives")
        void aPartiallyDeactivatedNameIsNotReportedUnbanned() {
            UUID target = UUID.randomUUID();
            BanData first = activeBan(target, "BannedPlayer", null);
            BanData second = activeBan(target, "BannedPlayer", null);
            store.insert(first);
            store.insert(second);
            store.ignoreUpdates();

            UnbanResult unbanned = banService.unbanPlayerByName("BannedPlayer");

            assertThat(store.updateAttempts()).as("both records were offered to the store").isEqualTo(2);
            assertThat(unbanned).isEqualTo(UnbanResult.FAILED);
            assertThat(banService.getActiveBan(target)).isNotNull();
        }
    }

    @Nested
    @DisplayName("By UUID")
    class ByUuidTests {

        @Test
        @DisplayName("an unban that really deactivates the record is reported as done")
        void aDeactivatedBanIsReportedUnbanned() {
            UUID target = UUID.randomUUID();
            store.insert(activeBan(target, "BannedPlayer", null));

            UnbanResult unbanned = banService.unbanPlayer(target);

            assertThat(store.updateAttempts()).isEqualTo(1);
            assertThat(unbanned).isEqualTo(UnbanResult.REMOVED);
            assertThat(banService.getActiveBan(target)).isNull();
        }

        @Test
        @DisplayName("an unban the store silently ignored is NOT reported as done")
        void aSurvivingBanIsNotReportedUnbanned() {
            UUID target = UUID.randomUUID();
            store.insert(activeBan(target, "BannedPlayer", null));
            store.ignoreUpdates();

            UnbanResult unbanned = banService.unbanPlayer(target);

            assertThat(store.updateAttempts()).isEqualTo(1);
            assertThat(unbanned).isEqualTo(UnbanResult.FAILED);
            assertThat(banService.getActiveBan(target)).isNotNull();
        }
    }

    @Nested
    @DisplayName("By IP")
    class ByIpTests {

        @Test
        @DisplayName("an unban that really deactivates the record is reported as done")
        void aDeactivatedIpBanIsReportedUnbanned() {
            store.insert(activeBan(UUID.randomUUID(), "BannedPlayer", "203.0.113.7"));

            UnbanResult unbanned = banService.unbanIp("203.0.113.7");

            assertThat(store.updateAttempts()).isEqualTo(1);
            assertThat(unbanned).isEqualTo(UnbanResult.REMOVED);
            assertThat(banService.getActiveIpBan("203.0.113.7")).isNull();
        }

        @Test
        @DisplayName("an unban the store silently ignored is NOT reported as done")
        void aSurvivingIpBanIsNotReportedUnbanned() {
            store.insert(activeBan(UUID.randomUUID(), "BannedPlayer", "203.0.113.7"));
            store.ignoreUpdates();

            UnbanResult unbanned = banService.unbanIp("203.0.113.7");

            assertThat(store.updateAttempts()).isEqualTo(1);
            assertThat(unbanned).isEqualTo(UnbanResult.FAILED);
            assertThat(banService.getActiveIpBan("203.0.113.7")).isNotNull();
        }

        @Test
        @DisplayName("an IP with no active ban is still reported as not banned, without an update")
        void anUnbannedIpIsReportedNotBanned() {
            UnbanResult unbanned = banService.unbanIp("203.0.113.8");

            assertThat(unbanned).isEqualTo(UnbanResult.NOT_BANNED);
            assertThat(store.updateAttempts()).isZero();
        }
    }

    // === fixtures ===

    private static BanData activeBan(UUID target, String name, String ipAddress) {
        return BanData.builder()
            .uuid(UUID.randomUUID())
            .playerUuid(target.toString())
            .playerName(name)
            .reason("test ban")
            .bannedByName("console")
            .banTime(System.currentTimeMillis())
            .expireTime(-1)
            .active(true)
            .ipAddress(ipAddress)
            .build();
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = BanService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
