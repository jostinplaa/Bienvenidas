package com.julesmc.subastas.commands;

import com.julesmc.subastas.SubastasPlugin;
import com.julesmc.subastas.SubastasPlugin;
import com.julesmc.subastas.database.DatabaseManager;
import com.julesmc.subastas.gui.GuiManager; // Added import
import com.julesmc.subastas.managers.ConfigManager;
import com.julesmc.subastas.managers.EconomyManager;
import com.julesmc.subastas.managers.LocaleManager;
import com.julesmc.subastas.objects.AuctionItem;
import com.julesmc.subastas.utils.SerializationUtil;
import com.julesmc.subastas.utils.TimeUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SubastasCommand implements CommandExecutor, TabCompleter {

    private final SubastasPlugin plugin;
    private final ConfigManager configManager;
    private final LocaleManager localeManager;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;
    private final GuiManager guiManager; // Added GuiManager


    public SubastasCommand(SubastasPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.localeManager = plugin.getLocaleManager();
        this.databaseManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();
        this.guiManager = plugin.getGuiManager(); // Get GuiManager instance
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                if (sender.hasPermission("subastas.player.help")) {
                    showHelp(sender);
                } else {
                    sender.sendMessage(localeManager.getMessage("command.no-permission"));
                }
                break;
            case "reload":
                if (sender.hasPermission("subastas.admin.reload")) {
                    try {
                        configManager.reloadConfig();
                        // Update locale manager with potentially new prefix from reloaded config
                        localeManager.setPluginPrefix(configManager.getString("plugin-prefix", "&e[Subastas]&r "));
                        localeManager.loadMessages(configManager.getString("language", "es"));
                        sender.sendMessage(localeManager.getMessage("command.reload-success"));
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error during plugin reload: " + e.getMessage());
                        e.printStackTrace();
                        sender.sendMessage(localeManager.getMessage("command.reload-fail"));
                    }
                } else {
                    sender.sendMessage(localeManager.getMessage("command.no-permission"));
                }
                break;
            case "crear":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(localeManager.getMessage("command.player-only"));
                    return true;
                }
                if (!sender.hasPermission("subastas.player.create")) {
                    sender.sendMessage(localeManager.getMessage("command.no-permission"));
                    return true;
                }
                handleCreateAuction((Player) sender, args);
                break;
            case "ver":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(localeManager.getMessage("command.player-only"));
                    return true;
                }
                if (!sender.hasPermission("subastas.player.view")) {
                    sender.sendMessage(localeManager.getMessage("command.no-permission"));
                    return true;
                }
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                        if (page < 1) page = 1;
                    } catch (NumberFormatException e) {
                        ((Player) sender).sendMessage(localeManager.getMessage("command.ver.invalid-page", "input", args[1]));
                        return true;
                    }
                }
                guiManager.openActiveAuctionsGUI((Player) sender, page);
                break;
            default:
                sender.sendMessage(localeManager.getMessage("command.unknown-command"));
                break;
        }
        return true;
    }

    private void handleCreateAuction(Player player, String[] args) {
        // Usage: /subastas crear <precio_inicial> [precio_compra_directa] [duracion]
        if (args.length < 2) { // args[0] is "crear"
            player.sendMessage(localeManager.getMessage("command.crear.usage", "usage", "/subastas crear <precio> [compra_directa] [duración]"));
            return;
        }

        org.bukkit.inventory.ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR || itemInHand.getAmount() == 0) {
            player.sendMessage(localeManager.getMessage("command.crear.no-item"));
            return;
        }

        double initialPrice;
        try {
            initialPrice = Double.parseDouble(args[1]);
            if (initialPrice <= 0) {
                player.sendMessage(localeManager.getMessage("command.crear.invalid-initial-price"));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(localeManager.getMessage("command.crear.invalid-price-format"));
            return;
        }

        Double buyNowPrice = null;
        if (args.length > 2) {
            try {
                double bnPrice = Double.parseDouble(args[2]);
                if (bnPrice > 0) {
                    if (bnPrice <= initialPrice) {
                        player.sendMessage(localeManager.getMessage("command.crear.buynow-too-low"));
                        return;
                    }
                    buyNowPrice = bnPrice;
                }
            } catch (NumberFormatException e) {
                // If not a number, it might be a duration string.
                // We'll parse duration next, assuming this arg was meant for duration if it's not a valid price.
            }
        }

        String durationString = configManager.getString("auction-settings.default-duration", "24h");
        if (args.length > 3) { // duration is explicitly set
            durationString = args[3];
        } else if (args.length > 2 && buyNowPrice == null) {
            // If buyNowPrice parse failed (e.g. it was "1h" instead of a number)
            // or if it was skipped and a duration was provided as 3rd arg
            durationString = args[2];
        }

        int durationSeconds = TimeUtil.parseDuration(durationString);
        if (durationSeconds <= 0) {
            player.sendMessage(localeManager.getMessage("command.crear.invalid-duration"));
            return;
        }

        // Check max auctions per player
        int maxAuctions = configManager.getInt("auction-settings.max-auctions-per-player", 5);
        List<AuctionItem> activeAuctions = databaseManager.getAuctionsBySeller(player.getUniqueId())
                                           .stream()
                                           .filter(auc -> auc.getStatus().equals("ACTIVE"))
                                           .collect(Collectors.toList());
        if (activeAuctions.size() >= maxAuctions) {
            player.sendMessage(localeManager.getMessage("command.crear.max-auctions-reached", "limit", String.valueOf(maxAuctions)));
            return;
        }

        // Optional: Commission fee
        double creationFee = configManager.getDouble("auction-settings.commission.create-auction-fee", 0.0);
        if (creationFee > 0) {
            if (!economyManager.hasEnough(player, creationFee)) {
                player.sendMessage(localeManager.getMessage("error.insufficient-funds", "amount", String.valueOf(creationFee)));
                return;
            }
            if (!economyManager.withdrawPlayer(player, creationFee)) {
                 player.sendMessage(localeManager.getMessage("error.economy-error", "action", "cobrar tarifa de creación"));
                return;
            }
             player.sendMessage(localeManager.getMessage("command.crear.fee-charged", "amount", String.valueOf(creationFee)));
        }


        String itemSerialized;
        try {
            itemSerialized = SerializationUtil.itemStackToBase64(itemInHand.clone()); // Clone to avoid issues if original is modified
        } catch (IllegalStateException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error serializing item for auction: " + e.getMessage(), e);
            player.sendMessage(localeManager.getMessage("error.item-serialization-error", "error", e.getMessage()));
            return;
        }

        String itemName = itemInHand.getType().toString();
        if (itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasDisplayName()) {
            itemName = itemInHand.getItemMeta().getDisplayName(); // Use display name if available
        }


        AuctionItem auction = new AuctionItem(
                player.getUniqueId(),
                player.getName(),
                itemInHand.clone(), // Store a clone in the object for immediate use if needed, though serialized is primary
                itemSerialized,
                itemName,
                initialPrice,
                buyNowPrice,
                System.currentTimeMillis(),
                durationSeconds,
                "ACTIVE"
        );

        int auctionId = databaseManager.createAuction(auction);
        if (auctionId != -1) {
            auction.setId(auctionId); // Set the generated ID
            player.getInventory().setItemInMainHand(null); // Remove item from hand
            player.sendMessage(localeManager.getMessage("auction.created",
                    "item", itemName,
                    "price", String.valueOf(initialPrice)
            ));
            // Potentially log to console or discord webhook etc.
        } else {
            player.sendMessage(localeManager.getMessage("command.crear.creation-failed"));
             // If fee was charged, try to refund it
            if (creationFee > 0) {
                economyManager.depositPlayer(player, creationFee);
                player.sendMessage(localeManager.getMessage("command.crear.fee-refunded", "amount", String.valueOf(creationFee)));
            }
        }
    }


    private void showHelp(CommandSender sender) {
        sender.sendMessage(localeManager.getRawMessage("command.help-header"));
        sender.sendMessage(localeManager.getMessage("command.help-format", "subcommand", "help", "description", localeManager.getRawMessage("command.help-description-help", "Muestra este mensaje de ayuda.")));
        if (sender.hasPermission("subastas.admin.reload")) {
            sender.sendMessage(localeManager.getMessage("command.help-format", "subcommand", "reload", "description", localeManager.getRawMessage("command.help-description-reload", "Recarga la configuración del plugin.")));
        }
        if (sender.hasPermission("subastas.player.create")) {
            sender.sendMessage(localeManager.getMessage("command.help-format", "subcommand", "crear <precio> [compra_dir] [duración]", "description", localeManager.getRawMessage("command.help-description-crear", "Crea una nueva subasta.")));
        }
        if (sender.hasPermission("subastas.player.view")) {
             sender.sendMessage(localeManager.getMessage("command.help-format", "subcommand", "ver [página]", "description", localeManager.getRawMessage("command.help-description-ver", "Abre la GUI de subastas.")));
        }
        sender.sendMessage(localeManager.getRawMessage("command.help-footer"));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            if (sender.hasPermission("subastas.player.help")) {
                subcommands.add("help");
            }
            if (sender.hasPermission("subastas.admin.reload")) {
                subcommands.add("reload");
            }
            if (sender.hasPermission("subastas.player.create")) {
                subcommands.add("crear");
            }
            if (sender.hasPermission("subastas.player.view")) {
                subcommands.add("ver");
            }
            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null; // No suggestions for arguments of subcommands yet
    }
}
