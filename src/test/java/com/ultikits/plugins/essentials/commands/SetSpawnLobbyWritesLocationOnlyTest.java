package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.LobbyConfig;
import com.ultikits.plugins.essentials.config.SpawnConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /setspawn} and {@code /setlobby} write exactly the six location settings the operator's command
 * names, through the framework's real write gate on a real file, and nothing else (maintainer decision
 * 2026-10-04, "what code may write, by file type": {@code /setspawn} writes only the location;
 * UltiKits/UltiEssentials#72). The command is the operator's consent for those six settings, so they are
 * written even where the operator edited one by hand; every other byte of the file stays. A write the
 * framework refuses tells the player the location was not saved and why, and the running location goes
 * back to what the file holds (maintainer decision 2026-10-05).
 * <p>
 * 设置出生点/主城只写六个坐标项：其他手改内容逐字节保留；被拒绝时告知未保存及原因，并撤回内存中的修改。
 */
@DisplayName("/setspawn and /setlobby write only the six location settings (UltiEssentials#72)")
class SetSpawnLobbyWritesLocationOnlyTest {

    private static final List<String> LOCATION_KEYS = Arrays.asList("world", "x", "y", "z", "yaw", "pitch");

    @TempDir
    Path tempDir;

    private EssentialsConfig config;
    private Player player;
    /** Held here because a {@link Location} keeps its world only weakly. */
    private World nether;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        config = new EssentialsConfig();
        player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        nether = EssentialsTestHelper.createMockWorld("world_nether");
        when(player.getLocation()).thenReturn(new Location(nether, 12.5, 70.0, -3.25, 90.0f, 15.0f));
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    // ==================== /setspawn ====================

    @Test
    @DisplayName("a hand edit of another spawn setting made while the server runs stays, and only the location lines change")
    void aHandEditOfAnotherSpawnSettingStays() throws Exception {
        SpawnConfig spawn = load(SpawnConfig::new);
        String edited = edit(spawn, "teleport-on-respawn: true", "teleport-on-respawn: false");

        setSpawn(spawn);

        verify(player).sendMessage(CatalogueText.text("zh", "essentials.spawn.set"));
        String after = read(spawn);
        assertOnlyLocationLinesChanged(edited, after);
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(file(spawn));
        assertThat(onDisk.getBoolean("spawn.teleport-on-respawn")).isFalse();
        assertLocation(onDisk, "spawn");
    }

    @Test
    @DisplayName("a location setting edited by hand since the load is replaced by the command's: all six are written")
    void theCommandWritesAllSixLocationSettings() throws Exception {
        SpawnConfig spawn = load(SpawnConfig::new);
        String edited = edit(spawn, "x: 0.0", "x: 123.0");

        setSpawn(spawn);

        verify(player).sendMessage(CatalogueText.text("zh", "essentials.spawn.set"));
        assertOnlyLocationLinesChanged(edited, read(spawn));
        assertLocation(YamlConfiguration.loadConfiguration(file(spawn)), "spawn");
    }

    @Test
    @DisplayName("a refused write leaves the file as it is, says the spawn was not saved and why, and puts the old spawn back")
    void aRefusedWriteIsReportedAndRolledBack() throws Exception {
        SpawnConfig spawn = load(SpawnConfig::new);
        String anchored = edit(spawn, "teleport-on-first-join: true", "teleport-on-first-join: &on true")
                .replace("teleport-on-respawn: true", "teleport-on-respawn: *on");
        Files.write(file(spawn).toPath(), anchored.getBytes(StandardCharsets.UTF_8));

        setSpawn(spawn);

        assertThat(read(spawn)).isEqualTo(anchored);
        // The running spawn is the one the file holds, not the one the command tried to set.
        assertThat(spawn.getWorld()).isEqualTo("world");
        assertThat(Arrays.asList(spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()))
                .containsExactly(0.0, 64.0, 0.0, 0.0, 0.0);
        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(reply.capture());
        assertThat(reply.getValue())
                .isNotEqualTo(CatalogueText.text("zh", "essentials.spawn.set"))
                .contains("anchors")
                .startsWith(CatalogueText.text("zh", "essentials.spawn.not_saved").split("%s")[0]);
    }

    // ==================== /setlobby ====================

    @Test
    @DisplayName("/setlobby writes all six lobby settings, the one edited by hand included, and nothing else changes")
    void setLobbyWritesTheSixLocationSettings() throws Exception {
        LobbyConfig lobby = load(LobbyConfig::new);
        String edited = edit(lobby, "y: 64.0", "y: 80.0");

        setLobby(lobby);

        verify(player).sendMessage(CatalogueText.text("zh", "essentials.lobby.set"));
        assertOnlyLocationLinesChanged(edited, read(lobby));
        assertLocation(YamlConfiguration.loadConfiguration(file(lobby)), "lobby");
    }

