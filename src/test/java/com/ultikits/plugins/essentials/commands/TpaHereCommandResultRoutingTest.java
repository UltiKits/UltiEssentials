package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.TpaService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the {@code TpaResult} directions {@code TpaCommandsTest}'s {@code TpaHereCommandTests}
 * nested class leaves untested (only SENT and SELF_REQUEST are exercised there, and its
 * offline-player test only covers {@code target == null}, never a target that is found but
 * {@code isOnline()} returns false).
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("TpaHereCommand result-routing Tests")
class TpaHereCommandResultRoutingTest {

    private TpaHereCommand command;
    private EssentialsConfig config;
    private TpaService tpaService;
    private Player sender;
    private Player target;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        tpaService = mock(TpaService.class);
        sender = EssentialsTestHelper.createMockPlayer("Sender", UUID.randomUUID());
        target = EssentialsTestHelper.createMockPlayer("Target", UUID.randomUUID());

        command = new TpaHereCommand();
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        EssentialsTestHelper.setField(command, "tpaService", tpaService);
        EssentialsTestHelper.setField(command, "config", config);

        Server server = Bukkit.getServer();
        when(server.getPlayer(eq("Target"))).thenReturn(target);
        when(target.isOnline()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Test
    @DisplayName("A target found by name but not online is rejected the same as a nonexistent target")
    void foundButOfflineTargetIsRejected() {
        when(target.isOnline()).thenReturn(false);

        command.sendTpaHere(sender, "Target");

        verify(tpaService, never()).sendTpaHereRequest(any(), any());
        verify(sender).sendMessage(anyString());
    }

    @Test
    @DisplayName("TARGET_BUSY notifies the sender that the target has a pending request")
    void targetBusyNotifiesSender() {
        when(tpaService.sendTpaHereRequest(sender, target)).thenReturn(TpaService.TpaResult.TARGET_BUSY);

        command.sendTpaHere(sender, "Target");

        verify(sender).sendMessage(anyString());
        verify(target, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("ON_COOLDOWN reports the remaining cooldown seconds to the sender")
    void onCooldownReportsRemainingSeconds() {
        when(tpaService.sendTpaHereRequest(sender, target)).thenReturn(TpaService.TpaResult.ON_COOLDOWN);
        when(tpaService.getRemainingCooldown(sender.getUniqueId())).thenReturn(17);

        command.sendTpaHere(sender, "Target");

        verify(sender).sendMessage(contains("17"));
    }

    @Test
    @DisplayName("CROSS_WORLD_DISABLED notifies the sender that cross-world teleport is not allowed")
    void crossWorldDisabledNotifiesSender() {
        when(tpaService.sendTpaHereRequest(sender, target)).thenReturn(TpaService.TpaResult.CROSS_WORLD_DISABLED);

        command.sendTpaHere(sender, "Target");

        verify(sender).sendMessage(anyString());
        verify(target, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("DISABLED (from the service layer) notifies the sender the feature is off")
    void serviceDisabledNotifiesSender() {
        when(tpaService.sendTpaHereRequest(sender, target)).thenReturn(TpaService.TpaResult.DISABLED);

        command.sendTpaHere(sender, "Target");

        verify(sender).sendMessage(anyString());
        verify(target, never()).sendMessage(anyString());
    }
}
