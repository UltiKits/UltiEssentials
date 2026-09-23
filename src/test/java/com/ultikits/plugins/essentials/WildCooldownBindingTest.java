package com.ultikits.plugins.essentials;

import com.ultikits.plugins.essentials.commands.WildCommand;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import com.ultikits.plugins.essentials.utils.TestHelper;
import com.ultikits.ultitools.UltiTools;
import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.ultitools.abstracts.command.CommandContext;
import com.ultikits.ultitools.abstracts.command.validation.validators.CooldownValidator;
import com.ultikits.ultitools.annotations.command.CmdCD;
import com.ultikits.ultitools.context.SimpleContainer;
import com.ultikits.ultitools.exceptions.ConfigurationException;
import com.ultikits.ultitools.manager.ConfigManager;
import com.ultikits.ultitools.manager.PluginManager;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code /wild}'s cooldown follows {@code features.wild.cooldown} in {@code config/essentials.yml}
 * (UltiKits/UltiEssentials#27, through the framework's config-bound {@code @CmdCD},
 * UltiKits/UltiTools-Reborn#531).
 * <p>
 * Each case loads {@code config/essentials.yml} through the framework's real {@link ConfigManager},
 * resolves the module's config bindings the way a module load does (the framework's own
 * load-time step, reached reflectively because it is package-private), and then asks the executor's
 * own {@link CooldownValidator} -- the one the command dispatch uses -- what a second {@code /wild}
 * gets. The assertions are on the cooldown a player actually meets, never on the annotation alone.
 * <p>
 * {@code /wild} 的冷却时间跟随配置项 {@code features.wild.cooldown}：0 表示不冷却，重载后新值生效。
 */
@DisplayName("/wild cooldown follows features.wild.cooldown (UltiKits/UltiEssentials#27, UltiTools-Reborn#531)")
class WildCooldownBindingTest {

    @TempDir
    Path moduleFolder;

    private File configFile;
    private UltiEssentials plugin;
    private ConfigManager configManager;
    private WildCommand wild;
    private Method wildTeleport;
    private Object previousUltiTools;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();
        previousUltiTools = ultiToolsField().get(null);
        TestHelper.mockUltiToolsInstance();
        configManager = new ConfigManager();
        when(UltiTools.getInstance().getConfigManager()).thenReturn(configManager);
        // The reload step runs only on the main thread.
        lenient().when(EssentialsTestHelper.getMockServer().isPrimaryThread()).thenReturn(true);
        configFile = moduleFolder.resolve("config").resolve("essentials.yml").toFile();
        assertThat(configFile.getParentFile().mkdirs()).isTrue();
        wildTeleport = WildCommand.class.getMethod("wildTeleport", Player.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        ultiToolsField().set(null, previousUltiTools);
        EssentialsTestHelper.tearDown();
    }

    // ==================== the configured value applies ====================

    @Test
    @DisplayName("A configured cooldown of 5 seconds is what a second /wild meets, not a fixed 60")
    void configuredCooldownApplies() throws Exception {
        load("features:\n  wild:\n    cooldown: 5\n");
        Player player = player();

        useWild(player);

        assertThat(validator().validate(context(player)).isValid()).isFalse();
        assertThat(remaining(player)).isBetween(5L, 6L);
    }

    @Test
    @DisplayName("With the key unset, the declared default of 60 seconds applies and is written into the file")
    void declaredDefaultIsSixtySeconds() throws Exception {
        load("");
        Player player = player();

        useWild(player);

        assertThat(remaining(player)).isBetween(60L, 61L);
        assertThat(YamlConfiguration.loadConfiguration(configFile).getInt("features.wild.cooldown", -1)).isEqualTo(60);
    }

    @Test
    @DisplayName("A configured 0 means no cooldown: an immediate second /wild is allowed")
    void zeroMeansNoCooldown() throws Exception {
        load("features:\n  wild:\n    cooldown: 0\n");
        Player player = player();

        useWild(player);

        assertThat(validator().validate(context(player)).isValid()).isTrue();
    }

    @Test
    @DisplayName("A negative value refuses the module at load, naming the file and the value")
    void negativeValueIsRefusedAtLoad() throws Exception {
        write("features:\n  wild:\n    cooldown: -1\n");

        assertThatThrownBy(this::load)
                .isInstanceOf(ConfigurationException.class)
                .hasMessageContaining("config/essentials.yml")
                .hasMessageContaining("-1");
    }

    // ==================== /ul reload ====================

    @Test
    @DisplayName("/ul reload applies a new value to the next /wild; a cooldown already running keeps its end time")
    void reloadAppliesTheNewValue() throws Exception {
        load("features:\n  wild:\n    cooldown: 5\n");
        Player before = player();
        useWild(before);

        write("features:\n  wild:\n    cooldown: 30\n");
        configManager.reloadConfigs(plugin);
        new PluginManager().applyReloadedConfigBindings(plugin);

        Player after = player();
        useWild(after);
        assertThat(remaining(after)).isBetween(30L, 31L);
        assertThat(remaining(before)).isBetween(1L, 6L);
    }

