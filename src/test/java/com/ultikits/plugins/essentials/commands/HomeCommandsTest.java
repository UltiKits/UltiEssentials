package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.HomeData;
import com.ultikits.plugins.essentials.enums.TeleportResult;
import com.ultikits.plugins.essentials.service.HomeService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Home Commands Tests")
class HomeCommandsTest {

    private EssentialsConfig config;
    private HomeService homeService;
    private Player player;
    private UUID playerUuid;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        homeService = mock(HomeService.class);
        playerUuid = UUID.randomUUID();
        player = EssentialsTestHelper.createMockPlayer("TestPlayer", playerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("HomeCommand")
    class HomeCommandTests {

        private HomeCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new HomeCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "homeService", homeService);
            EssentialsTestHelper.setField(command, "config", config);
        }

        @Test
        @DisplayName("Should teleport to default home")
        void shouldTeleportToDefaultHome() {
            when(homeService.teleportToHome(player, "home")).thenReturn(TeleportResult.SUCCESS);

            command.teleportToDefaultHome(player);

            verify(homeService).teleportToHome(player, "home");
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should try first home when default not found")
        void shouldTryFirstHomeWhenDefaultNotFound() {
            when(homeService.teleportToHome(eq(player), eq("home")))
                    .thenReturn(TeleportResult.NOT_FOUND);

            HomeData firstHome = HomeData.builder()
                    .uuid(UUID.randomUUID())
                    .playerUuid(playerUuid.toString())
                    .name("myhome")
                    .world("world")
                    .x(100).y(64).z(200)
                    .build();
            when(homeService.getHomes(playerUuid)).thenReturn(Collections.singletonList(firstHome));
            when(homeService.teleportToHome(player, "myhome")).thenReturn(TeleportResult.SUCCESS);

            command.teleportToDefaultHome(player);

            verify(homeService).teleportToHome(player, "myhome");
        }

        @Test
        @DisplayName("Should send message when no homes exist")
        void shouldSendMessageWhenNoHomes() {
            when(homeService.teleportToHome(eq(player), eq("home")))
                    .thenReturn(TeleportResult.NOT_FOUND);
            when(homeService.getHomes(playerUuid)).thenReturn(Collections.emptyList());

            command.teleportToDefaultHome(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setHomeEnabled(false);

            command.teleportToDefaultHome(player);

            verify(homeService, never()).teleportToHome(any(), anyString());
        }

        @Test
        @DisplayName("Should teleport to named home")
        void shouldTeleportToNamedHome() {
            when(homeService.teleportToHome(player, "base")).thenReturn(TeleportResult.SUCCESS);

            command.teleportToHome(player, "base");

            verify(homeService).teleportToHome(player, "base");
        }

        @Test
        @DisplayName("Should handle warmup started result")
        void shouldHandleWarmupStarted() {
            when(homeService.teleportToHome(player, "home")).thenReturn(TeleportResult.WARMUP_STARTED);

            command.teleportToDefaultHome(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle world not found result")
        void shouldHandleWorldNotFound() {
            when(homeService.teleportToHome(player, "home")).thenReturn(TeleportResult.WORLD_NOT_FOUND);

            command.teleportToDefaultHome(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle already teleporting result")
        void shouldHandleAlreadyTeleporting() {
            when(homeService.teleportToHome(player, "home")).thenReturn(TeleportResult.ALREADY_TELEPORTING);

            command.teleportToDefaultHome(player);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled result")
        void shouldHandleDisabledResult() {
            when(homeService.teleportToHome(player, "home")).thenReturn(TeleportResult.DISABLED);

            command.teleportToDefaultHome(player);

            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("SetHomeCommand")
    class SetHomeCommandTests {

        private SetHomeCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new SetHomeCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "homeService", homeService);
            EssentialsTestHelper.setField(command, "config", config);
        }

        @Test
        @DisplayName("Should set default home")
        void shouldSetDefaultHome() {
            when(homeService.setHome(player, "home")).thenReturn(HomeService.SetHomeResult.CREATED);
            when(homeService.getHomeCount(playerUuid)).thenReturn(1);
            when(homeService.getMaxHomes(player)).thenReturn(3);

            command.setDefaultHome(player);

            verify(homeService).setHome(player, "home");
        }

        @Test
        @DisplayName("Should set named home - created")
        void shouldSetNamedHomeCreated() {
            when(homeService.setHome(player, "base")).thenReturn(HomeService.SetHomeResult.CREATED);
            when(homeService.getHomeCount(playerUuid)).thenReturn(1);
            when(homeService.getMaxHomes(player)).thenReturn(3);

            command.setHome(player, "base");

            verify(player, times(2)).sendMessage(anyString()); // created + count
        }

        @Test
        @DisplayName("Should set named home - updated")
        void shouldSetNamedHomeUpdated() {
            when(homeService.setHome(player, "base")).thenReturn(HomeService.SetHomeResult.UPDATED);

            command.setHome(player, "base");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle limit reached")
        void shouldHandleLimitReached() {
            when(homeService.setHome(player, "base")).thenReturn(HomeService.SetHomeResult.LIMIT_REACHED);

            command.setHome(player, "base");

            verify(player, times(2)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle invalid name")
        void shouldHandleInvalidName() {
            when(homeService.setHome(player, "")).thenReturn(HomeService.SetHomeResult.INVALID_NAME);

            command.setHome(player, "");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabled() {
            config.setHomeEnabled(false);

            command.setHome(player, "base");

            verify(homeService, never()).setHome(any(), anyString());
        }
    }

    @Nested
    @DisplayName("DelHomeCommand")
    class DelHomeCommandTests {

        private DelHomeCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new DelHomeCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "homeService", homeService);
            EssentialsTestHelper.setField(command, "config", config);
        }

        @Test
        @DisplayName("Should delete home successfully")
        void shouldDeleteHome() {
            when(homeService.deleteHome(playerUuid, "base")).thenReturn(true);

            command.deleteHome(player, "base");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send message when home not found")
        void shouldSendMessageWhenNotFound() {
            when(homeService.deleteHome(playerUuid, "missing")).thenReturn(false);

            command.deleteHome(player, "missing");

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setHomeEnabled(false);

            command.deleteHome(player, "base");

            verify(homeService, never()).deleteHome(any(), anyString());
        }
    }

    @Nested
    @DisplayName("HomesCommand")
    class HomesCommandTests {

        private HomesCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new HomesCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "homeService", homeService);
            EssentialsTestHelper.setField(command, "config", config);
        }

        @Test
        @DisplayName("Should list homes")
        void shouldListHomes() {
            HomeData home = HomeData.builder()
                    .uuid(UUID.randomUUID())
                    .playerUuid(playerUuid.toString())
                    .name("base")
                    .world("world")
                    .x(100).y(64).z(200)
                    .build();
            when(homeService.getHomes(playerUuid)).thenReturn(Collections.singletonList(home));
            when(homeService.getMaxHomes(player)).thenReturn(3);

            command.listHomes(player);

            verify(player, atLeast(2)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should show message when no homes")
        void shouldShowMessageWhenNoHomes() {
            when(homeService.getHomes(playerUuid)).thenReturn(Collections.emptyList());
            when(homeService.getMaxHomes(player)).thenReturn(3);

            command.listHomes(player);

            verify(player, atLeast(2)).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setHomeEnabled(false);

            command.listHomes(player);

            verify(homeService, never()).getHomes(any());
        }

        @Test
        @DisplayName("Should list multiple homes with coordinates")
        void shouldListMultipleHomes() {
            List<HomeData> homes = Arrays.asList(
                HomeData.builder().uuid(UUID.randomUUID()).playerUuid(playerUuid.toString())
                    .name("home1").world("world").x(100).y(64).z(200).build(),
                HomeData.builder().uuid(UUID.randomUUID()).playerUuid(playerUuid.toString())
                    .name("home2").world("nether").x(-50).y(32).z(100).build()
            );
            when(homeService.getHomes(playerUuid)).thenReturn(homes);
            when(homeService.getMaxHomes(player)).thenReturn(5);

            command.listHomes(player);

            // header + 2 homes + footer
            verify(player, atLeast(3)).sendMessage(anyString());
        }
    }
}
