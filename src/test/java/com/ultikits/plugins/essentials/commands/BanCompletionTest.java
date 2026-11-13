package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BanCompletionTest {
    private BanService banService;
    private Command bukkitCommand;
    private Player viewer;
    private Player visible;
    private Player hidden;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        hiddenPlayers().clear();
        banService = mock(BanService.class);
        bukkitCommand = mock(Command.class);
        viewer = EssentialsTestHelper.createMockPlayer("Moderator", UUID.randomUUID());
        visible = EssentialsTestHelper.createMockPlayer("Alice", UUID.randomUUID());
        hidden = EssentialsTestHelper.createMockPlayer("AlexHidden", UUID.randomUUID());
        hiddenPlayers().add(hidden.getUniqueId());
        doReturn(Arrays.asList(visible, hidden)).when(EssentialsTestHelper.getMockServer()).getOnlinePlayers();
        when(banService.getActiveBans()).thenReturn(Arrays.asList(
                BanData.builder().playerName("AlexBanned").build(),
                BanData.builder().playerName("AlexBanned").build(),
                BanData.builder().playerName("BobBanned").build()));
    }

    @AfterEach
    void tearDown() throws Exception {
        hiddenPlayers().clear();
        EssentialsTestHelper.tearDown();
    }

    @SuppressWarnings("unchecked")
    private Set<UUID> hiddenPlayers() throws Exception {
        return (Set<UUID>) EssentialsTestHelper.getStaticField(HideCommand.class, "HIDDEN_PLAYERS");
    }

    private BaseEssentialsCommand executor(String name) throws Exception {
        BaseEssentialsCommand executor;
        switch (name) {
            case "ban": executor = new BanCommand(); break;
            case "tempban": executor = new TempBanCommand(); break;
            default: executor = new UnbanCommand(); break;
        }
        EssentialsTestHelper.setField(executor, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(executor, "banService", banService);
        return executor;
    }

    private String permission(String name) {
        return "tempban".equals(name) ? "ultiessentials.ban.temp" : "ultiessentials." + name;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ban", "tempban"})
    void consoleAndRconExplicitlyCompleteOnlineNames(String name) throws Exception {
        BaseEssentialsCommand executor = executor(name);
        for (CommandSender sender : Arrays.asList(mock(ConsoleCommandSender.class),
                mock(RemoteConsoleCommandSender.class))) {
            when(sender.hasPermission(permission(name))).thenReturn(true);
            assertThat(executor.onTabComplete(sender, bukkitCommand, name, new String[]{"aL"}))
                    .containsExactlyInAnyOrder("Alice", "AlexHidden");
        }
    }

    @Test
    void consoleAndRconCompleteActiveBanNamesNotOnlineNames() throws Exception {
        BaseEssentialsCommand executor = executor("unban");
        for (CommandSender sender : Arrays.asList(mock(ConsoleCommandSender.class),
                mock(RemoteConsoleCommandSender.class))) {
            when(sender.hasPermission("ultiessentials.unban")).thenReturn(true);
            assertThat(executor.onTabComplete(sender, bukkitCommand, "unban", new String[]{"aL"}))
                    .containsExactly("AlexBanned");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void consoleAndPlayerCompleteDurationPresetsWithPrefixFiltering(boolean playerSender) throws Exception {
        BaseEssentialsCommand executor = executor("tempban");
        CommandSender sender = playerSender ? viewer : mock(ConsoleCommandSender.class);
        when(sender.hasPermission("ultiessentials.ban.temp")).thenReturn(true);
        assertThat(executor.onTabComplete(sender, bukkitCommand, "tempban", new String[]{"Alice", "1"}))
                .containsExactlyInAnyOrder("1h", "12h", "1d", "1w");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ban", "tempban"})
    void playerCompletionPreservesVanishVisibility(String name) throws Exception {
        BaseEssentialsCommand executor = executor(name);
        when(viewer.hasPermission(permission(name))).thenReturn(true);
        assertThat(executor.onTabComplete(viewer, bukkitCommand, name, new String[]{"aL"}))
                .containsExactly("Alice");
        when(viewer.hasPermission("ultiessentials.hide.see")).thenReturn(true);
        assertThat(executor.onTabComplete(viewer, bukkitCommand, name, new String[]{"aL"}))
                .containsExactlyInAnyOrder("Alice", "AlexHidden");
    }

    @Test
    void playerUnbanCompletionUsesDeduplicatedActiveBanNames() throws Exception {
        when(viewer.hasPermission("ultiessentials.unban")).thenReturn(true);
        assertThat(executor("unban").onTabComplete(viewer, bukkitCommand, "unban", new String[]{"aL"}))
                .containsExactly("AlexBanned");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ban", "tempban", "unban"})
    void unauthorizedPlayerGetsNoCandidatesOrDenialMessages(String name) throws Exception {
        assertThat(executor(name).onTabComplete(viewer, bukkitCommand, name, new String[]{""})).isEmpty();
        verify(viewer, never()).sendMessage(anyString());
        verifyNoInteractions(banService);
    }

    @Test
    void consoleUnbanWithNoActiveBansRetainsFrameworkFallbackContract() throws Exception {
        CommandSender console = mock(ConsoleCommandSender.class);
        when(console.hasPermission("ultiessentials.unban")).thenReturn(true);
        when(banService.getActiveBans()).thenReturn(Collections.emptyList());
        assertThat(executor("unban").onTabComplete(console, bukkitCommand, "unban", new String[]{""})).isNull();
        verify(banService).getActiveBans();
    }
}
