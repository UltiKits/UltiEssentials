package com.ultikits.plugins.essentials.service;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.service.BanService.BanResult;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.plugins.essentials.utils.TestHelper;
import com.ultikits.ultitools.interfaces.DataOperator;
import org.bukkit.World;
import org.junit.jupiter.api.*;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BanService.
 * <p>
 * 测试封禁服务。
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("BanService Tests")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class BanServiceTest {

    private ServerMock server;
    private World world;
    private PlayerMock player;
    private BanService banService;

    @Mock
    private EssentialsConfig config;

    // RETURNS_DEEP_STUBS: BanService's query-based lookups (getActiveBan/unbanPlayer*) were
    // refactored from getAll()-then-filter onto banOperator.query().where(...).eq(...).list()
    // at some point after this class was written and switched off; the fluent chain was never
    // stubbed, so .query() returned null and every code path through it threw NPE (13-04
    // re-measurement). This mock's per-test when(banOperator.getAll(...)) stubs are untouched.
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DataOperator<BanData> banOperator;

    @BeforeEach
    void setUp() {
        MockBukkitHelper.clearForeignServer();
        server = MockBukkit.mock();
        TestHelper.mockUltiToolsInstance();
        MockitoAnnotations.openMocks(this);

        world = server.addSimpleWorld("world");
        player = server.addPlayer("TestPlayer");

        // Setup mocks
        when(config.isBanEnabled()).thenReturn(true);
        // Deep-stub baseline: RETURNS_DEEP_STUBS caches one child mock per method identity
        // (query() -> Q1, Q1.where(String) -> Q2, Q2.eq(Object) -> Q3), NOT per argument
        // value, so a single stub using matchers reaches the exact Q3 node every production
        // call site lands on (banOperator.query().where("player_uuid"/"player_name"/
        // "ip_address").eq(...).list()) regardless of which column name is passed. Stubbing
        // banOperator.query().list() directly (skipping where/eq) reaches a *different*,
        // never-called node and leaves the real chain answering null (measured, 13-04).
        // Per-test when(banOperator.getAll(...)) stubs are unaffected; tests that need a
        // specific query() result override .list() individually below.
        lenient().when(banOperator.query().where(anyString()).eq(any()).list())
                .thenReturn(new ArrayList<>());

        // Create service with mocked dependencies
        banService = new BanService();
        try {
            java.lang.reflect.Field configField = BanService.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(banService, config);

            java.lang.reflect.Field operatorField = BanService.class.getDeclaredField("banOperator");
            operatorField.setAccessible(true);
            operatorField.set(banService, banOperator);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    @Nested
    @DisplayName("Ban Player Tests")
    class BanPlayerTests {

        @Test
        @DisplayName("Should ban player permanently")
        void shouldBanPlayerPermanently() {
            when(banOperator.getAll(any())).thenReturn(new ArrayList<>());

            BanResult result = banService.banPlayer(
                player.getUniqueId(),
                player.getName(),
                "测试封禁",
                UUID.randomUUID(),
                "Admin"
            );

            assertThat(result).isEqualTo(BanResult.SUCCESS);
            verify(banOperator).insert(any(BanData.class));
        }

        @Test
        @DisplayName("Should ban player temporarily")
        void shouldBanPlayerTemporarily() {
            when(banOperator.getAll(any())).thenReturn(new ArrayList<>());

            BanResult result = banService.banPlayer(
                player.getUniqueId(),
                player.getName(),
                "临时封禁",
                UUID.randomUUID(),
                "Admin",
                TimeUnit.DAYS.toMillis(7),
                null
            );

            assertThat(result).isEqualTo(BanResult.SUCCESS);
        }

        @Test
        @DisplayName("Should reject duplicate ban")
        void shouldRejectDuplicateBan() {
            BanData existingBan = BanData.builder()
                .uuid(UUID.randomUUID())
                .playerUuid(player.getUniqueId().toString())
                .playerName(player.getName())
                .reason("已封禁")
                .bannedByName("Admin")
                .banTime(System.currentTimeMillis())
                .expireTime(-1)
                .active(true)
                .build();

            when(banOperator.getAll(any())).thenReturn(List.of(existingBan));
            when(banOperator.query().where(anyString()).eq(any()).list()).thenReturn(List.of(existingBan));

            BanResult result = banService.banPlayer(
                player.getUniqueId(),
                player.getName(),
                "测试",
                UUID.randomUUID(),
                "Admin"
            );

            assertThat(result).isEqualTo(BanResult.ALREADY_BANNED);
            verify(banOperator, never()).insert(any());
        }

        @Test
        @DisplayName("Should ban with IP address")
        void shouldBanWithIpAddress() {
            when(banOperator.getAll(any())).thenReturn(new ArrayList<>());

            BanResult result = banService.banPlayer(
                player.getUniqueId(),
                player.getName(),
                "IP封禁",
                UUID.randomUUID(),
                "Admin",
                -1,
                "127.0.0.1"
            );

            assertThat(result).isEqualTo(BanResult.SUCCESS);
        }

        @Test
        @DisplayName("Should return disabled when feature disabled")
        void shouldReturnDisabledWhenFeatureDisabled() {
            when(config.isBanEnabled()).thenReturn(false);

            BanResult result = banService.banPlayer(
                player.getUniqueId(),
                player.getName(),
                "测试",
                UUID.randomUUID(),
                "Admin"
            );

            assertThat(result).isEqualTo(BanResult.DISABLED);
        }
    }

    @Nested
    @DisplayName("Unban Player Tests")
    class UnbanPlayerTests {

        @Test
        @DisplayName("Should unban player successfully")
        void shouldUnbanPlayerSuccessfully() throws Exception {
            BanData ban = BanData.builder()
                .uuid(UUID.randomUUID())
                .playerUuid(player.getUniqueId().toString())
                .playerName(player.getName())
                .reason("测试")
                .bannedByName("Admin")
                .banTime(System.currentTimeMillis())
                .expireTime(-1)
                .active(true)
                .build();

            when(banOperator.getAll(any())).thenReturn(List.of(ban));
            when(banOperator.query().where(anyString()).eq(any()).list()).thenReturn(List.of(ban));

            boolean result = banService.unbanPlayer(player.getUniqueId());

            assertThat(result).isTrue();
            verify(banOperator).update(any(BanData.class));
        }

        @Test
        @DisplayName("Should return false when player not banned")
        void shouldReturnFalseWhenPlayerNotBanned() throws Exception {
            when(banOperator.getAll(any())).thenReturn(new ArrayList<>());

            boolean result = banService.unbanPlayer(player.getUniqueId());

            assertThat(result).isFalse();
            verify(banOperator, never()).update(any());
        }

        @Test
        @DisplayName("Should unban by player name")
        void shouldUnbanByPlayerName() {
            BanData ban = BanData.builder()
                .uuid(UUID.randomUUID())
                .playerUuid(player.getUniqueId().toString())
                .playerName(player.getName())
                .reason("测试")
                .bannedByName("Admin")
                .banTime(System.currentTimeMillis())
                .expireTime(-1)
                .active(true)
                .build();

            when(banOperator.getAll(any())).thenReturn(List.of(ban));
            when(banOperator.query().where(anyString()).eq(any()).list()).thenReturn(List.of(ban));

            boolean result = banService.unbanPlayerByName(player.getName());

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("IP Ban Tests")
    class IpBanTests {

        @Test
        @DisplayName("Should get active IP ban")
        void shouldGetActiveIpBan() {
            BanData ban = BanData.builder()
                .uuid(UUID.randomUUID())
                .playerUuid(UUID.randomUUID().toString())
                .playerName("Player")
                .reason("IP封禁")
                .bannedByName("Admin")
                .banTime(System.currentTimeMillis())
                .expireTime(-1)
                .active(true)
                .ipAddress("127.0.0.1")
                .build();

            when(banOperator.getAll(any())).thenReturn(List.of(ban));
            when(banOperator.query().where(anyString()).eq(any()).list()).thenReturn(List.of(ban));

            BanData result = banService.getActiveIpBan("127.0.0.1");

            assertThat(result).isNotNull();
            assertThat(result.getIpAddress()).isEqualTo("127.0.0.1");
        }

        @Test
        @DisplayName("Should unban IP address")
        void shouldUnbanIpAddress() {
            BanData ban = BanData.builder()
                .uuid(UUID.randomUUID())
                .playerUuid(UUID.randomUUID().toString())
                .playerName("Player")
                .reason("IP封禁")
                .bannedByName("Admin")
                .banTime(System.currentTimeMillis())
                .expireTime(-1)
                .active(true)
                .ipAddress("127.0.0.1")
                .build();

            when(banOperator.getAll(any())).thenReturn(List.of(ban));
            when(banOperator.query().where(anyString()).eq(any()).list()).thenReturn(List.of(ban));

            boolean result = banService.unbanIp("127.0.0.1");

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Duration Parsing Tests")
    class DurationParsingTests {

        @Test
        @DisplayName("Should parse days")
        void shouldParseDays() {
            long duration = BanService.parseDuration("7d");

            assertThat(duration).isEqualTo(TimeUnit.DAYS.toMillis(7));
        }

        @Test
        @DisplayName("Should parse hours")
        void shouldParseHours() {
            long duration = BanService.parseDuration("24h");

            assertThat(duration).isEqualTo(TimeUnit.HOURS.toMillis(24));
        }

        @Test
        @DisplayName("Should parse combined duration")
        void shouldParseCombinedDuration() {
            long duration = BanService.parseDuration("1d12h30m");

            long expected = TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(12) + TimeUnit.MINUTES.toMillis(30);
            assertThat(duration).isEqualTo(expected);
        }

        @Test
        @DisplayName("Should return -1 for invalid duration")
        void shouldReturnNegativeOneForInvalidDuration() {
            long duration = BanService.parseDuration("invalid");

            assertThat(duration).isEqualTo(-1);
        }
    }

    @Nested
    @DisplayName("Duration Formatting Tests")
    class DurationFormattingTests {

        @Test
        @DisplayName("Should format days")
        void shouldFormatDays() {
            String formatted = BanService.formatDuration(TimeUnit.DAYS.toMillis(7));

            assertThat(formatted).contains("7天");
        }

        @Test
        @DisplayName("Should format hours and minutes")
        void shouldFormatHoursAndMinutes() {
            long duration = TimeUnit.HOURS.toMillis(2) + TimeUnit.MINUTES.toMillis(30);
            String formatted = BanService.formatDuration(duration);

            assertThat(formatted).contains("2小时");
            assertThat(formatted).contains("30分钟");
        }

        @Test
        @DisplayName("Should format expired duration")
        void shouldFormatExpiredDuration() {
            String formatted = BanService.formatDuration(-1000);

            assertThat(formatted).isEqualTo("已过期");
        }
    }
}
