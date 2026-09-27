package com.ultikits.plugins.essentials.commands;

import com.ultikits.ultitools.annotations.command.CmdExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This module claims no command label that belongs to someone else: no extra alias equal to a
 * vanilla command label (maintainer decision 2026-09-27, UltiKits/UltiEssentials#48, #60), no alias
 * shared with the framework, UltiLogin or UltiSideBar (UltiKits/UltiEssentials#62), and a vanilla
 * label as a command's own name only where the maintainer kept it as a deliberate takeover
 * ({@code /ban}, {@code /banlist}, {@code /scoreboard}, UltiKits/UltiEssentials#61).
 */
@DisplayName("Command labels this module claims (#48, #60, #61, #62)")
class CommandLabelOwnershipTest {

    /** The command labels Paper 1.21.11's vanilla command set registers. */
    private static final Set<String> VANILLA_LABELS = new HashSet<>(Arrays.asList(
            "advancement", "attribute", "ban", "ban-ip", "banlist", "bossbar", "clear", "clone",
            "damage", "data", "datapack", "debug", "defaultgamemode", "deop", "dialog", "difficulty",
            "effect", "enchant", "execute", "experience", "xp", "fetchprofile", "fill", "fillbiome",
            "forceload", "function", "gamemode", "gamerule", "give", "help", "item", "jfr", "kick",
            "kill", "list", "locate", "loot", "me", "msg", "tell", "w", "op", "pardon", "pardon-ip",
            "particle", "perf", "place", "playsound", "publish", "random", "recipe", "reload",
            "return", "ride", "rotate", "save-all", "save-off", "save-on", "say", "schedule",
            "scoreboard", "seed", "setblock", "setidletimeout", "setworldspawn", "spawnpoint",
            "spectate", "spreadplayers", "stop", "stopsound", "summon", "tag", "team", "teammsg", "tm",
            "teleport", "tp", "tellraw", "test", "tick", "time", "title", "transfer", "trigger",
            "version", "waypoint", "weather", "whitelist", "worldborder"));

    /** Vanilla labels the maintainer kept as this module's own command names, documented as takeovers. */
    private static final Set<String> DELIBERATE_TAKEOVERS = new HashSet<>(Arrays.asList("ban", "banlist", "scoreboard"));

    /** Labels other UltiKits components own: the framework's /ul, UltiLogin's /login, UltiSideBar's /sidebar. */
    private static final Set<String> OTHER_COMPONENTS = new HashSet<>(Arrays.asList(
            "ul", "ultitools", "ulti", "login", "l", "sidebar", "sb"));

    private static List<CmdExecutor> executors() throws Exception {
        File folder = new File("src/main/java/com/ultikits/plugins/essentials/commands");
        String[] files = folder.list((dir, name) -> name.endsWith(".java"));
        assertThat(files).as("the command sources were found").isNotEmpty();
        List<CmdExecutor> executors = new ArrayList<>();
        for (String file : files) {
            Class<?> type = Class.forName("com.ultikits.plugins.essentials.commands." + file.replace(".java", ""));
            CmdExecutor executor = type.getAnnotation(CmdExecutor.class);
            if (executor != null) {
                executors.add(executor);
            }
        }
        return executors;
    }

    @Test
    @DisplayName("No extra alias is a vanilla command label")
    void noAliasIsAVanillaLabel() throws Exception {
        List<String> clashes = new ArrayList<>();
        for (CmdExecutor executor : executors()) {
            String[] labels = executor.alias();
            for (int i = 1; i < labels.length; i++) {
                if (VANILLA_LABELS.contains(labels[i])) {
                    clashes.add(labels[0] + " -> " + labels[i]);
                }
            }
        }
        assertThat(clashes).isEmpty();
    }

    @Test
    @DisplayName("A vanilla label is a command's own name only for the kept takeovers /ban, /banlist, /scoreboard")
    void onlyTheKeptTakeoversUseAVanillaLabelAsTheirName() throws Exception {
        Set<String> names = new HashSet<>();
        for (CmdExecutor executor : executors()) {
            if (VANILLA_LABELS.contains(executor.alias()[0])) {
                names.add(executor.alias()[0]);
            }
        }
        assertThat(names).isEqualTo(DELIBERATE_TAKEOVERS);
    }

    @Test
    @DisplayName("No label is shared with the framework, UltiLogin or UltiSideBar")
    void noLabelIsSharedWithAnotherComponent() throws Exception {
        List<String> clashes = new ArrayList<>();
        for (CmdExecutor executor : executors()) {
            for (String label : executor.alias()) {
                if (OTHER_COMPONENTS.contains(label)) {
                    clashes.add(executor.alias()[0] + " -> " + label);
                }
            }
        }
        assertThat(clashes).isEmpty();
    }
}
