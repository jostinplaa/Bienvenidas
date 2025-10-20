package com.jules.auctionhouse.commands;

import com.jules.auctionhouse.AuctionHouse;
import com.jules.auctionhouse.guis.MainMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AuctionCommand implements CommandExecutor {

    private final AuctionHouse plugin;

    public AuctionCommand(AuctionHouse plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Este comando solo puede ser ejecutado por un jugador.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            MainMenu mainMenu = new MainMenu();
            mainMenu.open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (args.length < 2) {
                player.sendMessage("§cIncorrecto. Uso: /auction create <precio> [duracion]");
                return true;
            }

            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand.getType().isAir() || plugin.getConfigManager().getBlacklistedItems().contains(itemInHand.getType().name())) {
                player.sendMessage(plugin.getConfigManager().getPrefix() + " §cNo puedes subastar este item.");
                return true;
            }

            double price;
            try {
                price = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cEl precio debe ser un número válido.");
                return true;
            }

            if (price <= 0) {
                player.sendMessage("§cEl precio debe ser mayor que cero.");
                return true;
            }

            long duration = plugin.getConfigManager().getDefaultDuration();
            if (args.length > 2) {
                try {
                    // Simple parser s, m, h, d
                    String dStr = args[2].toLowerCase();
                    long dVal = Long.parseLong(dStr.substring(0, dStr.length() - 1));
                    if (dStr.endsWith("s")) duration = dVal;
                    else if (dStr.endsWith("m")) duration = dVal * 60;
                    else if (dStr.endsWith("h")) duration = dVal * 3600;
                    else if (dStr.endsWith("d")) duration = dVal * 86400;
                    else duration = Long.parseLong(dStr);
                } catch (Exception e) {
                    player.sendMessage("§cFormato de duración inválido. Usa s, m, h, d. Ejemplo: 5m");
                    return true;
                }
            }

            double buyNowPrice = -1;
            if (args.length > 3) {
                try {
                    buyNowPrice = Double.parseDouble(args[3]);
                    if (buyNowPrice <= price) {
                        player.sendMessage("§cEl precio de 'Comprar Ya' debe ser mayor que el precio inicial.");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§cEl precio de 'Comprar Ya' debe ser un número válido.");
                    return true;
                }
            }

            plugin.getAuctionManager().createAuction(player, itemInHand.clone(), duration, price, buyNowPrice);
            return true;
        }

        if (args[0].equalsIgnoreCase("history")) {
            new com.jules.auctionhouse.guis.HistoryGUI(player, 0).open(player);
            return true;
        }

        // Mostrar ayuda o menú principal si el subcomando no es válido
        MainMenu mainMenu = new MainMenu();
        mainMenu.open(player);
        return true;
    }
}