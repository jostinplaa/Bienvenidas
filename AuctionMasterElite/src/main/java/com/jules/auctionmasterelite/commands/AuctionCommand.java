package com.jules.auctionmasterelite.commands;

import com.jules.auctionmasterelite.AuctionMasterElite;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import com.jules.auctionmasterelite.gui.claims.ClaimMenu;
import com.jules.auctionmasterelite.util.MessageUtil;
import org.bukkit.OfflinePlayer;
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
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "reload":
                    if (!sender.hasPermission("auctionmaster.admin.reload")) {
                        MessageUtil.sendMessage(sender, "no-permission");
                        return true;
                    }
                    plugin.reloadPlugin();
                    MessageUtil.sendRawMessage(sender, "&aConfiguration reloaded successfully.");
                    return true;
                case "admin":
                    handleAdminCommand(sender, args);
                    return true;
                // Player-only commands below
                case "invite":
                case "claim":
                    if (!(sender instanceof Player)) {
                        MessageUtil.sendRawMessage(sender, "player-only-command");
                        return true;
                    }
                    Player player = (Player) sender;
                     if (!player.hasPermission("auctionmaster.elite.use")) {
                        MessageUtil.sendMessage(player, "no-permission");
                        return true;
                    }
                    if (subCommand.equals("invite")) {
                        handleInvite(player, args);
                    } else { // claim
                        new ClaimMenu(plugin, player).open(player);
                    }
                    return true;
            }
        }

        // Default action: open GUI (player-only)
        if (!(sender instanceof Player)) {
            MessageUtil.sendRawMessage(sender, "player-only-command");
            // TODO: Show usage message for console
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("auctionmaster.elite.use")) {
            MessageUtil.sendMessage(player, "no-permission");
            return true;
        }
        new com.jules.auctionmasterelite.gui.menu.MainMenu(plugin).open(player);
        return true;
    }

    private void handleAdminCommand(CommandSender sender, String[] args) {
        // /ah admin [subcommand] [args...]
        if (args.length < 2) {
            MessageUtil.sendRawMessage(sender, "&cUsage: /auction admin <end|history>");
            return;
        }

        String adminSubCommand = args[1].toLowerCase();
        switch (adminSubCommand) {
            case "end":
                handleAdminEnd(sender, args);
                break;
            case "history":
                handleAdminHistory(sender, args);
                break;
            default:
                MessageUtil.sendRawMessage(sender, "&cUnknown admin command. Usage: /auction admin <end|history>");
                break;
        }
    }

    private void handleAdminHistory(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            MessageUtil.sendRawMessage(sender, "This command can only be run by a player.");
            return;
        }
        Player admin = (Player) sender;

        if (!admin.hasPermission("auctionmaster.admin.history")) {
            MessageUtil.sendMessage(admin, "no-permission");
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendRawMessage(admin, "&cUsage: /auction admin history <player_name>");
            return;
        }

        String targetName = args[2];
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (target == null || !target.hasPlayedBefore()) {
            MessageUtil.sendRawMessage(admin, "&cPlayer not found.");
            return;
        }

        new com.jules.auctionmasterelite.gui.menu.HistoryMenu(plugin, admin, target).open(admin);
    }

    private void handleAdminEnd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("auctionmaster.admin.end")) {
            MessageUtil.sendMessage(sender, "no-permission");
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendRawMessage(sender, "&cUsage: /auction admin end <seller_name>");
            return;
        }

        String sellerName = args[2];
        plugin.getAuctionManager().forceEndAuctionBySeller(sellerName, sender);
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
