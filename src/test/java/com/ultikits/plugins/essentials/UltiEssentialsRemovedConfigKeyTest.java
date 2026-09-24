package com.ultikits.plugins.essentials;

import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.TabBarConfig;
import com.ultikits.plugins.essentials.service.EntityIdBackfillService;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScheduledCommandService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;
import com.ultikits.ultitools.manager.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
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
 * {@code features.recall.enabled} in {@code config/essentials.yml} never took effect -- there is no
 * {@code /recall} command to switch -- and is removed in 6.3.0 (UltiKits/UltiEssentials#27; the
 * command is feature request UltiKits/UltiEssentials#53).
 * <p>
 * {@code features.wild.cooldown}, the other key #27 found unread, is <em>not</em> removed: it is
 * bound to {@code /wild}'s cooldown through the framework's config-bound {@code @CmdCD}
 * (UltiKits/UltiTools-Reborn#531), so it is a live key and must never be reported as removed. Its
 * behaviour is pinned by {@code WildCooldownBindingTest}.
 * <p>
 * Removing a key from the code does not remove it from an operator's file: the framework writes a
 * missing key's declared default into the file on first load and never deletes a key it no longer
 * declares, so every server that has run this module still carries the recall switch. These cases
 * pin the warning that tells that operator, at start-up and again on {@code /ul reload}, and they
 * pin that a fresh install no longer gets it written into its file.
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
    @DisplayName("EssentialsConfig declares the live wild cooldown and not the removed recall switch")
    void configDeclaresWildCooldownButNotRecall() {
        List<String> declared = new ArrayList<>();
        for (Field field : EssentialsConfig.class.getDeclaredFields()) {
            ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
            if (entry != null) {
                declared.add(entry.path());
            }
        }
        // Control: the reflection reads the real declarations -- a key that stays is found.
        assertThat(declared).contains("features.wild.enabled", "features.ban.broadcast-ban");
        assertThat(declared).contains(WILD_COOLDOWN);
        assertThat(declared).doesNotContain(RECALL_ENABLED);
    }

    @Test
    @DisplayName("A fresh install's file gets the wild cooldown but not the removed recall switch")
    void freshInstallFileCarriesWildCooldownButNotRecall() throws Exception {
        boot("");

        YamlConfiguration written = YamlConfiguration.loadConfiguration(configFile);
        // Control: the framework did write the declared defaults into the empty file.
        assertThat(written.contains("features.wild.enabled")).isTrue();
        assertThat(written.contains(WILD_COOLDOWN)).isTrue();
        assertThat(written.contains(RECALL_ENABLED)).isFalse();
    }

    // ==================== the residual-key warning at start-up ====================

    @Test
    @DisplayName("Start-up does not report features.wild.cooldown: it is a live, bound key")
    void startUpDoesNotReportTheLiveWildCooldown() throws Exception {
        boot("features:\n  wild:\n    cooldown: 5\n");

        assertThat(startUpWarnings()).isEmpty();
    }

    @Test
    @DisplayName("Start-up names features.recall.enabled when it is still in the operator's file")
    void startUpWarnsAboutResidualRecallSwitch() throws Exception {
        boot("features:\n  recall:\n    enabled: false\n");

        assertOneWarningNaming(startUpWarnings(), RECALL_ENABLED, "UltiEssentials#53");
    }

    @Test
    @DisplayName("With the recall switch and the wild cooldown both in the file, only the recall switch is reported, once")
    void startUpReportsOnlyTheRemovedKey() throws Exception {
        boot("features:\n  wild:\n    cooldown: 5\n  recall:\n    enabled: true\n");

        List<String> warnings = startUpWarnings();

        assertThat(warnings).hasSize(1);
        assertThat(warnings).filteredOn(line -> line.contains(RECALL_ENABLED)).hasSize(1);
        assertThat(warnings).filteredOn(line -> line.contains(WILD_COOLDOWN)).isEmpty();
    }

    // ==================== the residual-key warning on /ul reload ====================

    @Test
    @DisplayName("/ul reload names a removed key an operator put back into the file")
    void reloadWarnsAboutAResidualKeyAddedSinceStartUp() throws Exception {
        boot("");
        assertThat(startUpWarnings()).isEmpty();

        write("features:\n  recall:\n    enabled: false\n");
        configManager.reloadConfigs(plugin);
        List<String> warnings = warningsDuring(() -> invokeOnReload(plugin));

        assertOneWarningNaming(warnings, RECALL_ENABLED, "UltiEssentials#53");
    }

    @Test
    @DisplayName("Start-up says the file was not checked, rather than staying silent, when the configuration cannot be read")
    void startUpSaysSoWhenTheConfigurationCannotBeRead() throws Exception {
        boot("features:\n  recall:\n    enabled: false\n");
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

    // ==================== legacy text defaults (maintainer ruling 2026-09-24 (d)) ====================

    @Test
    @DisplayName("Start-up blanks a scoreboard title still at an earlier version's shipped default, and saves it")
    void startUpBlanksTheShippedScoreboardTitle() throws Exception {
        boot("features:\n  scoreboard:\n    title: \"&6&l\u670d\u52a1\u5668\u4fe1\u606f\"\n");

        assertThat(startUpWarnings()).isEmpty();

        assertThat(config.getScoreboardTitle()).isEmpty();
        assertThat(YamlConfiguration.loadConfiguration(configFile).getString("features.scoreboard.title")).isEmpty();
    }

    @Test
    @DisplayName("Start-up keeps a customised scoreboard title, in memory and in the file")
    void startUpKeepsACustomisedScoreboardTitle() throws Exception {
        boot("features:\n  scoreboard:\n    title: \"&bMy Server\"\n");

        assertThat(startUpWarnings()).isEmpty();

        assertThat(config.getScoreboardTitle()).isEqualTo("&bMy Server");
        assertThat(YamlConfiguration.loadConfiguration(configFile).getString("features.scoreboard.title"))
                .isEqualTo("&bMy Server");
    }

    @Test
    @DisplayName("Start-up blanks scoreboard lines still at the list earlier versions shipped, and saves them")
    void startUpBlanksTheShippedScoreboardLines() throws Exception {
        boot("features:\n  scoreboard:\n    lines:\n"
                + "    - \"&7\u6b22\u8fce, &e%player_name%\"\n    - \"&7\"\n"
                + "    - \"&6\u5728\u7ebf\u73a9\u5bb6: &f%online_players%/%max_players%\"\n"
                + "    - \"&6\u5f53\u524d\u4e16\u754c: &f%player_world%\"\n    - \"&7\"\n"
                + "    - \"&6\u751f\u547d\u503c: &c%player_health%\"\n    - \"&6\u9965\u997f\u503c: &a%player_food%\"\n"
                + "    - \"&6\u7b49\u7ea7: &e%player_level%\"\n    - \"&7\"\n    - \"&ewww.example.com\"\n");

        assertThat(startUpWarnings()).isEmpty();

        assertThat(config.getScoreboardLines()).isEmpty();
        assertThat(YamlConfiguration.loadConfiguration(configFile).getStringList("features.scoreboard.lines")).isEmpty();
    }

    @Test
    @DisplayName("Start-up blanks a tab-list header and footer still at the shipped default, and saves tabbar.yml")
    void startUpBlanksTheShippedTabBarText() throws Exception {
        File tabBarFile = moduleFolder.resolve("config").resolve("tabbar.yml").toFile();
        Files.write(tabBarFile.toPath(), ("tabbar:\n  header: \"&6=== \u670d\u52a1\u5668\u540d\u79f0 ===\"\n"
                + "  footer: \"&7\u5728\u7ebf: &e%online%&7/&e%max%\"\n").getBytes(StandardCharsets.UTF_8));
        boot("features:\n  scoreboard:\n    title: \"&bMy Server\"\n");
        TabBarConfig tabBar = new TabBarConfig();
        configManager.register(plugin, tabBar);
        plugin.getContext().registerType(TabBarConfig.class, tabBar);

        assertThat(startUpWarnings()).isEmpty();

        assertThat(tabBar.getHeader()).isEmpty();
        assertThat(tabBar.getFooter()).isEmpty();
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(tabBarFile);
        assertThat(saved.getString("tabbar.header")).isEmpty();
        assertThat(saved.getString("tabbar.footer")).isEmpty();
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
        doAnswer(CatalogueText.answer("en")).when(plugin).i18n(anyString());

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
