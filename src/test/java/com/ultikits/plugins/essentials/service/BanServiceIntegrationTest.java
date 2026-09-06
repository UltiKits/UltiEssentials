package com.ultikits.plugins.essentials.service;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.entity.BanData;
import com.ultikits.plugins.essentials.service.BanService.BanResult;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.plugins.essentials.utils.TestHelper;
import com.ultikits.ultitools.interfaces.DataOperator;
import com.ultikits.ultitools.interfaces.impl.data.json.SimpleJsonDataOperator;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration-style tests for {@link BanService}'s unban paths, exercised against a real
 * {@link DataOperator} (a {@link SimpleJsonDataOperator} backed by a temp directory) instead of a
 * stubbed query chain, so the by-name round trip proves the actual persistence path works --
 * not merely that a mock was configured to answer the way the test expects.
 * {@code BanServiceMockitoTest}'s by-name unban test stubs {@code banOperator.query()} directly
 * and therefore cannot make that proof; that is why this class exists separately rather than as
 * an added assertion there (13-11 read_first).
 * <p>
 * Covers UltiEssentials#12's two independently-decided halves (13-CONTEXT.md,
 * 13-RECONFIRMATION.md): unbanning a player this plugin itself banned (half 1 -- already closed
 * by the framework's own boolean-column read fix, #388) and unbanning a player banned only
 * through the server's own ban list (half 2 -- decided here). Reading {@link BanService#banPlayer}
 * directly (13-11 read_first) shows it never writes to the server's own ban list, so the chosen
 * fix for half 2 is a refusal that distinguishes "not in this plugin's records" from "not banned
 * anywhere" -- {@link BanService#isBannedInServerBanList(String)} -- rather than also clearing
 * the server's own list on unban, which would misrepresent what {@code banPlayer} actually does.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("BanService Integration Tests (real operator round trip)")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class BanServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private BanService banService;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkitHelper.clearForeignServer();
        server = MockBukkit.mock();
        TestHelper.mockUltiToolsInstance();

        EssentialsConfig config = mock(EssentialsConfig.class);
        lenient().when(config.isBanEnabled()).thenReturn(true);

        DataOperator<BanData> realOperator =
            new SimpleJsonDataOperator<>(tempDir.toFile().getAbsolutePath(), BanData.class);

        banService = new BanService();
        setField(banService, "config", config);
        setField(banService, "banOperator", realOperator);
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    @Test
    @DisplayName("unbanFindsABanThisPluginCreated: a ban created through this plugin's own path is found and cleared through a real operator round trip, not a stubbed query")
    void unbanFindsABanThisPluginCreated() {
        UUID targetUuid = UUID.randomUUID();
        String targetName = "PluginBannedPlayer";

        BanResult banResult = banService.banPlayer(targetUuid, targetName, "test ban", null, "console");
        assertThat(banResult).isEqualTo(BanResult.SUCCESS);
        assertThat(banService.getActiveBan(targetUuid)).isNotNull();

        boolean unbanned = banService.unbanPlayerByName(targetName);

        assertThat(unbanned).isTrue();
        assertThat(banService.getActiveBan(targetUuid)).isNull();
    }

    @Test
    @DisplayName("unbanOfAPlayerBannedOutsideThisPluginIsDecided: chosen behaviour is a distinguishing refusal, not silent clearing -- banPlayer() never writes to the server's own ban list, so unbanPlayerByName still reports false for a name absent from this plugin's own records, and isBannedInServerBanList reports true so a caller can tell 'not in my records' apart from 'not banned anywhere'")
    void unbanOfAPlayerBannedOutsideThisPluginIsDecided() {
        String targetName = "VanillaBannedPlayer";

        // Ban through the server's own ban list ONLY -- never through this plugin's own path.
        Bukkit.getBanList(BanList.Type.NAME).addBan(targetName, "vanilla ban", (Date) null, null);
        assertThat(banService.isBannedInServerBanList(targetName)).isTrue();

        boolean unbanned = banService.unbanPlayerByName(targetName);

        // This plugin's own records genuinely hold nothing for this name -- unbanPlayerByName
        // correctly reports false rather than silently claiming success for a store it never
        // touched. What matters is that the false here is NOT the same "not banned" as a player
        // who is banned nowhere at all: isBannedInServerBanList still distinguishes the two.
        assertThat(unbanned).isFalse();
        assertThat(banService.isBannedInServerBanList(targetName)).isTrue();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = BanService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
