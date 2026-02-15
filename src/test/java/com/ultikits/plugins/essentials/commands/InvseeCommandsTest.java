package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.*;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("Inventory See Commands Tests")
class InvseeCommandsTest {

    private EssentialsConfig config;
    private Player sender;
    private Player target;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        sender = EssentialsTestHelper.createMockPlayer("Sender", UUID.randomUUID());
        target = EssentialsTestHelper.createMockPlayer("Target", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Nested
    @DisplayName("InvseeCommand")
    class InvseeCommandTests {

        private InvseeCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new InvseeCommand(config);
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        }

        @Test
        @DisplayName("Should open target inventory")
        void shouldOpenTargetInventory() {
            PlayerInventory targetInv = mock(PlayerInventory.class);
            when(target.getInventory()).thenReturn(targetInv);

            command.invsee(sender, target);

            verify(sender).openInventory(targetInv);
            verify(sender).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle null target")
        void shouldHandleNullTarget() {
            command.invsee(sender, null);

            verify(sender).sendMessage(anyString());
            verify(sender, never()).openInventory(any(Inventory.class));
        }

        @Test
        @DisplayName("Should prevent viewing own inventory")
        void shouldPreventViewingOwnInventory() {
            command.invsee(sender, sender);

            verify(sender).sendMessage(anyString());
            verify(sender, never()).openInventory(any(Inventory.class));
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabled() {
            config.setInvseeEnabled(false);

            command.invsee(sender, target);

            verify(sender, never()).openInventory(any(Inventory.class));
        }

        @Test
        @DisplayName("handleHelp should send usage message")
        void handleHelpShouldSendUsage() {
            command.handleHelp(sender);
            verify(sender).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("EnderseeCommand")
    class EnderseeCommandTests {

        private EnderseeCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new EnderseeCommand(config);
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        }

        @Test
        @DisplayName("Should open target ender chest")
        void shouldOpenTargetEnderChest() {
            Inventory enderChest = mock(Inventory.class);
            when(target.getEnderChest()).thenReturn(enderChest);

            command.endersee(sender, target);

            verify(sender).openInventory(enderChest);
            verify(sender).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle null target")
        void shouldHandleNullTarget() {
            command.endersee(sender, null);

            verify(sender).sendMessage(anyString());
            verify(sender, never()).openInventory(any(Inventory.class));
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabled() {
            config.setInvseeEnabled(false);

            command.endersee(sender, target);

            verify(sender, never()).openInventory(any(Inventory.class));
        }

        @Test
        @DisplayName("handleHelp should send usage message")
        void handleHelpShouldSendUsage() {
            command.handleHelp(sender);
            verify(sender).sendMessage(anyString());
        }
    }

    @Nested
    @DisplayName("ArmorseeCommand")
    class ArmorseeCommandTests {

        private ArmorseeCommand command;

        @BeforeEach
        void setUp() throws Exception {
            command = new ArmorseeCommand(config);
            EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        }

        @Test
        @DisplayName("Should open armor display inventory")
        void shouldOpenArmorDisplay() {
            PlayerInventory targetInv = mock(PlayerInventory.class);
            when(target.getInventory()).thenReturn(targetInv);
            when(targetInv.getArmorContents()).thenReturn(new ItemStack[4]);
            when(targetInv.getItemInOffHand()).thenReturn(null);

            Inventory armorInventory = mock(Inventory.class);
            Server server = Bukkit.getServer();
            when(server.createInventory(isNull(), eq(9), anyString())).thenReturn(armorInventory);

            command.armorsee(sender, target);

            verify(sender).openInventory(armorInventory);
            verify(sender).sendMessage(anyString());
        }

        @Test
        @DisplayName("Should handle null target")
        void shouldHandleNullTarget() {
            command.armorsee(sender, null);

            verify(sender).sendMessage(anyString());
            verify(sender, never()).openInventory(any(Inventory.class));
        }

        @Test
        @DisplayName("Should handle disabled feature")
        void shouldHandleDisabled() {
            config.setInvseeEnabled(false);

            command.armorsee(sender, target);

            verify(sender, never()).openInventory(any(Inventory.class));
        }

        @Test
        @DisplayName("handleHelp should send usage message")
        void handleHelpShouldSendUsage() {
            command.handleHelp(sender);
            verify(sender).sendMessage(anyString());
        }
    }
}
