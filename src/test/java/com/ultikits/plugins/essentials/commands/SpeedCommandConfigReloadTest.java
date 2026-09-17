package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.UltiEssentials;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * UltiKits/UltiEssentials#23: {@code /ul reload UltiEssentials} re-reads
 * {@code config/essentials.yml} through {@code ConfigManager#reloadConfigs}, which calls
 * {@code init(plugin)} again on the SAME {@link EssentialsConfig} instance the container injected
 * into {@link SpeedCommand}. This test proves the command observes such an in-place re-init on its
 * very next invocation, i.e. it does not cache {@code features.speed.max-speed} when it is created.
 * <p>
 * 验证 /speed 在配置对象被原地重新加载后，下一次调用即使用新的 max-speed。
 */
@DisplayName("SpeedCommand observes an in-place EssentialsConfig reload (UltiKits/UltiEssentials#23)")
class SpeedCommandConfigReloadTest {

    private static final String OUT_OF_RANGE_KEY = "速度必须在 0-%d 之间";

    @TempDir
    Path moduleFolder;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    @Test
    @DisplayName("lowering features.speed.max-speed and re-initialising the same config refuses the next /speed 7")
    void inPlaceReloadOfMaxSpeedIsObserved() throws Exception {
        File configFile = moduleFolder.resolve("config").resolve("essentials.yml").toFile();
        assertThat(configFile.getParentFile().mkdirs()).isTrue();
        write(configFile, "features:\n  speed:\n    enabled: true\n    max-speed: 10\n");

        UltiToolsPlugin plugin = mock(UltiEssentials.class, CALLS_REAL_METHODS);
        setResourceFolderPath(plugin, moduleFolder.toString());

        EssentialsConfig config = new EssentialsConfig();
        config.init(plugin);
        assertThat(config.getSpeedMaxSpeed()).isEqualTo(10);

        SpeedCommand command = new SpeedCommand(config);
        EssentialsTestHelper.setField(command, "plugin", EssentialsTestHelper.getMockPlugin());
        Player player = EssentialsTestHelper.createMockPlayer("Runner", UUID.randomUUID());

        command.setSpeed(player, 7);
        verify(player).setWalkSpeed(anyFloat());
        verify(player, never()).sendMessage(String.format(OUT_OF_RANGE_KEY, 10));
        clearInvocations(player);

        write(configFile, "features:\n  speed:\n    enabled: true\n    max-speed: 5\n");
        config.init(plugin);

        command.setSpeed(player, 7);
        verify(player, never()).setWalkSpeed(anyFloat());
        verify(player).sendMessage(String.format(OUT_OF_RANGE_KEY, 5));
    }

    private static void write(File file, String content) throws Exception {
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    private static void setResourceFolderPath(UltiToolsPlugin plugin, String path) throws Exception {
        Field field = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        field.setAccessible(true);
        field.set(plugin, path);
    }
}
