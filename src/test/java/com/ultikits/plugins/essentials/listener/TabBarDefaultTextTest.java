package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.config.TabBarConfig;
import com.ultikits.plugins.essentials.i18n.CatalogueText;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.verify;

/**
 * A blank tab-list header or footer shows the language file's default in the server's language;
 * a customised one is shown unchanged (maintainer ruling 2026-09-24 (d)).
 */
@DisplayName("Tab-list header and footer defaults follow the language setting")
class TabBarDefaultTextTest {

    private TabBarListener listener;
    private TabBarConfig tabBar;
    private Player player;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        tabBar = new TabBarConfig();
        listener = new TabBarListener();
        EssentialsTestHelper.setField(listener, "tabBarConfig", tabBar);
        player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private static int online() {
        return org.bukkit.Bukkit.getOnlinePlayers().size();
    }

    private static int max() {
        return org.bukkit.Bukkit.getMaxPlayers();
    }

    private void speak(String language) throws Exception {
        EssentialsTestHelper.setField(listener, "plugin", CatalogueText.plugin(language));
    }

    @Test
    @DisplayName("blank header and footer: the catalogue's text, in English under en")
    void blankInEnglish() throws Exception {
        speak("en");
        tabBar.setHeader("");
        tabBar.setFooter(" ");

        listener.updateTabBar(player);

        verify(player).setPlayerListHeaderFooter("§6=== Server Name ===", "§7Online: §e" + online() + "§7/§e" + max());
    }

    @Test
    @DisplayName("blank header and footer: the catalogue's text, in Chinese under zh")
    void blankInChinese() throws Exception {
        speak("zh");
        tabBar.setHeader("");
        tabBar.setFooter("");

        listener.updateTabBar(player);

        verify(player).setPlayerListHeaderFooter("§6=== 服务器名称 ===",
                "§7在线: §e" + online() + "§7/§e" + max());
    }

    @Test
    @DisplayName("a customised header and footer are shown unchanged")
    void customisedIsKept() throws Exception {
        speak("en");
        tabBar.setHeader("&aWelcome");
        tabBar.setFooter("&7%online% online");

        listener.updateTabBar(player);

        verify(player).setPlayerListHeaderFooter("§aWelcome", "§7" + online() + " online");
    }
}
