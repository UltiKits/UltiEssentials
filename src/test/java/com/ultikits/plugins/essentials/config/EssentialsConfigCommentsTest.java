package com.ultikits.plugins.essentials.config;

import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The comments above the keys of this module's five configuration files come from the module's
 * language files, so a server set to English writes English comments (UltiKits/UltiEssentials#67;
 * framework UltiTools-Reborn#542, rewritten in the current language on every write of the file by the
 * maintainer's decision of 2026-09-29). Every case runs the framework's real load
 * ({@code AbstractConfigEntity#init}) on a temporary folder and answers {@code i18n} from the module's
 * real catalogues.
 * <p>
 * The expected texts come from {@code src/test/resources/config/config-comments.tsv}: each comment as
 * master {@code 0875d16} wrote it (which the zh catalogue must hold verbatim) and its English
 * translation. They are not read from the catalogue, so a missing or changed catalogue entry fails on
 * the written file, not in the fixture.
 * <p>
 * 注释从语言文件取：英文服务器写入英文注释，中文服务器写入与之前完全相同的中文注释；升级时只改注释，不改值。
 */
@DisplayName("config comments are written in the server's language (#67)")
class EssentialsConfigCommentsTest {

    /** The five configuration classes, each with the file it writes. */
    private static final List<Supplier<AbstractConfigEntity>> CONFIGS = Arrays.<Supplier<AbstractConfigEntity>>asList(
            EssentialsConfig::new, LobbyConfig::new, MotdConfig::new, SpawnConfig::new, TabBarConfig::new);

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    /** One fixture row. */
    private static final class Row {
        final String file;
        final String path;
        final String key;
        final String zh;
        final String en;

        Row(String[] cells) {
            this.file = cells[0];
            this.path = cells[1];
            this.key = cells[2];
            this.zh = cells[3];
            this.en = cells[4];
        }
    }

    private static List<Row> rows() throws IOException {
        List<Row> rows = new ArrayList<>();
        try (InputStream in = EssentialsConfigCommentsTest.class.getResourceAsStream("/config/config-comments.tsv");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] cells = line.split("\t", -1);
                assertThat(cells).as("fixture line " + line).hasSize(5);
                rows.add(new Row(cells));
            }
        }
        return rows;
    }

    private static List<Row> rowsFor(String file) throws IOException {
        List<Row> found = new ArrayList<>();
        for (Row row : rows()) {
            if (row.file.equals(file)) {
                found.add(row);
            }
        }
        return found;
    }

    private static String fileOf(AbstractConfigEntity config) {
        return config.getClass().getAnnotation(ConfigEntity.class).value();
    }

    // ==================== the declarations ====================

    @Test
    @DisplayName("every @ConfigEntry comment of the five files is one {key} token, the key the fixture names; 78 in all")
    void everyCommentIsOneCatalogueToken() throws Exception {
        Map<String, String> declared = new LinkedHashMap<>();
        for (Supplier<AbstractConfigEntity> config : CONFIGS) {
            AbstractConfigEntity entity = config.get();
            for (Field field : entity.getClass().getDeclaredFields()) {
                ConfigEntry entry = field.getAnnotation(ConfigEntry.class);
                if (entry != null && !entry.comment().trim().isEmpty()) {
                    declared.put(fileOf(entity) + " " + entry.path(), entry.comment());
                }
            }
        }
        Map<String, String> expected = new LinkedHashMap<>();
        for (Row row : rows()) {
            expected.put(row.file + " " + row.path, "{" + row.key + "}");
        }
        // Control: the reflection reads the real declarations, all 78 of them.
        assertThat(declared).hasSize(78);
        assertThat(declared).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    @DisplayName("the catalogues hold exactly these texts: zh the comment master wrote, en its translation")
    void theCataloguesHoldTheseTexts() throws Exception {
        for (Row row : rows()) {
            assertThat(CatalogueText.entries("zh").get(row.key)).as("zh " + row.key).isEqualTo(row.zh);
            assertThat(CatalogueText.entries("en").get(row.key)).as("en " + row.key).isEqualTo(row.en);
        }
    }

    // ==================== the written files ====================

    @Test
    @DisplayName("a fresh install under language: en writes the English comment above every key, and no Chinese comment")
    void freshInstallWritesEnglishComments() throws Exception {
        for (Supplier<AbstractConfigEntity> config : CONFIGS) {
            AbstractConfigEntity entity = load(config, "en");
            String text = read(entity);
            for (Row row : rowsFor(fileOf(entity))) {
                assertThat(commentAbove(text, row.path)).as(row.file + " " + row.path).isEqualTo(row.en);
            }
            assertThat(text).as("no Chinese comment in " + fileOf(entity))
                    .doesNotContainPattern("#.*[\\u3000-\\u303f\\u4e00-\\u9fff\\uff00-\\uffef]");
        }
    }

    @Test
    @DisplayName("a fresh install under language: zh writes the Chinese comment above every key, the text master wrote")
    void freshInstallWritesChineseComments() throws Exception {
        for (Supplier<AbstractConfigEntity> config : CONFIGS) {
            AbstractConfigEntity entity = load(config, "zh");
            String text = read(entity);
            for (Row row : rowsFor(fileOf(entity))) {
                assertThat(commentAbove(text, row.path)).as(row.file + " " + row.path).isEqualTo(row.zh);
            }
        }
    }

    @Test
    @DisplayName("an upgrade: a file with the Chinese comments gets the English ones, its values stay, and a second start leaves it byte for byte")
    void upgradeSwitchesTheCommentsAndKeepsTheValues() throws Exception {
        // The file a server running master holds: the Chinese comments master wrote (the zh catalogue
        // text, verbatim, as the case above proves) with an operator-edited value.
        load(EssentialsConfig::new, "zh");
        File file = new File(tempDir.toFile(), "config/essentials.yml");
        String chinese = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .replace("default-max-homes: 3", "default-max-homes: 7");
        assertThat(chinese).as("precondition: the value was edited").contains("default-max-homes: 7");
        Files.write(file.toPath(), chinese.getBytes(StandardCharsets.UTF_8));

        EssentialsConfig first = (EssentialsConfig) load(EssentialsConfig::new, "en");

        String afterFirst = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        for (Row row : rowsFor("config/essentials.yml")) {
            assertThat(commentAbove(afterFirst, row.path)).as(row.path).isEqualTo(row.en);
        }
        assertThat(first.getHomeDefaultMaxHomes()).isEqualTo(7);
        assertThat(nonCommentLines(afterFirst)).as("only comment lines changed").isEqualTo(nonCommentLines(chinese));

        load(EssentialsConfig::new, "en");

        assertThat(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
                .as("the second start").isEqualTo(afterFirst);
    }

    // ==================== helpers ====================

    /** Loads one configuration through the framework's real {@code init}, answering {@code i18n} in {@code language}. */
    private AbstractConfigEntity load(Supplier<AbstractConfigEntity> config, String language) throws IOException {
        final org.mockito.stubbing.Answer<String> text = CatalogueText.answer(language);
        // getConfigFile is protected final, so it is answered by name rather than stubbed. The module's own
        // class is mocked so that shippedCatalogueTexts, which the framework (UltiTools-API 6.3.0) uses to tell
        // its own comments from an operator's, really reads this module's shipped catalogues: an old comment
        // is replaced only when it equals one of them (maintainer decision 2026-10-04).
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
        AbstractConfigEntity entity = config.get();
        Files.createDirectories(new File(tempDir.toFile(), fileOf(entity)).getParentFile().toPath());
        entity.init(plugin);
        return entity;
    }

    private String read(AbstractConfigEntity entity) throws IOException {
        return new String(Files.readAllBytes(new File(tempDir.toFile(), fileOf(entity)).toPath()), StandardCharsets.UTF_8);
    }

    /**
     * The comment line directly above the key at the dotted {@code path} in the file text, without its
     * indentation and leading {@code # }. Walks the file's indentation so a nested key is found under
     * its own parents, not under a sibling section with a key of the same name.
     */
    static String commentAbove(String text, String path) {
        String[] lines = text.split("\\R");
        List<Integer> indents = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-") || !trimmed.contains(":")) {
                continue;
            }
            int indent = line.length() - line.replaceAll("^\\s+", "").length();
            while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                indents.remove(indents.size() - 1);
                keys.remove(keys.size() - 1);
            }
            String key = trimmed.substring(0, trimmed.indexOf(':'));
            if (key.startsWith("'") || key.startsWith("\"")) {
                key = key.substring(1, key.length() - 1);
            }
            indents.add(indent);
            keys.add(key);
            if (String.join(".", keys).equals(path)) {
                String above = i > 0 ? lines[i - 1].trim() : "";
                return above.startsWith("# ") ? above.substring(2) : above;
            }
        }
        throw new AssertionError("no line for key " + path + " in:\n" + text);
    }

    private static List<String> nonCommentLines(String text) {
        List<String> kept = new ArrayList<>();
        for (String line : text.split("\\R")) {
            if (!line.trim().startsWith("#")) {
                kept.add(line);
            }
        }
        return kept;
    }
}
