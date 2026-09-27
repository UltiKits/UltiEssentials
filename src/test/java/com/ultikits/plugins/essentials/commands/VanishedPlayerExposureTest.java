package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.plugins.essentials.service.TpaService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A vanished player stays hidden from players who may not see vanished players, on every path the
 * maintainer decided on 2026-09-27 (UltiKits/UltiEssentials#56): {@code /tpa} and {@code /tpahere}
 * treat the vanished target as offline, ban completion leaves the name out, and un-vanishing works
 * whatever {@code features.hide.enabled} says.
 */
@DisplayName("A vanished player is not exposed through /tpa, /tpahere or ban completion, and can always un-vanish (#56)")
class VanishedPlayerExposureTest {

    private static final String SEE = "ultiessentials.hide.see";

    private EssentialsConfig config;
    private Player vanished;
    private Player sender;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        when(Bukkit.getServer().getPluginManager().getPlugin("UltiTools")).thenReturn(mock(Plugin.class));
        hiddenPlayers().clear();
        vanished = EssentialsTestHelper.createMockPlayer("Ghost", UUID.randomUUID());
        when(vanished.isOnline()).thenReturn(true);
        sender = EssentialsTestHelper.createMockPlayer("Seeker", UUID.randomUUID());
        when(sender.isOnline()).thenReturn(true);
        when(Bukkit.getServer().getPlayer(eq("Ghost"))).thenReturn(vanished);
        doReturn(Arrays.asList(vanished, sender)).when(Bukkit.getServer()).getOnlinePlayers();
    }

    @AfterEach
    void tearDown() throws Exception {
        hiddenPlayers().clear();
        EssentialsTestHelper.tearDown();
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> hiddenPlayers() throws Exception {
        Field field = HideCommand.class.getDeclaredField("HIDDEN_PLAYERS");
        field.setAccessible(true); // NOPMD
        return (Set<UUID>) field.get(null);
    }

    private void vanish() throws Exception {
        hiddenPlayers().add(vanished.getUniqueId());
    }

    @Nested
    @DisplayName("/tpa and /tpahere")
    class TeleportRequests {

        private TpaService tpaService;

        @BeforeEach
        void services() {
            tpaService = mock(TpaService.class);
            when(tpaService.sendTpaRequest(any(), any())).thenReturn(TpaService.TpaResult.SENT);
            when(tpaService.sendTpaHereRequest(any(), any())).thenReturn(TpaService.TpaResult.SENT);
        }

        private TpaCommand tpa() throws Exception {
            TpaCommand command = new TpaCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "tpaService", tpaService);
            EssentialsTestHelper.setField(command, "config", config);
            return command;
        }

        private TpaHereCommand tpaHere() throws Exception {
            TpaHereCommand command = new TpaHereCommand();
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(command, "tpaService", tpaService);
            EssentialsTestHelper.setField(command, "config", config);
            return command;
        }

        @Test
        @DisplayName("/tpa to a vanished player answers as for an offline player and sends nothing")
        void tpaTreatsAVanishedTargetAsOffline() throws Exception {
            vanish();

            tpa().sendTpa(sender, "Ghost");

            verify(tpaService, never()).sendTpaRequest(any(), any());
            verify(sender).sendMessage(CatalogueText.text("zh", "essentials.tpa.player_offline"));
            verify(vanished, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("/tpahere to a vanished player answers as for an offline player and sends nothing")
        void tpaHereTreatsAVanishedTargetAsOffline() throws Exception {
            vanish();

            tpaHere().sendTpaHere(sender, "Ghost");

            verify(tpaService, never()).sendTpaHereRequest(any(), any());
            verify(sender).sendMessage(CatalogueText.text("zh", "essentials.tpa.player_offline"));
            verify(vanished, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("A sender who may see vanished players still reaches them")
        void aSenderWhoMaySeeVanishedPlayersReachesThem() throws Exception {
            vanish();
            when(sender.hasPermission(SEE)).thenReturn(true);

            tpa().sendTpa(sender, "Ghost");

            verify(tpaService).sendTpaRequest(sender, vanished);
        }
    }

    @Nested
    @DisplayName("ban completion")
    class BanCompletion {

        @Test
        @DisplayName("/ban and /tempban completion leave a vanished player out for a sender who may not see them")
        void completionHidesVanishedPlayers() throws Exception {
            vanish();
            BanCommand ban = new BanCommand();
            EssentialsTestHelper.setField(ban, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(ban, "banService", mock(BanService.class));
            TempBanCommand tempBan = new TempBanCommand();
            EssentialsTestHelper.setField(tempBan, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(tempBan, "banService", mock(BanService.class));

            List<String> banNames = ban.suggest(sender, mock(Command.class), new String[]{""});
            List<String> tempBanNames = tempBan.suggest(sender, mock(Command.class), new String[]{""});

            assertThat(banNames).contains("Seeker").doesNotContain("Ghost");
            assertThat(tempBanNames).contains("Seeker").doesNotContain("Ghost");
        }

        @Test
        @DisplayName("A sender who may see vanished players gets their names")
        void completionShowsVanishedPlayersToThoseWhoMaySee() throws Exception {
            vanish();
            when(sender.hasPermission(SEE)).thenReturn(true);
            BanCommand ban = new BanCommand();
            EssentialsTestHelper.setField(ban, "plugin", EssentialsTestHelper.getMockPlugin());
            EssentialsTestHelper.setField(ban, "banService", mock(BanService.class));

            assertThat(ban.suggest(sender, mock(Command.class), new String[]{""})).contains("Ghost", "Seeker");
        }
    }

    @Nested
    @DisplayName("un-vanish with features.hide.enabled false")
    class UnvanishWhileDisabled {

        @Test
        @DisplayName("A vanished player can un-vanish after the switch was turned off; the switch refuses only a new vanish")
        void unvanishWorksWhateverTheSwitchSays() throws Exception {
            vanish();
            config.setHideEnabled(false);
            HideCommand hide = new HideCommand(config);
            EssentialsTestHelper.setField(hide, "plugin", EssentialsTestHelper.getMockPlugin());

            hide.toggleHide(vanished);

            assertThat(HideCommand.isHidden(vanished)).isFalse();
            verify(sender).showPlayer(any(Plugin.class), eq(vanished));
            verify(vanished).sendMessage(CatalogueText.text("zh", "essentials.hide.disabled"));

            hide.toggleHide(vanished);

            assertThat(HideCommand.isHidden(vanished)).isFalse();
            verify(vanished).sendMessage(CatalogueText.text("zh", "essentials.error.feature_disabled"));
        }
    }
}
