package com.ultikits.plugins.essentials;

import com.ultikits.plugins.essentials.commands.WildCommand;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.service.EntityIdBackfillService;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScheduledCommandService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.command.CmdCD;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.manager.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Two settings in {@code config/essentials.yml} never took effect and are removed in 6.3.0
 * (UltiKits/UltiEssentials#27):
 * <ul>
 *   <li>{@code features.wild.cooldown} -- {@code /wild}'s cooldown is the framework's
 *       {@code @CmdCD(60)}, a compile-time constant no configuration read can change. Making it
 *       configurable is a framework capability, requested as UltiKits/UltiTools-Reborn#531;</li>
 *   <li>{@code features.recall.enabled} -- there is no {@code /recall} command to switch.</li>
 * </ul>
 * Removing a key from the code does not remove it from an operator's file: the framework writes a
 * missing key's declared default into the file on first load and never deletes a key it no longer
 * declares, so every server that has run this module still carries both. These cases pin the
 * warning that tells that operator, at start-up and again on {@code /ul reload}, and they pin that
 * a fresh install no longer gets either key written into its file.
 *
 * <h2>Controls</h2>
 * A check that never fires and a server with nothing left over look identical in the log, so the
 * positive cases sit beside two controls: a file with no removed key produces no warning at all, and
 * a file holding {@code features.wild.enabled} -- a key this module still reads, in the same section
 * as the removed cooldown -- produces none either, so the check tells "this key is in your file"
 * apart from "this key is in your file and is dead".
 * <p>
 * 两个从未生效的配置项在 6.3.0 删除；运维文件里残留的旧键在启动和重载时各报一条警告。
 */
@DisplayName("Removed configuration keys (UltiKits/UltiEssentials#27)")
class UltiEssentialsRemovedConfigKeyTest {

    private static final String CONFIG_FILE = "config/essentials.yml";
    private static final String WILD_COOLDOWN = "features.wild.cooldown";
    private static final String RECALL_ENABLED = "features.recall.enabled";

    @TempDir
    Path moduleFolder;

    private File configFile;
    private UltiEssentials plugin;
    private ConfigManager configManager;
    private EssentialsConfig config;
    private PluginLogger logger;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        configFile = moduleFolder.resolve("config").resolve("essentials.yml").toFile();
        assertThat(configFile.getParentFile().mkdirs()).isTrue();
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    // ==================== the declaration ====================

    @Test
    @DisplayName("EssentialsConfig declares neither removed key")
    void configDeclaresNeitherRemovedKey() {
        List<String> declared = new ArrayList<>();
        for (Field field : EssentialsConfig.class.getDeclaredFields()) {
            ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
            if (entry != null) {
                declared.add(entry.path());
            }
        }
        // Control: the reflection reads the real declarations -- a key that stays is found.
        assertThat(declared).contains("features.wild.enabled", "features.ban.broadcast-ban");
        assertThat(declared).doesNotContain(WILD_COOLDOWN, RECALL_ENABLED);
    }

    @Test
    @DisplayName("A fresh install's file gets neither removed key written into it")
    void freshInstallFileCarriesNeitherRemovedKey() throws Exception {
        boot("");

        YamlConfiguration written = YamlConfiguration.loadConfiguration(configFile);
        // Control: the framework did write the declared defaults into the empty file.
        assertThat(written.contains("features.wild.enabled")).isTrue();
        assertThat(written.contains(WILD_COOLDOWN)).isFalse();
        assertThat(written.contains(RECALL_ENABLED)).isFalse();
    }

    @Test
    @DisplayName("/wild's cooldown is the fixed 60 seconds the documentation now states")
    void wildCooldownIsFixedAtSixtySeconds() throws Exception {
        Method wild = WildCommand.class.getMethod("wildTeleport", Player.class);
        CmdCD cooldown = wild.getAnnotation(CmdCD.class);

        assertThat(cooldown).isNotNull();
        assertThat(cooldown.value()).isEqualTo(60);
    }

    // ==================== the residual-key warning at start-up ====================

    @Test
    @DisplayName("Start-up names features.wild.cooldown when it is still in the operator's file")
    void startUpWarnsAboutResidualWildCooldown() throws Exception {
        boot("features:\n  wild:\n    cooldown: 5\n");

        assertOneWarningNaming(startUpWarnings(), WILD_COOLDOWN, "UltiTools-Reborn#531");
    }

    @Test
    @DisplayName("Start-up names features.recall.enabled when it is still in the operator's file")
    void startUpWarnsAboutResidualRecallSwitch() throws Exception {
        boot("features:\n  recall:\n    enabled: false\n");

        assertOneWarningNaming(startUpWarnings(), RECALL_ENABLED, "UltiEssentials#53");
    }

    @Test
    @DisplayName("Start-up warns once per residual key when both are left in the file")
    void startUpWarnsOncePerResidualKey() throws Exception {
        boot("features:\n  wild:\n    cooldown: 5\n  recall:\n    enabled: true\n");

        List<String> warnings = startUpWarnings();

        assertThat(warnings).hasSize(2);
        assertThat(warnings).filteredOn(line -> line.contains(WILD_COOLDOWN)).hasSize(1);
        assertThat(warnings).filteredOn(line -> line.contains(RECALL_ENABLED)).hasSize(1);
    }

    // ==================== the residual-key warning on /ul reload ====================

    @Test
    @DisplayName("/ul reload names a removed key an operator put back into the file")
    void reloadWarnsAboutAResidualKeyAddedSinceStartUp() throws Exception {
        boot("");
        assertThat(startUpWarnings()).isEmpty();

        write("features:\n  wild:\n    cooldown: 5\n");
        configManager.reloadConfigs(plugin);
        List<String> warnings = warningsDuring(() -> invokeOnReload(plugin));

        assertOneWarningNaming(warnings, WILD_COOLDOWN, "UltiTools-Reborn#531");
    }

    @Test
    @DisplayName("Start-up says the file was not checked, rather than staying silent, when the configuration cannot be read")
    void startUpSaysSoWhenTheConfigurationCannotBeRead() throws Exception {
        boot("features:\n  wild:\n    cooldown: 5\n");
        plugin.setContext(containerWith(null));

        List<String> warnings = startUpWarnings();

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("UltiEssentials")
                .contains(CONFIG_FILE)
                .contains("could not read");
    }

    // ==================== controls ====================

    @Test
    @DisplayName("Control: a file with no removed key left in it produces no warning at start-up or reload")
    void warnsNothingWhenNoRemovedKeyIsLeft() throws Exception {
        boot("");

        assertThat(startUpWarnings()).isEmpty();
        assertThat(warningsDuring(() -> invokeOnReload(plugin))).isEmpty();
    }

    @Test
    @DisplayName("Control: a key this module still reads is not reported as removed")
    void doesNotWarnAboutAKeyThatIsStillDeclared() throws Exception {
        boot("features:\n  wild:\n    enabled: false\n  ban:\n    broadcast-ban: false\n");

        assertThat(startUpWarnings()).isEmpty();
    }

    // ==================== helpers ====================

    private static void assertOneWarningNaming(List<String> warnings, String key, String whereItWent) {
        assertThat(warnings)
                .withFailMessage("expected exactly one warning naming '%s', got %s", key, warnings)
                .hasSize(1);
        assertThat(warnings.get(0))
                .contains("UltiEssentials")
                .contains(CONFIG_FILE)
                .contains(key)
                .contains(whereItWent);
    }

    /**
     * Loads {@code config/essentials.yml} with the given content through the framework's real
     * {@link ConfigManager}, exactly as a server start does, and registers the loaded configuration
     * in the module's container. The start-up data repair and the three services a reload restarts
     * are present as no-op beans, so the only warnings start-up or a reload can log are the ones
     * under test.
     */
    private void boot(String yaml) throws Exception {
        write(yaml);
        plugin = mock(UltiEssentials.class, CALLS_REAL_METHODS);
        setResourceFolderPath(plugin, moduleFolder.toString());
        logger = mock(PluginLogger.class);
        doReturn(logger).when(plugin).getLogger();
        doAnswer(inv -> inv.getArgument(0)).when(plugin).i18n(anyString());

        config = new EssentialsConfig();
        configManager = new ConfigManager();
        configManager.register(plugin, config);

        plugin.setContext(containerWith(config));
    }

    /**
     * The module's container with the given configuration bean ({@code null} for none), plus the
     * start-up data repair and the three services a reload restarts as no-op beans.
     */
    private static SimpleContainer containerWith(EssentialsConfig configBean) {
        SimpleContainer container = new SimpleContainer();
        if (configBean != null) {
            container.registerType(EssentialsConfig.class, configBean);
        }
        container.registerType(EntityIdBackfillService.class, mock(EntityIdBackfillService.class));
        // The three services onReload() restarts, as no-ops, so a reload logs no "could not reach"
        // warning of its own and the only warnings left are the ones under test.
        container.registerType(ScheduledCommandService.class, mock(ScheduledCommandService.class));
        container.registerType(ScoreboardService.class, mock(ScoreboardService.class));
        container.registerType(NamePrefixService.class, mock(NamePrefixService.class));
        return container;
    }

    private List<String> startUpWarnings() throws Exception {
        return warningsDuring(() -> assertThat(plugin.registerSelf()).isTrue());
    }

    private List<String> warningsDuring(ThrowingRunnable action) throws Exception {
        org.mockito.Mockito.clearInvocations(logger);
        action.run();
        ArgumentCaptor<String> warned = ArgumentCaptor.forClass(String.class);
        verify(logger, atLeast(0)).warn(warned.capture());
        return new ArrayList<>(warned.getAllValues());
    }

    private void write(String content) throws Exception {
        Files.write(configFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    // onReload() is protected in the framework's package; reloadSelf() calls it virtually, and so
    // does this reflective call, so it reaches whatever this module declares.
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // invokes the protected framework hook as reloadSelf() does
    private static void invokeOnReload(UltiToolsPlugin plugin) throws Exception {
        Method hook = UltiToolsPlugin.class.getDeclaredMethod("onReload");
        hook.setAccessible(true);
        hook.invoke(plugin);
    }

    // No supported setter exists: the field is private, its accessors are protected final, and the
    // only public constructor that takes the path also builds the language catalogue from disk.
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    private static void setResourceFolderPath(UltiToolsPlugin plugin, String path) throws Exception {
        Field field = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        field.setAccessible(true);
        field.set(plugin, path);
    }
}
