package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("FlyCommand Tests")
class FlyCommandTest {

    private FlyCommand flyCommand;
    private EssentialsConfig config;
    private Player player;
    private Player target;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        flyCommand = new FlyCommand(config);
        EssentialsTestHelper.setField(flyCommand, "plugin", EssentialsTestHelper.getMockPlugin());

        player = EssentialsTestHelper.createMockPlayer("Sender", UUID.randomUUID());
        target = EssentialsTestHelper.createMockPlayer("Target", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("toggleFly")
    class ToggleFlyTests {

        @Test
        @DisplayName("Should enable flight when currently disabled")
        void shouldEnableFlightWhenDisabled() {
            when(player.getAllowFlight()).thenReturn(false);

            flyCommand.toggleFly(player);

            verify(player).setAllowFlight(true);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should disable flight when currently enabled")
        void shouldDisableFlightWhenEnabled() {
            when(player.getAllowFlight()).thenReturn(true);

            flyCommand.toggleFly(player);

            verify(player).setAllowFlight(false);
            verify(player).setFlying(false);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessageWhenFeatureOff() {
            config.setFlyEnabled(false);

            flyCommand.toggleFly(player);

            verify(player, never()).setAllowFlight(anyBoolean());
            verify(player).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("toggleFlyOther")
    class ToggleFlyOtherTests {

        @Test
        @DisplayName("Should enable flight for another player")
        void shouldEnableFlightForOtherPlayer() {
            when(target.getAllowFlight()).thenReturn(false);

            flyCommand.toggleFlyOther(player, target);

            verify(target).setAllowFlight(true);
            verify(player).sendMessage(anyString());
            verify(target).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should disable flight for another player")
        void shouldDisableFlightForOtherPlayer() {
            when(target.getAllowFlight()).thenReturn(true);

            flyCommand.toggleFlyOther(player, target);

            verify(target).setAllowFlight(false);
            verify(target).setFlying(false);
        }

        @Test
        @DisplayName("Should send message when target is null")
        void shouldSendMessageWhenTargetNull() {
            flyCommand.toggleFlyOther(player, null);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessageWhenFeatureOff() {
            config.setFlyEnabled(false);

            flyCommand.toggleFlyOther(player, target);

            verify(target, never()).setAllowFlight(anyBoolean());
        }
    }

    @Test
    @DisplayName("handleHelp should send usage message")
    void handleHelpShouldSendUsageMessage() {
        flyCommand.handleHelp(player);

        verify(player).sendMessage(anyString());
    }
}
