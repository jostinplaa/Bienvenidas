package com.aetherauctions.command;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.config.MessageManager; // Import MessageManager
import com.aetherauctions.gui.GUIManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final AetherAuctions plugin;
    private final AuctionManager auctionManager;
    private final GUIManager guiManager;
    private final MessageManager messageManager; // Add MessageManager

    public CommandManager(AetherAuctions plugin, AuctionManager auctionManager, GUIManager guiManager) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
        this.guiManager = guiManager;
        this.messageManager = plugin.getMessageManager(); // Get MessageManager from plugin
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                guiManager.openMainAuctionGui((Player) sender);
            } else {
                sendHelpMessage(sender, label);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "crear":
                if (sender instanceof Player) {
                    // Permission check can be added here if desired, e.g., aetherauctions.create
                    guiManager.clearCreateAuctionData(((Player) sender).getUniqueId()); // Clear previous temp data
                    guiManager.openCreateAuctionGui((Player) sender);
                } else {
                    messageManager.sendMessage(sender, "player_only_command");
                }
                break;
            case "mis":
                if (sender instanceof Player) {
                    guiManager.openMyAuctionsGui((Player) sender);
                } else {
                    messageManager.sendMessage(sender, "player_only_command");
                }
                break;
            case "historial":
                if (sender instanceof Player) {
                    guiManager.openAuctionHistoryGui((Player) sender);
                } else {
                    messageManager.sendMessage(sender, "player_only_command");
                }
                break;
            case "cancelar":
                handleCancelCommand(sender, args, label);
                break;
            case "admin":
                handleAdminCommand(sender, args, label);
                break;
            default:
                sendHelpMessage(sender, label);
                break;
        }
        return true;
    }

    private void handleCancelCommand(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player)) {
            messageManager.sendMessage(sender, "player_only_command");
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
                         messageManager.sendMessage(sender, "player_only_command"); // Current cancelAuction expects a player
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
        // These could also be moved to messages.yml as a multi-line string or list
        sender.sendMessage(messageManager.getPrefix() + "--- Ayuda de AetherAuctions ---");
        sender.sendMessage(messageManager.getPrefixedRaw(" &/e/" + commandLabel + " &7- Abre la GUI principal."));
        sender.sendMessage(messageManager.getPrefixedRaw(" &/e/" + commandLabel + " crear &7- Abre la GUI para crear una subasta."));
        sender.sendMessage(messageManager.getPrefixedRaw(" &/e/" + commandLabel + " mis &7- Ve tus subastas activas."));
        sender.sendMessage(messageManager.getPrefixedRaw(" &/e/" + commandLabel + " historial &7- Ve tu historial de subastas."));
        sender.sendMessage(messageManager.getPrefixedRaw(" &/e/" + commandLabel + " cancelar <id> &7- Cancela una de tus subastas."));
        if (sender.hasPermission("aetherauctions.admin")) {
            sender.sendMessage(messageManager.getPrefixedRaw(" &/c/" + commandLabel + " admin &7- Muestra ayuda de administración."));
        }
    }

    private void sendAdminHelpMessage(CommandSender sender, String commandLabel) {
        sender.sendMessage(messageManager.getRaw("admin_help_header")); // Assumes admin_help_header includes prefix or is styled
        sender.sendMessage(messageManager.getMessage("admin_help_reload").replace("/auc", "/" + commandLabel));
        sender.sendMessage(messageManager.getMessage("admin_help_remove").replace("/auc", "/" + commandLabel));
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("crear", "mis", "historial", "cancelar");
            if (sender.hasPermission("aetherauctions.admin")) {
                subCommands = new ArrayList<>(subCommands); // Convert to modifiable list
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
