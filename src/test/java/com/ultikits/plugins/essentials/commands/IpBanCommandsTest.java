package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.service.BanService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.annotations.command.CmdExecutor;
import com.ultikits.ultitools.annotations.command.CmdMapping;
import com.ultikits.ultitools.annotations.command.CmdTarget;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IpBanCommandsTest {
    private BanService service;
    private EssentialsConfig config;
    private CommandSender sender;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        service = mock(BanService.class);
        config = new EssentialsConfig();
        sender = mock(CommandSender.class);
    }

    @AfterEach
    void tearDown() throws Exception { EssentialsTestHelper.tearDown(); }

    private BaseEssentialsCommand command(String name) throws Exception {
        BaseEssentialsCommand command = (BaseEssentialsCommand) Class.forName(
                "com.ultikits.plugins.essentials.commands." + name).getConstructor().newInstance();
        EssentialsTestHelper.setField(command, "plugin", CatalogueText.plugin("en"));
        EssentialsTestHelper.setField(command, "banService", service);
        EssentialsTestHelper.setField(command, "config", config);
        return command;
    }

    private void ban(boolean reason) throws Exception {
        BaseEssentialsCommand command = command("BanIpCommand");
        if (reason) command.getClass().getMethod("banWithReason", CommandSender.class, String.class, String.class)
                .invoke(command, sender, "2001:db8::1", "griefing");
        else command.getClass().getMethod("ban", CommandSender.class, String.class)
                .invoke(command, sender, "2001:db8::1");
    }

    private void unban(String ip) throws Exception {
        BaseEssentialsCommand command = command("UnbanIpCommand");
        command.getClass().getMethod("unban", CommandSender.class, String.class).invoke(command, sender, ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BanIpCommand", "UnbanIpCommand"})
    void registersDedicatedPermissionsAndBothSenderTypes(String name) throws Exception {
        Class<?> type = command(name).getClass();
        CmdExecutor executor = type.getAnnotation(CmdExecutor.class);
        String label = name.equals("BanIpCommand") ? "banip" : "unbanip";
        assertThat(executor.alias()).containsExactly(label);
        assertThat(executor.permission()).isEqualTo("ultiessentials." + label);
        assertThat(type.getAnnotation(CmdTarget.class).value()).isEqualTo(CmdTarget.CmdTargetType.BOTH);
        assertThat(java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(CmdMapping.class))
                .map(m -> m.getAnnotation(CmdMapping.class).format()))
                .contains("<ip>");
        command(name).handleHelp(sender);
        verify(sender).sendMessage(contains("/" + label));
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "192.0.2.999", "127.1"})
    void invalidInputsNeverReachMutation(String ip) throws Exception {
        BaseEssentialsCommand command = command("BanIpCommand");
        command.getClass().getMethod("ban", CommandSender.class, String.class).invoke(command, sender, ip);
        unban(ip);
        verifyNoInteractions(service);
        verify(sender, times(2)).sendMessage(contains("numeric IPv4 or IPv6"));
    }

    @ParameterizedTest
    @EnumSource(BanService.IpBanResult.class)
    void reportsEveryCreationOutcome(BanService.IpBanResult result) throws Exception {
        when(service.banIp(anyString(), anyString(), isNull(), anyString())).thenReturn(result);
        ban(true);
        verify(service).banIp(eq("2001:db8:0:0:0:0:0:1"), eq("griefing"), isNull(), eq("Console"));
        if (result == BanService.IpBanResult.SUCCESS) {
            verify(sender).sendMessage(contains("Banned IP"));
            verify(EssentialsTestHelper.getMockServer()).broadcastMessage(contains("griefing"));
        } else {
            verify(sender).sendMessage(anyString());
            verify(EssentialsTestHelper.getMockServer(), never()).broadcastMessage(anyString());
        }
    }

    @Test
    void defaultReasonAndPlayerIdentityAndSilentBroadcastSwitch() throws Exception {
        sender = mock(Player.class);
        java.util.UUID id = java.util.UUID.randomUUID();
        when(((Player) sender).getUniqueId()).thenReturn(id);
        when(sender.getName()).thenReturn("Moderator");
        config.setBanBroadcast(false);
        when(service.banIp(anyString(), anyString(), eq(id), eq("Moderator")))
                .thenReturn(BanService.IpBanResult.SUCCESS);
        ban(false);
        verify(service).banIp(anyString(), eq("No reason"), eq(id), eq("Moderator"));
        verify(sender).sendMessage(contains("Banned IP"));
        verify(EssentialsTestHelper.getMockServer(), never()).broadcastMessage(anyString());
    }

    @ParameterizedTest
    @EnumSource(BanService.UnbanResult.class)
    void reportsAllLiftingOutcomesWithoutFalseSuccess(BanService.UnbanResult result) throws Exception {
        when(service.unbanIp(anyString())).thenReturn(result);
        unban("192.0.2.1");
        String expected = result == BanService.UnbanResult.REMOVED ? "Removed this plugin's IP bans" :
                result == BanService.UnbanResult.FAILED ? "Failed to lift IP ban" : "No active IP ban";
        verify(sender).sendMessage(contains(expected));
        if (result == BanService.UnbanResult.REMOVED)
            verify(EssentialsTestHelper.getMockServer()).broadcastMessage(contains("192.0.2.1"));
        else verify(EssentialsTestHelper.getMockServer(), never()).broadcastMessage(anyString());
    }

    @ParameterizedTest
    @EnumSource(value = BanService.UnbanResult.class, names = {"REMOVED", "NOT_BANNED"})
    void separateVanillaBanIsReportedAndNeverAnnouncedAsLifted(BanService.UnbanResult result) throws Exception {
        when(service.unbanIp(anyString())).thenReturn(result);
        when(service.isIpBannedInServerBanList(anyString())).thenReturn(true);
        unban("2001:db8::1");
        verify(service).unbanIp("2001:db8:0:0:0:0:0:1");
        verify(sender).sendMessage(contains("server's IP ban list"));
        verify(EssentialsTestHelper.getMockServer(), never()).broadcastMessage(anyString());
    }

    @Test
    void liftingWhileBroadcastDisabledStillConfirmsToIssuer() throws Exception {
        config.setUnbanBroadcast(false);
        when(service.unbanIp(anyString())).thenReturn(BanService.UnbanResult.REMOVED);
        unban("192.0.2.1");
        verify(sender).sendMessage(contains("Removed this plugin's IP bans"));
        verify(EssentialsTestHelper.getMockServer(), never()).broadcastMessage(anyString());
    }
}
