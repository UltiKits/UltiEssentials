package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("HealCommand Tests")
class HealCommandTest {

    private HealCommand healCommand;
    private EssentialsConfig config;
    private Player player;
    private Player target;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        healCommand = new HealCommand(config);
        EssentialsTestHelper.setField(healCommand, "plugin", EssentialsTestHelper.getMockPlugin());

        player = EssentialsTestHelper.createMockPlayer("Sender", UUID.randomUUID());
        target = EssentialsTestHelper.createMockPlayer("Target", UUID.randomUUID());

        // Mock Attribute for health
        AttributeInstance attrSender = mock(AttributeInstance.class);
        when(attrSender.getValue()).thenReturn(20.0);
        lenient().when(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(attrSender);

        AttributeInstance attrTarget = mock(AttributeInstance.class);
        when(attrTarget.getValue()).thenReturn(20.0);
        lenient().when(target.getAttribute(Attribute.GENERIC_MAX_HEALTH)).thenReturn(attrTarget);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("healSelf")
    class HealSelfTests {

        @Test
        @DisplayName("Should restore health to max")
        void shouldRestoreHealth() {
            healCommand.healSelf(player);

            verify(player).setHealth(20.0);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setHealEnabled(false);

            healCommand.healSelf(player);

            verify(player, never()).setHealth(anyDouble());
        }
    }

    @Nested
    @DisplayName("healOther")
    class HealOtherTests {

        @Test
        @DisplayName("Should restore health for another player")
        void shouldRestoreHealthForOther() {
            healCommand.healOther(player, target);

            verify(target).setHealth(20.0);
            verify(player).sendMessage(anyString());
            verify(target).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send message when target is null")
        void shouldSendMessageWhenTargetNull() {
            healCommand.healOther(player, null);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setHealEnabled(false);

            healCommand.healOther(player, target);

            verify(target, never()).setHealth(anyDouble());
        }
    }

    @Test
    @DisplayName("handleHelp should send usage message")
    void handleHelpShouldSendUsage() {
        healCommand.handleHelp(player);
        verify(player).sendMessage(anyString());
    }
}
