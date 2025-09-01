package com.jules.auctionmasterelite.managers;

import com.jules.auctionmasterelite.AuctionMasterElite;
import com.jules.auctionmasterelite.data.Auction;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import org.bukkit.inventory.ItemStack;

import java.awt.Color;

public class DiscordManager {

    private final AuctionMasterElite plugin;
    private final DiscordSRV discordSrv;

    public DiscordManager(AuctionMasterElite plugin) {
        this.plugin = plugin;
        this.discordSrv = DiscordSRV.getPlugin();
    }

    public void sendNewAuctionNotification(Auction auction) {
        // Find the configured channel for auctions
        // For now, let's assume a channel named "auctions"
        TextChannel channel = discordSrv.getJda().getTextChannelsByName("auctions", true).stream().findFirst().orElse(null);
        if (channel == null) {
            plugin.getLogger().warning("Could not find a Discord channel named 'auctions' to send notification.");
            return;
        }

        ItemStack item = auction.getItem();
        String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().toString().replace("_", " ").toLowerCase();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("✨ Nueva Subasta Creada")
                .setColor(new Color(75, 0, 130)) // Indigo color
                .addField("Vendedor", auction.getSellerName(), true)
                .addField("Objeto", item.getAmount() + "x " + itemName, true)
                .addField("Precio Inicial", String.format("%.2f", auction.getStartingBid()), true)
                .setFooter("AuctionMaster Elite", null)
                .setTimestamp(java.time.Instant.now());

        channel.sendMessage(embed.build()).queue();
    }
}
