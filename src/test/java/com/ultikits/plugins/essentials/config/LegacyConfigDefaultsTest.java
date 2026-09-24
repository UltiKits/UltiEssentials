package com.ultikits.plugins.essentials.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrade handling of the four text defaults that used to be Chinese (maintainer ruling 2026-09-24
 * (d)): a value exactly equal to a default an earlier version shipped is rewritten to blank, so the
 * language file's text takes over; anything else is the operator's and is kept; a blank value is not
 * rewritten again.
 * <p>
 * The migration is reached by name so this class compiles, and fails, on a tree without it.
 */
@DisplayName("Legacy shipped config defaults are recognised and blanked on upgrade")
class LegacyConfigDefaultsTest {

    /** The scoreboard lines every earlier version shipped (read from the config class's git history). */
    static final List<String> SHIPPED_LINES = Arrays.asList(
            "&7欢迎, &e%player_name%",
            "&7",
            "&6在线玩家: &f%online_players%/%max_players%",
            "&6当前世界: &f%player_world%",
            "&7",
            "&6生命值: &c%player_health%",
            "&6饥饿值: &a%player_food%",
            "&6等级: &e%player_level%",
            "&7",
            "&ewww.example.com");

    private static boolean migrate(Object config) throws Exception {
        Method m = config.getClass().getMethod("migrateLegacyDefaults");
        return (Boolean) m.invoke(config);
    }

    @Test
    @DisplayName("essentials.yml: the shipped scoreboard title and lines are blanked, once")
    void shippedScoreboardDefaultsAreBlanked() throws Exception {
        EssentialsConfig config = new EssentialsConfig();
        config.setScoreboardTitle("&6&l服务器信息");
        config.setScoreboardLines(new ArrayList<>(SHIPPED_LINES));

        assertThat(migrate(config)).isTrue();
        assertThat(config.getScoreboardTitle()).isEmpty();
        assertThat(config.getScoreboardLines()).isEmpty();
        assertThat(migrate(config)).as("a blank value is not rewritten again").isFalse();
    }

    @Test
    @DisplayName("essentials.yml: a customised title and customised lines are kept")
    void customisedScoreboardValuesAreKept() throws Exception {
        EssentialsConfig config = new EssentialsConfig();
        config.setScoreboardTitle("&6&lMy Server");
        List<String> lines = new ArrayList<>(SHIPPED_LINES);
        lines.set(9, "&emc.example.org");
        config.setScoreboardLines(lines);

        assertThat(migrate(config)).isFalse();
        assertThat(config.getScoreboardTitle()).isEqualTo("&6&lMy Server");
        assertThat(config.getScoreboardLines()).isEqualTo(lines);
    }

    @Test
    @DisplayName("tabbar.yml: the shipped header and footer are blanked, a customised one is kept")
    void tabBarDefaults() throws Exception {
        TabBarConfig shipped = new TabBarConfig();
        shipped.setHeader("&6=== 服务器名称 ===");
        shipped.setFooter("&7在线: &e%online%&7/&e%max%");
        assertThat(migrate(shipped)).isTrue();
        assertThat(shipped.getHeader()).isEmpty();
        assertThat(shipped.getFooter()).isEmpty();
        assertThat(migrate(shipped)).isFalse();

        TabBarConfig customised = new TabBarConfig();
        customised.setHeader("&aMy header");
        customised.setFooter("&7在线: &e%online%&7/&e%max%");
        assertThat(migrate(customised)).as("only the footer is the shipped default").isTrue();
        assertThat(customised.getHeader()).isEqualTo("&aMy header");
        assertThat(customised.getFooter()).isEmpty();
    }

    @Test
    @DisplayName("a fresh install's defaults are blank, never null")
    void freshDefaultsAreBlank() {
        EssentialsConfig config = new EssentialsConfig();
        TabBarConfig tabBar = new TabBarConfig();
        assertThat(config.getScoreboardTitle()).isNotNull().isEmpty();
        assertThat(config.getScoreboardLines()).isNotNull().isEmpty();
        assertThat(tabBar.getHeader()).isNotNull().isEmpty();
        assertThat(tabBar.getFooter()).isNotNull().isEmpty();
    }
}
