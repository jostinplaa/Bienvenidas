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

        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("invite")) {
                handleInvite(player, args);
                return true;
            }
        }

        // Open the main auction GUI
        player.openInventory(new com.jules.auctionmasterelite.gui.menu.MainMenu(plugin).getInventory());
        return true;
    }

    private void handleInvite(Player player, String[] args) {
        // auction invite <player>
        if (args.length < 2) {
            player.sendMessage("§cUso: /auction invite <jugador>");
            return;
        }

        Player target = org.bukkit.Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cJugador no encontrado.");
            return;
        }

        // Find the player's most recent active private auction
        com.jules.auctionmasterelite.data.Auction auction = plugin.getAuctionManager().getAuctionsBySeller(player.getUniqueId()).stream()
                .filter(a -> a.getType() == com.jules.auctionmasterelite.data.AuctionType.PRIVATE && a.getStatus() == com.jules.auctionmasterelite.data.AuctionStatus.ACTIVE)
                .findFirst()
                .orElse(null);

        if (auction == null) {
            player.sendMessage("§cNo tienes ninguna subasta privada activa para invitar.");
            return;
        }

        auction.invitePlayer(target.getUniqueId());
        plugin.getDatabaseManager().saveInvitedPlayer(auction.getAuctionId(), target.getUniqueId());
        player.sendMessage("§aHas invitado a " + target.getName() + " a tu subasta.");
        target.sendMessage("§aHas sido invitado a una subasta por " + player.getName() + ".");
    }
}
