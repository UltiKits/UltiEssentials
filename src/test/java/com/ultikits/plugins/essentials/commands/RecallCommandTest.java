package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.service.OwnTeleports;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.CommandContext;
import com.ultikits.ultitools.abstracts.command.validation.validators.PermissionValidator;
import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.annotations.command.CmdTarget;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** The new staff summon must not inherit self-service authority or bypass Bukkit cancellation. */
class RecallCommandTest {
    private ServerMock server;
    private PluginMock bukkitPlugin;
    private EssentialsConfig config;
    private RecallCommand recall;
    private BackCommand back;
    private PlayerMock sender;
    private PlayerMock target;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkitHelper.clearForeignServer();
        server = MockBukkit.mock();
        bukkitPlugin = MockBukkit.createMockPlugin();
        config = new EssentialsConfig();
        recall = new RecallCommand(config);
        back = new BackCommand(config);
        UltiToolsPlugin plugin = mock(UltiToolsPlugin.class);
        when(plugin.i18n(anyString())).thenAnswer(CatalogueText.answer("en"));
        Field field = BaseEssentialsCommand.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(recall, plugin);
        field.set(back, plugin);
        sender = server.addPlayer("Staff");
        target = server.addPlayer("Traveler");
        sender.teleport(new Location(server.addSimpleWorld("staff_world"), 42.5, 80, -12.5, 90, 15));
        target.teleport(new Location(server.addSimpleWorld("travel_world"), 200, 65, 300));
        BackCommand.removePlayer(sender.getUniqueId());
        BackCommand.removePlayer(target.getUniqueId());
        clearMessages();
    }

    @AfterEach
    void tearDown() {
        HideCommand.removePlayer(target.getUniqueId());
        BackCommand.removePlayer(sender.getUniqueId());
        BackCommand.removePlayer(target.getUniqueId());
        MockBukkitHelper.safeUnmock();
    }

    @Test
    void recallsExactNameCaseInsensitivelyAcrossWorldsAndNotifiesBoth() {
        Location destination = sender.getLocation();
        recall.recall(sender, "tRaVeLeR");
        assertThat(target.getLocation()).isEqualTo(destination);
        assertThat(sender.getLocation()).isEqualTo(destination);
        assertThat(sender.nextMessage()).isEqualTo("Recalled Traveler to your location");
        assertThat(target.nextMessage()).isEqualTo("Staff recalled you to their location");
        assertThat(sender.nextMessage()).isNull();
        assertThat(target.nextMessage()).isNull();
    }

    @Test
    void prefixDoesNotRecallAnUnintendedPlayer() {
        Location before = target.getLocation();
        recall.recall(sender, "Trav");
        assertThat(target.getLocation()).isEqualTo(before);
        assertThat(sender.nextMessage()).isEqualTo("Player not found or offline");
        assertThat(target.nextMessage()).isNull();
    }

    @Test
    void offlineTargetIsRefused() {
        target.disconnect();
        recall.recall(sender, "Traveler");
        assertThat(sender.nextMessage()).isEqualTo("Player not found or offline");
    }

    @Test
    void selfTargetIsRefused() {
        Location before = sender.getLocation();
        recall.recall(sender, "Staff");
        assertThat(sender.nextMessage()).isEqualTo("You cannot recall yourself");
        assertThat(sender.getLocation()).isEqualTo(before);
    }

    @Test
    void disabledFeatureDoesNotMoveOrNotifyTargetAndAppliesAtCallTime() {
        Location before = target.getLocation();
        config.setRecallEnabled(false);
        recall.recall(sender, "Traveler");
        assertThat(sender.nextMessage()).isEqualTo("This feature is disabled");
        assertThat(target.getLocation()).isEqualTo(before);
        assertThat(target.nextMessage()).isNull();
        config.setRecallEnabled(true);
        recall.recall(sender, "Traveler");
        assertThat(target.getLocation()).isEqualTo(sender.getLocation());
    }

    @Test
    void vanishedTargetLooksOfflineWithoutSeePermission() throws Exception {
        markHidden();
        Location before = target.getLocation();
        recall.recall(sender, "Traveler");
        assertThat(sender.nextMessage()).isEqualTo("Player not found or offline");
        assertThat(target.getLocation()).isEqualTo(before);
        assertThat(target.nextMessage()).isNull();
        sender.addAttachment(bukkitPlugin, "ultiessentials.hide.see", true);
        recall.recall(sender, "Traveler");
        assertThat(target.getLocation()).isEqualTo(sender.getLocation());
    }

    @Test
    void completionExcludesSelfAndHiddenTargetsButKeepsVisibleControl() throws Exception {
        server.addPlayer("TravelBuddy");
        markHidden();
        Command command = mock(Command.class);
        assertThat(recall.suggest(sender, command, new String[]{"trav"})).containsExactly("TravelBuddy");
        sender.addAttachment(bukkitPlugin, "ultiessentials.hide.see", true);
        assertThat(recall.suggest(sender, command, new String[]{"trav"})).containsExactlyInAnyOrder("Traveler", "TravelBuddy");
        assertThat(recall.suggest(sender, command, new String[]{"Sta"})).isEmpty();
        assertThat(recall.suggest(sender, command, new String[]{"Traveler", ""})).isEmpty();
    }

    @Test
    void helpExplainsUsage() {
        recall.handleHelp(sender);
        assertThat(sender.nextMessage()).isEqualTo("Usage: /recall <player> - bring an online player to you");
    }

    @Test
    void ordinarySelfServiceNodesDoNotGrantRecallButDedicatedNodeDoes() throws Exception {
        sender.addAttachment(bukkitPlugin, "ultiessentials.back", true);
        sender.addAttachment(bukkitPlugin, "ultiessentials.tpahere", true);
        CmdExecutor executor = RecallCommand.class.getAnnotation(CmdExecutor.class);
        assertThat(executor.alias()).containsExactly("recall");
        assertThat(executor.permission()).isEqualTo("ultiessentials.recall.other");
        assertThat(RecallCommand.class.getAnnotation(CmdTarget.class).value()).isEqualTo(CmdTarget.CmdTargetType.PLAYER);
        CommandContext context = CommandContext.builder().sender(sender).executorClass(RecallCommand.class)
                .matchedMethod(RecallCommand.class.getDeclaredMethod("recall", Player.class, String.class)).build();
        PermissionValidator validator = new PermissionValidator(executor.permission(), executor.requireOp());
        assertThat(validator.validate(context).isValid()).isFalse();
        sender.addAttachment(bukkitPlugin, "ultiessentials.recall.other", true);
        assertThat(sender.isOp()).isFalse();
        assertThat(validator.validate(context).isValid()).isTrue();
    }

    @Test
    void falseTeleportReturnReportsFailureWithoutTargetSuccessAndClearsMarker() {
        Player refused = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(refused.getUniqueId()).thenReturn(id);
        when(refused.isOnline()).thenReturn(true);
        when(refused.teleport(any(Location.class))).thenAnswer(invocation -> {
            assertThat(OwnTeleports.isInProgress(refused)).isTrue();
            return false;
        });
        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(() -> org.bukkit.Bukkit.getPlayerExact("Refused")).thenReturn(refused);
            recall.recall(sender, "Refused");
        }
        verify(refused).teleport(sender.getLocation());
        verify(refused, never()).sendMessage(anyString());
        assertThat(sender.nextMessage()).isEqualTo("Recall failed; the player was not teleported");
        assertThat(OwnTeleports.isInProgress(refused)).isFalse();
    }

    @Test
    void successfulRecallRecordsTargetsOriginForBackNotSenders() {
        server.getPluginManager().registerEvents(back, bukkitPlugin);
        Location origin = target.getLocation();
        recall.recall(sender, "Traveler");
        clearMessages();
        back.back(target);
        assertThat(target.getLocation()).isEqualTo(origin);
        back.back(sender);
        assertThat(sender.nextMessage()).isEqualTo("No location to return to");
    }

    @Test
    void cancelledBukkitEventHasNoSuccessOrBackRecord() {
        server.getPluginManager().registerEvents(back, bukkitPlugin);
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void cancel(PlayerTeleportEvent event) {
                event.setCancelled(true);
            }
        }, bukkitPlugin);
        Location origin = target.getLocation();
        recall.recall(sender, "Traveler");
        assertThat(target.getLocation()).isEqualTo(origin);
        assertThat(sender.nextMessage()).isEqualTo("Recall failed; the player was not teleported");
        assertThat(target.nextMessage()).isNull();
        assertThat(OwnTeleports.isInProgress(target)).isFalse();
        back.back(target);
        assertThat(target.nextMessage()).isEqualTo("No location to return to");
    }

    @SuppressWarnings("unchecked")
    private void markHidden() throws Exception {
        Field field = HideCommand.class.getDeclaredField("HIDDEN_PLAYERS");
        field.setAccessible(true);
        ((java.util.Set<UUID>) field.get(null)).add(target.getUniqueId());
    }

    private void clearMessages() {
        while (sender.nextMessage() != null) { /* drain */ }
        while (target.nextMessage() != null) { /* drain */ }
    }
}
