package com.ultikits.plugins.essentials.entity;

import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Entity Data Tests")
class EntityDataTest {

    @Nested
    @DisplayName("BanData")
    class BanDataTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuildWithAllFields() {
            UUID uuid = UUID.randomUUID();
            BanData ban = BanData.builder()
                    .uuid(uuid)
                    .playerUuid("player-uuid")
                    .playerName("Player")
                    .reason("hacking")
                    .bannedBy("admin-uuid")
                    .bannedByName("Admin")
                    .banTime(1000L)
                    .expireTime(2000L)
                    .active(true)
                    .ipAddress("192.168.1.1")
                    .build();

            assertThat(ban.getUuid()).isEqualTo(uuid);
            assertThat(ban.getPlayerUuid()).isEqualTo("player-uuid");
            assertThat(ban.getPlayerName()).isEqualTo("Player");
            assertThat(ban.getReason()).isEqualTo("hacking");
            assertThat(ban.getBannedBy()).isEqualTo("admin-uuid");
            assertThat(ban.getBannedByName()).isEqualTo("Admin");
            assertThat(ban.getBanTime()).isEqualTo(1000L);
            assertThat(ban.getExpireTime()).isEqualTo(2000L);
            assertThat(ban.isActive()).isTrue();
            assertThat(ban.getIpAddress()).isEqualTo("192.168.1.1");
        }

        @Test
        @DisplayName("isPermanent should return true for -1 expire time")
        void isPermanentShouldReturnTrueForNegativeOne() {
            BanData ban = BanData.builder().expireTime(-1).build();

            assertThat(ban.isPermanent()).isTrue();
        }

        @Test
        @DisplayName("isPermanent should return false for positive expire time")
        void isPermanentShouldReturnFalseForPositive() {
            BanData ban = BanData.builder().expireTime(System.currentTimeMillis() + 3600000).build();

            assertThat(ban.isPermanent()).isFalse();
        }

        @Test
        @DisplayName("hasExpired should return false for permanent ban")
        void hasExpiredShouldReturnFalseForPermanent() {
            BanData ban = BanData.builder().expireTime(-1).build();

            assertThat(ban.hasExpired()).isFalse();
        }

        @Test
        @DisplayName("hasExpired should return true for past expire time")
        void hasExpiredShouldReturnTrueForPast() {
            BanData ban = BanData.builder().expireTime(1000L).build(); // Far in the past

            assertThat(ban.hasExpired()).isTrue();
        }

        @Test
        @DisplayName("hasExpired should return false for future expire time")
        void hasExpiredShouldReturnFalseForFuture() {
            BanData ban = BanData.builder().expireTime(System.currentTimeMillis() + 3600000).build();

            assertThat(ban.hasExpired()).isFalse();
        }

        @Test
        @DisplayName("getRemainingTime should return -1 for permanent ban")
        void getRemainingTimeShouldReturnNegativeForPermanent() {
            BanData ban = BanData.builder().expireTime(-1).build();

            assertThat(ban.getRemainingTime()).isEqualTo(-1);
        }

        @Test
        @DisplayName("getRemainingTime should return 0 for expired ban")
        void getRemainingTimeShouldReturnZeroForExpired() {
            BanData ban = BanData.builder().expireTime(1000L).build();

            assertThat(ban.getRemainingTime()).isEqualTo(0);
        }

        @Test
        @DisplayName("getRemainingTime should return positive for active ban")
        void getRemainingTimeShouldReturnPositiveForActive() {
            BanData ban = BanData.builder().expireTime(System.currentTimeMillis() + 3600000).build();

            assertThat(ban.getRemainingTime()).isGreaterThan(0);
        }

        @Test
        @DisplayName("getId and setId should work")
        void getIdAndSetIdShouldWork() {
            BanData ban = new BanData();
            UUID id = UUID.randomUUID();

            ban.setId(id);

            assertThat(ban.getId()).isEqualTo(id);
            assertThat(ban.getUuid()).isEqualTo(id);
        }

