package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.HomeData;
import com.ultikits.plugins.essentials.enums.TeleportResult;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.Query;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mockito-based unit tests for HomeService.
 * <p>
 * 家服务测试。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("HomeService Tests (Mockito)")
class HomeServiceMockitoTest {

    private HomeService homeService;
    private EssentialsConfig config;
    private TeleportService teleportService;

    @SuppressWarnings("unchecked")
    private DataOperator<HomeData> homeOperator = mock(DataOperator.class);

    @SuppressWarnings("unchecked")
    private Query<HomeData> query = mock(Query.class);

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        config = new EssentialsConfig();
        teleportService = mock(TeleportService.class);

        homeService = new HomeService();
        EssentialsTestHelper.setField(homeService, "config", config);
        EssentialsTestHelper.setField(homeService, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(homeService, "homeOperator", homeOperator);
        EssentialsTestHelper.setField(homeService, "teleportService", teleportService);

        reset(homeOperator, query);
        when(homeOperator.query()).thenReturn(query);
        when(query.where(anyString())).thenReturn(query);
        when(query.eq(any())).thenReturn(query);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("getHomes")
    class GetHomesTests {

        @Test
        @DisplayName("Should return homes for player")
        void shouldReturnHomes() {
            UUID playerUuid = UUID.randomUUID();
            List<HomeData> homes = Arrays.asList(
                    HomeData.builder().uuid(UUID.randomUUID()).name("home1").build(),
                    HomeData.builder().uuid(UUID.randomUUID()).name("home2").build()
            );
            when(query.list()).thenReturn(homes);

            List<HomeData> result = homeService.getHomes(playerUuid);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("Should return empty list when no homes")
        void shouldReturnEmptyList() {
            when(query.list()).thenReturn(new ArrayList<>());

            List<HomeData> result = homeService.getHomes(UUID.randomUUID());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getHome")
    class GetHomeTests {

        @Test
        @DisplayName("Should return home by name")
        void shouldReturnHomeByName() {
            HomeData home = HomeData.builder().uuid(UUID.randomUUID()).name("home").build();
            when(query.first()).thenReturn(home);

            HomeData result = homeService.getHome(UUID.randomUUID(), "home");

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("home");
        }

        @Test
        @DisplayName("Should return null when home not found")
        void shouldReturnNull() {
            when(query.first()).thenReturn(null);

            HomeData result = homeService.getHome(UUID.randomUUID(), "nonexistent");

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getHomeCount")
    class GetHomeCountTests {

        @Test
        @DisplayName("Should return count of homes")
        void shouldReturnCount() {
            when(query.list()).thenReturn(Arrays.asList(
                    HomeData.builder().build(),
                    HomeData.builder().build()
            ));

            int count = homeService.getHomeCount(UUID.randomUUID());

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getMaxHomes")
    class GetMaxHomesTests {

        @Test
        @DisplayName("Should return default max homes")
        void shouldReturnDefault() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            int max = homeService.getMaxHomes(player);

            assertThat(max).isEqualTo(config.getHomeDefaultMaxHomes());
        }

        @Test
        @DisplayName("Should return permission-based max homes")
        void shouldReturnPermissionBased() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(player.hasPermission("ultiessentials.home.max.10")).thenReturn(true);

            int max = homeService.getMaxHomes(player);

            assertThat(max).isEqualTo(10);
        }

        @Test
        @DisplayName("Should return unlimited for unlimited permission")
        void shouldReturnUnlimited() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(player.hasPermission("ultiessentials.home.unlimited")).thenReturn(true);

            int max = homeService.getMaxHomes(player);

            assertThat(max).isEqualTo(Integer.MAX_VALUE);
        }
    }

    @Nested
    @DisplayName("setHome")
    class SetHomeTests {

        @Test
        @DisplayName("Should create new home")
        void shouldCreateNewHome() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(query.first()).thenReturn(null);
            when(query.list()).thenReturn(new ArrayList<>());

            HomeService.SetHomeResult result = homeService.setHome(player, "home");

            assertThat(result).isEqualTo(HomeService.SetHomeResult.CREATED);
            verify(homeOperator).insert(any(HomeData.class));
        }

        @Test
        @DisplayName("Should update existing home")
        void shouldUpdateExistingHome() throws Exception {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            HomeData existing = HomeData.builder()
                    .uuid(UUID.randomUUID())
                    .name("home")
                    .world("world").x(0).y(0).z(0)
                    .build();
            when(query.first()).thenReturn(existing);

            HomeService.SetHomeResult result = homeService.setHome(player, "home");

            assertThat(result).isEqualTo(HomeService.SetHomeResult.UPDATED);
            verify(homeOperator).update(any(HomeData.class));
        }

        @Test
        @DisplayName("Should return DISABLED when home is disabled")
        void shouldReturnDisabled() {
            config.setHomeEnabled(false);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            HomeService.SetHomeResult result = homeService.setHome(player, "home");

            assertThat(result).isEqualTo(HomeService.SetHomeResult.DISABLED);
        }

        @Test
        @DisplayName("Should return INVALID_NAME for empty name")
        void shouldReturnInvalidNameEmpty() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            HomeService.SetHomeResult result = homeService.setHome(player, "   ");

            assertThat(result).isEqualTo(HomeService.SetHomeResult.INVALID_NAME);
        }

        @Test
        @DisplayName("Should return INVALID_NAME for name longer than 32")
        void shouldReturnInvalidNameTooLong() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            String longName = "a".repeat(33);

            HomeService.SetHomeResult result = homeService.setHome(player, longName);

            assertThat(result).isEqualTo(HomeService.SetHomeResult.INVALID_NAME);
        }

        @Test
        @DisplayName("Should return LIMIT_REACHED when at max homes")
        void shouldReturnLimitReached() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(query.first()).thenReturn(null); // No existing home with this name

            // Return max homes count
            List<HomeData> existingHomes = new ArrayList<>();
            for (int i = 0; i < config.getHomeDefaultMaxHomes(); i++) {
                existingHomes.add(HomeData.builder().build());
            }
            when(query.list()).thenReturn(existingHomes);

            HomeService.SetHomeResult result = homeService.setHome(player, "newhome");

            assertThat(result).isEqualTo(HomeService.SetHomeResult.LIMIT_REACHED);
        }
    }

    @Nested
    @DisplayName("deleteHome")
    class DeleteHomeTests {

        @Test
        @DisplayName("Should delete home successfully")
        void shouldDeleteHome() {
            UUID homeId = UUID.randomUUID();
            HomeData home = HomeData.builder().uuid(homeId).name("home").build();
            when(query.first()).thenReturn(home);

            boolean result = homeService.deleteHome(UUID.randomUUID(), "home");

            assertThat(result).isTrue();
            verify(homeOperator).delById(homeId);
        }

        @Test
        @DisplayName("Should return false when home not found")
        void shouldReturnFalseWhenNotFound() {
            when(query.first()).thenReturn(null);

            boolean result = homeService.deleteHome(UUID.randomUUID(), "nonexistent");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("teleportToHome")
    class TeleportToHomeTests {

        @Test
        @DisplayName("Should return DISABLED when disabled")
        void shouldReturnDisabled() {
            config.setHomeEnabled(false);
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());

            TeleportResult result = homeService.teleportToHome(player, "home");

            assertThat(result).isEqualTo(TeleportResult.DISABLED);
        }

        @Test
        @DisplayName("Should return ALREADY_TELEPORTING when in warmup")
        void shouldReturnAlreadyTeleporting() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(teleportService.isTeleporting(player.getUniqueId())).thenReturn(true);

            TeleportResult result = homeService.teleportToHome(player, "home");

            assertThat(result).isEqualTo(TeleportResult.ALREADY_TELEPORTING);
        }

        @Test
        @DisplayName("Should return NOT_FOUND when home does not exist")
        void shouldReturnNotFound() {
            Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
            when(query.first()).thenReturn(null);

            TeleportResult result = homeService.teleportToHome(player, "nonexistent");

            assertThat(result).isEqualTo(TeleportResult.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("cancelTeleport and isTeleporting")
    class TeleportStateTests {

        @Test
        @DisplayName("Should delegate cancelTeleport to TeleportService")
        void shouldDelegateCancelTeleport() {
            UUID uuid = UUID.randomUUID();
            homeService.cancelTeleport(uuid);
            verify(teleportService).cancelTeleport(uuid);
        }

        @Test
        @DisplayName("Should delegate isTeleporting to TeleportService")
        void shouldDelegateIsTeleporting() {
            UUID uuid = UUID.randomUUID();
            when(teleportService.isTeleporting(uuid)).thenReturn(true);

            assertThat(homeService.isTeleporting(uuid)).isTrue();
        }
    }

    @Nested
    @DisplayName("SetHomeResult enum")
    class SetHomeResultTests {

        @Test
        @DisplayName("Should have all values")
        void shouldHaveAllValues() {
            assertThat(HomeService.SetHomeResult.values()).hasSize(5);
        }
    }
}
