package dev.jules.proauction.command;

import dev.jules.proauction.ProAuction;
import dev.jules.proauction.gui.AuctionGUI;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AuctionHouseCommand implements CommandExecutor {

    private final ProAuction plugin; // Or directly AuctionGUI if preferred
    private final AuctionGUI auctionGUI;


    public AuctionHouseCommand(ProAuction plugin, AuctionGUI auctionGUI) {
        this.plugin = plugin;
        this.auctionGUI = auctionGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, ChatColor.RED + "Only players can open the Auction House GUI.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("proauction.gui")) {
            plugin.sendMessage(player, ChatColor.RED + "You don't have permission to open the Auction House.");
            return true;
        }

        auctionGUI.openMainAuctionPage(player, 0); // Open first page
        return true;
    }
}
