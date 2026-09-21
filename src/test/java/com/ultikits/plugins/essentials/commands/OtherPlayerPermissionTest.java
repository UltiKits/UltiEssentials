package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.plugins.essentials.utils.TestHelper;
import com.ultikits.ultitools.abstracts.command.CommandContext;
import com.ultikits.ultitools.abstracts.command.validation.CommandValidator.ValidationResult;
import com.ultikits.ultitools.abstracts.command.validation.validators.PermissionValidator;
import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.annotations.command.CmdMapping;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acting on another player must require a node the self variant does not grant
 * (UltiKits/UltiEssentials#25).
 * <p>
 * {@code /fly <player>} declared no method-level permission, so it inherited the class-level
 * {@code ultiessentials.fly} — the same node that gates {@code /fly}. Any server granting ordinary
 * players self-flight, a common low-risk convenience grant, was also letting them force flight on or
 * off for anyone online, including cutting another player's flight mid-air.
 * <p>
 * The refusal is exercised through the framework's own {@link PermissionValidator} against a real
 * {@link Player} carrying real permission attachments, not by reading the annotation and trusting it
 * means something: the validator is what actually decides, and it is what a sender meets. The
 * annotation is the input to that decision, which is why the sweep below reads it — but the sweep
 * exists for a different purpose, namely to make the next self/other pair added to this module fail
 * the build if it repeats the omission.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("Other-player commands need their own permission node (UltiKits/UltiEssentials#25)")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class OtherPlayerPermissionTest {

    /**
     * Every command class in this module holding both a self mapping and a mapping that acts on a
     * named other player. Kept explicit rather than discovered, because "acts on someone else" is a
     * judgement about each command's meaning, not something a scan can decide: {@code /invsee},
     * {@code /armorsee} and {@code /endersee} take a player too, but have no self variant whose node
     * could leak — their class-level node is already the elevated one.
     */
    private static final List<SelfOtherPair> SELF_OTHER_PAIRS = Arrays.asList(
        new SelfOtherPair(FlyCommand.class, "toggleFly", "toggleFlyOther"),
        new SelfOtherPair(HealCommand.class, "healSelf", "healOther"),
        new SelfOtherPair(FeedCommand.class, "feedSelf", "feedOther"),
        new SelfOtherPair(GameModeCommand.class, "setGameMode", "setGameModeOther"));

    private ServerMock server;

    @BeforeEach
    void setUp() {
        MockBukkitHelper.clearForeignServer();
        server = MockBukkit.mock();
        TestHelper.mockUltiToolsInstance();
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    @Nested
    @DisplayName("/fly <player>")
    class FlyOtherTests {

        @Test
        @DisplayName("a sender holding only the self-fly node is refused")
        void selfNodeAloneIsRefused() throws Exception {
            PlayerMock sender = server.addPlayer("SelfFlyOnly");
            sender.addAttachment(pluginForAttachments(), "ultiessentials.fly", true);
            assertThat(sender.hasPermission("ultiessentials.fly"))
                .as("precondition: the sender really holds the self node")
                .isTrue();

            ValidationResult result = validateFlyOther(sender);

            assertThat(result.isValid())
                .as("a self-fly grant must not carry flight control over other players")
                .isFalse();
        }

        @Test
        @DisplayName("a sender holding the other-player node as well is allowed")
        void bothNodesAreAllowed() throws Exception {
            PlayerMock sender = server.addPlayer("FlyAdmin");
            sender.addAttachment(pluginForAttachments(), "ultiessentials.fly", true);
            sender.addAttachment(pluginForAttachments(), "ultiessentials.fly.other", true);

            ValidationResult result = validateFlyOther(sender);

            assertThat(result.isValid())
                .as("the elevated grant must still work, or the node is just a wall")
                .isTrue();
        }

        @Test
        @DisplayName("toggling one's own flight still needs only the self node")
        void selfMappingIsUnchanged() throws Exception {
            PlayerMock sender = server.addPlayer("SelfFlyOnly");
            sender.addAttachment(pluginForAttachments(), "ultiessentials.fly", true);

            Method self = FlyCommand.class.getDeclaredMethod("toggleFly", Player.class);
            ValidationResult result = validate(sender, FlyCommand.class, self);

            assertThat(result.isValid())
                .as("the self variant must not have been dragged up to the elevated node")
                .isTrue();
        }

        private ValidationResult validateFlyOther(PlayerMock sender) throws Exception {
            Method other = FlyCommand.class.getDeclaredMethod("toggleFlyOther", Player.class, Player.class);
            return validate(sender, FlyCommand.class, other);
        }
    }

    @Nested
    @DisplayName("Every self/other pair in this module")
    class SweepTests {

        @Test
        @DisplayName("the other-player mapping declares a node the class-level one does not grant")
        void everyOtherMappingHasItsOwnNode() throws Exception {
            List<String> offenders = new ArrayList<>();
            for (SelfOtherPair pair : SELF_OTHER_PAIRS) {
                String classNode = pair.type.getAnnotation(CmdExecutor.class).permission();
                String otherNode = pair.otherMapping().permission();
                if (otherNode.isEmpty() || otherNode.equals(classNode)) {
                    offenders.add(pair.type.getSimpleName() + '#' + pair.otherMethod
                        + " is gated by '" + (otherNode.isEmpty() ? classNode : otherNode)
                        + "', the same node as its self variant");
                }
            }
            assertThat(offenders).isEmpty();
        }

        @Test
        @DisplayName("the self mapping does not declare an elevated node of its own")
        void everySelfMappingKeepsTheBaseNode() throws Exception {
            for (SelfOtherPair pair : SELF_OTHER_PAIRS) {
                assertThat(pair.selfMapping().permission())
                    .as(pair.type.getSimpleName() + '#' + pair.selfMethod + " permission override")
                    .isEmpty();
            }
        }

        @Test
        @DisplayName("the pair list names methods that exist (positive control)")
        void thePairListResolves() throws Exception {
            assertThat(SELF_OTHER_PAIRS).hasSize(4);
            for (SelfOtherPair pair : SELF_OTHER_PAIRS) {
                assertThat(pair.selfMapping()).as(pair.selfMethod).isNotNull();
                assertThat(pair.otherMapping()).as(pair.otherMethod).isNotNull();
            }
        }
    }

    // === helpers ===

    /**
     * Runs the framework's own permission validator exactly as the command pipeline does: the
     * class-level node from {@code @CmdExecutor}, then the matched method's own
     * {@code @CmdMapping(permission = ...)}.
     */
    private static ValidationResult validate(Player sender, Class<?> executor, Method matched) {
        CmdExecutor declaration = executor.getAnnotation(CmdExecutor.class);
        CommandContext context = CommandContext.builder()
            .sender(sender)
            .executorClass(executor)
            .matchedMethod(matched)
            .build();
        return new PermissionValidator(declaration.permission(), declaration.requireOp())
            .validate(context);
    }

    /**
     * MockBukkit attaches permissions through a plugin; any enabled plugin will do.
     */
    private org.bukkit.plugin.Plugin pluginForAttachments() {
        return MockBukkit.createMockPlugin();
    }

    private static final class SelfOtherPair {
        private final Class<?> type;
        private final String selfMethod;
        private final String otherMethod;

        SelfOtherPair(Class<?> type, String selfMethod, String otherMethod) {
            this.type = type;
            this.selfMethod = selfMethod;
            this.otherMethod = otherMethod;
        }

        CmdMapping selfMapping() {
            return mappingOf(selfMethod);
        }

        CmdMapping otherMapping() {
            return mappingOf(otherMethod);
        }

        private CmdMapping mappingOf(String name) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.isAnnotationPresent(CmdMapping.class)) {
                    return method.getAnnotation(CmdMapping.class);
                }
            }
            throw new AssertionError("No @CmdMapping method named " + name + " on " + type.getName());
        }
    }
}
