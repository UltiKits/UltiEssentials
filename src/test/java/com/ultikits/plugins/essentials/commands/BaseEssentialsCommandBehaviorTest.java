package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.enums.TeleportResult;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.annotations.command.CmdTarget;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link BaseEssentialsCommand}'s {@code checkFeatureEnabled} and
 * {@code sendTeleportResultMessage} -- the class's own real behaviour, entirely untested
 * because the only existing test class for it ({@code BaseEssentialsCommandTest}) is
 * {@code @Disabled} (the abandoned MockBukkit harness). This class exercises the same
 * decisions with plain Mockito instead, per the {@code ChannelCommandsTest} idiom.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("BaseEssentialsCommand behaviour Tests (Mockito)")
class BaseEssentialsCommandBehaviorTest {

    private TestCommand command;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        command = new TestCommand();
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("checkFeatureEnabled")
    class CheckFeatureEnabledTests {

        @Test
        @DisplayName("Returns true and sends nothing when the feature is enabled")
        void returnsTrueWhenEnabled() {
            boolean result = command.testCheckFeatureEnabled(true, player);

            assertThat(result).isTrue();
            verify(player, never()).sendMessage(anyString());
        }

        @Test
        @DisplayName("Returns false and sends the disabled message when the feature is disabled")
        void returnsFalseWhenDisabled() {
            boolean result = command.testCheckFeatureEnabled(false, player);

            assertThat(result).isFalse();
            verify(player).sendMessage("feature_disabled");
        }
    }

    @Nested
    @DisplayName("sendTeleportResultMessage")
    class SendTeleportResultMessageTests {

        @Test
        @DisplayName("SUCCESS sends the success message")
        void success() {
            command.testSendTeleportResultMessage(player, TeleportResult.SUCCESS);
            verify(player).sendMessage("teleport_success");
        }

        @Test
        @DisplayName("WARMUP_STARTED sends the warmup message")
        void warmupStarted() {
            command.testSendTeleportResultMessage(player, TeleportResult.WARMUP_STARTED);
            verify(player).sendMessage("teleport_warmup_started");
        }

        @Test
        @DisplayName("NOT_FOUND sends the target-not-found message")
        void notFound() {
            command.testSendTeleportResultMessage(player, TeleportResult.NOT_FOUND);
            verify(player).sendMessage("teleport_target_not_found");
        }

        @Test
        @DisplayName("WORLD_NOT_FOUND sends the world-not-found message")
        void worldNotFound() {
            command.testSendTeleportResultMessage(player, TeleportResult.WORLD_NOT_FOUND);
            verify(player).sendMessage("teleport_world_not_found");
        }

        @Test
        @DisplayName("NO_PERMISSION sends the no-permission message")
        void noPermission() {
            command.testSendTeleportResultMessage(player, TeleportResult.NO_PERMISSION);
            verify(player).sendMessage("teleport_no_permission");
        }

        @Test
        @DisplayName("ALREADY_TELEPORTING sends the already-in-progress message")
        void alreadyTeleporting() {
            command.testSendTeleportResultMessage(player, TeleportResult.ALREADY_TELEPORTING);
            verify(player).sendMessage("teleport_already_in_progress");
        }

        @Test
        @DisplayName("DISABLED sends the feature-disabled message")
        void disabled() {
            command.testSendTeleportResultMessage(player, TeleportResult.DISABLED);
            verify(player).sendMessage("feature_disabled");
        }

        @Test
        @DisplayName("CANCELLED sends the cancelled message")
        void cancelled() {
            command.testSendTeleportResultMessage(player, TeleportResult.CANCELLED);
            verify(player).sendMessage("teleport_cancelled");
        }
    }

    /**
     * Test subclass that exposes the protected helper methods, and overrides {@code i18n} to
     * return the key verbatim so assertions can check the routed key rather than a translated
     * string that depends on the loaded language file.
     */
    @CmdTarget(CmdTarget.CmdTargetType.PLAYER)
    @CmdExecutor(alias = {"testbase"}, permission = "test.testbase", description = "Test command")
    static class TestCommand extends BaseEssentialsCommand {

        @Override
        protected String i18n(String key) {
            return key;
        }

        @Override
        protected void handleHelp(CommandSender sender) {
            sender.sendMessage("help");
        }

        boolean testCheckFeatureEnabled(boolean enabled, CommandSender sender) {
            return checkFeatureEnabled(enabled, sender);
        }

        void testSendTeleportResultMessage(Player player, TeleportResult result) {
            sendTeleportResultMessage(player, result);
        }
    }
}
