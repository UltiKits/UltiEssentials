package com.ultikits.plugins.essentials;

import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.config.TabBarConfig;
import com.ultikits.plugins.essentials.service.EntityIdBackfillService;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScheduledCommandService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.plugins.essentials.utils.TestHelper;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.ReloadReport;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.exceptions.ConfigurationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Recall's former removed-key warning must disappear when #53 makes the switch live again.
 * Exercises real framework file loading and module start/reload, preserving legacy values.
 */
@DisplayName("Live recall configuration and former removed-key warnings (#53)")
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
    private Object previousUltiTools;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        previousUltiTools = ultiToolsField().get(null);
        configFile = moduleFolder.resolve("config").resolve("essentials.yml").toFile();
        assertThat(configFile.getParentFile().mkdirs()).isTrue();
    }

    @AfterEach
    void tearDown() throws Exception {
        ultiToolsField().set(null, previousUltiTools);
        EssentialsTestHelper.tearDown();
    }

    private static Field ultiToolsField() throws Exception {
        Field field = UltiTools.class.getDeclaredField("ultiTools");
        field.setAccessible(true);
        return field;
    }

    // ==================== the declaration ====================

    @Test
    @DisplayName("EssentialsConfig declares both the live wild cooldown and restored recall switch")
    void configDeclaresWildCooldownAndRecall() {
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
        assertThat(declared).contains(RECALL_ENABLED);
    }

    @Test
    @DisplayName("A fresh install gets both live keys and recall defaults to true")
    void freshInstallFileCarriesWildCooldownAndRecall() throws Exception {
        boot("");

        YamlConfiguration written = YamlConfiguration.loadConfiguration(configFile);
        // Control: the framework did write the declared defaults into the empty file.
        assertThat(written.contains("features.wild.enabled")).isTrue();
        assertThat(written.contains(WILD_COOLDOWN)).isTrue();
        assertThat(written.contains(RECALL_ENABLED)).isTrue();
        assertThat(written.getBoolean(RECALL_ENABLED)).isTrue();
    }

    // ==================== the residual-key warning at start-up ====================

    @Test
    @DisplayName("Start-up does not report features.wild.cooldown: it is a live, bound key")
    void startUpDoesNotReportTheLiveWildCooldown() throws Exception {
        boot("features:\n  wild:\n    cooldown: 5\n");

        assertThat(startUpWarnings()).isEmpty();
    }

    @Test
    @DisplayName("Start-up preserves legacy false without a removed-key warning")
    void startUpPreservesLegacyRecallFalse() throws Exception {
        boot("features:\n  recall:\n    enabled: false\n");

        assertThat(startUpWarnings()).isEmpty();
        assertThat(config.isRecallEnabled()).isFalse();
        assertThat(YamlConfiguration.loadConfiguration(configFile).getBoolean(RECALL_ENABLED)).isFalse();
    }

    @Test
    @DisplayName("Both live switches in the file produce no removed-key warning")
    void startUpReportsNeitherLiveKey() throws Exception {
        boot("features:\n  wild:\n    cooldown: 5\n  recall:\n    enabled: true\n");

        assertThat(startUpWarnings()).isEmpty();
        assertThat(YamlConfiguration.loadConfiguration(configFile).getBoolean(RECALL_ENABLED)).isTrue();
    }

    // ==================== the residual-key warning on /ul reload ====================

    @Test
    @DisplayName("/ul reload reads restored recall false without a removed-key warning")
    void reloadReadsLiveRecallSwitch() throws Exception {
        boot("");
        assertThat(startUpWarnings()).isEmpty();

        write("features:\n  recall:\n    enabled: false\n");
        configManager.reloadConfigs(plugin);
        List<String> warnings = warningsDuring(() -> invokeOnReload(plugin));

        assertThat(warnings).isEmpty();
        assertThat(YamlConfiguration.loadConfiguration(configFile).getBoolean(RECALL_ENABLED)).isFalse();
        assertThat(config.isRecallEnabled()).isFalse();
    }

    @Test
    void reloadChangesTheAlreadyConstructedRecallCommandWithoutRewritingLegacyFalse() throws Exception {
        boot("features:\n  recall:\n    enabled: false # operator choice\n");
        com.ultikits.plugins.essentials.commands.RecallCommand command =
                new com.ultikits.plugins.essentials.commands.RecallCommand(config);
        Field field = com.ultikits.plugins.essentials.commands.BaseEssentialsCommand.class.getDeclaredField("plugin");
        field.setAccessible(true);
        field.set(command, plugin);
        org.bukkit.entity.Player sender = mock(org.bukkit.entity.Player.class);
        command.recall(sender, "Nobody");
        verify(sender).sendMessage("This feature is disabled");
        assertThat(new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8))
                .contains("enabled: false # operator choice");

        write("features:\n  recall:\n    enabled: true\n");
        configManager.reloadConfigs(plugin);
        org.mockito.Mockito.clearInvocations(sender);
        command.recall(sender, "Nobody");
        verify(sender).sendMessage("Player not found or offline");
        verify(sender, org.mockito.Mockito.never()).sendMessage("This feature is disabled");

        write("features:\n  recall:\n    enabled: false\n");
        configManager.reloadConfigs(plugin);
        org.mockito.Mockito.clearInvocations(sender);
        command.recall(sender, "Nobody");
        verify(sender).sendMessage("This feature is disabled");
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

    // ==================== presence read through the framework (UltiKits/UltiEssentials#68) ====================

    @Test
    @DisplayName("#53: null recall value is not falsely reported as a removed key")
    void nullRecallDoesNotProduceRemovedKeyWarning() throws Exception {
        boot("features:\n  recall:\n    enabled:\n");

        assertThat(startUpWarnings()).isEmpty();
    }

    @Test
    @DisplayName("#68: a file the framework could not parse reports no removed key -- the framework already logs that it could not load the file")
    void anUnparseableFileReportsNoRemovedKey() throws Exception {
        boot("features:\n  recall:\n    enabled: [unclosed\n");

        assertThat(startUpWarnings()).filteredOn(line -> line.contains(RECALL_ENABLED)).isEmpty();
    }

    @Test
    @DisplayName("#68: a file the server cannot read reports no removed key")
    void anUnreadableFileReportsNoRemovedKey() throws Exception {
        write("features:\n  recall:\n    enabled: false\n");
        org.junit.jupiter.api.Assumptions.assumeTrue(configFile.setReadable(false, false) && !configFile.canRead(),
                "the file system lets this user make the file unreadable");
        try {
            bootWithoutWriting("en");

            assertThat(startUpWarnings()).filteredOn(line -> line.contains(RECALL_ENABLED)).isEmpty();
        } finally {
            assertThat(configFile.setReadable(true, false)).isTrue();
        }
    }

    @Test
    @DisplayName("#53: adding the now-live recall key between reloads never reports it removed")
    void liveRecallRestoredBetweenReloadsIsNotReported() throws Exception {
        boot("");
        assertThat(startUpWarnings()).isEmpty();

        configManager.reloadConfigs(plugin);
        assertThat(warningsDuring(() -> invokeOnReload(plugin))).as("first reload, clean file").isEmpty();

        write("features:\n  recall:\n    enabled: true\n");
        configManager.reloadConfigs(plugin);
        assertThat(warningsDuring(() -> invokeOnReload(plugin))).isEmpty();

        write("features:\n  wild:\n    enabled: true\n");
        configManager.reloadConfigs(plugin);
        assertThat(warningsDuring(() -> invokeOnReload(plugin))).as("third reload, key removed again").isEmpty();
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

    // ==================== config text materializer (maintainer decision 2026-09-25, UltiKits/UltiEssentials#26) ====================

    @Test
    @DisplayName("Start-up rewrites a scoreboard title still at an earlier version's shipped default, in the server's language, and saves it")
    void startUpMaterializesTheShippedScoreboardTitle() throws Exception {
        boot("features:\n  scoreboard:\n    title: \"&6&l\u670d\u52a1\u5668\u4fe1\u606f\"\n", "en");

        assertThat(startUpWarnings()).isEmpty();

        assertThat(config.getScoreboardTitle()).isEqualTo("&6&lServer Info");
        assertThat(YamlConfiguration.loadConfiguration(configFile).getString("features.scoreboard.title"))
                .isEqualTo("&6&lServer Info");
    }

    @Test
    @DisplayName("Start-up keeps a customised scoreboard title, in memory and in the file")
    void startUpKeepsACustomisedScoreboardTitle() throws Exception {
        boot("features:\n  scoreboard:\n    title: \"&bMy Server\"\n", "en");

        assertThat(startUpWarnings()).isEmpty();

        assertThat(config.getScoreboardTitle()).isEqualTo("&bMy Server");
        assertThat(YamlConfiguration.loadConfiguration(configFile).getString("features.scoreboard.title"))
                .isEqualTo("&bMy Server");
    }

    @Test
    @DisplayName("A blank scoreboard title is refused by the framework before this module runs (@NotEmpty), exactly as every released version")
    void blankScoreboardTitleIsRefusedByTheFramework() {
        assertThatThrownBy(() -> boot("features:\n  scoreboard:\n    title: \"\"\n", "en"))
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("scoreboardTitle");
    }

    @Test
    @DisplayName("Start-up rewrites scoreboard lines still at the list earlier versions shipped, in the server's language, and saves them")
    void startUpMaterializesTheShippedScoreboardLines() throws Exception {
        boot("features:\n  scoreboard:\n    lines:\n"
                + "    - \"&7\u6b22\u8fce, &e%player_name%\"\n    - \"&7\"\n"
                + "    - \"&6\u5728\u7ebf\u73a9\u5bb6: &f%online_players%/%max_players%\"\n"
                + "    - \"&6\u5f53\u524d\u4e16\u754c: &f%player_world%\"\n    - \"&7\"\n"
                + "    - \"&6\u751f\u547d\u503c: &c%player_health%\"\n    - \"&6\u9965\u997f\u503c: &a%player_food%\"\n"
                + "    - \"&6\u7b49\u7ea7: &e%player_level%\"\n    - \"&7\"\n    - \"&ewww.example.com\"\n", "en");

        assertThat(startUpWarnings()).isEmpty();

        List<String> expected = java.util.Arrays.asList(
                "&7Welcome, &e%player_name%", "&7", "&6Online: &f%online_players%/%max_players%",
                "&6World: &f%player_world%", "&7", "&6Health: &c%player_health%", "&6Food: &a%player_food%",
                "&6Level: &e%player_level%", "&7", "&ewww.example.com");
        assertThat(config.getScoreboardLines()).containsExactlyElementsOf(expected);
        assertThat(YamlConfiguration.loadConfiguration(configFile).getStringList("features.scoreboard.lines"))
                .containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("An empty scoreboard-lines list is kept empty, exactly as at origin/master: never materialized")
    void emptyScoreboardLinesAreNeverMaterialized() throws Exception {
        boot("features:\n  scoreboard:\n    lines: []\n", "en");

        assertThat(startUpWarnings()).isEmpty();

        assertThat(config.getScoreboardLines()).isEmpty();
        assertThat(YamlConfiguration.loadConfiguration(configFile).getStringList("features.scoreboard.lines")).isEmpty();
    }

    @Test
    @DisplayName("Start-up rewrites a tab-list header and footer still at the shipped default, in the server's language, and saves tabbar.yml")
    void startUpMaterializesTheShippedTabBarText() throws Exception {
        File tabBarFile = moduleFolder.resolve("config").resolve("tabbar.yml").toFile();
        Files.write(tabBarFile.toPath(), ("tabbar:\n  header: \"&6=== \u670d\u52a1\u5668\u540d\u79f0 ===\"\n"
                + "  footer: \"&7\u5728\u7ebf: &e%online%&7/&e%max%\"\n").getBytes(StandardCharsets.UTF_8));
        boot("features:\n  scoreboard:\n    title: \"&bMy Server\"\n", "en");
        TabBarConfig tabBar = new TabBarConfig();
        configManager.register(plugin, tabBar);
        plugin.getContext().registerType(TabBarConfig.class, tabBar);

        assertThat(startUpWarnings()).isEmpty();

        assertThat(tabBar.getHeader()).isEqualTo("&6=== Server Name ===");
        assertThat(tabBar.getFooter()).isEqualTo("&7Online: &e%online%&7/&e%max%");
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(tabBarFile);
        assertThat(saved.getString("tabbar.header")).isEqualTo("&6=== Server Name ===");
        assertThat(saved.getString("tabbar.footer")).isEqualTo("&7Online: &e%online%&7/&e%max%");
    }

    @Test
    @DisplayName("A blank tab-list header and footer are kept blank, exactly as at origin/master: never materialized (neither field carries @NotEmpty)")
    void blankTabBarTextIsNeverMaterialized() throws Exception {
        File tabBarFile = moduleFolder.resolve("config").resolve("tabbar.yml").toFile();
        Files.write(tabBarFile.toPath(), "tabbar:\n  header: \"\"\n  footer: \"\"\n".getBytes(StandardCharsets.UTF_8));
        boot("", "en");
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

    @Test
    @DisplayName("Start-up rewrites the scoreboard title in Chinese under language: zh, and saves nothing when it is already the Chinese shipped default")
    void startUpMaterializesInChineseUnderZh() throws Exception {
        boot("", "zh");

        assertThat(startUpWarnings()).isEmpty();

        // A fresh install's Java default is the Chinese shipped text (the last version shipped it in
        // Chinese), so under language: zh nothing changes and the framework's own first-start write
        // already matches -- no second save.
        assertThat(config.getScoreboardTitle()).isEqualTo("&6&l\u670d\u52a1\u5668\u4fe1\u606f");
        assertThat(YamlConfiguration.loadConfiguration(configFile).getString("features.scoreboard.title"))
                .isEqualTo("&6&l\u670d\u52a1\u5668\u4fe1\u606f");
    }

    @Test
    @DisplayName("A second start-up materializes nothing: the file already holds the current-language text")
    void aSecondStartUpMaterializesNothing() throws Exception {
        boot("features:\n  scoreboard:\n    title: \"&6&l\u670d\u52a1\u5668\u4fe1\u606f\"\n", "en");

        assertThat(startUpWarnings()).isEmpty();
        assertThat(config.getScoreboardTitle()).isEqualTo("&6&lServer Info");

        // registerSelf() again, against the file this module itself just wrote.
        assertThat(startUpWarnings()).isEmpty();

        assertThat(config.getScoreboardTitle()).isEqualTo("&6&lServer Info");
    }

    // ==================== helpers ====================

    /**
     * {@link #boot(String, String)} under {@code language: en}, the framework's default -- every
     * existing case in this file (removed-key warnings, wild cooldown) is language-independent, and the
     * config text materializer's own cases pass their language explicitly.
     */
    private void boot(String yaml) throws Exception {
        boot(yaml, "en");
    }

    /**
     * Loads {@code config/essentials.yml} with the given content through the framework's real
     * {@link ConfigManager}, exactly as a server start does, and registers the loaded configuration
     * in the module's container. The start-up data repair and the three services a reload restarts
     * are present as no-op beans, so the only warnings start-up or a reload can log are the ones
     * under test. {@code language} is what {@code registerSelf()}'s config-text materializer
     * ({@code UltiToolsPlugin#getLanguageCode()}) reads from the framework's own {@code config.yml}.
     */
    private void boot(String yaml, String language) throws Exception {
        write(yaml);
        bootWithoutWriting(language);
    }

    /** {@link #boot(String, String)} over whatever {@code config/essentials.yml} already holds. */
    private void bootWithoutWriting(String language) throws Exception {
        plugin = mock(UltiEssentials.class, CALLS_REAL_METHODS);
        setResourceFolderPath(plugin, moduleFolder.toString());
        logger = mock(PluginLogger.class);
        doReturn(logger).when(plugin).getLogger();
        doAnswer(CatalogueText.answer("en")).when(plugin).i18n(anyString());

        TestHelper.mockUltiToolsInstance();
        YamlConfiguration frameworkConfig = new YamlConfiguration();
        frameworkConfig.set("language", language);
        lenient().when(UltiTools.getInstance().getConfig()).thenReturn(frameworkConfig);

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

    // onReload(ReloadReport) is protected in the framework's package; reloadSelf() calls it virtually
    // with a fresh report, and so does this reflective call, so it reaches whatever this module
    // declares (UltiKits/UltiEssentials#66).
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // invokes the protected framework hook as reloadSelf() does
    private static void invokeOnReload(UltiToolsPlugin plugin) throws Exception {
        Method hook = UltiToolsPlugin.class.getDeclaredMethod("onReload", ReloadReport.class);
        hook.setAccessible(true);
        hook.invoke(plugin, new ReloadReport());
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
