package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.service.TpaService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UltiKits/UltiEssentials#26: command, ban and kick text follows the framework's {@code language}
 * setting.
 * <p>
 * Each case answers the module's {@code i18n} from the real shipped {@code en} catalogue
 * ({@link CatalogueText}). Before the fix most keys these paths use were Chinese sentences missing
 * from both catalogues, and the kick screen and ban-duration text were Chinese literals, so every
 * case here showed Chinese text on an English server.
 */
@DisplayName("UltiEssentials#26: command, ban and kick text follows the language setting")
class EssentialsLanguageTest {

    private UltiToolsPlugin en;
    private Server server;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        server = EssentialsTestHelper.getMockServer();
        en = CatalogueText.plugin("en");
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private static List<String> said(Player player) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("/scoreboard under language: en is English, and differs from language: zh")
    void scoreboardToggle() throws Exception {
        ScoreboardService scoreboard = mock(ScoreboardService.class);
        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        when(scoreboard.toggleScoreboard(player)).thenReturn(true);
        ScoreboardCommand command = new ScoreboardCommand();
        EssentialsTestHelper.setField(command, "plugin", en);
        EssentialsTestHelper.setField(command, "scoreboardService", scoreboard);

        command.toggle(player);

        assertThat(said(player)).containsExactly("§aScoreboard enabled");
        assertThat(CatalogueText.entries("zh").get("essentials.scoreboard.enabled"))
                .isEqualTo("§a计分板已启用");
    }

    @Test
    @DisplayName("/ban from the console under language: en announces the ban in English, operator included")
    void banAnnouncement() throws Exception {
        BanService bans = mock(BanService.class);
        when(bans.banPlayer(any(), anyString(), anyString(), any(), anyString()))
                .thenReturn(BanService.BanResult.SUCCESS);
        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(UUID.randomUUID());
        when(target.hasPlayedBefore()).thenReturn(true);
        when(target.getName()).thenReturn("Griefer");
        when(server.getOfflinePlayer(eq("Griefer"))).thenReturn(target);
        EssentialsConfig config = new EssentialsConfig();
        config.setBanBroadcast(false);
        BanCommand command = new BanCommand();
        EssentialsTestHelper.setField(command, "plugin", en);
        EssentialsTestHelper.setField(command, "banService", bans);
        EssentialsTestHelper.setField(command, "config", config);
        org.bukkit.command.ConsoleCommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);

        command.ban(console, "Griefer");

        ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
        verify(console, atLeastOnce()).sendMessage(lines.capture());
        assertThat(lines.getAllValues()).containsExactly(
                "§c[Ban] §fGriefer §7was permanently banned by Console",
                "§7Reason: §fNo reason");
    }

    @Test
    @DisplayName("/tpa under language: en tells the sender and the target in English")
    void tpaRequest() throws Exception {
        TpaService tpa = mock(TpaService.class);
        Player sender = EssentialsTestHelper.createMockPlayer("Alice", UUID.randomUUID());
        Player target = EssentialsTestHelper.createMockPlayer("Bob", UUID.randomUUID());
        when(target.isOnline()).thenReturn(true);
        when(server.getPlayer(eq("Bob"))).thenReturn(target);
        when(tpa.sendTpaRequest(sender, target)).thenReturn(TpaService.TpaResult.SENT);
        EssentialsConfig config = new EssentialsConfig();
        TpaCommand command = new TpaCommand();
        EssentialsTestHelper.setField(command, "plugin", en);
        EssentialsTestHelper.setField(command, "tpaService", tpa);
        EssentialsTestHelper.setField(command, "config", config);

        command.sendTpa(sender, "Bob");

        assertThat(said(sender)).containsExactly("Teleport request sent to Bob", "Waiting for them to accept...");
        assertThat(said(target)).containsExactly("Alice wants to teleport to you",
                "Use /tpaccept to accept or /tpdeny to deny");
    }

    @Test
    @DisplayName("the kick screen and a ban's remaining time under language: en are English")
    void kickScreen() throws Exception {
        BanService bans = new BanService();
        EssentialsTestHelper.setField(bans, "plugin", en);
        BanData ban = BanData.builder().reason("griefing").bannedByName("Moderator")
                .banTime(System.currentTimeMillis())
                .expireTime(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(2)
                        + TimeUnit.MINUTES.toMillis(30) + 500)
                .active(true).build();

        String kick = bans.formatKickMessage(ban);

        assertThat(kick).startsWith("§cYou have been banned\n\n§7Reason: §fgriefing\n"
                + "§7Banned by: §fModerator\n§7Time left: §f1d 2h 30m")
                .endsWith("\n\n§7If you think this is a mistake, contact the server staff");
        assertThat(bans.formatDuration(0)).isEqualTo("Expired");
    }
}
