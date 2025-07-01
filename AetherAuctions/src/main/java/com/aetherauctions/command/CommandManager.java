package com.aetherauctions.command;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.gui.GUIManager; // Podría ser necesario si MainAuctionGUI se mueve aquí o se referencia
import com.aetherauctions.guis.ClaimRewardsGUI;
import com.aetherauctions.guis.MyActiveAuctionsGUI;
import com.aetherauctions.guis.PlayerHistoryGUI;
import com.aetherauctions.guis.AdminHistoryGUI;
import com.aetherauctions.config.MessageManager;
import com.aetherauctions.config.ConfigManager;
import com.aetherauctions.auction.AuctionManager;
import com.aetherauctions.model.Auction;
import com.aetherauctions.util.InventoryUtil;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import java.util.Collections;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;


public class CommandManager implements CommandExecutor, TabCompleter {
    private final AetherAuctions plugin;
    private final MessageManager msgManager;
    private final AuctionManager auctionManager;
    private final ConfigManager cfgManager;
    private ClaimRewardsGUI claimRewardsGUI;
    // No es necesario instanciar MyActiveAuctionsGUI o PlayerHistoryGUI aquí si sus métodos open son estáticos

    private final List<String> validUserSubCommands = Arrays.asList("ayuda", "crear", "cancelar", "mis", "historial", "reclamar");
    private final List<String> validAdminSubCommands = Arrays.asList("reload", "ver", "borrar", "historial"); // Añadido historial admin
    private static final double SIMILARITY_THRESHOLD = 0.75;
    private static final SimpleDateFormat ADMIN_DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy");

