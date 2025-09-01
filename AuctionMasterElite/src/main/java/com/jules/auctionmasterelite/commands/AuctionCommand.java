package com.jules.auctionmasterelite.commands;

import com.jules.auctionmasterelite.AuctionMasterElite;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AuctionCommand implements CommandExecutor {

    private final AuctionMasterElite plugin;

    public AuctionCommand(AuctionMasterElite plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("auctionmaster.elite.use")) {
            player.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        // Open the main auction GUI
        player.openInventory(new com.jules.auctionmasterelite.gui.menu.MainMenu(plugin).getInventory());
        return true;
    }
}
