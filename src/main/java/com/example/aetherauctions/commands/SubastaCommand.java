package com.example.aetherauctions.commands;

import com.example.aetherauctions.AetherAuctions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;

public class SubastaCommand implements CommandExecutor {

    private final AetherAuctions plugin;

    public SubastaCommand(AetherAuctions plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessages().getString("player-only-command", "&cThis command can only be run by a player.")));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("aetherauctions.use")) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getMessages().getString("no-permission", "&cYou do not have permission to use this command.")));
            return true;
        }

        if (args.length == 0) {
            // Open main auction GUI
            plugin.getGuiManager().openMainGui(player);
            return true;
        }

        // Handle sub-commands like /subasta crear, /subasta vender, /subasta pujar, etc.
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "help":
            case "?":
                // TODO: Send help message from messages.yml
                player.sendMessage(ChatColor.GOLD + "AetherAuctions Help:");
                player.sendMessage(ChatColor.YELLOW + "/subasta - Opens the main auction house.");
                // ... add more when implemented
                return true;
            // Placeholder for future commands - these would interact with AuctionManager or specific GUIs
            case "vender": // Alias for create, perhaps with GUI for setting price
            case "crear":
                player.sendMessage(ChatColor.GRAY + "Hint: Use /subasta crear <precio> [precio_compra_directa] while holding the item.");
                // This will be handled by another command or a GUI flow.
                // For now, this is just a message.
                return true;
            case "pujar": // /subasta pujar <id_subasta> <cantidad>
                 if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /subasta pujar <auction_id> <amount>");
                    return true;
                }
                try {
                    int auctionId = Integer.parseInt(args[1]);
                    double bidAmount = Double.parseDouble(args[2]);
                    // Call AuctionManager method here (to be implemented or called from GUI context)
                    player.sendMessage(ChatColor.YELLOW + "Attempting to bid " + bidAmount + " on auction " + auctionId + " (feature in progress).");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid auction ID or bid amount.");
                }
                return true;
            case "comprar": // /subasta comprar <id_subasta> (for buy now)
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /subasta comprar <auction_id>");
                    return true;
                }
                try {
                    int auctionId = Integer.parseInt(args[1]);
                    // Call AuctionManager method here
                     player.sendMessage(ChatColor.YELLOW + "Attempting to buy now auction " + auctionId + " (feature in progress).");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid auction ID.");
                }
                return true;

            default:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getMessages().getString("invalid-command-usage", "&cInvalid usage! Try: &e/subasta")
                    .replace("%usage%", "/subasta")
                ));
                return true;
        }
    }
}
