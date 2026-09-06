package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.*;

import java.net.InetAddress;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Mockito-based unit tests for BanListener.
 * <p>
 * 纯 Mockito 测试封禁登录监听器。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("BanListener Tests (Mockito)")
class BanListenerMockitoTest {

    private BanListener banListener;
    private BanService banService;
    private EssentialsConfig config;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        // EssentialsTestHelper.setUp() above just installed its own raw, unstubbed
        // Mockito Server mock into Bukkit.server via reflection. That mock never
        // stubs Server.createProfile(...), so AsyncPlayerPreLoginEvent's 3-arg
        // constructor -- which delegates to Bukkit.createProfile(uuid, name) --
        // silently gets back null (Mockito's default return for an unstubbed
        // Object-returning method), and the NPE only surfaces one call later
        // inside production code (BanListener.onPlayerLogin -> getUniqueId()).
        // Give this class its own explicit, live-server bootstrap for that one
        // call, via the module's shared MockBukkitHelper.bootstrapLiveServer():
        // clears the raw mock (bypassing Bukkit.setServer()'s "already set"
        // guard the same way EssentialsTestHelper itself does) and installs a
        // real MockBukkit ServerMock instead, so createProfile() genuinely
        // resolves rather than depending on either Mockito's default or on
        // another class's leftover state (14-09, shape 3). Routed through the
        // shared helper method (not an inlined MockBukkit.mock() call) so this
        // class's greenness -- a real production-path test of
        // BanListener.onPlayerLogin -- is exactly what UltiEssentialsRegistrySentinelTest
        // is now also standing guard over (14-13).
        MockBukkitHelper.bootstrapLiveServer();

        config = new EssentialsConfig();
        banService = mock(BanService.class);

        banListener = new BanListener();
        EssentialsTestHelper.setField(banListener, "banService", banService);
        EssentialsTestHelper.setField(banListener, "config", config);
    }

    @AfterEach
    void tearDown() throws Exception {
        MockBukkitHelper.safeUnmock();
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("onPlayerLogin")
    class OnPlayerLoginTests {

        @Test
        @DisplayName("Should allow login when ban feature is disabled")
        void shouldAllowLoginWhenBanDisabled() throws Exception {
            config.setBanEnabled(false);

            UUID playerUuid = UUID.randomUUID();
            InetAddress address = InetAddress.getByName("127.0.0.1");
            AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                "TestPlayer", address, playerUuid
            );

            banListener.onPlayerLogin(event);

            assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
            verify(banService, never()).getActiveBan(any());
            verify(banService, never()).getActiveIpBan(anyString());
        }

        @Test
        @DisplayName("Should block login when player is UUID-banned")
        void shouldBlockLoginWhenUuidBanned() throws Exception {
            UUID playerUuid = UUID.randomUUID();
            InetAddress address = InetAddress.getByName("127.0.0.1");

            BanData ban = BanData.builder()
                .uuid(UUID.randomUUID())
                .playerUuid(playerUuid.toString())
                .playerName("TestPlayer")
                .reason("Cheating")
                .bannedByName("Admin")
                .banTime(System.currentTimeMillis())
                .expireTime(-1)
                .active(true)
                .build();

            when(banService.getActiveBan(playerUuid)).thenReturn(ban);
            when(banService.formatKickMessage(ban)).thenReturn("You are banned");

            AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                "TestPlayer", address, playerUuid
            );

            banListener.onPlayerLogin(event);

            assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
            assertThat(event.getKickMessage()).isEqualTo("You are banned");
        }

        @Test
        @DisplayName("Should block login when player is IP-banned")
        void shouldBlockLoginWhenIpBanned() throws Exception {
            UUID playerUuid = UUID.randomUUID();
            InetAddress address = InetAddress.getByName("192.168.1.1");

            // No UUID ban
            when(banService.getActiveBan(playerUuid)).thenReturn(null);

            BanData ipBan = BanData.builder()
                .uuid(UUID.randomUUID())
                .playerUuid(UUID.randomUUID().toString())
                .playerName("OtherPlayer")
                .reason("IP Ban")
                .bannedByName("Admin")
                .banTime(System.currentTimeMillis())
                .expireTime(-1)
                .active(true)
                .ipAddress("192.168.1.1")
                .build();

            when(banService.getActiveIpBan("192.168.1.1")).thenReturn(ipBan);
            when(banService.formatKickMessage(ipBan)).thenReturn("IP banned");

            AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                "TestPlayer", address, playerUuid
            );

            banListener.onPlayerLogin(event);

            assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
            assertThat(event.getKickMessage()).isEqualTo("IP banned");
        }

        @Test
        @DisplayName("Should allow login when not banned")
        void shouldAllowLoginWhenNotBanned() throws Exception {
            UUID playerUuid = UUID.randomUUID();
            InetAddress address = InetAddress.getByName("10.0.0.1");

            when(banService.getActiveBan(playerUuid)).thenReturn(null);
            when(banService.getActiveIpBan("10.0.0.1")).thenReturn(null);

            AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                "TestPlayer", address, playerUuid
            );

            banListener.onPlayerLogin(event);

            assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        }

        @Test
        @DisplayName("Should check UUID ban before IP ban")
        void shouldCheckUuidBanBeforeIpBan() throws Exception {
            UUID playerUuid = UUID.randomUUID();
            InetAddress address = InetAddress.getByName("10.0.0.1");

            BanData uuidBan = BanData.builder()
                .uuid(UUID.randomUUID())
                .playerUuid(playerUuid.toString())
                .playerName("TestPlayer")
                .reason("UUID Ban")
                .bannedByName("Admin")
                .banTime(System.currentTimeMillis())
                .expireTime(-1)
                .active(true)
                .build();

            when(banService.getActiveBan(playerUuid)).thenReturn(uuidBan);
            when(banService.formatKickMessage(uuidBan)).thenReturn("UUID banned");

            AsyncPlayerPreLoginEvent event = new AsyncPlayerPreLoginEvent(
                "TestPlayer", address, playerUuid
            );

            banListener.onPlayerLogin(event);

            // Should not check IP ban since UUID ban was found
            verify(banService, never()).getActiveIpBan(anyString());
        }
    }
}
