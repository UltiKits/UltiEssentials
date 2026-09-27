package com.ultikits.plugins.essentials;

import com.ultikits.plugins.essentials.service.EntityIdBackfillService;
import com.ultikits.plugins.essentials.service.NamePrefixService;
import com.ultikits.plugins.essentials.service.ScheduledCommandService;
import com.ultikits.plugins.essentials.service.ScoreboardService;
import com.ultikits.plugins.essentials.service.TeleportService;
import com.ultikits.plugins.essentials.service.TpaService;
import com.ultikits.plugins.essentials.utils.ModuleJarFixture;
import com.ultikits.plugins.essentials.utils.MockBukkitHelper;
import com.ultikits.plugins.essentials.utils.TestHelper;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.context.SimpleContainer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The module's main class, constructed the way a server constructs it -- from a module jar holding
 * its {@code plugin.yml} -- and driven through its lifecycle hooks: enabling repairs stored primary
 * keys, a reload restarts the three task-owning services, and an unload shuts every service down
 * (UltiKits/UltiEssentials#21).
 */
@DisplayName("UltiEssentials main class lifecycle (#21)")
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class UltiEssentialsTest {

    @TempDir
    Path folder;

    private UltiToolsPlugin plugin;
    private SimpleContainer container;

    @BeforeEach
    void setUp() throws Exception {
        // Another test class may have installed a mocked server through Bukkit's static field.
        MockBukkitHelper.clearForeignServer();
        MockBukkit.mock();
        TestHelper.mockUltiToolsInstance();
        UltiTools framework = UltiTools.getInstance();
        lenient().when(framework.getLogger()).thenReturn(Logger.getLogger("UltiEssentialsTest"));
        YamlConfiguration frameworkConfig = new YamlConfiguration();
        frameworkConfig.set("language", "en");
        lenient().when(framework.getConfig()).thenReturn(frameworkConfig);
        lenient().when(framework.getDataFolder()).thenReturn(folder.resolve("UltiTools").toFile());
        // Configuration registration is the framework's own step and is tested there.
        lenient().when(framework.getConfigManager()).thenReturn(mock(com.ultikits.ultitools.manager.ConfigManager.class));

        plugin = ModuleJarFixture.construct("com.ultikits.plugins.essentials.UltiEssentials", folder);

        container = new SimpleContainer();
        container.registerType(EntityIdBackfillService.class, mock(EntityIdBackfillService.class));
        container.registerType(ScheduledCommandService.class, mock(ScheduledCommandService.class));
        container.registerType(ScoreboardService.class, mock(ScoreboardService.class));
        container.registerType(NamePrefixService.class, mock(NamePrefixService.class));
        container.registerType(TeleportService.class, mock(TeleportService.class));
        container.registerType(TpaService.class, mock(TpaService.class));
        plugin.setContext(container);
    }

    @AfterEach
    void tearDown() {
        MockBukkitHelper.safeUnmock();
    }

    private void invokeHook(String name) throws Exception {
        Method hook = UltiToolsPlugin.class.getDeclaredMethod(name);
        hook.setAccessible(true); // NOPMD - the hooks are protected; the framework calls them the same way
        hook.invoke(plugin);
    }

    @Test
    @DisplayName("The module is constructed from its plugin.yml, named and versioned as that file says")
    void constructedFromItsPluginYml() {
        assertThat(plugin.getPluginName()).isEqualTo("UltiEssentials");
        assertThat(plugin.getMainClass()).isEqualTo("com.ultikits.plugins.essentials.UltiEssentials");
    }

    @Test
    @DisplayName("Enabling reports success and repairs stored primary keys")
    void registerSelfRepairsStoredKeys() throws Exception {
        assertThat(plugin.registerSelf()).isTrue();

        verify(container.getBean(EntityIdBackfillService.class)).run();
    }

    @Test
    @DisplayName("A reload restarts the scheduled-command, scoreboard and name-prefix services")
    void onReloadRestartsTheTaskServices() throws Exception {
        invokeHook("onReload");

        verify(container.getBean(ScheduledCommandService.class)).reload();
        verify(container.getBean(ScoreboardService.class)).reload();
        verify(container.getBean(NamePrefixService.class)).reload();
    }

    @Test
    @DisplayName("An unload shuts every service down")
    void onUnregisterShutsEveryServiceDown() throws Exception {
        invokeHook("onUnregister");

        verify(container.getBean(ScheduledCommandService.class)).shutdown();
        verify(container.getBean(ScoreboardService.class)).shutdown();
        verify(container.getBean(NamePrefixService.class)).shutdown();
        verify(container.getBean(TeleportService.class)).shutdown();
        verify(container.getBean(TpaService.class)).shutdown();
    }
}
