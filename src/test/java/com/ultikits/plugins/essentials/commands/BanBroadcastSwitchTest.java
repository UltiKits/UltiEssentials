package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code features.ban.broadcast-ban} and {@code features.ban.broadcast-unban} were declared in
 * {@code config/essentials.yml} and read by nothing: {@code /ban}, {@code /tempban} and
 * {@code /unban} broadcast unconditionally (UltiKits/UltiEssentials#27). Each case runs one command
 * with the switch at its declared default, {@code true}, and at {@code false}.
 * <p>
 * <b>The off branch is walked, not only the on branch.</b> On a successful ban the broadcast was
 * the issuer's only confirmation -- {@code BanCommand} and {@code TempBanCommand} send the issuer
 * nothing else -- so a switch that merely skipped the broadcast would have turned a ban into a
 * silent success. With the switch off the same two lines therefore go to the issuer alone.
 * {@code /unban} already confirms to the issuer before broadcasting, so its off branch only drops
 * the broadcast.
 * <p>
 * The two switches are independent: each case with one switch off keeps the other at its default
 * and checks that command still broadcasts.
 * <p>
 * 封禁/解禁广播开关：开启时向全服广播，关闭时只告知执行者。
 */
@DisplayName("Ban and unban broadcast switches (UltiKits/UltiEssentials#27)")
class BanBroadcastSwitchTest {

    private BanService banService;
    private EssentialsConfig config;
    private Player issuer;
    private UUID issuerUuid;
    private Server server;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        server = EssentialsTestHelper.getMockServer();
        banService = mock(BanService.class);
        config = new EssentialsConfig();
        issuerUuid = UUID.randomUUID();
        issuer = EssentialsTestHelper.createMockPlayer("Moderator", issuerUuid);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private void stubExistingTarget(String name) {
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.hasPlayedBefore()).thenReturn(true);
        when(target.getName()).thenReturn(name);
        when(server.getOfflinePlayer(eq(name))).thenReturn(target);
    }

    private <T extends BaseEssentialsCommand> T wire(T command) throws Exception {
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(command, "banService", banService);
        EssentialsTestHelper.setField(command, "config", config);
        return command;
    }

    @Nested
    @DisplayName("/ban")
    class Ban {

        private BanCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = wire(new BanCommand());
            stubExistingTarget("Griefer");
            when(banService.banPlayer(any(), anyString(), anyString(), any(), anyString()))
                    .thenReturn(BanService.BanResult.SUCCESS);
        }

        @Test
        @DisplayName("broadcast-ban true (declared default): the ban and its reason are broadcast")
        void broadcastsWhenSwitchIsOn() {
            command.banWithReason(issuer, "Griefer", "griefing");

            verify(server).broadcastMessage(contains("Griefer"));
            verify(server).broadcastMessage(contains("griefing"));
            verify(issuer, never()).sendMessage(contains("Griefer"));
        }

        @Test
        @DisplayName("broadcast-ban false: nothing is broadcast, and the issuer alone is told the ban and its reason")
        void tellsOnlyTheIssuerWhenSwitchIsOff() {
            config.setBanBroadcast(false);

            command.banWithReason(issuer, "Griefer", "griefing");

            verify(server, never()).broadcastMessage(anyString());
            verify(issuer).sendMessage(contains("Griefer"));
            verify(issuer).sendMessage(contains("griefing"));
            verify(banService).banPlayer(any(), eq("Griefer"), eq("griefing"), eq(issuerUuid), eq("Moderator"));
        }

        @Test
        @DisplayName("broadcast-unban false does not silence a ban")
        void unbanSwitchDoesNotAffectBan() {
            config.setUnbanBroadcast(false);

            command.banWithReason(issuer, "Griefer", "griefing");

            verify(server).broadcastMessage(contains("Griefer"));
        }
    }

    @Nested
    @DisplayName("/tempban")
    class TempBan {

        private TempBanCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = wire(new TempBanCommand());
            stubExistingTarget("Griefer");
            when(banService.banPlayer(any(), anyString(), anyString(), any(), anyString(), anyLong(), isNull()))
                    .thenReturn(BanService.BanResult.SUCCESS);
        }

        @Test
        @DisplayName("broadcast-ban true (declared default): the temporary ban and its reason are broadcast")
        void broadcastsWhenSwitchIsOn() {
            command.tempbanWithReason(issuer, "Griefer", "1d", "griefing");

            verify(server).broadcastMessage(contains("Griefer"));
            verify(server).broadcastMessage(contains("griefing"));
            verify(issuer, never()).sendMessage(contains("Griefer"));
        }

        @Test
        @DisplayName("broadcast-ban false: nothing is broadcast, and the issuer alone is told the temporary ban and its reason")
        void tellsOnlyTheIssuerWhenSwitchIsOff() {
            config.setBanBroadcast(false);

            command.tempbanWithReason(issuer, "Griefer", "1d", "griefing");

            verify(server, never()).broadcastMessage(anyString());
            verify(issuer).sendMessage(contains("Griefer"));
            verify(issuer).sendMessage(contains("griefing"));
        }
    }

    @Nested
    @DisplayName("/unban")
    class Unban {

        private UnbanCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = wire(new UnbanCommand());
            when(banService.unbanPlayerByName("Griefer")).thenReturn(BanService.UnbanResult.REMOVED);
            when(banService.isBannedInServerBanList("Griefer")).thenReturn(false);
        }

        @Test
        @DisplayName("broadcast-unban true (declared default): the issuer is told and the unban is broadcast")
        void broadcastsWhenSwitchIsOn() {
            command.unban(issuer, "Griefer");

            verify(issuer).sendMessage(contains("Griefer"));
            verify(server).broadcastMessage(contains("Griefer"));
        }

        @Test
        @DisplayName("broadcast-unban false: the issuer is still told, and nothing is broadcast")
        void tellsOnlyTheIssuerWhenSwitchIsOff() {
            config.setUnbanBroadcast(false);

            command.unban(issuer, "Griefer");

            verify(issuer).sendMessage(contains("Griefer"));
            verify(server, never()).broadcastMessage(anyString());
        }

        @Test
        @DisplayName("broadcast-ban false does not silence an unban")
        void banSwitchDoesNotAffectUnban() {
            config.setBanBroadcast(false);

            command.unban(issuer, "Griefer");

            verify(server).broadcastMessage(contains("Griefer"));
        }
    }
}
