package com.ultikits.plugins.essentials.commands;

import com.ultikits.plugins.essentials.config.EssentialsConfig;
import com.ultikits.ultitools.annotations.command.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Command to view another player's armor and offhand.
 */
@CmdTarget(CmdTarget.CmdTargetType.PLAYER)
@CmdExecutor(alias = {"armorsee"}, permission = "ultiessentials.armorsee", description = "essentials.command.armorsee.description")
public class ArmorseeCommand extends BaseEssentialsCommand {

    private final EssentialsConfig config;

    public ArmorseeCommand(EssentialsConfig config) {
        this.config = config;
    }

    @CmdMapping(format = "<player>")
    public void armorsee(@CmdSender Player sender, @CmdParam("player") Player target) {
        if (!config.isInvseeEnabled()) {
            sender.sendMessage(i18n("essentials.error.feature_disabled"));
            return;
        }

        if (target == null) {
            sender.sendMessage(i18n("essentials.error.player_not_found_or_offline"));
            return;
        }

        // Create a temporary display inventory
        Inventory armorInventory = Bukkit.createInventory(null, 9,
                String.format(i18n("essentials.armorsee.title"), target.getName()));

        ItemStack[] armor = target.getInventory().getArmorContents();
        // Place in reverse order: helmet, chestplate, leggings, boots
        for (int i = 0; i < armor.length; i++) {
            armorInventory.setItem(i, armor[armor.length - 1 - i]);
        }

        // Offhand item
        armorInventory.setItem(5, target.getInventory().getItemInOffHand());

        sender.openInventory(armorInventory);
        sender.sendMessage(String.format(i18n("essentials.armorsee.viewing"), target.getName()));
    }

    @Override
    protected void handleHelp(CommandSender sender) {
        sender.sendMessage(i18n("essentials.help.armorsee"));
    }
}
