package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests the two directions of BanService's own "an active, non-expired ban" domain rule
 * ({@code b.isActive() && !b.hasExpired()}) that {@code BanServiceMockitoTest} does not reach for
 * every call site: {@code unbanPlayerByName}, {@code unbanIp}, and {@code getActiveIpBan} are
 * never tested against an active-but-expired ban record there, and {@code getActiveBan} is never
 * tested against an inactive one. Each of the five call sites shares the identical lambda body,
 * but JaCoCo's branch counter is per call site, not per shared predicate -- this class closes
 * exactly the outcomes the existing suite leaves untested at each site.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("BanService active-ban filter coverage Tests")
class BanServiceFilterCoverageTest {

    private BanService banService;

    @SuppressWarnings("unchecked")
    private final DataOperator<BanData> banOperator = mock(DataOperator.class);

    @SuppressWarnings("unchecked")
    private final Query<BanData> query = mock(Query.class);

    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        EssentialsConfig config = new EssentialsConfig();
        banService = new BanService();
        EssentialsTestHelper.setField(banService, "config", config);
        EssentialsTestHelper.setField(banService, "banOperator", banOperator);

        playerUuid = UUID.randomUUID();

        reset(banOperator, query);
        when(banOperator.query()).thenReturn(query);
        when(query.where(anyString())).thenReturn(query);
        when(query.eq(any())).thenReturn(query);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private BanData expiredButStillMarkedActive(String ip) {
        return BanData.builder()
                .uuid(UUID.randomUUID())
                .playerUuid(playerUuid.toString())
                .playerName("TestPlayer")
                .reason("stale")
                .bannedByName("Admin")
                .banTime(System.currentTimeMillis() - 200000)
                .expireTime(System.currentTimeMillis() - 100000)
                .active(true)
                .ipAddress(ip)
                .build();
    }

    private BanData inactive() {
        return BanData.builder()
                .uuid(UUID.randomUUID())
                .playerUuid(playerUuid.toString())
                .playerName("TestPlayer")
                .reason("was banned")
                .bannedByName("Admin")
                .banTime(System.currentTimeMillis())
                .expireTime(-1)
                .active(false)
                .build();
    }

    @Test
    @DisplayName("unbanPlayer does not unban a record that is active but already expired")
    void unbanPlayerIgnoresExpiredRecord() throws Exception {
        when(query.list()).thenReturn(
                new java.util.ArrayList<>(Collections.singletonList(expiredButStillMarkedActive(null))));

        boolean result = banService.unbanPlayer(playerUuid);

        assertThat(result).isFalse();
        verify(banOperator, never()).update(any(BanData.class));
    }

    @Test
    @DisplayName("getActiveBan does not treat an inactive ban record as an active ban")
    void getActiveBanIgnoresInactiveRecord() {
        when(query.list()).thenReturn(Collections.singletonList(inactive()));

        BanData result = banService.getActiveBan(playerUuid);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("unbanPlayerByName does not unban a record that is active but already expired")
    void unbanPlayerByNameIgnoresExpiredRecord() throws Exception {
        when(query.list()).thenReturn(
                new java.util.ArrayList<>(Collections.singletonList(expiredButStillMarkedActive(null))));

        boolean result = banService.unbanPlayerByName("TestPlayer");

        assertThat(result).isFalse();
        verify(banOperator, never()).update(any(BanData.class));
    }

    @Test
    @DisplayName("unbanIp does not unban a record that is active but already expired")
    void unbanIpIgnoresExpiredRecord() throws Exception {
        when(query.list()).thenReturn(
                new java.util.ArrayList<>(Collections.singletonList(expiredButStillMarkedActive("10.0.0.5"))));

        boolean result = banService.unbanIp("10.0.0.5");

        assertThat(result).isFalse();
        verify(banOperator, never()).update(any(BanData.class));
    }

    @Test
    @DisplayName("getActiveIpBan does not return a record that is active but already expired")
    void getActiveIpBanIgnoresExpiredRecord() {
        when(query.list()).thenReturn(Collections.singletonList(expiredButStillMarkedActive("10.0.0.6")));

        BanData result = banService.getActiveIpBan("10.0.0.6");

        assertThat(result).isNull();
    }
}
