package com.ultikits.plugins.essentials.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Settings this module used to declare in {@code config/essentials.yml} and no longer reads, and the
 * warning that tells an operator whose file still holds one.
 * <p>
 * <b>Why a module needs this at all.</b> Deleting a {@code @ConfigEntry} field removes the key from
 * the code, not from anybody's disk. The framework writes a missing key's declared default into the
 * operator's file on first load and never deletes a key it no longer declares
 * ({@code AbstractConfigEntity#init}), so every server that has run this module still has the keys
 * below in its file, carrying whatever value its operator last set. Without this warning an edited
 * value would go on looking like a setting.
 * <p>
 * Each line names the module, the file and the key, and says where the setting's job went, because
 * "this does nothing" without "here is what does" is only half of what the operator has to act on.
 * <p>
 * The check is scoped to an explicit list rather than derived by comparing the file against the
 * declared fields: an unknown key in the file is not necessarily a key this module ever had (a typo,
 * a hand-written note, another tool's key), and reporting all of them would bury the real removals.
 * <p>
 * 本模块已删除、但仍可能留在运维 {@code config/essentials.yml} 中的配置项，以及提示运维的警告。
 */
public final class RemovedConfigKeys {

    /** The module name each warning names, matching this module's runtime name. */
    private static final String MODULE = "UltiEssentials";

    /** The file every key below lived in; used when the configuration cannot be read at all. */
    private static final String FILE = "config/essentials.yml";

    /**
     * One entry per removed key: the key path as it appears in the file, then where its job went.
     * The second element completes the sentence "... and can be deleted from the file -- %s."
     */
    private static final String[][] REMOVED = {
            {"features.wild.cooldown",
                    "/wild's cooldown is fixed at 60 seconds by the command itself and never read "
                            + "this setting; making the cooldown configurable is requested as "
                            + "UltiKits/UltiTools-Reborn#531 (UltiKits/UltiEssentials#27)"},
            {"features.recall.enabled",
                    "this module has no /recall command, so the setting never switched anything; "
                            + "the command is recorded as a feature request in "
                            + "UltiKits/UltiEssentials#53 (UltiKits/UltiEssentials#27)"},
    };

    private RemovedConfigKeys() {
    }

    /**
     * Returns one warning per removed key that is still present in the operator's file, in the
     * order the keys are listed above; an empty list when there is none.
     * <p>
     * Reads {@link EssentialsConfig#getConfig()}, the parsed file as it is on disk -- including keys
     * the entity no longer declares, which is exactly what a residual key is. A fresh install has
     * none of them, so a clean server gets an empty list.
     * <p>
     * A configuration that cannot be read yields a warning saying so rather than an empty list: a
     * check that silently does nothing and a server with no residual key would otherwise look the
     * same in the log.
     *
     * @param config the module's configuration after the framework has loaded it; may be null
     * @return the warnings to log, never null
     */
    public static List<String> warningsFor(EssentialsConfig config) {
        YamlConfiguration onDisk = config == null ? null : config.getConfig();
        if (onDisk == null) {
            return Collections.singletonList(String.format("%s: could not read %s, so it was not "
                    + "checked for settings this module has removed (UltiKits/UltiEssentials#27)",
                    MODULE, FILE));
        }
        String file = config.getConfigFilePath();
        List<String> warnings = new ArrayList<>();
        for (String[] removed : REMOVED) {
            if (onDisk.contains(removed[0])) {
                warnings.add(String.format("%s: '%s' in %s has no effect and can be deleted from the "
                        + "file -- %s.", MODULE, removed[0], file, removed[1]));
            }
        }
        return warnings;
    }
}