    @Test
    @DisplayName("An invalid value on /ul reload is refused and the running cooldown is kept")
    void invalidValueOnReloadKeepsTheRunningOne() throws Exception {
        load("features:\n  wild:\n    cooldown: 5\n");

        write("features:\n  wild:\n    cooldown: -3\n");
        assertThatThrownBy(() -> configManager.reloadConfigs(plugin)).isInstanceOf(ConfigurationException.class);
        new PluginManager().applyReloadedConfigBindings(plugin);

        Player player = player();
        useWild(player);
        assertThat(remaining(player)).isBetween(5L, 6L);
    }

    // ==================== the declaration ====================

    @Test
    @DisplayName("/wild's @CmdCD is bound to features.wild.cooldown with no literal, and plugin.yml declares api-version 630 or higher")
    void wildIsBoundAndTheModuleDeclaresTheFloor() throws Exception {
        CmdCD cooldown = wildTeleport.getAnnotation(CmdCD.class);

        assertThat(cooldown).isNotNull();
        assertThat(cooldown.config()).isEqualTo(EssentialsConfig.class);
        assertThat(cooldown.key()).isEqualTo("features.wild.cooldown");
        assertThat(cooldown.value()).isZero();
        try (InputStream in = WildCooldownBindingTest.class.getResourceAsStream("/plugin.yml")) {
            assertThat(in).isNotNull();
            YamlConfiguration pluginYml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            assertThat(pluginYml.getInt("api-version")).isGreaterThanOrEqualTo(630);
        }
    }

    // ==================== helpers ====================

    private void load(String yaml) throws Exception {
        write(yaml);
        load();
    }

    private void load() throws Exception {
        boot();
        resolveBindings();
    }

    private void boot() throws Exception {
        plugin = mock(UltiEssentials.class, CALLS_REAL_METHODS);
        setResourceFolderPath(plugin, moduleFolder.toString());
        doReturn("UltiEssentials").when(plugin).getPluginName();
        doReturn(630).when(plugin).getMinUltiToolsVersion();

        EssentialsConfig config = new EssentialsConfig();
        configManager.register(plugin, config);

        wild = new WildCommand(config);
        SimpleContainer container = new SimpleContainer();
        container.registerType(UltiToolsPlugin.class, plugin);
        // A singleton, as the component scan registers an executor: the framework's binding step
        // enumerates the container's CommandExecutor beans.
        container.registerSingleton("wildCommand", wild);
        EssentialsTestHelper.setField(wild, "plugin", plugin);
        plugin.setContext(container);
    }

    /** The framework's load-time binding step, package-private in {@code PluginManager}. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // reaches the framework's package-private load step
    private void resolveBindings() throws Exception {
        Method step = PluginManager.class.getDeclaredMethod("validateConfigBindings",
                UltiToolsPlugin.class, SimpleContainer.class);
        step.setAccessible(true);
        try {
            step.invoke(null, plugin, plugin.getContext());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw (Error) cause;
        }
    }

    private void useWild(Player player) {
        CommandContext context = context(player);
        assertThat(validator().validate(context).isValid()).isTrue();
        validator().onComplete(context, true);
    }

    private long remaining(Player player) {
        return validator().getRemainingCooldown(player.getUniqueId(), wildTeleport.toString());
    }

    private CooldownValidator validator() {
        return wild.getCooldownValidator();
    }

    private CommandContext context(Player player) {
        return CommandContext.builder()
                .sender(player)
                .command(mock(Command.class))
                .alias("wild")
                .rawArgs(new String[0])
                .matchedMethod(wildTeleport)
                .executorClass(WildCommand.class)
                .executor(wild)
                .build();
    }

    private static Player player() {
        return EssentialsTestHelper.createMockPlayer("Wanderer", UUID.randomUUID());
    }

    private void write(String content) throws Exception {
        Files.write(configFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // saves and restores the framework singleton the test replaces
    private static Field ultiToolsField() throws Exception {
        Field field = UltiTools.class.getDeclaredField("ultiTools");
        field.setAccessible(true);
        return field;
    }

    // No supported setter exists: the field is private, its accessors are protected final, and the
    // only public constructor that takes the path also builds the language catalogue from disk.
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration") // points the module's config folder at a temp directory
    private static void setResourceFolderPath(UltiToolsPlugin target, String path) throws Exception {
        Field field = UltiToolsPlugin.class.getDeclaredField("resourceFolderPath");
        field.setAccessible(true);
        field.set(target, path);
    }
}
