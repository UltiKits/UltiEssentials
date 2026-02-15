package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("FeedCommand Tests")
class FeedCommandTest {

    private FeedCommand feedCommand;
    private EssentialsConfig config;
    private Player player;
    private Player target;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        feedCommand = new FeedCommand(config);
        EssentialsTestHelper.setField(feedCommand, "plugin", EssentialsTestHelper.getMockPlugin());

        player = EssentialsTestHelper.createMockPlayer("Sender", UUID.randomUUID());
        target = EssentialsTestHelper.createMockPlayer("Target", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("feedSelf")
    class FeedSelfTests {

        @Test
        @DisplayName("Should restore food level to 20")
        void shouldRestoreFoodLevel() {
            feedCommand.feedSelf(player);

            verify(player).setFoodLevel(20);
            verify(player).setSaturation(20.0f);
            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setHealEnabled(false);

            feedCommand.feedSelf(player);

            verify(player, never()).setFoodLevel(anyInt());
        }
    }

    @Nested
    @DisplayName("feedOther")
    class FeedOtherTests {

        @Test
        @DisplayName("Should restore food for another player")
        void shouldRestoreFoodForOther() {
            feedCommand.feedOther(player, target);

            verify(target).setFoodLevel(20);
            verify(target).setSaturation(20.0f);
            verify(player).sendMessage(anyString());
            verify(target).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send message when target is null")
        void shouldSendMessageWhenTargetNull() {
            feedCommand.feedOther(player, null);

            verify(player).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should send disabled message when feature is off")
        void shouldSendDisabledMessage() {
            config.setHealEnabled(false);

            feedCommand.feedOther(player, target);

            verify(target, never()).setFoodLevel(anyInt());
        }
    }

    @Test
    @DisplayName("handleHelp should send usage message")
    void handleHelpShouldSendUsage() {
        feedCommand.handleHelp(player);
        verify(player).sendMessage(anyString());
    }
}
