package com.ultikits.plugins.essentials.config;

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

    /**
     * Blank by default: a blank header shows the language file's {@code essentials.tabbar.default_header}
     * in the server's language, resolved when the tab list is drawn (maintainer ruling 2026-09-24 (d)).
     */
    @ConfigEntry(path = "tabbar.header", comment = "Tab 栏头部（留空则使用语言文件中的默认文本）")
    private String header = "";

    /**
     * Blank by default, like {@link #header}; the language file's text is
     * {@code essentials.tabbar.default_footer}.
     */
    @ConfigEntry(path = "tabbar.footer", comment = "Tab 栏底部，支持 %online% %max% 变量（留空则使用语言文件中的默认文本）")
    private String footer = "";

    /** The header every earlier version shipped as the default; one value in every version. */
    private static final String SHIPPED_HEADER = "&6=== 服务器名称 ===";

    /** The footer every earlier version shipped as the default; one value in every version. */
    private static final String SHIPPED_FOOTER = "&7在线: &e%online%&7/&e%max%";

    /**
     * Blanks a header or footer that is exactly the default an earlier version shipped, so the language
     * file's text takes over; any other value is the operator's and is kept. Idempotent. The caller
     * saves the file when this returns true.
     *
     * @return whether a value was rewritten
     */
    public boolean migrateLegacyDefaults() {
        boolean changed = false;
        if (SHIPPED_HEADER.equals(header)) {
            header = "";
            changed = true;
        }
        if (SHIPPED_FOOTER.equals(footer)) {
            footer = "";
            changed = true;
        }
        return changed;
    }

    public TabBarConfig() {
        super("config/tabbar.yml");
    }
}
