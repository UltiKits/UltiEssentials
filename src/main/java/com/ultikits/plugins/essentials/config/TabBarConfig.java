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
 * Configuration for player tab list header and footer.
 */
@Getter
@Setter
@ConfigEntity("config/tabbar.yml")
public class TabBarConfig extends AbstractConfigEntity {

    // The Java default is the header every earlier version shipped: the framework writes it for a
    // missing key, and materializeText() then rewrites it in the server's language (maintainer
    // decision 2026-09-25, UltiKits/UltiEssentials#26).
    @ConfigEntry(path = "tabbar.header", comment = "Tab 栏头部")
    private String header = SHIPPED_HEADER;

    @ConfigEntry(path = "tabbar.footer", comment = "Tab 栏底部，支持 %online% %max% 变量")
    private String footer = SHIPPED_FOOTER;

    /** The catalogue key of the header's text in the server's language. */
    static final String HEADER_KEY = "essentials.tabbar.default_header";

    /** The catalogue key of the footer's text in the server's language. */
    static final String FOOTER_KEY = "essentials.tabbar.default_footer";

    /**
     * The header every earlier version shipped; the Java default of {@link #header}, and one of the
     * values {@link #materializeText} recognises as built-in text, compared byte for byte.
     */
    private static final String SHIPPED_HEADER = "&6=== 服务器名称 ===";

    /**
     * The footer every earlier version shipped; the Java default of {@link #footer}, and one of the
     * values {@link #materializeText} recognises as built-in text, compared byte for byte.
     */
    private static final String SHIPPED_FOOTER = "&7在线: &e%online%&7/&e%max%";

    /**
     * Writes the header and footer in the server's language (maintainer decision 2026-09-25,
     * UltiKits/UltiEssentials#26): each is replaced with {@code text}'s current text when it is still
     * built-in text -- the header/footer an earlier version shipped, or this jar's text for it in any
     * language -- and differs from the current text. Any other value is the operator's and is kept,
     * including a blank one: neither field carries {@code @NotEmpty}, a blank header or footer shows
     * nothing exactly as at {@code origin/master}, and a blank value is never materialized. Idempotent.
     * Must run after the module's language is loaded ({@code registerSelf()} and {@code onReload()}),
     * never from a change listener; the caller saves the file when this returns {@code true}.
     *
     * @param text catalogue key to text in the server's language, from this jar's own catalogue
     *             ({@code ConfigTextDefaults#jarLanguage}), so every value written is in the tracked set
     * @return whether any value was rewritten
     */
    public boolean materializeText(Function<String, String> text) {
        Map<String, Map<String, String>> jar = ConfigTextDefaults.jarCatalogues(TabBarConfig.class);
        boolean changed = false;

        String newHeader = ConfigTextDefaults.materialize(TabBarConfig.class, "header", header,
                ConfigTextDefaults.currentText(text, "", HEADER_KEY),
                ConfigTextDefaults.tracked(jar, "", HEADER_KEY, SHIPPED_HEADER));
        if (!Objects.equals(newHeader, header)) {
            header = newHeader;
            changed = true;
        }

        String newFooter = ConfigTextDefaults.materialize(TabBarConfig.class, "footer", footer,
                ConfigTextDefaults.currentText(text, "", FOOTER_KEY),
                ConfigTextDefaults.tracked(jar, "", FOOTER_KEY, SHIPPED_FOOTER));
        if (!Objects.equals(newFooter, footer)) {
            footer = newFooter;
            changed = true;
        }

        return changed;
    }

    public TabBarConfig() {
        super("config/tabbar.yml");
    }
}
