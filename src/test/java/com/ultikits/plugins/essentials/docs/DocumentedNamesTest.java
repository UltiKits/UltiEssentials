package com.ultikits.plugins.essentials.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The feature inventory and the real-machine checklist name things the way the module does, so a
 * tester following a row can find them.
 */
@DisplayName("FEATURES.md and UAT-CHECKLIST.md name keys and commands as the module does")
class DocumentedNamesTest {

    private static String read(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("The scheduled command list is named by its full key, features.scheduled-commands.commands (#42)")
    void scheduledCommandListIsNamedByItsFullKey() throws Exception {
        Pattern bare = Pattern.compile("(?<![.\\w])`scheduled-commands\\.commands`");
        for (String file : new String[]{"FEATURES.md", "UAT-CHECKLIST.md"}) {
            String text = read(file);
            assertThat(text).as("control: %s names the full key", file).contains("`features.scheduled-commands.commands`");
            Matcher matcher = bare.matcher(text);
            assertThat(matcher.find()).as("%s names the list by a key that does not exist", file).isFalse();
        }
    }

    @Test
    @DisplayName("/ban, /banlist and /scoreboard are documented as deliberate takeovers of the vanilla commands, which stay reachable as minecraft:<name> (#61)")
    void vanillaTakeoversAreDocumented() throws Exception {
        String features = read("FEATURES.md");
        String readme = read("README.md");
        String[][] rows = {
            {"| ultiessentials.ban.ban |", "minecraft:ban"},
            {"| ultiessentials.banlist.list |", "minecraft:banlist"},
            {"| ultiessentials.scoreboard.toggle |", "minecraft:scoreboard"},
        };
        for (String[] row : rows) {
            int start = features.indexOf(row[0]);
            assertThat(start).as("control: the row %s exists", row[0]).isNotNegative();
            String line = features.substring(start, features.indexOf('\n', start));
            assertThat(line).as("%s states the takeover", row[0]).contains("deliberately takes over").contains(row[1]);
            assertThat(readme).as("the README names %s", row[1]).contains(row[1]);
        }
    }
}
