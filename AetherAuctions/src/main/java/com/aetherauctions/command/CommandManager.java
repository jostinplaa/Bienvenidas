package com.aetherauctions.command;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionItem; // Import AuctionItem
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.auction.AuctionStatus; // Import AuctionStatus
import com.aetherauctions.config.MessageManager; // Import MessageManager
import com.aetherauctions.gui.GUIManager;
import com.aetherauctions.gui.rework.NewGUIManager; // Import NewGUIManager
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack; // Added import
import org.bukkit.Material; // Added import

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final AetherAuctions plugin;
    private final AuctionManager auctionManager;
    private final GUIManager guiManager; // Old GUI Manager, might be phased out or used for other GUIs
    private final NewGUIManager newGuiManager; // New GUI Manager
    private final MessageManager messageManager; // Add MessageManager

    public CommandManager(AetherAuctions plugin, AuctionManager auctionManager, GUIManager guiManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
        this.guiManager = guiManager; // Keep for now
        this.newGuiManager = plugin.getNewGuiManager(); // Initialize NewGUIManager
        this.messageManager = plugin.getMessageManager(); // Get MessageManager from plugin
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(messageManager.getMessage("error_console_command_ingame_only"));
                return true;
            }
            newGuiManager.openNewMainAuctionGUI((Player) sender, 0); // Use NewGUIManager
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Temporary debug command for AuctionDetailsGUI
        if (subCommand.equalsIgnoreCase("guidetalles")) {
            if (!(sender instanceof Player)) {
                messageManager.sendMessage(sender, "player_only_command");
                return true;
            }
            Player player = (Player) sender;

            // Optional permission check
            // if (!player.hasPermission("aetherauctions.admin.debug")) {
            //     messageManager.sendMessage(player, "no_permission");
            //     return true;
            // }

            AuctionItem testAuction = null;
            if (auctionManager.getActiveAuctionsMap().isEmpty()) {
                messageManager.sendMessage(player, "new_gui_debug_no_active_auctions_for_test");
            } else {
                for (AuctionItem item : auctionManager.getActiveAuctionsMap().values()) {
                    if (item.getStatus() == AuctionStatus.ACTIVE) {
                        testAuction = item;
                        break;
                    }
                }

                if (testAuction == null) {
                    messageManager.sendMessage(player, "new_gui_debug_no_active_auctions_for_test");
                } else {
                    plugin.getLogger().info("[CommandManager DEBUG] AuctionItem encontrado: ID " + testAuction.getId() + ". Vendedor: " + testAuction.getSellerName());
                    plugin.getLogger().info("[CommandManager DEBUG] Intentando llamar a newGuiManager.openAuctionDetailsGUI para jugador: " + player.getName() + ", Subasta ID: " + testAuction.getId());
                    newGuiManager.openAuctionDetailsGUI(player, testAuction, 0); // Open with page 0 as default
                    messageManager.sendMessage(player, "new_gui_debug_details_gui_opened", "%id%", String.valueOf(testAuction.getId()));
                }
            }
            return true;
        }

        switch (subCommand) {
            case "crear":
                if (args.length == 1) { // /subasta crear -> open GUI
                    if (sender instanceof Player) {
                        guiManager.clearCreateAuctionData(((Player) sender).getUniqueId());
                        guiManager.openCreateAuctionGui((Player) sender);
                    } else {
                        sender.sendMessage(messageManager.getMessage("error_console_command_ingame_only"));
                    }
                } else if (args.length == 4) { // /subasta crear [precioCompraDirecta] [precioPuja] [duración]
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(messageManager.getMessage("error_console_command_ingame_only"));
                        return true;
                    }
                    Player player = (Player) sender;
                    ItemStack itemInHand = player.getInventory().getItemInMainHand();

                    if (itemInHand == null || itemInHand.getType() == Material.AIR) {
                        messageManager.sendMessage(player, "error_must_hold_item_command");
                        return true;
                    }

                    double precioCompraDirecta;
                    double precioPuja;
                    String duracionStr = args[3];
                    long durationMillis;

                    try {
                        precioCompraDirecta = Double.parseDouble(args[1]);
                        if (precioCompraDirecta < 0 && precioCompraDirecta != -1) { // Allow 0 or -1 specifically
                             messageManager.sendMessage(player, "error_invalid_buyout_price", "%value%", args[1]);
                             return true;
                        }
                         if (precioCompraDirecta == 0) precioCompraDirecta = -1; // Treat 0 as "no buyout"

                    } catch (NumberFormatException e) {
                        messageManager.sendMessage(player, "error_invalid_buyout_price", "%value%", args[1]);
                        return true;
                    }

                    try {
                        precioPuja = Double.parseDouble(args[2]);
                        if (precioPuja <= 0) {
                            messageManager.sendMessage(player, "error_invalid_bid_price", "%value%", args[2]);
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        messageManager.sendMessage(player, "error_invalid_bid_price", "%value%", args[2]);
                        return true;
                    }

                    // Validate buyout price against bid price if buyout is set
                    if (precioCompraDirecta > 0 && precioCompraDirecta <= precioPuja) {
                        messageManager.sendMessage(player, "buy_now_must_be_greater"); // Assumes this message key exists
                        return true;
                    }

                    durationMillis = parseDurationArgument(duracionStr);
                    if (durationMillis == -1) { // Indicates parsing failure
                        messageManager.sendMessage(player, "error_invalid_duration_format", "%value%", duracionStr);
                        return true;
                    }
                    if (durationMillis == 0) { // User might have typed "0s" or similar, intending to use GUI.
                         // For command version, 0 duration is invalid. Min duration is handled by AuctionManager.
                         // Or, if "0" is meant to open GUI, handle that:
                         // guiManager.openCreateAuctionGui(player); return true;
                         // For now, treat as invalid if it parses to 0 or less (after helper method).
                         // The helper parseDurationArgument should return >0 for valid durations.
                          messageManager.sendMessage(player, "error_invalid_duration_value"); // Or more specific, like min duration from config
                          return true;
                    }
                     if (durationMillis < plugin.getConfigManager().getMinDurationSeconds() * 1000L) {
                        messageManager.sendMessage(player, "duration_too_short", "%duration%", formatDurationMillis(plugin.getConfigManager().getMinDurationSeconds() * 1000L));
                        return true;
                    }
                    long maxPlayerDurationMillis = (player.hasPermission(plugin.getConfigManager().getVipPermission()) ? plugin.getConfigManager().getVipExtendedDurationSeconds() : plugin.getConfigManager().getMaxDurationSeconds()) * 1000L;
                    if (durationMillis > maxPlayerDurationMillis) {
                        messageManager.sendMessage(player, "duration_too_long", "%duration%", formatDurationMillis(maxPlayerDurationMillis));
                        return true;
                    }


                    ItemStack itemToAuction = itemInHand.clone(); // Quantity is taken from the full stack in hand

                    boolean success = auctionManager.createAuction(player, itemToAuction, durationMillis, precioPuja, precioCompraDirecta);

                    if (success) {
                        // Success message is handled by AuctionManager's createAuction method
                    }
                    // Failure messages also handled by AuctionManager
                } else {
                    messageManager.sendMessage(sender, "error_invalid_crear_usage");
                }
                break;
            case "mis":
                if (sender instanceof Player) {
                    guiManager.openMyAuctionsGui((Player) sender, 1); // Added page argument
                } else {
                    sender.sendMessage(messageManager.getMessage("error_console_command_ingame_only"));
                }
                break;
            case "historial":
                if (sender instanceof Player) {
                    guiManager.openAuctionHistoryGui((Player) sender, 1); // Added page argument
                } else {
                    sender.sendMessage(messageManager.getMessage("error_console_command_ingame_only"));
                }
                break;
            case "cancelar":
                handleCancelCommand(sender, args, label);
                break;
            case "admin":
                handleAdminCommand(sender, args, label);
                break;
            default:
                if (!(sender instanceof Player)) {
                    sender.sendMessage(messageManager.getMessage("error_console_command_ingame_only"));
                    return true;
                }
                sendHelpMessage(sender, label);
                break;
        }
        return true;
    }

    private void handleCancelCommand(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.getMessage("error_console_command_ingame_only"));
            return;
        }
        Player player = (Player) sender;
        // Assuming aetherauctions.user is default true and covers basic cancel for own auctions
        // Specific permission for cancelling *any* auction would be aetherauctions.admin.cancel
        // For now, auctionManager.cancelAuction will check ownership.

        if (args.length < 2) {
            messageManager.sendMessage(player, "invalid_command_usage", "%usage%", "/" + label + " cancelar <id>");
            return;
        }
        try {
            int auctionId = Integer.parseInt(args[1]);
            boolean success = auctionManager.cancelAuction(player, auctionId);
            // auctionManager.cancelAuction already sends success/failure messages with more context
            if (!success && !player.hasPermission("aetherauctions.admin")) { // If basic cancel failed and not admin
                 // messageManager.sendMessage(player, "cannot_cancel_auction"); // Generic, AuctionManager should be more specific
            } else if (!success && player.hasPermission("aetherauctions.admin")) {
                 // messageManager.sendMessage(player, "cannot_cancel_auction"); // Or a specific admin one
            }
        } catch (NumberFormatException e) {
            messageManager.sendMessage(player, "auction_id_must_be_number");
        }
    }

    private void handleAdminCommand(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission("aetherauctions.admin")) {
            messageManager.sendMessage(sender, "no_permission");
            return;
        }
        if (args.length < 2) {
            sendAdminHelpMessage(sender, label);
            return;
        }
        String adminSubCommand = args[1].toLowerCase();
        switch (adminSubCommand) {
            case "reload":
                plugin.reloadPluginConfig(); // This now calls reload on ConfigManager and MessageManager
                messageManager.sendMessage(sender, "reload_success");
                plugin.getLogger().info(sender.getName() + " reloaded AetherAuctions configuration.");
                break;
            case "eliminar": // This is effectively an admin-forced cancel
                if (args.length < 3) {
                    messageManager.sendMessage(sender, "invalid_command_usage", "%usage%", "/" + label + " admin eliminar <id>");
                    return;
                }
                try {
                    int auctionId = Integer.parseInt(args[2]);
                    // For admin removal, we might want a more forceful version in AuctionManager
                    // that doesn't necessarily check if the sender is the owner.
                    // For now, using cancelAuction which has permission checks.
                    // If sender is console, auctionManager.cancelAuction would need adaptation or a new method.
                    if (!(sender instanceof Player)) {
                         sender.sendMessage(messageManager.getMessage("error_console_command_ingame_only"));
                         plugin.getLogger().warning("Admin command 'eliminar' from console is not fully supported yet for auction ID: " + auctionId + " due to cancel requiring player.");
                         // TODO: Implement a console-friendly force-remove in AuctionManager
                        return;
                    }
                    Player adminPlayer = (Player) sender;
                    boolean success = auctionManager.cancelAuction(adminPlayer, auctionId); // cancelAuction checks for admin perm too for cancelling others
                    if (success) {
                        messageManager.sendMessage(sender, "admin_auction_removed", "%id%", String.valueOf(auctionId));
                    } else {
                        // auctionManager sends more specific messages
                        // messageManager.sendMessage(sender, "cannot_cancel_auction"); // Fallback if needed
                    }
                } catch (NumberFormatException e) {
                    messageManager.sendMessage(sender, "auction_id_must_be_number");
                }
                break;
            default:
                sendAdminHelpMessage(sender, label);
                break;
        }
    }

    private void sendHelpMessage(CommandSender sender, String commandLabel) {
        sender.sendMessage(messageManager.getMessage("help_header"));
        sender.sendMessage(messageManager.getMessage("help_title")); // Plugin name is part of this message now
        sender.sendMessage(messageManager.getMessage("help_line_main", "%label%", commandLabel));
        sender.sendMessage(messageManager.getMessage("help_line_crear", "%label%", commandLabel));
        // sender.sendMessage(messageManager.getMessage("help_line_crear_console_hint", "%label%", commandLabel)); // Optional hint
        sender.sendMessage(messageManager.getMessage("help_line_mis", "%label%", commandLabel));
        sender.sendMessage(messageManager.getMessage("help_line_historial", "%label%", commandLabel));
        sender.sendMessage(messageManager.getMessage("help_line_cancelar", "%label%", commandLabel));
        sender.sendMessage(messageManager.getMessage("help_line_ayuda", "%label%", commandLabel));
        if (sender.hasPermission("aetherauctions.admin")) {
            sender.sendMessage(messageManager.getMessage("help_line_admin_command", "%label%", commandLabel));
        }
        sender.sendMessage(messageManager.getMessage("help_footer"));
    }

    private void sendAdminHelpMessage(CommandSender sender, String commandLabel) {
        // Assuming admin_help_header in messages.yml contains the full styled header
        sender.sendMessage(messageManager.getMessage("admin_help_header"));
        sender.sendMessage(messageManager.getMessage("admin_help_line_reload", "%label%", commandLabel));
        sender.sendMessage(messageManager.getMessage("admin_help_line_remove", "%label%", commandLabel));
        // Potentially add a footer similar to the main help if desired
        sender.sendMessage(messageManager.getMessage("help_footer")); // Using the same footer for consistency
    }

    private long parseDurationArgument(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            return -1; // Invalid format
        }
        durationStr = durationStr.trim().toLowerCase();
        long value;
        char unit;

        try {
            if (durationStr.matches("^\\d+$")) { // Only numbers, assume minutes as per subtask for <duracion>
                 value = Long.parseLong(durationStr);
                 unit = 'm'; // Default to minutes if only number is provided
            } else {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)([smhd])").matcher(durationStr);
                if (matcher.matches()) {
                    value = Long.parseLong(matcher.group(1));
                    unit = matcher.group(2).charAt(0);
                } else {
                    return -1; // Invalid format
                }
            }
        } catch (NumberFormatException e) {
            return -1; // Invalid number part
        }


        switch (unit) {
            case 's': return value * 1000; // seconds to millis
            case 'm': return value * 60 * 1000; // minutes to millis
            case 'h': return value * 60 * 60 * 1000; // hours to millis
            case 'd': return value * 24 * 60 * 60 * 1000; // days to millis
            default: return -1; // Unknown unit
        }
    }

    private String formatDurationMillis(long millis) {
        // This is a simplified formatter, ideally use a shared one or from ConfigManager/InventoryUtil if available and suitable
        if (millis < 0) return "N/A";
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        return days + "d";
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("crear", "mis", "historial", "cancelar", "guidetalles")); // Added guidetalles
            if (sender.hasPermission("aetherauctions.admin")) {
                // subCommands = new ArrayList<>(subCommands); // Already an ArrayList
                subCommands.add("admin");
            }
            for (String subCmd : subCommands) {
                if (subCmd.startsWith(currentArg)) {
                    completions.add(subCmd);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("aetherauctions.admin")) {
                List<String> adminSubCommands = Arrays.asList("reload", "eliminar");
                for (String adminSubCmd : adminSubCommands) {
                    if (adminSubCmd.startsWith(currentArg)) {
                        completions.add(adminSubCmd);
                    }
                }
            } else if (args[0].equalsIgnoreCase("cancelar")) {
                // Sugerir IDs de subastas activas del jugador (avanzado, omitir por ahora)
                // Ejemplo:
                // if (sender instanceof Player) {
                // Player player = (Player) sender;
                // auctionManager.getActiveAuctions().values().stream()
                // .filter(auc -> auc.getSellerUUID().equals(player.getUniqueId().toString()))
                // .map(auc -> String.valueOf(auc.getId()))
                // .filter(idStr -> idStr.startsWith(currentArg))
                // .forEach(completions::add);
                // }
                return Collections.emptyList(); // Placeholder
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("eliminar") && sender.hasPermission("aetherauctions.admin")) {
                // Sugerir IDs de todas las subastas activas (avanzado, omitir por ahora)
                // Ejemplo:
                // auctionManager.getActiveAuctions().keySet().stream()
                // .map(String::valueOf)
                // .filter(idStr -> idStr.startsWith(currentArg))
                // .forEach(completions::add);
                return Collections.emptyList(); // Placeholder
            }
        }
        Collections.sort(completions);
        return completions;
    }
}