    @Test
    @DisplayName("a refused /setlobby leaves the file as it is, says the lobby was not saved, and puts the old lobby back")
    void aRefusedSetLobbyIsReportedAndRolledBack() throws Exception {
        LobbyConfig lobby = load(LobbyConfig::new);
        String anchored = edit(lobby, "x: 0.0", "x: &zero 0.0").replace("z: 0.0", "z: *zero");
        Files.write(file(lobby).toPath(), anchored.getBytes(StandardCharsets.UTF_8));

        setLobby(lobby);

        assertThat(read(lobby)).isEqualTo(anchored);
        assertThat(lobby.getWorld()).isEqualTo("world");
        assertThat(Arrays.asList(lobby.getX(), lobby.getY(), lobby.getZ(), lobby.getYaw(), lobby.getPitch()))
                .containsExactly(0.0, 64.0, 0.0, 0.0, 0.0);
        ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
        verify(player).sendMessage(reply.capture());
        assertThat(reply.getValue())
                .isNotEqualTo(CatalogueText.text("zh", "essentials.lobby.set"))
                .contains("anchors")
                .startsWith(CatalogueText.text("zh", "essentials.lobby.not_saved").split("%s")[0]);
    }

    // ==================== helpers ====================

    private void setSpawn(SpawnConfig spawn) throws Exception {
        SetSpawnCommand command = new SetSpawnCommand(config, spawn);
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        command.setSpawn(player);
    }

    private void setLobby(LobbyConfig lobby) throws Exception {
        SetLobbyCommand command = new SetLobbyCommand(config, lobby);
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        command.setLobby(player);
    }

    /** The six location settings hold the player's location. */
    private static void assertLocation(YamlConfiguration onDisk, String section) {
        assertThat(onDisk.getString(section + ".location.world")).isEqualTo("world_nether");
        assertThat(onDisk.getDouble(section + ".location.x")).isEqualTo(12.5);
        assertThat(onDisk.getDouble(section + ".location.y")).isEqualTo(70.0);
        assertThat(onDisk.getDouble(section + ".location.z")).isEqualTo(-3.25);
        assertThat(onDisk.getDouble(section + ".location.yaw")).isEqualTo(90.0);
        assertThat(onDisk.getDouble(section + ".location.pitch")).isEqualTo(15.0);
    }

    /**
     * Every line except the six location value lines is byte-identical and in the same place, and each of
     * the six changed. The location keys occur nowhere else in these files.
     */
    private static void assertOnlyLocationLinesChanged(String before, String after) {
        List<String> beforeLines = Arrays.asList(before.split("\n", -1));
        List<String> afterLines = Arrays.asList(after.split("\n", -1));
        assertThat(afterLines).as("line count").hasSameSizeAs(beforeLines);
        List<String> changed = new ArrayList<>();
        for (int i = 0; i < beforeLines.size(); i++) {
            String line = beforeLines.get(i);
            if (isLocationLine(line)) {
                changed.add(line.trim().substring(0, line.trim().indexOf(':')));
                assertThat(afterLines.get(i)).as("location line " + (i + 1)).isNotEqualTo(line);
            } else {
                assertThat(afterLines.get(i)).as("line " + (i + 1)).isEqualTo(line);
            }
        }
        assertThat(changed).as("control: the six location lines were found").containsExactlyElementsOf(LOCATION_KEYS);
    }

    private static boolean isLocationLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("#") || !trimmed.contains(":")) {
            return false;
        }
        return LOCATION_KEYS.contains(trimmed.substring(0, trimmed.indexOf(':')));
    }

    /** Edits the file on disk behind the framework's back, as an operator over SSH would, and returns the new text. */
    private String edit(AbstractConfigEntity entity, String from, String to) throws IOException {
        String text = read(entity);
        assertThat(text).as("control: the edited line is in the file").contains(from);
        String edited = text.replace(from, to);
        Files.write(file(entity).toPath(), edited.getBytes(StandardCharsets.UTF_8));
        return edited;
    }

    private String read(AbstractConfigEntity entity) throws IOException {
        return new String(Files.readAllBytes(file(entity).toPath()), StandardCharsets.UTF_8);
    }

    private File file(AbstractConfigEntity entity) {
        return new File(tempDir.toFile(), entity.getClass().getAnnotation(ConfigEntity.class).value());
    }

    /** Loads one configuration through the framework's real {@code init} on a fresh file under {@code language: en}. */
    @SuppressWarnings("unchecked")
    private <T extends AbstractConfigEntity> T load(Supplier<T> config) throws IOException {
        final org.mockito.stubbing.Answer<String> text = CatalogueText.answer("en");
        // getConfigFile is protected final, so it is answered by name rather than stubbed.
        UltiToolsPlugin plugin = mock(com.ultikits.plugins.essentials.UltiEssentials.class, invocation -> {
            switch (invocation.getMethod().getName()) {
                case "i18n":
                    return text.answer(invocation);
                case "getPluginName":
                    return "UltiEssentials";
                case "getConfigFile":
                    return new File(tempDir.toFile(), invocation.<String>getArgument(0));
                case "shippedCatalogueTexts":
                    return invocation.callRealMethod();
                default:
                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }
        });
        T entity = config.get();
        Files.createDirectories(file(entity).getParentFile().toPath());
        entity.init(plugin);
        return entity;
    }
}
