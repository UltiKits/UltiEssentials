package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.plugins.essentials.utils.SilentlyFailingStore;
import com.ultikits.ultitools.interfaces.impl.data.json.SimpleJsonDataOperator;
import org.bukkit.BanList;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IpBanServiceTest {
    @TempDir Path directory;
    private BanService service;
    private EssentialsConfig config;
    private SilentlyFailingStore<BanData> store;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        store = new SilentlyFailingStore<>(directory.toString(), BanData.class);
        service = new BanService();
        EssentialsTestHelper.setField(service, "config", config);
        EssentialsTestHelper.setField(service, "plugin", CatalogueText.plugin("en"));
        EssentialsTestHelper.setField(service, "banOperator", store);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    // Reflection lets the feature's absence fail as an assertion/execution, not a compiler error.
    private String ban(String ip, String reason) throws Exception {
        return BanService.class.getMethod("banIp", String.class, String.class, UUID.class, String.class)
                .invoke(service, ip, reason, null, "Console").toString();
    }

    private String unban(String ip) throws Exception {
        return BanService.class.getMethod("unbanIp", String.class).invoke(service, ip).toString();
    }

    private String normalize(String ip) throws Exception {
        return (String) BanService.class.getMethod("normalizeIpAddress", String.class).invoke(null, ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.0.2.1", "2001:db8::1", "2001:0DB8:0:0:0:0:0:1", "::1", "::ffff:192.0.2.1"})
    void storesAndLiftsCanonicalIpOnlyRecordsAcrossReopen(String ip) throws Exception {
        String canonical = InetAddress.getByName(ip).getHostAddress();
        assertThat(normalize(ip)).isEqualTo(canonical);
        assertThat(ban(ip, null)).isEqualTo("SUCCESS");
        BanData record = service.getActiveIpBan(canonical);
        assertThat(record).isNotNull();
        assertThat(record.getPlayerUuid()).isNull();
        assertThat(record.getPlayerName()).isEqualTo(canonical);
        assertThat(record.getId()).isNotBlank();
        assertThat(record.getReason()).isEqualTo("No reason");
        assertThat(record.isPermanent()).isTrue();
        assertThat(ban(canonical, "again")).isEqualTo("ALREADY_BANNED");
        assertThat(store.getAll()).hasSize(1);
        store.flush();
        SimpleJsonDataOperator<BanData> reopened = new SimpleJsonDataOperator<>(directory.toString(), BanData.class);
        EssentialsTestHelper.setField(service, "banOperator", reopened);
        assertThat(service.getActiveIpBan(canonical)).isNotNull();
        assertThat(unban(ip)).isEqualTo("REMOVED");
        reopened.flush();
        EssentialsTestHelper.setField(service, "banOperator",
                new SimpleJsonDataOperator<>(directory.toString(), BanData.class));
        assertThat(service.getActiveIpBan(canonical)).isNull();
        assertThat(unban(ip)).isEqualTo("NOT_BANNED");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"localhost", "example.com", "127.1", "1234", "192.0.2.999", "01.2.3.4",
            "192.0.2.1:25565", "192.0.2.0/24", "[::1]", "fe80::1%eth0", "2001:::1", " ::1", "abcd:host"})
    void rejectsNonliteralOrAmbiguousAddressesWithoutWriting(String ip) throws Exception {
        assertThat(normalize(ip)).isNull();
        assertThat(ban(ip, "bad")).isEqualTo("INVALID_ADDRESS");
        assertThat(store.getAll()).isEmpty();
    }

    @Test
    void disabledCreationDoesNotPreventLifting() throws Exception {
        assertThat(ban("192.0.2.1", "test")).isEqualTo("SUCCESS");
        config.setBanEnabled(false);
        assertThat(ban("192.0.2.2", "test")).isEqualTo("DISABLED");
        assertThat(unban("192.0.2.1")).isEqualTo("REMOVED");
    }

    @Test
    void noOpInsertDoesNotKickOrReportSuccess() throws Exception {
        EssentialsTestHelper.setField(service, "banOperator", spy(store));
        SimpleJsonDataOperator<BanData> noInsert = spy(store);
        doNothing().when(noInsert).insert(any(BanData.class));
        EssentialsTestHelper.setField(service, "banOperator", noInsert);
        assertThat(ban("192.0.2.1", "test")).isEqualTo("FAILED");
    }

    @Test
    void kicksEveryMatchingConnectionButLeavesOtherAddressesAndNullSockets() throws Exception {
        Player first = mock(Player.class), second = mock(Player.class), other = mock(Player.class), missing = mock(Player.class);
        when(first.getAddress()).thenReturn(new InetSocketAddress("192.0.2.1", 1000));
        when(second.getAddress()).thenReturn(new InetSocketAddress("192.0.2.1", 2000));
        when(other.getAddress()).thenReturn(new InetSocketAddress("192.0.2.2", 1000));
        doReturn(Arrays.asList(first, second, other, missing)).when(EssentialsTestHelper.getMockServer()).getOnlinePlayers();
        assertThat(ban("192.0.2.1", "griefing")).isEqualTo("SUCCESS");
        verify(first).kickPlayer(contains("griefing"));
        verify(second).kickPlayer(contains("griefing"));
        verify(other, never()).kickPlayer(anyString());
        verify(missing, never()).kickPlayer(anyString());
    }

    @Test
    void survivingRecordsAreFailureAndAllMatchingRecordsAreAttempted() throws Exception {
        assertThat(ban("192.0.2.1", "test")).isEqualTo("SUCCESS");
        BanData combined = BanData.builder().uuid(UUID.randomUUID()).playerUuid(UUID.randomUUID().toString())
                .playerName("Legacy").ipAddress("192.0.2.1").active(true).expireTime(-1).build();
        store.insert(combined);
        store.ignoreUpdates();
        assertThat(unban("192.0.2.1")).isEqualTo("FAILED");
        assertThat(store.updateAttempts()).isEqualTo(2);
        assertThat(service.getActiveIpBan("192.0.2.1")).isNotNull();
    }

    @Test
    void removesAllMatchingRecordsWithoutTouchingOtherIpsOrUuidBans() throws Exception {
        ban("192.0.2.1", "test");
        store.insert(BanData.builder().uuid(UUID.randomUUID()).playerName("Legacy")
                .ipAddress("192.0.2.1").active(true).expireTime(-1).build());
        ban("192.0.2.2", "other");
        UUID player = UUID.randomUUID();
        service.banPlayer(player, "Player", "UUID ban", null, "Console");
        assertThat(unban("192.0.2.1")).isEqualTo("REMOVED");
        assertThat(service.getActiveIpBan("192.0.2.1")).isNull();
        assertThat(service.getActiveIpBan("192.0.2.2")).isNotNull();
        assertThat(service.getActiveBan(player)).isNotNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void checksServerIpListWithoutChangingIt() throws Exception {
        BanList<?> list = mock(BanList.class);
        doReturn(list).when(EssentialsTestHelper.getMockServer()).getBanList(BanList.Type.IP);
        when(list.isBanned("192.0.2.1")).thenReturn(true);
        assertThat(BanService.class.getMethod("isIpBannedInServerBanList", String.class)
                .invoke(service, "192.0.2.1")).isEqualTo(true);
        verify(list).isBanned("192.0.2.1");
        verifyNoMoreInteractions(list);
    }
}
