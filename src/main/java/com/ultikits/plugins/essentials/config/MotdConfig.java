package com.ultikits.plugins.essentials.config;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.ultikits.ultitools.abstracts.AbstractConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntity;
import com.ultikits.ultitools.annotations.ConfigEntry;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for server MOTD (Message of the Day).
 */
@Getter
@Setter
@ConfigEntity("config/motd.yml")
public class MotdConfig extends AbstractConfigEntity {

    @ConfigEntry(path = "motd.line1", comment = "MOTD 第一行")
    private String line1 = SHIPPED_LINE1;

    @ConfigEntry(path = "motd.line2", comment = "MOTD 第二行")
    private String line2 = SHIPPED_LINE2;

    @ConfigEntry(path = "motd.max-players", comment = "显示的最大玩家数 (-1 使用服务器默认)")
    private int maxPlayers = -1;

    /** The catalogue key of the first MOTD line's text in the server's language. */
    static final String LINE1_KEY = "essentials.motd.default_line1";

    /** The catalogue key of the second MOTD line's text in the server's language. */
    static final String LINE2_KEY = "essentials.motd.default_line2";

    /**
     * The first MOTD line every earlier version shipped; the Java default of {@link #line1}, and one
     * of the values {@link #materializeText} recognises as built-in text, compared byte for byte.
     */
    private static final String SHIPPED_LINE1 = "&6Welcome to our server!";

    /**
     * The second MOTD line every earlier version shipped; the Java default of {@link #line2}, and one
     * of the values {@link #materializeText} recognises as built-in text, compared byte for byte.
     */
    private static final String SHIPPED_LINE2 = "&7Powered by UltiTools";

    /**
     * Writes the two MOTD lines in the server's language (maintainer decision 2026-09-25,
     * UltiKits/UltiEssentials#26): each is replaced with {@code text}'s current text when it is still
     * built-in text -- the line an earlier version shipped, or this jar's text for it in any language
     * -- and differs from the current text. Any other value is the operator's and is kept. A blank
     * value is never materialized. Idempotent. Must run after the module's language is loaded
     * ({@code registerSelf()} and {@code onReload()}), never from a change listener; the caller saves
     * the file when this returns {@code true}.
     *
     * @param text catalogue key to text in the server's language, from this jar's own catalogue
     *             ({@code ConfigTextDefaults#jarLanguage}), so every value written is in the tracked set
     * @return whether any value was rewritten
     */
    public boolean materializeText(Function<String, String> text) {
        Map<String, Map<String, String>> jar = ConfigTextDefaults.jarCatalogues(MotdConfig.class);
        boolean changed = false;

        String newLine1 = ConfigTextDefaults.materialize(MotdConfig.class, "line1", line1,
                ConfigTextDefaults.currentText(text, "", LINE1_KEY),
                ConfigTextDefaults.tracked(jar, "", LINE1_KEY, SHIPPED_LINE1));
        if (!Objects.equals(newLine1, line1)) {
            line1 = newLine1;
            changed = true;
        }

        String newLine2 = ConfigTextDefaults.materialize(MotdConfig.class, "line2", line2,
                ConfigTextDefaults.currentText(text, "", LINE2_KEY),
                ConfigTextDefaults.tracked(jar, "", LINE2_KEY, SHIPPED_LINE2));
        if (!Objects.equals(newLine2, line2)) {
            line2 = newLine2;
            changed = true;
        }

        return changed;
    }

    public MotdConfig() {
        super("config/motd.yml");
    }
}