    public CommandManager(AetherAuctions plugin) {
        this.plugin = plugin;
        this.msgManager = plugin.getMessageManager();
        this.auctionManager = plugin.getAuctionManager();
        this.cfgManager = plugin.getConfigManager();
        this.claimRewardsGUI = new ClaimRewardsGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("subasta")) {
            return false;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                msgManager.sendMessage(sender, "player_only_command");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("aetherauctions.user")) {
                msgManager.sendMessage(player, "no_permission");
                return true;
            }
            GUIManager.openMainAuctionGUI(player, 0);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("ayuda") || subCommand.equals("help")) {
            sendHelpMessage(sender);
            return true;
        } else if (subCommand.equals("crear")) {
            if (!(sender instanceof Player)) {
                msgManager.sendMessage(sender, "player_only_command");
                return true;
            }
            Player seller = (Player) sender;
            if (!seller.hasPermission("aetherauctions.command.crear")) {
                msgManager.sendMessage(seller, "no_permission");
                return true;
            }
            if (args.length < 3 || args.length > 4) {
                msgManager.sendMessage(seller, "command_usage_crear");
                return true;
            }
            ItemStack itemInHand = seller.getInventory().getItemInMainHand();
            if (itemInHand == null || itemInHand.getType() == Material.AIR) {
                msgManager.sendMessage(seller, "auction_create_error_no_item_in_hand");
                return true;
            }
            if (cfgManager.getItemBlacklist().contains(itemInHand.getType())) {
                msgManager.sendMessage(seller, "auction_create_error_item_blacklisted", "%item_name%", InventoryUtil.formatMaterialName(itemInHand.getType()));
                return true;
            }
            try {
                double startPrice = Double.parseDouble(args[1]);
                double buyNowPrice = -1;
                long durationSeconds;
                if (args.length == 4) {
                    buyNowPrice = Double.parseDouble(args[2]);
                    durationSeconds = Long.parseLong(args[3]);
                } else {
                    durationSeconds = Long.parseLong(args[2]);
                }
                auctionManager.createAuction(seller, itemInHand.clone(), startPrice, buyNowPrice, durationSeconds);
            } catch (NumberFormatException e) {
                msgManager.sendMessage(seller, "command_crear_error_invalid_numbers");
            }
            return true;
        } else if (subCommand.equals("cancelar")) {
            if (!(sender instanceof Player)) {
                msgManager.sendMessage(sender, "player_only_command");
                return true;
            }
            Player canceller = (Player) sender;
            if (!canceller.hasPermission("aetherauctions.command.cancelar")) {
                msgManager.sendMessage(canceller, "no_permission");
                return true;
            }
            if (args.length < 2) {
                msgManager.sendMessage(canceller, "command_usage_cancelar");
                return true;
            }
            String idToCancelStr = args[1];
            Auction auctionToCancel = auctionManager.getAuctionByIdFuzzy(idToCancelStr);

            if (auctionToCancel == null) {
                msgManager.sendMessage(canceller, "auction_cancel_error_not_found", "%id%", idToCancelStr);
                return true;
            }
            auctionManager.cancelAuction(canceller, auctionToCancel.getId());
            return true;
        } else if (subCommand.equals("mis")) {
            if (!(sender instanceof Player)) {
                msgManager.sendMessage(sender, "player_only_command");
                return true;
            }
            Player myAuctionsPlayer = (Player) sender;
            if (!myAuctionsPlayer.hasPermission("aetherauctions.command.mis")) {
                msgManager.sendMessage(myAuctionsPlayer, "no_permission");
                return true;
            }
            List<Auction> myActiveAuctions = auctionManager.getPlayerActiveAuctions(myAuctionsPlayer.getUniqueId());
            if (myActiveAuctions.isEmpty()) {
                msgManager.sendMessage(myAuctionsPlayer, "command_mis_no_auctions");
            } else {
                msgManager.sendMessage(myAuctionsPlayer, "command_mis_header");
                for (Auction auc : myActiveAuctions) {
                    // Usar %id% y pasar el ID completo. El messages.yml para command_mis_auction_line debe usar %id%.
                    // Si se quisiera un ID corto aquí específicamente, se haría el substring aquí y se pasaría.
                    // Por ahora, asumimos que command_mis_auction_line usará %id% para el ID completo o se adaptará.
                    // Para mantener la funcionalidad anterior de ID corto aquí:
                    String idToShow = auc.getId().toString();
                     // La clave "command_mis_auction_line" ahora debe usar %id%
                    msgManager.sendMessage(myAuctionsPlayer, "command_mis_auction_line",
                        "%id%", idToShow,
                        "%item%", InventoryUtil.formatMaterialName(auc.getItemStack().getType()),
                        "%bid%", String.format("%.2f %s", auc.getCurrentBid(), cfgManager.getCurrencySymbol()),
                        "%time%", InventoryUtil.formatTime(auc.getRemainingTimeMillis())
                    );
                }
                msgManager.sendMessage(myAuctionsPlayer, "command_mis_footer");
            }
            return true;
        } else if (subCommand.equals("historial")) {
            if (!(sender instanceof Player)) {
                msgManager.sendMessage(sender, "player_only_command");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("aetherauctions.command.historial")) {
                msgManager.sendMessage(player, "no_permission");
                return true;
            }
            if (!cfgManager.isHistoryEnabled()) {
                msgManager.sendMessage(player, "history_disabled");
                return true;
            }
            PlayerHistoryGUI.open(player, 0);
            return true;
        } else if (subCommand.equals("admin")) {
            if (args.length < 2) {
                sendAdminHelpMessage(sender);
                return true;
            }
            if (!sender.hasPermission("aetherauctions.admin")) {
                msgManager.sendMessage(sender, "no_permission");
                return true;
            }
            handleAdminCommands(sender, Arrays.copyOfRange(args, 1, args.length), label);
            return true;
        } else if (subCommand.equals("reclamar")) {
            if (!(sender instanceof Player)) {
                msgManager.sendMessage(sender, "player_only_command");
                return true;
            }
            Player playerToClaim = (Player) sender;
            if (!playerToClaim.hasPermission("aetherauctions.command.reclamar")) {
                msgManager.sendMessage(playerToClaim, "no_permission");
                return true;
            }
            // plugin.getRewardManager().attemptClaimNextReward(playerToClaim); // Old text-based claim
            claimRewardsGUI.open(playerToClaim); // Open the new GUI
            return true;
        }
        else {
            String inputSubCommand = args[0].toLowerCase();
            List<String> relevantCommands = new ArrayList<>(validUserSubCommands);
            if (sender.hasPermission("aetherauctions.admin")) {
                relevantCommands.add("admin");
            }

            String suggestion = findBestMatch(inputSubCommand, relevantCommands);

            if (suggestion != null) {
                String formattedSuggestion = msgManager.getRawMessage("command_suggestion_prefix", "&7¿Quizás quisiste decir: ") +
                                           label + " " + suggestion +
                                           msgManager.getRawMessage("command_suggestion_suffix", "&7?");
                sender.sendMessage(formattedSuggestion);
            } else {
                msgManager.sendMessage(sender, "unknown_command");
            }
            return true;
        }
    }

    private void handleAdminCommands(CommandSender sender, String[] adminArgs, String mainCommandLabel) {
        if (adminArgs.length == 0) {
            sendAdminHelpMessage(sender);
            return;
        }
        String command = adminArgs[0].toLowerCase();
        switch (command) {
            case "reload":
                if (!sender.hasPermission("aetherauctions.admin.reload")) {
                    msgManager.sendMessage(sender, "no_permission"); return;
                }
                this.cfgManager.loadConfig();
                this.msgManager.loadMessages();
                msgManager.sendMessage(sender, "admin_reload_success");
                break;
            case "ver":
                if (!sender.hasPermission("aetherauctions.admin.ver")) {
                    msgManager.sendMessage(sender, "no_permission"); return;
                }
                if (adminArgs.length < 2) {
                    msgManager.sendMessage(sender, "command_usage_admin_ver"); return;
                }
                String idToViewStr = adminArgs[1];
                Auction auctionToView = auctionManager.getAuctionByIdFuzzy(idToViewStr);
                if (auctionToView == null) {
                    auctionToView = plugin.getAuctionStorage().loadAuctionFuzzy(idToViewStr);
                }
                if (auctionToView != null) {
                    msgManager.sendMessage(sender, "command_admin_ver_details_header", "%id%", auctionToView.getId().toString());
                    msgManager.sendMessage(sender, "command_admin_ver_seller", "%name%", auctionToView.getSellerName(), "%uuid%", auctionToView.getSellerId().toString());
                    msgManager.sendMessage(sender, "command_admin_ver_item", "%item%", InventoryUtil.formatMaterialName(auctionToView.getItemStack().getType()), "%amount%", String.valueOf(auctionToView.getItemStack().getAmount()));
                    msgManager.sendMessage(sender, "command_admin_ver_status", "%status%", auctionToView.getStatus().name());
                    msgManager.sendMessage(sender, "command_admin_ver_bid", "%bid%", String.format("%.2f %s", auctionToView.getCurrentBid(), cfgManager.getCurrencySymbol()));
                    if(auctionToView.getHighestBidderName() != null) {
                        msgManager.sendMessage(sender, "command_admin_ver_hb", "%name%", auctionToView.getHighestBidderName(), "%uuid%", auctionToView.getHighestBidderId().toString());
                    }
                    if(auctionToView.hasBuyNow()){
                        msgManager.sendMessage(sender, "command_admin_ver_buynow", "%price%", String.format("%.2f %s", auctionToView.getBuyNowPrice(), cfgManager.getCurrencySymbol()));
                    }
                    msgManager.sendMessage(sender, "command_admin_ver_time", "%time%", InventoryUtil.formatTime(auctionToView.getRemainingTimeMillis()));
                } else {
                    msgManager.sendMessage(sender, "command_admin_ver_not_found", "%id%", idToViewStr);
                }
                break;
            case "borrar":
                 if (!sender.hasPermission("aetherauctions.admin.borrar")) {
                    msgManager.sendMessage(sender, "no_permission"); return;
                }
                if (adminArgs.length < 2) {
                    msgManager.sendMessage(sender, "command_usage_admin_borrar"); return;
                }
                String idToDeleteStr = adminArgs[1];
                Auction toDelete = auctionManager.getAuctionByIdFuzzy(idToDeleteStr);
                if (toDelete == null) {
                     toDelete = plugin.getAuctionStorage().loadAuctionFuzzy(idToDeleteStr);
                }
                if (toDelete != null) {
                    if (auctionManager.adminDeleteAuction(toDelete, sender)) {
                       msgManager.sendMessage(sender, "admin_borrar_success",
                           "%id%", idToDeleteStr,
                           "%item_name%", InventoryUtil.formatMaterialName(toDelete.getItemStack().getType())
                       );
                    } else {
                       msgManager.sendMessage(sender, "admin_borrar_failed", "%id%", idToDeleteStr);
                    }
                } else {
                     msgManager.sendMessage(sender, "command_admin_ver_not_found", "%id%", idToDeleteStr);
                }
                break;
            case "historial":
                if (!sender.hasPermission("aetherauctions.admin.historial")) { // Nueva permission
                    msgManager.sendMessage(sender, "no_permission"); return;
                }
                if (!cfgManager.isHistoryEnabled()) {
                    msgManager.sendMessage(sender, "history_disabled"); return;
                }
                if (adminArgs.length < 2) {
                    msgManager.sendMessage(sender, "command_usage_admin_historial_player"); // Nueva clave: /subasta admin historial <jugador> [fecha_ini] [fecha_fin]
                    return;
                }
                String targetPlayerName = adminArgs[1];
                long startDate = 0;
                long endDate = 0;

                if (adminArgs.length >= 3) {
                    try {
                        Date parsedStartDate = ADMIN_DATE_FORMAT.parse(adminArgs[2]);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(parsedStartDate);
                        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                        startDate = cal.getTimeInMillis();
                    } catch (ParseException e) {
                        msgManager.sendMessage(sender, "admin_history_invalid_date_format"); return;
                    }
                }
                if (adminArgs.length >= 4) {
                    try {
                        Date parsedEndDate = ADMIN_DATE_FORMAT.parse(adminArgs[3]);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(parsedEndDate);
                        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999);
                        endDate = cal.getTimeInMillis();
                    } catch (ParseException e) {
                        msgManager.sendMessage(sender, "admin_history_invalid_date_format"); return;
                    }
                }
                if (startDate > 0 && endDate > 0 && startDate > endDate) {
                    msgManager.sendMessage(sender, "admin_history_start_date_after_end"); return;
                }
                if (startDate == 0 && endDate == 0 && cfgManager.getDefaultHistoryDaysToShowForAdmin() > 0) {
                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.DAY_OF_MONTH, -cfgManager.getDefaultHistoryDaysToShowForAdmin());
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
                    startDate = cal.getTimeInMillis();
                    // endDate remains 0 (meaning up to now) or could be set to end of current day
                }

                if (sender instanceof Player) {
                    AdminHistoryGUI.open((Player) sender, targetPlayerName, startDate, endDate, 0);
                } else {
                    // Potentially add console output for admin history if needed, though GUI is primary
                    msgManager.sendMessage(sender, "player_only_command"); // Or a specific "admin_history_console_unsupported"
                }
                break;
            default:
                String inputAdminSubCommand = adminArgs[0].toLowerCase();
                String adminSuggestion = findBestMatch(inputAdminSubCommand, validAdminSubCommands);
                if (adminSuggestion != null) {
                    String formattedSuggestion = msgManager.getRawMessage("command_suggestion_prefix", "&7¿Quizás quisiste decir: ") +
                                               mainCommandLabel + " admin " + adminSuggestion + // Use main command label
                                               msgManager.getRawMessage("command_suggestion_suffix", "&7?");
                    sender.sendMessage(formattedSuggestion);
                } else {
                    sendAdminHelpMessage(sender);
                }
                break;
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(msgManager.getRawMessage("help_header"));
        msgManager.sendMessage(sender, "help_subasta");
        if (sender.hasPermission("aetherauctions.command.crear")) msgManager.sendMessage(sender, "help_subasta_crear");
        if (sender.hasPermission("aetherauctions.command.cancelar")) msgManager.sendMessage(sender, "help_subasta_cancelar");
        if (sender.hasPermission("aetherauctions.command.mis") && cfgManager.isMyAuctionsGuiEnabled()) msgManager.sendMessage(sender, "help_subasta_mis");
        if (sender.hasPermission("aetherauctions.command.historial") && cfgManager.isHistoryEnabled()) msgManager.sendMessage(sender, "help_subasta_historial");
        if (sender.hasPermission("aetherauctions.command.reclamar")) msgManager.sendMessage(sender, "help_subasta_reclamar");
        if (sender.hasPermission("aetherauctions.admin")) {
            msgManager.sendMessage(sender, "help_subasta_admin");
        }
        sender.sendMessage(msgManager.getRawMessage("help_footer"));
    }

    private void sendAdminHelpMessage(CommandSender sender) {
        sender.sendMessage(msgManager.getRawMessage("help_header"));
        if (sender.hasPermission("aetherauctions.admin.reload")) msgManager.sendMessage(sender, "admin_help_reload");
        if (sender.hasPermission("aetherauctions.admin.ver")) msgManager.sendMessage(sender, "admin_help_ver");
        if (sender.hasPermission("aetherauctions.admin.borrar")) msgManager.sendMessage(sender, "admin_help_borrar");
        if (sender.hasPermission("aetherauctions.admin.historial") && cfgManager.isHistoryEnabled()) msgManager.sendMessage(sender, "admin_help_historial_player"); // Nueva clave
        sender.sendMessage(msgManager.getRawMessage("help_footer"));
    }

    private String findBestMatch(String input, List<String> candidates) {
        if (input == null || input.isEmpty() || candidates == null || candidates.isEmpty()) {
            return null;
        }
        JaroWinklerSimilarity similarityAlgorithm = new JaroWinklerSimilarity();
        String bestMatch = null;
        double highestSimilarity = 0.0;

        for (String candidate : candidates) {
            double currentSimilarity = similarityAlgorithm.apply(input, candidate);
            if (currentSimilarity > highestSimilarity) {
                highestSimilarity = currentSimilarity;
                bestMatch = candidate;
            }
        }
        if (highestSimilarity >= SIMILARITY_THRESHOLD) {
            return bestMatch;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("subasta")) {
            return null;
        }
        List<String> completions = new ArrayList<>();
        String currentArg = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            // Use a copy for modification if needed, or iterate directly over validUserSubCommands
            for (String sc : validUserSubCommands) { // Iterate directly over the updated list
                String permissionNode = "aetherauctions.command." + sc;
                if (sc.equals("ayuda")) permissionNode = "aetherauctions.user"; // Special case for ayuda

                if (sender.hasPermission(permissionNode)) {
                     if (sc.startsWith(currentArg)) {
                        completions.add(sc);
                    }
                }
            }
            if (sender.hasPermission("aetherauctions.admin")) {
                if ("admin".startsWith(currentArg)) completions.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("aetherauctions.admin")) {
            for (String asc : validAdminSubCommands) {
                if (sender.hasPermission("aetherauctions.admin." + asc)) {
                    if (asc.startsWith(currentArg)) {
                        completions.add(asc);
                    }
                }
            }
        }
        // TODO: Add more specific tab completion for command arguments (e.g., auction IDs)

        return completions.stream().sorted().collect(Collectors.toList());
    }
}
