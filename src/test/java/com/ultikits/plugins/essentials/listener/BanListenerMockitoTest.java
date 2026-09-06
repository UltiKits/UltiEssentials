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
        // No EssentialsTestHelper.setUp() call here, deliberately. It installs a raw,
        // unstubbed Mockito mock(Server.class) into Bukkit.server via reflection and never
        // clears it -- and this class consumed nothing it produced. Every helper member was
        // checked: only setField (a static reflection helper that needs no fixture) and
        // tearDown are referenced; getMockPlugin, getMockLogger, getMockServer,
        // createDefaultConfig and createMockPlayer are all absent, and both
        // new EssentialsConfig() and mock(BanService.class) below need nothing from it.
        // Its one observable effect was the foreign server that the next line then had to
        // clear again, so the call was removed at the root rather than worked around.
        //
        // A genuinely live server is still required: AsyncPlayerPreLoginEvent's 3-arg
        // constructor delegates to Bukkit.createProfile(uuid, name), and an unstubbed
        // Mockito Server returns null there (Mockito's default for an Object-returning
        // method), with the NPE surfacing one call later inside production code at
        // BanListener.onPlayerLogin -> getUniqueId(). Routed through the module's shared
        // MockBukkitHelper.bootstrapLiveServer() rather than an inlined MockBukkit.mock()
        // so this class's greenness -- a real production-path test, not a sentinel -- is
        // exactly what UltiEssentialsRegistrySentinelTest stands guard over (14-13).
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
