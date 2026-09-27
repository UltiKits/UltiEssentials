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
}
