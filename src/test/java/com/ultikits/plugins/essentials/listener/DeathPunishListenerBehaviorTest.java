package com.ultikits.plugins.essentials.listener;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.plugins.essentials.utils.EssentialsTestHelper;
import org.bukkit.entity.Player;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DeathPunishListener}'s item-drop selection ({@code processItemDrop}) -- a
 * decision surface the existing {@code DeathPunishListenerMockitoTest} never reaches at all
 * (none of its tests enable {@code deathPunishItemDropEnabled}).
 * <p>
 * Note on {@code keepOtherItems}: reading the source is required here, because the flag's name
 * is the opposite of its effect. When {@code true}, the method filters {@code drops} down to
 * ONLY the items selected by the drop-chance roll (the comment directly above that line reads
 * "Keep only dropped items (remove others)"); when {@code false}, {@code retainAll} is never
 * called and the original drop list is left completely untouched. The tests below assert that
 * actual, source-verified behaviour rather than the name's intuitive-but-wrong reading.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@DisplayName("DeathPunishListener item-drop selection Tests")
class DeathPunishListenerBehaviorTest {

    private DeathPunishListener listener;
    private EssentialsConfig config;
    private DamageSource damageSource;

    @BeforeEach
    void setUp() throws Exception {
        EssentialsTestHelper.setUp();

        listener = new DeathPunishListener();
        config = new EssentialsConfig();
        EssentialsTestHelper.setField(listener, "config", config);

        // Paper 1.21's PlayerDeathEvent constructors all require a DamageSource; a mock is enough
        // here since none of these tests assert on the death cause itself.
        damageSource = mock(DamageSource.class);

        config.setDeathPunishEnabled(true);
        config.setDeathPunishWorldWhitelist(Collections.emptyList());
        config.setDeathPunishMoneyEnabled(false);
        config.setDeathPunishExpEnabled(false);
        config.setDeathPunishCommandEnabled(false);
        config.setDeathPunishItemDropEnabled(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        EssentialsTestHelper.tearDown();
    }

    private Player createDeathPlayer() {
        Player player = EssentialsTestHelper.createMockPlayer("Steve", UUID.randomUUID());
        org.bukkit.World world = EssentialsTestHelper.createMockWorld("world");
        lenient().when(player.getWorld()).thenReturn(world);
        lenient().when(player.hasPermission("ultiessentials.deathpunish.bypass")).thenReturn(false);
        lenient().when(player.getTotalExperience()).thenReturn(0);
        return player;
    }

    private ItemStack mockItem(org.bukkit.Material material) {
        ItemStack item = mock(ItemStack.class);
        lenient().when(item.getType()).thenReturn(material);
        return item;
    }

    @Nested
    @DisplayName("Whitelist and drop-chance selection")
    class SelectionTests {

        @Test
        @DisplayName("A whitelisted item type is never counted toward the drop count")
        void whitelistedItemIsSkipped() {
            config.setDeathPunishItemDropChance(100.0); // would always be selected if not whitelisted
            config.setDeathPunishItemWhitelist(Collections.singletonList("DIAMOND"));
            config.setDeathPunishKeepOtherItems(false);

            Player player = createDeathPlayer();
            List<ItemStack> drops = new ArrayList<>(Collections.singletonList(mockItem(org.bukkit.Material.DIAMOND)));
            PlayerDeathEvent event = new PlayerDeathEvent(player, damageSource, drops, 0, "died");

            listener.onPlayerDeath(event);

            // whitelisted item never enters toDrop, so the message never mentions any drop count
            verify(player, never()).sendMessage(contains("物品掉落"));
        }

        @Test
        @DisplayName("A non-whitelisted item within the drop chance is counted and reported")
        void nonWhitelistedItemWithinChanceIsCounted() {
            config.setDeathPunishItemDropChance(100.0); // guarantee the "within chance" branch
            config.setDeathPunishItemWhitelist(Collections.emptyList());
            config.setDeathPunishKeepOtherItems(false);

            Player player = createDeathPlayer();
            List<ItemStack> drops = new ArrayList<>(Collections.singletonList(mockItem(org.bukkit.Material.STONE)));
            PlayerDeathEvent event = new PlayerDeathEvent(player, damageSource, drops, 0, "died");

            listener.onPlayerDeath(event);

            verify(player).sendMessage(contains("1件物品掉落"));
        }

        @Test
        @DisplayName("A drop chance of zero never counts any non-whitelisted item")
        void zeroChanceNeverCounts() {
            config.setDeathPunishItemDropChance(0.0); // guarantee the "outside chance" branch
            config.setDeathPunishItemWhitelist(Collections.emptyList());
            config.setDeathPunishKeepOtherItems(false);

            Player player = createDeathPlayer();
            List<ItemStack> drops = new ArrayList<>(Collections.singletonList(mockItem(org.bukkit.Material.STONE)));
            PlayerDeathEvent event = new PlayerDeathEvent(player, damageSource, drops, 0, "died");

            listener.onPlayerDeath(event);

            verify(player, never()).sendMessage(contains("物品掉落"));
        }
    }

    @Nested
    @DisplayName("keepOtherItems drop-list mutation")
    class KeepOtherItemsTests {

        @Test
        @DisplayName("keepOtherItems=false leaves the original drop list completely untouched")
        void keepOtherItemsFalseLeavesListUntouched() {
            config.setDeathPunishItemDropChance(0.0); // this item is never selected into toDrop
            config.setDeathPunishItemWhitelist(Collections.emptyList());
            config.setDeathPunishKeepOtherItems(false);

            Player player = createDeathPlayer();
            ItemStack notSelected = mockItem(org.bukkit.Material.STONE);
            List<ItemStack> drops = new ArrayList<>(Collections.singletonList(notSelected));
            PlayerDeathEvent event = new PlayerDeathEvent(player, damageSource, drops, 0, "died");

            listener.onPlayerDeath(event);

            // retainAll is never called: the un-selected item stays in the drop list
            assertThat(event.getDrops()).contains(notSelected);
        }

        @Test
        @DisplayName("keepOtherItems=true filters the drop list down to only the selected items")
        void keepOtherItemsTrueFiltersToSelectedOnly() {
            config.setDeathPunishItemDropChance(0.0); // this item is never selected into toDrop
            config.setDeathPunishItemWhitelist(Collections.emptyList());
            config.setDeathPunishKeepOtherItems(true);

            Player player = createDeathPlayer();
            ItemStack notSelected = mockItem(org.bukkit.Material.STONE);
            List<ItemStack> drops = new ArrayList<>(Collections.singletonList(notSelected));
            PlayerDeathEvent event = new PlayerDeathEvent(player, damageSource, drops, 0, "died");

            listener.onPlayerDeath(event);

            // retainAll(toDrop) removes the un-selected item, since toDrop is empty
            assertThat(event.getDrops()).doesNotContain(notSelected);
        }
    }
}
