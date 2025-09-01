package com.jules.auctionmasterelite.commands;

import com.jules.auctionmasterelite.AuctionMasterElite;
import org.bukkit.command.Command;
import com.jules.auctionmasterelite.util.MessageUtil;
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
            MessageUtil.sendRawMessage(sender, "player-only-command");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("auctionmaster.elite.use")) {
            MessageUtil.sendMessage(player, "no-permission");
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
            MessageUtil.sendMessage(player, "invalid-usage", "usage", "/auction invite <player>");
            return;
        }

        Player target = org.bukkit.Bukkit.getPlayer(args[1]);
        if (target == null) {
            MessageUtil.sendMessage(player, "player-not-found");
            return;
        }

        // Find the player's most recent active private auction
        com.jules.auctionmasterelite.data.Auction auction = plugin.getAuctionManager().getAuctionsBySeller(player.getUniqueId()).stream()
                .filter(a -> a.getType() == com.jules.auctionmasterelite.data.AuctionType.PRIVATE && a.getStatus() == com.jules.auctionmasterelite.data.AuctionStatus.ACTIVE)
                .findFirst()
                .orElse(null);

        if (auction == null) {
            MessageUtil.sendMessage(player, "no-active-private-auction");
            return;
        }

        auction.invitePlayer(target.getUniqueId());
        plugin.getDatabaseManager().saveInvitedPlayer(auction.getAuctionId(), target.getUniqueId());
        MessageUtil.sendMessage(player, "invite-success", "player", target.getName());
        MessageUtil.sendMessage(target, "invite-received", "player", player.getName());
    }
}