        @Test
        @DisplayName("Default constructor should create empty instance")
        void defaultConstructorShouldCreateEmpty() {
            BanData ban = new BanData();

            assertThat(ban.getUuid()).isNull();
            assertThat(ban.getPlayerName()).isNull();
        }
    }

    @Nested
    @DisplayName("ChestLockData")
    class ChestLockDataTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuildWithAllFields() {
            UUID uuid = UUID.randomUUID();
            ChestLockData lock = ChestLockData.builder()
                    .uuid(uuid)
                    .world("world")
                    .x(100)
                    .y(64)
                    .z(200)
                    .ownerUuid("owner-uuid")
                    .ownerName("Owner")
                    .createdAt(System.currentTimeMillis())
                    .build();

            assertThat(lock.getUuid()).isEqualTo(uuid);
            assertThat(lock.getWorld()).isEqualTo("world");
            assertThat(lock.getX()).isEqualTo(100);
            assertThat(lock.getY()).isEqualTo(64);
            assertThat(lock.getZ()).isEqualTo(200);
            assertThat(lock.getOwnerUuid()).isEqualTo("owner-uuid");
            assertThat(lock.getOwnerName()).isEqualTo("Owner");
        }

        @Test
        @DisplayName("getLocationKey should return formatted key")
        void getLocationKeyShouldReturnFormattedKey() {
            ChestLockData lock = ChestLockData.builder()
                    .world("world")
                    .x(100)
                    .y(64)
                    .z(200)
                    .build();

            assertThat(lock.getLocationKey()).isEqualTo("world:100:64:200");
        }

        @Test
        @DisplayName("createLocationKey static method should work")
        void createLocationKeyShouldWork() {
            String key = ChestLockData.createLocationKey("nether", -50, 30, 100);

            assertThat(key).isEqualTo("nether:-50:30:100");
        }

        @Test
        @DisplayName("getId and setId should work")
        void getIdAndSetIdShouldWork() {
            ChestLockData lock = new ChestLockData();
            UUID id = UUID.randomUUID();

            lock.setId(id);

            assertThat(lock.getId()).isEqualTo(id);
        }

        @Test
        @DisplayName("Default constructor should create empty instance")
        void defaultConstructorShouldCreateEmpty() {
            ChestLockData lock = new ChestLockData();

            assertThat(lock.getUuid()).isNull();
            assertThat(lock.getWorld()).isNull();
        }
    }

    @Nested
    @DisplayName("HomeData")
    class HomeDataTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuildWithAllFields() {
            UUID uuid = UUID.randomUUID();
            HomeData home = HomeData.builder()
                    .uuid(uuid)
                    .playerUuid("player-uuid")
                    .name("home")
                    .world("world")
                    .x(100)
                    .y(64)
                    .z(200)
                    .build();

            assertThat(home.getUuid()).isEqualTo(uuid);
            assertThat(home.getPlayerUuid()).isEqualTo("player-uuid");
            assertThat(home.getName()).isEqualTo("home");
            assertThat(home.getWorld()).isEqualTo("world");
        }

        @Test
        @DisplayName("getId and setId should work")
        void getIdAndSetIdShouldWork() {
            HomeData home = new HomeData();
            UUID id = UUID.randomUUID();

            home.setId(id);

            assertThat(home.getId()).isEqualTo(id);
        }
    }

    @Nested
    @DisplayName("WarpData")
    class WarpDataTests {

        @Test
        @DisplayName("Should build with all fields")
        void shouldBuildWithAllFields() {
            UUID uuid = UUID.randomUUID();
            WarpData warp = WarpData.builder()
                    .uuid(uuid)
                    .name("spawn")
                    .world("world")
                    .x(0)
                    .y(64)
                    .z(0)
                    .permission("vip.warp")
                    .createdBy("creator-uuid")
                    .createdAt(System.currentTimeMillis())
                    .build();

            assertThat(warp.getUuid()).isEqualTo(uuid);
            assertThat(warp.getName()).isEqualTo("spawn");
            assertThat(warp.getPermission()).isEqualTo("vip.warp");
            assertThat(warp.getCreatedBy()).isEqualTo("creator-uuid");
        }

        @Test
        @DisplayName("Should build warp without permission")
        void shouldBuildWarpWithoutPermission() {
            WarpData warp = WarpData.builder()
                    .uuid(UUID.randomUUID())
                    .name("public")
                    .world("world")
                    .x(0).y(64).z(0)
                    .build();

            assertThat(warp.getPermission()).isNull();
        }

        @Test
        @DisplayName("getId and setId should work")
        void getIdAndSetIdShouldWork() {
            WarpData warp = new WarpData();
            UUID id = UUID.randomUUID();

            warp.setId(id);

            assertThat(warp.getId()).isEqualTo(id);
        }
    }
}
