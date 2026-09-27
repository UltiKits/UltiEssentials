package com.ultikits.plugins.essentials.listener;

import com.ultikits.ultitools.abstracts.UltiToolsPlugin;
import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.Autowired;
import com.ultikits.ultitools.annotations.EventListener;
import com.ultikits.ultitools.utils.EconomyUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Listener for death punishment.
 *
 * @author wisdomme
 * @version 1.0.0
 */
@EventListener
public class DeathPunishListener implements Listener {
    
    @Autowired
    private EssentialsConfig config;

    @Autowired
    private UltiToolsPlugin plugin;
    
    private final Random random = new Random();
    
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!config.isDeathPunishEnabled()) {
            return;
        }
        
        Player player = event.getEntity();
        
        // Check if player is in whitelisted world
        if (config.getDeathPunishWorldWhitelist().contains(player.getWorld().getName())) {
            return;
        }
        
        // Check bypass permission
        if (player.hasPermission("ultiessentials.deathpunish.bypass")) {
            return;
        }
        
        StringBuilder message = new StringBuilder();
        message.append(ChatColor.RED).append(plugin.i18n("essentials.deathpunish.prefix"));
        // Whether any penalty was applied: the summary is sent only then. (The line used to be sent
        // when the builder grew past 10 characters, which depended on the prefix's length.)
        boolean punished = false;
        
        // Money loss
        if (config.isDeathPunishMoneyEnabled() && EconomyUtils.isAvailable()) {
            double balance = EconomyUtils.getBalance(player);
            double lossPercent = config.getDeathPunishMoneyPercent();
            double maxLoss = config.getDeathPunishMoneyMax();
            
            double loss = balance * (lossPercent / 100.0);
            if (maxLoss > 0 && loss > maxLoss) {
                loss = maxLoss;
            }
            
            if (loss > 0) {
                EconomyUtils.withdraw(player, loss);
                message.append(' ').append(ChatColor.GOLD).append(String.format(plugin.i18n("essentials.deathpunish.money"), loss));
                punished = true;
            }
        }
        
        // Item drop
        if (config.isDeathPunishItemDropEnabled()) {
            int dropCount = processItemDrop(event);
            if (dropCount > 0) {
                message.append(' ').append(ChatColor.YELLOW).append(String.format(plugin.i18n("essentials.deathpunish.items"), dropCount));
                punished = true;
            }
        }
        
        // Experience loss
        if (config.isDeathPunishExpEnabled()) {
            int expLoss = (int) (player.getTotalExperience() * (config.getDeathPunishExpPercent() / 100.0));
            if (expLoss > 0) {
                event.setDroppedExp(Math.max(0, event.getDroppedExp() - expLoss));
                message.append(' ').append(ChatColor.GREEN).append(String.format(plugin.i18n("essentials.deathpunish.exp"), expLoss));
                punished = true;
            }
        }
        
        // Execute command punishment
        if (config.isDeathPunishCommandEnabled()) {
            executeCommands(player);
        }
        
        // Send message
        if (punished) {
            player.sendMessage(message.toString());
        }
    }
    
    /**
     * Processes item drop punishment.
     * @return number of items dropped
     */
    private int processItemDrop(PlayerDeathEvent event) {
        double dropChance = config.getDeathPunishItemDropChance();
        List<String> whitelist = config.getDeathPunishItemWhitelist();
        
        int dropCount = 0;
        List<ItemStack> drops = event.getDrops();
        List<ItemStack> toDrop = new ArrayList<>();
        
        for (ItemStack item : drops) {
            if (item == null) continue;
            
            // Check whitelist
            if (whitelist.contains(item.getType().name())) {
                continue;
            }
            
            // Random chance to keep
            if (random.nextDouble() * 100 < dropChance) {
                toDrop.add(item);
                dropCount++;
            }
        }
        
        // Keep only dropped items (remove others)
        if (config.isDeathPunishKeepOtherItems()) {
            drops.retainAll(toDrop);
        }
        
        return dropCount;
    }
    
    /**
     * Executes punishment commands.
     */
    private void executeCommands(Player player) {
        List<String> commands = config.getDeathPunishCommands();
        
        for (String command : commands) {
            String parsed = command.replace("{PLAYER}", player.getName())
                                   .replace("%player%", player.getName());
            
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }
}
