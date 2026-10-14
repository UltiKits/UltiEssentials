package com.ultikits.plugins.essentials.config;

import com.ultikits.ultitools.annotations.command.CmdExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No alias this module ships in {@code features.commandalias.aliases} may have the name of one of this module's
 * own commands: the alias listener rewrites the typed command before Bukkit dispatches it, so a same-named alias
 * sends the command elsewhere (to vanilla {@code /gamemode} for the old {@code gmc}/{@code gms}/{@code gma}/
 * {@code gmsp} entries) and the module's own command, with its own permission, never runs (maintainer decisions
 * 2026-10-14). Every {@code @CmdExecutor} class compiled into the module's {@code commands} package is read.
 */
@DisplayName("shipped command aliases shadow no module command")
class ShippedAliasesShadowNoCommandTest {

    private static final String COMMANDS = "com.ultikits.plugins.essentials.commands";

    /** Alias name -> the command class that declares it. */
    private static Map<String, String> moduleCommandAliases() throws ClassNotFoundException, URISyntaxException {
        File root = new File(EssentialsConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File dir = new File(root, COMMANDS.replace('.', File.separatorChar));
        String[] files = dir.list((d, name) -> name.endsWith(".class") && !name.contains("$"));
        assertThat(files).as("control: the compiled commands package is found").isNotEmpty();
        Map<String, String> aliases = new TreeMap<>();
        for (String file : files) {
            Class<?> type = Class.forName(COMMANDS + "." + file.substring(0, file.length() - ".class".length()));
            CmdExecutor executor = type.getAnnotation(CmdExecutor.class);
            if (executor != null) {
                for (String alias : executor.alias()) {
                    aliases.put(alias, type.getSimpleName());
                }
            }
        }
        return aliases;
    }

    @Test
    @DisplayName("every game-mode shortcut is a module command, and no shipped alias has a module command's name")
    void noShippedAliasShadowsAModuleCommand() throws Exception {
        Map<String, String> commands = moduleCommandAliases();
        assertThat(commands).as("the four game-mode shortcuts are module commands")
                .containsEntry("gmc", "GmCreativeCommand")
                .containsEntry("gms", "GmSurvivalCommand")
                .containsEntry("gma", "GmAdventureCommand")
                .containsEntry("gmsp", "GmSpectatorCommand");

        Set<String> shipped = new TreeSet<>(new EssentialsConfig().getCommandAliases().keySet());
        Set<String> shadowed = new TreeSet<>(shipped);
        shadowed.retainAll(commands.keySet());
        assertThat(shadowed).as("shipped aliases %s against module commands %s", shipped, commands.keySet()).isEmpty();
        assertThat(shipped).as("what the shipped map holds now").isEqualTo(new TreeSet<>(Arrays.asList("day", "night")));
    }
}
