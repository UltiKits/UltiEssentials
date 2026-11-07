package com.ultikits.plugins.essentials.config;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;

import java.util.Collections;
import java.util.List;

/**
 * Compatibility entry point for the module's removed-setting check.
 * The only formerly removed key, features.recall.enabled, is live again with #53. There are
 * currently no removed settings to warn about; neither recall nor wild cooldown may be reported
 * as having no effect. Retains the existing method and missing-configuration diagnostic.
 */
public final class RemovedConfigKeys {
    private RemovedConfigKeys() {
    }

    /**
     * Returns no removed-setting warnings for a loaded configuration.
     *
     * @param config the loaded configuration, or null when it could not be resolved
     * @param plugin the module providing the diagnostic's translation
     * @return an empty list, or the existing missing-configuration warning
     */
    public static List<String> warningsFor(EssentialsConfig config, UltiToolsPlugin plugin) {
        if (config == null) {
            return Collections.singletonList(String.format(plugin.i18n("essentials.warn.removed_unreadable"),
                    "UltiEssentials", "config/essentials.yml"));
        }
        return Collections.emptyList();
    }
}
