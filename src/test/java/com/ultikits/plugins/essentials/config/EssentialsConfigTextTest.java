package com.ultikits.plugins.essentials.config;

import com.ultikits.plugins.essentials.UltiEssentials;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.listener.DeathPunishListener;
import com.ultikits.plugins.essentials.listener.MotdListener;
import com.ultikits.plugins.essentials.listener.TabBarListener;
import com.ultikits.plugins.essentials.service.EntityIdBackfillService;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScheduledCommandService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.ultitools.annotations.ConfigEntry;
import com.ultikits.ultitools.annotations.config.NotEmpty;
import com.ultikits.ultitools.exceptions.ConfigurationException;
import com.ultikits.ultitools.interfaces.ConfigChangeListener;
import com.ultikits.ultitools.interfaces.impl.logger.PluginLogger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@code config/essentials.yml}, {@code config/tabbar.yml} and {@code config/motd.yml} hold their
 * built-in text in the server's language, and the module reads exactly what the file holds
 * (maintainer decision 2026-09-25; UltiKits/UltiEssentials#26). A value that is still built-in text
 * -- any language's text from this jar, or the default an earlier version shipped -- follows
 * {@code language} at enable and on reload, in both directions; anything else is the operator's and
 * is kept byte for byte. A blank {@code scoreboard.title} is refused by the framework
 * ({@code @NotEmpty}); a blank {@code scoreboard.lines}, {@code tabbar.header} or
 * {@code tabbar.footer} is kept blank, exactly as at {@code origin/master}. Every case runs the
 * framework's real {@code AbstractConfigEntity#init} on a temporary folder, the module's real
 * {@code registerSelf()} and {@code onReload()}, and answers {@code i18n}/{@code getLocalizedText}
 * from the module's real catalogues.
 */
@DisplayName("essentials.yml, tabbar.yml, motd.yml hold their built-in text in the server's language (UltiKits/UltiEssentials#26)")
class EssentialsConfigTextTest {

    private static final String[] LANGUAGES = {"en", "zh"};

    /** The title every earlier version shipped; the Java default and the zh catalogue text, byte for byte. */
    private static final String SHIPPED_TITLE_ZH = "&6&l服务器信息";

    /** The lines every earlier version shipped; the Java default and the zh catalogue text, byte for byte. */
    private static final List<String> SHIPPED_LINES_ZH = Arrays.asList(
            "&7欢迎, &e%player_name%", "&7", "&6在线玩家: &f%online_players%/%max_players%",
            "&6当前世界: &f%player_world%", "&7", "&6生命值: &c%player_health%",
            "&6饥饿值: &a%player_food%", "&6等级: &e%player_level%", "&7", "&ewww.example.com");

    /** The header/footer every earlier version shipped; the Java default and the zh catalogue text, byte for byte. */
    private static final String SHIPPED_HEADER_ZH = "&6=== 服务器名称 ===";
    private static final String SHIPPED_FOOTER_ZH = "&7在线: &e%online%&7/&e%max%";

    /** The scheduled commands every earlier version shipped; the Java default and the en catalogue text, byte for byte. */
    private static final List<String> SHIPPED_SCHEDULED_EN = Arrays.asList(
            "300:say Server is online!", "600:broadcast &cReminder: follow server rules!");

    /** The death-punishment command every earlier version shipped; the Java default and the zh catalogue text, byte for byte. */
    private static final List<String> SHIPPED_DEATHPUNISH_ZH = java.util.Collections.singletonList("say {PLAYER} 死亡了!");

    /** The MOTD lines every earlier version shipped; the Java default and the en catalogue text, byte for byte. */
    private static final String SHIPPED_MOTD_LINE1_EN = "&6Welcome to our server!";
    private static final String SHIPPED_MOTD_LINE2_EN = "&7Powered by UltiTools";

    @TempDir
    Path tempDir;

    private final String[] language = {"en"};
    private final PluginLogger logger = mock(PluginLogger.class);

    /** Catalogue texts an operator changed in the extracted language file on disk, answered by i18n first. */
    private final Map<String, String> diskOverrides = new LinkedHashMap<>();

    private EssentialsConfig currentEssentials;
    private TabBarConfig currentTabBar;
    private MotdConfig currentMotd;
    private final EntityIdBackfillService backfillService = mock(EntityIdBackfillService.class);
    /** The scheduled-command service the module double's getBean returns; a test may put a real one here. */
    private ScheduledCommandService scheduledCommandServiceBean = mock(ScheduledCommandService.class);
    private final ScoreboardService scoreboardServiceBean = mock(ScoreboardService.class);
    private final NamePrefixService namePrefixServiceBean = mock(NamePrefixService.class);

    private UltiEssentials plugin;

    @BeforeEach
    void setUp() {
        plugin = moduleDouble();
    }

    @AfterEach
    void tearDown() {
        currentEssentials = null;
        currentTabBar = null;
        currentMotd = null;
    }

    // ================================================================== catalogue texts

    // Local literal copies of the catalogue keys, not references to EssentialsConfig/TabBarConfig/
    // MotdConfig's own key constants: this file must compile with only ConfigTextDefaults.java kept
    // at head (the revert proof restores the fix's production files only), and the three entities
    // are reverted to their pre-fix shape (no such constants) in that proof.
    private static final String SCOREBOARD_TITLE_KEY = "essentials.scoreboard.default_title";
    private static final String SCOREBOARD_LINES_KEY = "essentials.scoreboard.default_lines";
    private static final String SCHEDULED_COMMANDS_KEY = "essentials.scheduled-commands.default_commands";
    private static final String DEATHPUNISH_COMMANDS_KEY = "essentials.deathpunish.default_commands";
    private static final String TABBAR_HEADER_KEY = "essentials.tabbar.default_header";
    private static final String TABBAR_FOOTER_KEY = "essentials.tabbar.default_footer";
    private static final String MOTD_LINE1_KEY = "essentials.motd.default_line1";
    private static final String MOTD_LINE2_KEY = "essentials.motd.default_line2";

    private static String titleText(String code) {
        return CatalogueText.text(code, SCOREBOARD_TITLE_KEY);
    }

    private static List<String> linesText(String code) {
        return Arrays.asList(CatalogueText.text(code, SCOREBOARD_LINES_KEY).split("\n", -1));
    }

    private static List<String> scheduledText(String code) {
        return Arrays.asList(CatalogueText.text(code, SCHEDULED_COMMANDS_KEY).split("\n", -1));
    }

    private static List<String> deathpunishText(String code) {
        return Arrays.asList(CatalogueText.text(code, DEATHPUNISH_COMMANDS_KEY).split("\n", -1));
    }

    private static String headerText(String code) {
        return CatalogueText.text(code, TABBAR_HEADER_KEY);
    }

    private static String footerText(String code) {
        return CatalogueText.text(code, TABBAR_FOOTER_KEY);
    }

    private static String line1Text(String code) {
        return CatalogueText.text(code, MOTD_LINE1_KEY);
    }

    private static String line2Text(String code) {
        return CatalogueText.text(code, MOTD_LINE2_KEY);
    }

    /** The command text a "interval:command" scheduled-commands entry dispatches, as the service parses it. */
    private static String commandTextOf(String entry) {
        int colonIndex = entry.indexOf(':');
        return entry.substring(colonIndex + 1).trim();
    }

    @Test
    @DisplayName("control: the catalogues give the expected text for every key in both languages, matching the shipped defaults")
    void catalogueTextsMatchShippedDefaults() {
        assertThat(titleText("zh")).isEqualTo(SHIPPED_TITLE_ZH);
        assertThat(linesText("zh")).isEqualTo(SHIPPED_LINES_ZH);
        assertThat(headerText("zh")).isEqualTo(SHIPPED_HEADER_ZH);
        assertThat(footerText("zh")).isEqualTo(SHIPPED_FOOTER_ZH);
        assertThat(scheduledText("en")).isEqualTo(SHIPPED_SCHEDULED_EN);
        assertThat(deathpunishText("zh")).isEqualTo(SHIPPED_DEATHPUNISH_ZH);
        assertThat(line1Text("en")).isEqualTo(SHIPPED_MOTD_LINE1_EN);
        assertThat(line2Text("en")).isEqualTo(SHIPPED_MOTD_LINE2_EN);
        for (String code : LANGUAGES) {
            assertThat(titleText(code)).isNotEmpty();
            assertThat(headerText(code)).isNotEmpty();
            assertThat(scheduledText(code)).isNotEmpty();
            assertThat(deathpunishText(code)).isNotEmpty();
        }
    }

    // ================================================================== fresh start / upgrade

    @Nested
    @DisplayName("fresh start and upgrade")
    class FreshStartAndUpgrade {

        @Test
        @DisplayName("fresh start under en: essentials.yml, tabbar.yml and motd.yml hold every setting's English text, getters match the file")
        void freshStartEnglish() throws Exception {
            language[0] = "en";
            EssentialsConfig essentials = spy(loadEssentials());
            TabBarConfig tabBar = spy(loadTabBar());
            MotdConfig motd = spy(loadMotd());

            start(essentials, tabBar, motd);

            YamlConfiguration e = onDisk(essentialsFile());
            assertThat(e.getString("features.scoreboard.title")).isEqualTo(titleText("en"));
            assertThat(e.getStringList("features.scoreboard.lines")).isEqualTo(linesText("en"));
            assertThat(essentials.getScoreboardTitle()).isEqualTo(e.getString("features.scoreboard.title"));
            assertThat(essentials.getScoreboardLines()).isEqualTo(e.getStringList("features.scoreboard.lines"));
            // scheduled-commands and deathpunish-commands are already English at master; only the
            // deathpunish command (whose shipped default is Chinese) actually changes under en.
            assertThat(e.getStringList("features.deathpunish.command.commands")).isEqualTo(deathpunishText("en"));
            assertThat(essentials.getDeathPunishCommands()).isEqualTo(deathpunishText("en"));
            verify(essentials, times(1)).save();

            YamlConfiguration t = onDisk(tabBarFile());
            assertThat(t.getString("tabbar.header")).isEqualTo(headerText("en"));
            assertThat(t.getString("tabbar.footer")).isEqualTo(footerText("en"));
            assertThat(tabBar.getHeader()).isEqualTo(t.getString("tabbar.header"));
            assertThat(tabBar.getFooter()).isEqualTo(t.getString("tabbar.footer"));
            verify(tabBar, times(1)).save();

            // motd's shipped defaults are already English, and the fresh file the framework wrote
            // already equals the en catalogue text, so nothing changes and nothing is saved.
            YamlConfiguration m = onDisk(motdFile());
            assertThat(m.getString("motd.line1")).isEqualTo(line1Text("en"));
            assertThat(m.getString("motd.line2")).isEqualTo(line2Text("en"));
            verify(motd, never()).save();
        }

        @Test
        @DisplayName("fresh start under zh: files already hold the Chinese shipped text where the default was Chinese, and the module saves only the settings whose shipped default was English")
        void freshStartChinese() throws Exception {
            language[0] = "zh";
            EssentialsConfig essentials = spy(loadEssentials());
            TabBarConfig tabBar = spy(loadTabBar());
            MotdConfig motd = spy(loadMotd());
            byte[] essentialsAfterFramework = bytes(essentialsFile());
            byte[] tabBarAfterFramework = bytes(tabBarFile());

            start(essentials, tabBar, motd);

            // scoreboard title/lines and tabbar header/footer: shipped defaults were Chinese, so the
            // framework's own first-start write already matches -- no save.
            assertThat(bytes(essentialsFile())).isNotEqualTo(essentialsAfterFramework); // scheduled-commands still changes
            assertThat(bytes(tabBarFile())).isEqualTo(tabBarAfterFramework);
            verify(tabBar, never()).save();
            assertThat(essentials.getScoreboardTitle()).isEqualTo(SHIPPED_TITLE_ZH);
            assertThat(essentials.getScoreboardLines()).isEqualTo(SHIPPED_LINES_ZH);
            assertThat(tabBar.getHeader()).isEqualTo(SHIPPED_HEADER_ZH);
            assertThat(tabBar.getFooter()).isEqualTo(SHIPPED_FOOTER_ZH);

            // scheduled-commands: shipped default was English, so under zh it is rewritten and saved.
            YamlConfiguration e = onDisk(essentialsFile());
            assertThat(e.getStringList("features.scheduled-commands.commands")).isEqualTo(scheduledText("zh"));
            assertThat(essentials.getScheduledCommands()).isEqualTo(scheduledText("zh"));
            assertThat(e.getStringList("features.deathpunish.command.commands")).isEqualTo(SHIPPED_DEATHPUNISH_ZH);
            verify(essentials, times(1)).save();

            // motd: shipped defaults were English, so under zh both lines are rewritten and saved.
            YamlConfiguration m = onDisk(motdFile());
            assertThat(m.getString("motd.line1")).isEqualTo(line1Text("zh"));
            assertThat(m.getString("motd.line2")).isEqualTo(line2Text("zh"));
            verify(motd, times(1)).save();
        }

        @Test
        @DisplayName("an upgraded essentials.yml holding the shipped scoreboard title reads exactly the pinned English text under en, not read from the catalogue")
        void upgradedFileReadsExactEnglish() throws Exception {
            language[0] = "en";
            writeEssentials(SHIPPED_TITLE_ZH, SHIPPED_LINES_ZH, SHIPPED_SCHEDULED_EN, SHIPPED_DEATHPUNISH_ZH);
            EssentialsConfig essentials = spy(loadEssentials());

            start(essentials, spy(loadTabBar()), spy(loadMotd()));

            YamlConfiguration disk = onDisk(essentialsFile());
            assertThat(disk.getString("features.scoreboard.title")).isEqualTo("&6&lServer Info");
            assertThat(essentials.getScoreboardTitle()).isEqualTo("&6&lServer Info");
            assertThat(disk.getStringList("features.deathpunish.command.commands"))
                    .containsExactly("say {PLAYER} died!");
            verify(essentials, times(1)).save();
        }

        @Test
        @DisplayName("an upgraded tabbar.yml holding the shipped header/footer reads exactly the pinned English text under en, not read from the catalogue")
        void upgradedTabBarReadsExactEnglish() throws Exception {
            language[0] = "en";
            writeTabBar(SHIPPED_HEADER_ZH, SHIPPED_FOOTER_ZH);
            TabBarConfig tabBar = spy(loadTabBar());

            start(spy(loadEssentials()), tabBar, spy(loadMotd()));

            YamlConfiguration disk = onDisk(tabBarFile());
            assertThat(disk.getString("tabbar.header")).isEqualTo("&6=== Server Name ===");
            assertThat(disk.getString("tabbar.footer")).isEqualTo("&7Online: &e%online%&7/&e%max%");
            assertThat(tabBar.getHeader()).isEqualTo("&6=== Server Name ===");
            verify(tabBar, times(1)).save();
        }
    }

    // ================================================================== every tracked member follows the language

    @Nested
    @DisplayName("every tracked member (shipped, jar en, jar zh) follows language, in both directions")
    class TrackedValuesFollowLanguage {

        @Test
        @DisplayName("scoreboard.title and .lines: each tracked member is replaced by the current language's text and saved, under en and zh")
        void scoreboardTracked() throws Exception {
            for (String code : LANGUAGES) {
                for (String member : new String[] {"shipped", "en", "zh"}) {
                    language[0] = code;
                    String title = "shipped".equals(member) ? SHIPPED_TITLE_ZH : titleText(member);
                    List<String> lines = "shipped".equals(member) ? SHIPPED_LINES_ZH : linesText(member);
                    writeEssentials(title, lines, SHIPPED_SCHEDULED_EN, SHIPPED_DEATHPUNISH_ZH);
                    EssentialsConfig essentials = spy(loadEssentials());

                    start(essentials, spy(loadTabBar()), spy(loadMotd()));

                    YamlConfiguration disk = onDisk(essentialsFile());
                    String what = "language " + code + ", title/lines held the " + member + " member";
                    assertThat(disk.getString("features.scoreboard.title")).as(what).isEqualTo(titleText(code));
                    assertThat(disk.getStringList("features.scoreboard.lines")).as(what).isEqualTo(linesText(code));
                    assertThat(essentials.getScoreboardTitle()).as(what).isEqualTo(titleText(code));
                    assertThat(essentials.getScoreboardLines()).as(what).isEqualTo(linesText(code));
                }
            }
        }

        @Test
        @DisplayName("tabbar.header and .footer: each tracked member is replaced by the current language's text and saved, under en and zh")
        void tabBarTracked() throws Exception {
            for (String code : LANGUAGES) {
                for (String member : new String[] {"shipped", "en", "zh"}) {
                    language[0] = code;
                    String header = "shipped".equals(member) ? SHIPPED_HEADER_ZH : headerText(member);
                    String footer = "shipped".equals(member) ? SHIPPED_FOOTER_ZH : footerText(member);
                    writeTabBar(header, footer);
                    TabBarConfig tabBar = spy(loadTabBar());

                    start(spy(loadEssentials()), tabBar, spy(loadMotd()));

                    YamlConfiguration disk = onDisk(tabBarFile());
                    String what = "language " + code + ", header/footer held the " + member + " member";
                    assertThat(disk.getString("tabbar.header")).as(what).isEqualTo(headerText(code));
                    assertThat(disk.getString("tabbar.footer")).as(what).isEqualTo(footerText(code));
                }
            }
        }

        @Test
        @DisplayName("scheduled-commands and deathpunish-commands: each tracked member is replaced by the current language's text and saved, under en and zh")
        void commandsTracked() throws Exception {
            for (String code : LANGUAGES) {
                for (String member : new String[] {"shipped-scheduled", "en", "zh"}) {
                    language[0] = code;
                    List<String> scheduled = "shipped-scheduled".equals(member) ? SHIPPED_SCHEDULED_EN : scheduledText(member);
                    List<String> deathpunish = "shipped-scheduled".equals(member) ? SHIPPED_DEATHPUNISH_ZH : deathpunishText(member);
                    writeEssentials(SHIPPED_TITLE_ZH, SHIPPED_LINES_ZH, scheduled, deathpunish);
                    EssentialsConfig essentials = spy(loadEssentials());

                    start(essentials, spy(loadTabBar()), spy(loadMotd()));

                    YamlConfiguration disk = onDisk(essentialsFile());
                    String what = "language " + code + ", commands held the " + member + " member";
                    assertThat(disk.getStringList("features.scheduled-commands.commands")).as(what).isEqualTo(scheduledText(code));
                    assertThat(disk.getStringList("features.deathpunish.command.commands")).as(what).isEqualTo(deathpunishText(code));
                }
            }
        }

        @Test
        @DisplayName("motd.line1 and .line2: each tracked member is replaced by the current language's text and saved, under en and zh")
        void motdTracked() throws Exception {
            for (String code : LANGUAGES) {
                for (String member : new String[] {"shipped", "en", "zh"}) {
                    language[0] = code;
                    String line1 = "shipped".equals(member) ? SHIPPED_MOTD_LINE1_EN : line1Text(member);
                    String line2 = "shipped".equals(member) ? SHIPPED_MOTD_LINE2_EN : line2Text(member);
                    writeMotd(line1, line2);
                    MotdConfig motd = spy(loadMotd());

                    start(spy(loadEssentials()), spy(loadTabBar()), motd);

                    YamlConfiguration disk = onDisk(motdFile());
                    String what = "language " + code + ", motd held the " + member + " member";
                    assertThat(disk.getString("motd.line1")).as(what).isEqualTo(line1Text(code));
                    assertThat(disk.getString("motd.line2")).as(what).isEqualTo(line2Text(code));
                }
            }
        }
    }

    // ================================================================== customisation is kept

    @Test
    @DisplayName("a customised value, or built-in text changed by one character, is kept byte for byte under both languages, and the file is not rewritten")
    void customisedValuesAreKept() throws Exception {
        for (String code : LANGUAGES) {
            language[0] = code;
            writeEssentials("&bMy Server " + code, Arrays.asList("&aCustom line 1", "&bCustom line 2"),
                    Arrays.asList("120:say custom"), Arrays.asList("say custom {PLAYER}"));
            writeTabBar("&aCustom header " + code, "&bCustom footer " + code);
            writeMotd("Custom line1 " + code, "Custom line2 " + code);
            EssentialsConfig essentials = spy(loadEssentials());
            TabBarConfig tabBar = spy(loadTabBar());
            MotdConfig motd = spy(loadMotd());
            byte[] eBefore = bytes(essentialsFile());
            byte[] tBefore = bytes(tabBarFile());
            byte[] mBefore = bytes(motdFile());

            start(essentials, tabBar, motd);

            assertThat(bytes(essentialsFile())).as(code).isEqualTo(eBefore);
            assertThat(bytes(tabBarFile())).as(code).isEqualTo(tBefore);
            assertThat(bytes(motdFile())).as(code).isEqualTo(mBefore);
            verify(essentials, never()).save();
            verify(tabBar, never()).save();
            verify(motd, never()).save();
        }

        // a built-in text changed by one character is treated as the operator's, not tracked.
        language[0] = "en";
        writeEssentials(SHIPPED_TITLE_ZH + " ", SHIPPED_LINES_ZH, SHIPPED_SCHEDULED_EN, SHIPPED_DEATHPUNISH_ZH);
        EssentialsConfig essentials = spy(loadEssentials());
        byte[] before = bytes(essentialsFile());
        start(essentials, spy(loadTabBar()), spy(loadMotd()));
        assertThat(essentials.getScoreboardTitle()).isEqualTo(SHIPPED_TITLE_ZH + " ");
        assertThat(bytes(essentialsFile())).isNotEqualTo(before); // deathpunish/scheduled still change under en
        assertThat(onDisk(essentialsFile()).getString("features.scoreboard.title")).isEqualTo(SHIPPED_TITLE_ZH + " ");
    }

    // ================================================================== blank semantics

    @Nested
    @DisplayName("blank semantics: @NotEmpty refuses a blank scoreboard.title; a blank lines/header/footer is kept blank")
    class BlankSemantics {

        @Test
        @DisplayName("a blank scoreboard.title is refused by the framework before this module runs (@NotEmpty), exactly as every released version")
        void blankTitleIsRefused() throws Exception {
            language[0] = "en";
            writeEssentials("", SHIPPED_LINES_ZH, SHIPPED_SCHEDULED_EN, SHIPPED_DEATHPUNISH_ZH);

            assertThatThrownBy(() -> loadEssentials())
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining("scoreboardTitle");
        }

        @Test
        @DisplayName("an empty scoreboard.lines list is kept empty, exactly as at origin/master: never materialized")
        void blankLinesAreKept() throws Exception {
            language[0] = "en";
            writeEssentials(SHIPPED_TITLE_ZH, new ArrayList<String>(), SHIPPED_SCHEDULED_EN, SHIPPED_DEATHPUNISH_ZH);
            EssentialsConfig essentials = spy(loadEssentials());

            start(essentials, spy(loadTabBar()), spy(loadMotd()));

            assertThat(essentials.getScoreboardLines()).isEmpty();
            assertThat(onDisk(essentialsFile()).getStringList("features.scoreboard.lines")).isEmpty();
        }

        @Test
        @DisplayName("a blank tabbar.header and .footer are kept blank, exactly as at origin/master: neither field carries @NotEmpty, never materialized")
        void blankHeaderAndFooterAreKept() throws Exception {
            language[0] = "en";
            writeTabBar("", "");
            TabBarConfig tabBar = spy(loadTabBar());

            start(spy(loadEssentials()), tabBar, spy(loadMotd()));

            assertThat(tabBar.getHeader()).isEmpty();
            assertThat(tabBar.getFooter()).isEmpty();
            YamlConfiguration disk = onDisk(tabBarFile());
            assertThat(disk.getString("tabbar.header")).isEmpty();
            assertThat(disk.getString("tabbar.footer")).isEmpty();
        }
    }

    // ================================================================== second enable / reload / change listeners

    @Test
    @DisplayName("a second enable with the same language writes nothing")
    void secondEnableWritesNothing() throws Exception {
        language[0] = "en";
        start(loadEssentials(), loadTabBar(), loadMotd());
        byte[] eAfterFirst = bytes(essentialsFile());
        byte[] tAfterFirst = bytes(tabBarFile());
        byte[] mAfterFirst = bytes(motdFile());

        EssentialsConfig essentials = spy(loadEssentials());
        TabBarConfig tabBar = spy(loadTabBar());
        MotdConfig motd = spy(loadMotd());
        start(essentials, tabBar, motd);

        assertThat(bytes(essentialsFile())).isEqualTo(eAfterFirst);
        assertThat(bytes(tabBarFile())).isEqualTo(tAfterFirst);
        assertThat(bytes(motdFile())).isEqualTo(mAfterFirst);
        verify(essentials, never()).save();
        verify(tabBar, never()).save();
        verify(motd, never()).save();
    }

    @Test
    @DisplayName("onReload() after a language switch rewrites every setting in the new language, in both directions")
    void reloadFollowsALanguageSwitchBothWays() throws Exception {
        for (String[] direction : new String[][] {{"en", "zh"}, {"zh", "en"}}) {
            language[0] = direction[0];
            Files.deleteIfExists(essentialsFile().toPath());
            Files.deleteIfExists(tabBarFile().toPath());
            Files.deleteIfExists(motdFile().toPath());
            EssentialsConfig essentials = loadEssentials();
            TabBarConfig tabBar = loadTabBar();
            MotdConfig motd = loadMotd();
            start(essentials, tabBar, motd);

            language[0] = direction[1];
            essentials.init(plugin);
            tabBar.init(plugin);
            motd.init(plugin);
            reload();

            String what = direction[0] + " -> " + direction[1];
            assertThat(essentials.getScoreboardTitle()).as(what).isEqualTo(titleText(direction[1]));
            assertThat(essentials.getScoreboardLines()).as(what).isEqualTo(linesText(direction[1]));
            assertThat(essentials.getScheduledCommands()).as(what).isEqualTo(scheduledText(direction[1]));
            assertThat(essentials.getDeathPunishCommands()).as(what).isEqualTo(deathpunishText(direction[1]));
            assertThat(tabBar.getHeader()).as(what).isEqualTo(headerText(direction[1]));
            assertThat(tabBar.getFooter()).as(what).isEqualTo(footerText(direction[1]));
            assertThat(motd.getLine1()).as(what).isEqualTo(line1Text(direction[1]));
            assertThat(motd.getLine2()).as(what).isEqualTo(line2Text(direction[1]));
        }
    }

    @Test
    @DisplayName("no configuration change listener rewrites the text (the framework fires them before it reloads the language)")
    void changeListenersDoNotMaterialize() throws Exception {
        language[0] = "en";
        EssentialsConfig essentials = loadEssentials();
        start(essentials, loadTabBar(), loadMotd());
        byte[] before = bytes(essentialsFile());

        language[0] = "zh";
        // This module registers no change listener, and the framework registers none for it, so
        // nothing can write the file before the framework rebuilds the language; pinned so that
        // adding one is seen.
        assertThat(essentials.getChangeListeners()).isEmpty();
        for (ConfigChangeListener listener : new ArrayList<>(essentials.getChangeListeners())) {
            listener.onConfigReload(essentials);
        }

        assertThat(bytes(essentialsFile())).isEqualTo(before);
        assertThat(essentials.getScoreboardTitle()).isEqualTo(titleText("en"));
    }

    // ================================================================== the jar's own catalogue, not the disk copy

    @Test
    @DisplayName("an operator's edit of the extracted language file is not written into any of the three files, so each value keeps following a language switch (text source decision of 2026-09-25)")
    void diskCatalogueEditDoesNotReachTheFile() throws Exception {
        diskOverrides.put(SCOREBOARD_TITLE_KEY, "Edited title");
        diskOverrides.put(SCHEDULED_COMMANDS_KEY, "999:say Edited");
        diskOverrides.put(DEATHPUNISH_COMMANDS_KEY, "say Edited");
        diskOverrides.put(TABBAR_HEADER_KEY, "Edited header");
        diskOverrides.put(MOTD_LINE1_KEY, "Edited motd");
        language[0] = "en";
        writeEssentials(SHIPPED_TITLE_ZH, SHIPPED_LINES_ZH, SHIPPED_SCHEDULED_EN, SHIPPED_DEATHPUNISH_ZH);
        writeTabBar(SHIPPED_HEADER_ZH, SHIPPED_FOOTER_ZH);
        EssentialsConfig essentials = loadEssentials();
        TabBarConfig tabBar = loadTabBar();
        MotdConfig motd = loadMotd();

        start(essentials, tabBar, motd);

        assertThat(essentials.getScoreboardTitle()).as("en, jar text not the disk edit").isEqualTo(titleText("en"));
        assertThat(tabBar.getHeader()).as("en, jar text not the disk edit").isEqualTo(headerText("en"));

        language[0] = "zh";
        essentials.init(plugin);
        tabBar.init(plugin);
        motd.init(plugin);
        reload();

        assertThat(essentials.getScoreboardTitle()).as("after a switch to zh, follows").isEqualTo(titleText("zh"));
        assertThat(tabBar.getHeader()).as("after a switch to zh, follows").isEqualTo(headerText("zh"));
    }

    @Test
    @DisplayName("an operator-edited language file on disk does not widen what counts as built-in text")
    void diskCatalogueDoesNotWidenTheTrackedSet() throws Exception {
        diskOverrides.put(SCOREBOARD_TITLE_KEY, "Edited title");
        language[0] = "en";
        writeEssentials("Edited title", SHIPPED_LINES_ZH, SHIPPED_SCHEDULED_EN, SHIPPED_DEATHPUNISH_ZH);
        EssentialsConfig essentials = spy(loadEssentials());

        start(essentials, spy(loadTabBar()), spy(loadMotd()));

        assertThat(onDisk(essentialsFile()).getString("features.scoreboard.title")).isEqualTo("Edited title");
    }

    // ================================================================== save failure

    @Test
    @DisplayName("a file that cannot be saved is reported in the server's language, and the module still uses the new text")
    void saveFailureIsReported() throws Exception {
        language[0] = "en";
        EssentialsConfig essentials = spy(loadEssentials());
        doThrow(new IOException("read-only")).when(essentials).save();

        assertThat(start(essentials, spy(loadTabBar()), spy(loadMotd()))).isTrue();

        String expected = String.format(CatalogueText.text("en", "essentials.log.config_default_save_failed"),
                essentials.getConfigFilePath(), "read-only");
        verify(logger).warn(expected);
        assertThat(essentials.getScoreboardTitle()).isEqualTo(titleText("en"));
    }

    // ================================================================== @NotEmpty and Java defaults

    @Test
    @DisplayName("@NotEmpty is on exactly the field that carried it at origin/master (scoreboardTitle); the four text fields' Java defaults are their shipped defaults")
    void validationAndJavaDefaults() throws Exception {
        Set<String> notEmpty = new TreeSet<>();
        for (Field f : EssentialsConfig.class.getDeclaredFields()) {
            if (f.isAnnotationPresent(ConfigEntry.class) && f.isAnnotationPresent(NotEmpty.class)) {
                notEmpty.add(f.getName());
            }
        }
        assertThat(notEmpty).containsExactly("scoreboardTitle");
        for (Field f : TabBarConfig.class.getDeclaredFields()) {
            assertThat(f.isAnnotationPresent(NotEmpty.class)).as(f.getName()).isFalse();
        }

        EssentialsConfig freshEssentials = new EssentialsConfig();
        assertThat(freshEssentials.getScoreboardTitle()).isEqualTo(SHIPPED_TITLE_ZH);
        assertThat(freshEssentials.getScoreboardLines()).isEqualTo(SHIPPED_LINES_ZH);
        assertThat(freshEssentials.getScheduledCommands()).isEqualTo(SHIPPED_SCHEDULED_EN);
        assertThat(freshEssentials.getDeathPunishCommands()).isEqualTo(SHIPPED_DEATHPUNISH_ZH);

        TabBarConfig freshTabBar = new TabBarConfig();
        assertThat(freshTabBar.getHeader()).isEqualTo(SHIPPED_HEADER_ZH);
        assertThat(freshTabBar.getFooter()).isEqualTo(SHIPPED_FOOTER_ZH);

        MotdConfig freshMotd = new MotdConfig();
        assertThat(freshMotd.getLine1()).isEqualTo(SHIPPED_MOTD_LINE1_EN);
        assertThat(freshMotd.getLine2()).isEqualTo(SHIPPED_MOTD_LINE2_EN);
    }

    // ================================================================== consumers

    @Nested
    @DisplayName("consumers render the text the file holds")
    class Consumers {

        @Test
        @DisplayName("TabBarListener's header/footer come from tabbar.yml")
        void tabBarListenerUsesTheFile() throws Exception {
            language[0] = "en";
            TabBarConfig tabBar = loadTabBar();
            EssentialsConfig essentials = loadEssentials();
            start(essentials, tabBar, loadMotd());

            TabBarListener listener = new TabBarListener();
            set(listener, "config", essentials);
            set(listener, "tabBarConfig", tabBar);
            Player player = mock(Player.class);

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getOnlinePlayers).thenReturn(java.util.Collections.emptyList());
                bukkit.when(Bukkit::getMaxPlayers).thenReturn(20);
                listener.updateTabBar(player);
            }

            String expectedHeader = ChatColor.translateAlternateColorCodes('&',
                    tabBar.getHeader().replace("%online%", "0").replace("%max%", "20"));
            String expectedFooter = ChatColor.translateAlternateColorCodes('&',
                    tabBar.getFooter().replace("%online%", "0").replace("%max%", "20"));
            verify(player).setPlayerListHeaderFooter(expectedHeader, expectedFooter);
        }

        @Test
        @DisplayName("MotdListener's MOTD lines come from motd.yml")
        void motdListenerUsesTheFile() throws Exception {
            language[0] = "zh";
            MotdConfig motd = loadMotd();
            EssentialsConfig essentials = loadEssentials();
            start(essentials, loadTabBar(), motd);

            MotdListener listener = new MotdListener();
            set(listener, "config", essentials);
            set(listener, "motdConfig", motd);
            ServerListPingEvent event = mock(ServerListPingEvent.class);

            listener.onServerListPing(event);

            String expected = ChatColor.translateAlternateColorCodes('&', motd.getLine1()) + "\n"
                    + ChatColor.translateAlternateColorCodes('&', motd.getLine2());
            verify(event).setMotd(expected);
        }

        @Test
        @DisplayName("ScoreboardService's title and lines come from essentials.yml")
        void scoreboardServiceUsesTheFile() throws Exception {
            language[0] = "en";
            EssentialsConfig essentials = loadEssentials();
            start(essentials, loadTabBar(), loadMotd());

            ScoreboardService service = new ScoreboardService();
            set(service, "config", essentials);
            set(service, "plugin", plugin);
            org.bukkit.scoreboard.ScoreboardManager manager = mock(org.bukkit.scoreboard.ScoreboardManager.class);
            org.bukkit.scoreboard.Scoreboard scoreboard = mock(org.bukkit.scoreboard.Scoreboard.class);
            org.bukkit.scoreboard.Objective objective = mock(org.bukkit.scoreboard.Objective.class);
            org.bukkit.scoreboard.Score score = mock(org.bukkit.scoreboard.Score.class);
            org.mockito.Mockito.when(manager.getNewScoreboard()).thenReturn(scoreboard);
            org.mockito.Mockito.when(scoreboard.registerNewObjective(anyString(), anyString(), anyString())).thenReturn(objective);
            org.mockito.Mockito.when(objective.getScore(anyString())).thenReturn(score);
            org.mockito.Mockito.when(scoreboard.getEntries()).thenReturn(new java.util.HashSet<>());
            set(service, "manager", manager);
            Player player = mock(Player.class);
            UUID uuid = UUID.randomUUID();
            org.mockito.Mockito.when(player.getUniqueId()).thenReturn(uuid);
            org.mockito.Mockito.when(player.getName()).thenReturn("Steve");
            org.bukkit.World world = mock(org.bukkit.World.class);
            org.mockito.Mockito.when(world.getName()).thenReturn("world");
            org.mockito.Mockito.when(player.getWorld()).thenReturn(world);
            @SuppressWarnings("unchecked")
            Set<UUID> enabled = (Set<UUID>) getField(service, "enabledPlayers");
            enabled.add(uuid);

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
                service.updateScoreboard(player);
            }

            org.mockito.ArgumentCaptor<String> titleCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(scoreboard).registerNewObjective(anyString(), anyString(), titleCaptor.capture());
            assertThat(titleCaptor.getValue()).isEqualTo(ChatColor.translateAlternateColorCodes('&', essentials.getScoreboardTitle()));
        }

        @Test
        @DisplayName("ScheduledCommandService dispatches the file's command text, materialized before the task's first run")
        void scheduledCommandServiceUsesTheFile() throws Exception {
            language[0] = "zh";
            EssentialsConfig essentials = loadEssentials();
            essentials.setScheduledCommandsEnabled(true);
            start(essentials, loadTabBar(), loadMotd());

            ScheduledCommandService service = new ScheduledCommandService();
            set(service, "config", essentials);
            set(service, "plugin", plugin);
            org.bukkit.plugin.Plugin bukkitPlugin = mock(org.bukkit.plugin.Plugin.class);
            set(service, "bukkitPlugin", bukkitPlugin);

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
                java.util.List<Runnable> scheduled = new ArrayList<>();
                bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
                // BukkitRunnable#runTaskTimer(Plugin, long, long) calls the BukkitScheduler overload typed
                // Runnable, not the BukkitRunnable-typed overload -- stubbing the latter silently matches
                // nothing and every task is scheduled with a null BukkitTask.
                org.mockito.Mockito.when(scheduler.runTaskTimer(any(org.bukkit.plugin.Plugin.class),
                                any(Runnable.class), org.mockito.ArgumentMatchers.anyLong(),
                                org.mockito.ArgumentMatchers.anyLong()))
                        .thenAnswer(inv -> {
                            scheduled.add(inv.getArgument(1));
                            return mock(org.bukkit.scheduler.BukkitTask.class);
                        });
                org.bukkit.command.ConsoleCommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
                bukkit.when(Bukkit::getConsoleSender).thenReturn(console);

                service.startTasks();
                assertThat(scheduled).hasSize(2);
                for (Runnable r : scheduled) {
                    r.run();
                }

                // Expected texts are computed before verify(): a failure while computing them inside the
                // verify lambda would leave Mockito's matcher state open and fail the next test instead.
                String first = commandTextOf(scheduledText("zh").get(0));
                String second = commandTextOf(scheduledText("zh").get(1));
                bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), eq(first)), org.mockito.Mockito.times(1));
                bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), eq(second)), org.mockito.Mockito.times(1));
            }
        }

        @Test
        @DisplayName("DeathPunishListener dispatches the command text rewritten in the file under en")
        void deathPunishListenerUsesTheRewrittenText() throws Exception {
            language[0] = "en";
            writeEssentials(SHIPPED_TITLE_ZH, SHIPPED_LINES_ZH, SHIPPED_SCHEDULED_EN, SHIPPED_DEATHPUNISH_ZH);
            EssentialsConfig essentials = loadEssentials();
            essentials.setDeathPunishEnabled(true);
            essentials.setDeathPunishCommandEnabled(true);
            start(essentials, loadTabBar(), loadMotd());
            assertThat(onDisk(essentialsFile()).getStringList("features.deathpunish.command.commands"))
                    .containsExactly("say {PLAYER} died!");

            DeathPunishListener listener = new DeathPunishListener();
            set(listener, "config", essentials);
            set(listener, "plugin", plugin);
            Player player = mock(Player.class);
            org.mockito.Mockito.when(player.getName()).thenReturn("Steve");
            org.mockito.Mockito.when(player.hasPermission(anyString())).thenReturn(false);
            org.mockito.Mockito.when(player.getWorld()).thenReturn(mock(org.bukkit.World.class));
            org.bukkit.event.entity.PlayerDeathEvent event = mock(org.bukkit.event.entity.PlayerDeathEvent.class);
            org.mockito.Mockito.when(event.getEntity()).thenReturn(player);
            org.mockito.Mockito.when(event.getDrops()).thenReturn(new ArrayList<>());

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                org.bukkit.command.ConsoleCommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
                bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
                listener.onPlayerDeath(event);

                bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), eq("say Steve died!")));
            }
        }

        /**
         * Production order: the container's {@code @PostConstruct} pass schedules the tasks before
         * {@code registerSelf()} writes the file's text in the server's language. Every task still
         * running after enable must send the rewritten text.
         */
        @Test
        @DisplayName("tasks scheduled before enable send the text written at enable (the container schedules them first)")
        void tasksScheduledBeforeEnableSendTheWrittenText() throws Exception {
            language[0] = "zh";
            writeEssentials(SHIPPED_TITLE_ZH, SHIPPED_LINES_ZH, SHIPPED_SCHEDULED_EN, SHIPPED_DEATHPUNISH_ZH);
            EssentialsConfig essentials = loadEssentials();
            essentials.setScheduledCommandsEnabled(true);
            ScheduledCommandService service = new ScheduledCommandService();
            set(service, "config", essentials);
            set(service, "plugin", plugin);
            set(service, "bukkitPlugin", mock(org.bukkit.plugin.Plugin.class));
            scheduledCommandServiceBean = service;
            String first = commandTextOf(scheduledText("zh").get(0));
            String second = commandTextOf(scheduledText("zh").get(1));

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
                Map<Runnable, org.bukkit.scheduler.BukkitTask> scheduled = new LinkedHashMap<>();
                bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
                org.mockito.Mockito.when(scheduler.runTaskTimer(any(org.bukkit.plugin.Plugin.class),
                                any(Runnable.class), org.mockito.ArgumentMatchers.anyLong(),
                                org.mockito.ArgumentMatchers.anyLong()))
                        .thenAnswer(inv -> {
                            org.bukkit.scheduler.BukkitTask task = mock(org.bukkit.scheduler.BukkitTask.class);
                            scheduled.put(inv.getArgument(1), task);
                            return task;
                        });
                org.bukkit.command.ConsoleCommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
                bukkit.when(Bukkit::getConsoleSender).thenReturn(console);

                service.startTasks();          // the @PostConstruct pass
                start(essentials, loadTabBar(), loadMotd());  // registerSelf()

                int live = 0;
                for (Map.Entry<Runnable, org.bukkit.scheduler.BukkitTask> e : scheduled.entrySet()) {
                    if (Mockito.mockingDetails(e.getValue()).getInvocations().stream()
                            .noneMatch(i -> "cancel".equals(i.getMethod().getName()))) {
                        e.getKey().run();
                        live++;
                    }
                }
                assertThat(live).isEqualTo(2);
                bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), eq(first)), org.mockito.Mockito.times(1));
                bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), eq(second)), org.mockito.Mockito.times(1));
                bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), eq("say Server is online!")), never());
            }
        }

        /**
         * A task keeps the command it was scheduled with until the next reload: a list changed in memory
         * without a reload (a panel write) must not move another entry's command onto this task's interval.
         */
        @Test
        @DisplayName("a running task keeps its own command when the list changes without a reload")
        void aRunningTaskKeepsItsOwnCommandWhenTheListChanges() throws Exception {
            language[0] = "en";
            EssentialsConfig essentials = loadEssentials();
            essentials.setScheduledCommandsEnabled(true);
            essentials.setScheduledCommands(new ArrayList<>(Arrays.asList("60:say Tip of the minute", "86400:stop")));
            ScheduledCommandService service = new ScheduledCommandService();
            set(service, "config", essentials);
            set(service, "plugin", plugin);
            set(service, "bukkitPlugin", mock(org.bukkit.plugin.Plugin.class));

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
                List<Runnable> scheduled = new ArrayList<>();
                bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
                org.mockito.Mockito.when(scheduler.runTaskTimer(any(org.bukkit.plugin.Plugin.class),
                                any(Runnable.class), org.mockito.ArgumentMatchers.anyLong(),
                                org.mockito.ArgumentMatchers.anyLong()))
                        .thenAnswer(inv -> {
                            scheduled.add(inv.getArgument(1));
                            return mock(org.bukkit.scheduler.BukkitTask.class);
                        });
                org.bukkit.command.ConsoleCommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
                bukkit.when(Bukkit::getConsoleSender).thenReturn(console);

                service.startTasks();
                assertThat(scheduled).hasSize(2);
                essentials.setScheduledCommands(new ArrayList<>(Arrays.asList("86400:stop")));
                scheduled.get(0).run();

                bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), eq("say Tip of the minute")), org.mockito.Mockito.times(1));
                bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), eq("stop")), never());
            }
        }

        @Test
        @DisplayName("DeathPunishListener dispatches the file's command text")
        void deathPunishListenerUsesTheFile() throws Exception {
            language[0] = "zh";
            EssentialsConfig essentials = loadEssentials();
            essentials.setDeathPunishEnabled(true);
            essentials.setDeathPunishCommandEnabled(true);
            start(essentials, loadTabBar(), loadMotd());

            DeathPunishListener listener = new DeathPunishListener();
            set(listener, "config", essentials);
            set(listener, "plugin", plugin);
            Player player = mock(Player.class);
            org.mockito.Mockito.when(player.getName()).thenReturn("Steve");
            org.mockito.Mockito.when(player.hasPermission(anyString())).thenReturn(false);
            org.mockito.Mockito.when(player.getWorld()).thenReturn(mock(org.bukkit.World.class));
            org.bukkit.event.entity.PlayerDeathEvent event = mock(org.bukkit.event.entity.PlayerDeathEvent.class);
            org.mockito.Mockito.when(event.getEntity()).thenReturn(player);
            org.mockito.Mockito.when(event.getDrops()).thenReturn(new ArrayList<>());

            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                org.bukkit.command.ConsoleCommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
                bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
                listener.onPlayerDeath(event);

                bukkit.verify(() -> Bukkit.dispatchCommand(eq(console), eq("say Steve 死亡了!")));
            }
        }
    }

    // ================================================================== harness

    private File essentialsFile() {
        return new File(tempDir.toFile(), "config/essentials.yml");
    }

    private File tabBarFile() {
        return new File(tempDir.toFile(), "config/tabbar.yml");
    }

    private File motdFile() {
        return new File(tempDir.toFile(), "config/motd.yml");
    }

    private byte[] bytes(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    private YamlConfiguration onDisk(File file) {
        return YamlConfiguration.loadConfiguration(file);
    }

    private void writeEssentials(String title, List<String> lines, List<String> scheduled, List<String> deathpunish) throws IOException {
        Files.createDirectories(essentialsFile().getParentFile().toPath());
        YamlConfiguration persisted = new YamlConfiguration();
        persisted.set("features.scoreboard.title", title);
        persisted.set("features.scoreboard.lines", lines);
        if (scheduled != null) {
            persisted.set("features.scheduled-commands.commands", scheduled);
        }
        if (deathpunish != null) {
            persisted.set("features.deathpunish.command.commands", deathpunish);
        }
        persisted.save(essentialsFile());
    }

    private void writeTabBar(String header, String footer) throws IOException {
        Files.createDirectories(tabBarFile().getParentFile().toPath());
        YamlConfiguration persisted = new YamlConfiguration();
        persisted.set("tabbar.header", header);
        persisted.set("tabbar.footer", footer);
        persisted.save(tabBarFile());
    }

    private void writeMotd(String line1, String line2) throws IOException {
        Files.createDirectories(motdFile().getParentFile().toPath());
        YamlConfiguration persisted = new YamlConfiguration();
        persisted.set("motd.line1", line1);
        persisted.set("motd.line2", line2);
        persisted.save(motdFile());
    }

    /** The framework's own load: {@code init} fills missing keys with the Java defaults, saves, validates. */
    private EssentialsConfig loadEssentials() throws IOException {
        Files.createDirectories(essentialsFile().getParentFile().toPath());
        EssentialsConfig config = new EssentialsConfig();
        config.init(plugin);
        return config;
    }

    private TabBarConfig loadTabBar() throws IOException {
        Files.createDirectories(tabBarFile().getParentFile().toPath());
        TabBarConfig config = new TabBarConfig();
        config.init(plugin);
        return config;
    }

    private MotdConfig loadMotd() throws IOException {
        Files.createDirectories(motdFile().getParentFile().toPath());
        MotdConfig config = new MotdConfig();
        config.init(plugin);
        return config;
    }

    /** The module's enable path: {@code UltiEssentials#registerSelf()} with the three configs as the module's beans. */
    private boolean start(EssentialsConfig essentials, TabBarConfig tabBar, MotdConfig motd) {
        currentEssentials = essentials;
        currentTabBar = tabBar;
        currentMotd = motd;
        return plugin.registerSelf();
    }

    /** The module's {@code onReload()} (protected), as the framework calls it after rebuilding the language. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private void reload() throws Exception {
        Method onReload = UltiEssentials.class.getDeclaredMethod("onReload");
        onReload.setAccessible(true);
        onReload.invoke(plugin);
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static void set(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /**
     * A module double whose {@code registerSelf()} and {@code onReload()} are the real ones, whose
     * configuration folder is the temporary directory, whose {@code i18n} and {@code getLanguageCode()}
     * answer from the module's real catalogue for the language in {@link #language} (read at call
     * time), and whose {@code getContext().getBean(...)} answers from the {@code current*} fields and
     * the no-op service beans.
     */
    private UltiEssentials moduleDouble() {
        return Mockito.mock(UltiEssentials.class, this::moduleAnswer);
    }

    private Object moduleAnswer(org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
        final Map<String, org.mockito.stubbing.Answer<String>> answers = new LinkedHashMap<>();
        for (String code : LANGUAGES) {
            answers.put(code, CatalogueText.answer(code));
        }
        String name = invocation.getMethod().getName();
        switch (name) {
            case "registerSelf":
            case "onReload":
                return invocation.callRealMethod();
            case "getConfigFolder":
                return tempDir.toString();
            case "getConfigFile":
                return new File(tempDir.toFile(), invocation.<String>getArgument(0));
            case "i18n": {
                String key = invocation.getArgument(invocation.getArguments().length - 1);
                return diskOverrides.containsKey(key) ? diskOverrides.get(key) : answers.get(language[0]).answer(invocation);
            }
            case "getLanguageCode":
                return language[0];
            case "getLogger":
                return logger;
            case "getContext":
                return Mockito.mock(invocation.getMethod().getReturnType(), inv -> {
                    if (!"getBean".equals(inv.getMethod().getName())) {
                        return Answers.RETURNS_DEFAULTS.answer(inv);
                    }
                    Class<?> type = inv.getArgument(0);
                    if (type == EssentialsConfig.class) {
                        return currentEssentials;
                    }
                    if (type == TabBarConfig.class) {
                        return currentTabBar;
                    }
                    if (type == MotdConfig.class) {
                        return currentMotd;
                    }
                    if (type == EntityIdBackfillService.class) {
                        return backfillService;
                    }
                    if (type == ScheduledCommandService.class) {
                        return scheduledCommandServiceBean;
                    }
                    if (type == ScoreboardService.class) {
                        return scoreboardServiceBean;
                    }
                    if (type == NamePrefixService.class) {
                        return namePrefixServiceBean;
                    }
                    return Answers.RETURNS_DEFAULTS.answer(inv);
                });
            default:
                return Answers.RETURNS_DEFAULTS.answer(invocation);
        }
    }
}
