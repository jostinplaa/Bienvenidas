package com.aetherauctions.command;

import com.aetherauctions.AetherAuctions;
import com.aetherauctions.gui.GUIManager;
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
import org.apache.commons.text.similarity.JaroWinklerSimilarity; // Import for suggestions
import java.util.Collections; // For Collections.emptyList() if needed

public class CommandManager implements CommandExecutor, TabCompleter {
    private final AetherAuctions plugin;
    private final MessageManager msgManager;
    private final AuctionManager auctionManager;
    private final ConfigManager cfgManager;
    // No se necesita mapa de cooldown aquí, AuctionManager lo gestionará.

    private final List<String> validUserSubCommands = Arrays.asList("ayuda", "crear", "crearmisteriosa", "cancelar", "mis", "historial", "reclamar");
    private final List<String> validAdminSubCommands = Arrays.asList("reload", "ver", "borrar");
    // SIMILARITY_THRESHOLD se leerá de config
    public static final String MYSTERY_AUCTION_PERMISSION = "aetherauctions.command.crearmisteriosa";

    public CommandManager(AetherAuctions plugin) {
        this.plugin = plugin;
        this.msgManager = plugin.getMessageManager();
        this.auctionManager = plugin.getAuctionManager();
        this.cfgManager = plugin.getConfigManager();
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

            // Cooldown Check
            if (auctionManager.isPlayerOnAuctionCreationCooldown(seller.getUniqueId())) {
                long timeLeft = auctionManager.getAuctionCreationCooldownTimeLeft(seller.getUniqueId());
                msgManager.sendMessage(seller, "auction_creation_cooldown_active", "%time%", String.valueOf(timeLeft)); // Nueva clave de mensaje
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
            auctionManager.cancelAuction(canceller, UUID.fromString(auctionToCancel.getId()));
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
                    msgManager.sendMessage(myAuctionsPlayer, "command_mis_auction_line",
                        "%id_corto%", auc.getId().toString().substring(0, 8),
                        "%item%", InventoryUtil.formatMaterialName(auc.getItemStack().getType()),
                        "%bid%", String.format("%.2f %s", auc.getCurrentBid(), cfgManager.getCurrencySymbol()),
                        "%time%", InventoryUtil.formatTime(auc.getRemainingTimeMillis())
                    );
                }
                msgManager.sendMessage(myAuctionsPlayer, "command_mis_footer");
            }
            return true;
        } else if (subCommand.equals("historial")) {
            msgManager.sendMessage(sender, "command_historial_not_implemented");
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
            plugin.getRewardManager().attemptClaimNextReward(playerToClaim);
            return true;
        }
        else if (subCommand.equals("crearmisteriosa")) {
            if (!(sender instanceof Player)) {
                msgManager.sendMessage(sender, "player_only_command");
                return true;
            }
            Player seller = (Player) sender;
            if (!seller.hasPermission(MYSTERY_AUCTION_PERMISSION)) {
                msgManager.sendMessage(seller, "no_permission");
                return true;
            }

            // Cooldown Check (usa el mismo cooldown que la creación normal)
            if (auctionManager.isPlayerOnAuctionCreationCooldown(seller.getUniqueId())) {
                long timeLeft = auctionManager.getAuctionCreationCooldownTimeLeft(seller.getUniqueId());
                msgManager.sendMessage(seller, "auction_creation_cooldown_active", "%time%", String.valueOf(timeLeft));
                return true;
            }

            if (!cfgManager.isMysteryAuctionsAllowed()){ // Este getter usa la ruta nueva "auctions.behavior.allow_mystery_auctions"
                msgManager.sendMessage(seller, "mystery_auctions_disabled");
                return true;
            }

            // Uso: /subasta crearmisteriosa <precio_inicial> [precio_compra_directa] <duracion_horas> <descripcion...>
            // Mínimo 4 args: crearmisteriosa, precio, duracion, desc1
            // Con compra directa: 5 args: crearmisteriosa, precio, compradirecta, duracion, desc1
            if (args.length < 5) { // nombre_cmd precio_inicial duracion_horas desc1 desc2 ... (sin compra directa) -> min 4 args para comando, desc
                                   // nombre_cmd precio_inicial precio_compra_directa duracion_horas desc1 ... -> min 5 args
                msgManager.sendMessage(seller, "command_usage_crear_misteriosa"); // Nueva clave de mensaje
                return true;
            }

            try {
                double startPrice = Double.parseDouble(args[1]);
                double buyNowPrice = -1;
                int durationArgIndex;
                int descriptionStartIndex;

                // Chequear si el segundo argumento es un número (precio compra directa) o texto (parte de duración o descripción)
                try {
                    buyNowPrice = Double.parseDouble(args[2]);
                    // Si args[2] es un número, entonces es precio_compra_directa
                    durationArgIndex = 3;
                    descriptionStartIndex = 4;
                } catch (NumberFormatException e) {
                    // Si args[2] no es un número, entonces no hay precio_compra_directa
                    // args[2] es duracion_horas
                    durationArgIndex = 2;
                    descriptionStartIndex = 3;
                }

                if (args.length <= descriptionStartIndex) { // No hay descripción
                    msgManager.sendMessage(seller, "command_usage_crear_misteriosa_no_desc"); // Nueva clave
                    return true;
                }

                long durationHours = Long.parseLong(args[durationArgIndex]);
                if (durationHours <= 0) {
                    msgManager.sendMessage(seller, "auction_create_error_invalid_duration");
                    return true;
                }

                StringBuilder descBuilder = new StringBuilder();
                for (int i = descriptionStartIndex; i < args.length; i++) {
                    descBuilder.append(args[i]).append(" ");
                }
                String description = descBuilder.toString().trim();
                if (description.isEmpty()) {
                     msgManager.sendMessage(seller, "command_usage_crear_misteriosa_no_desc");
                     return true;
                }
                if (description.length() > 255) { // Límite arbitrario para la descripción
                    description = description.substring(0, 255);
                }


                // Abrir la GUI para preparar el lote
                com.aetherauctions.gui.PrepareMysteryLotGUI prepareGui = new com.aetherauctions.gui.PrepareMysteryLotGUI(plugin, seller, startPrice, buyNowPrice, durationHours, description);
                prepareGui.open();

            } catch (NumberFormatException e) {
                msgManager.sendMessage(seller, "command_crear_error_invalid_numbers"); // Reutilizar o crear nueva clave
            }
            return true;
        }
        else {
            String inputSubCommand = args[0].toLowerCase();
            if (cfgManager.isCommandSuggestionsEnabled()) { // Usar config
                List<String> relevantCommands = new ArrayList<>(validUserSubCommands);
                if (sender.hasPermission("aetherauctions.admin")) {
                    relevantCommands.add("admin");
                }
                String suggestion = findBestMatch(inputSubCommand, relevantCommands, cfgManager.getCommandSuggestionSimilarityThreshold()); // Pasar umbral
                if (suggestion != null) {
                    String formattedSuggestion = msgManager.getRawMessage("command_suggestion_prefix") + // No necesita valor por defecto si está en messages.yml
                                               label + " " + suggestion +
                                               msgManager.getRawMessage("command_suggestion_suffix");
                    sender.sendMessage(formattedSuggestion);
                } else {
                    msgManager.sendMessage(sender, "unknown_command");
                }
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
                // ConfigManager ahora tiene reloadAllConfigs() que también recarga MessageManager y SoundManager
                this.cfgManager.reloadAllConfigs();
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
                    msgManager.sendMessage(sender, "command_admin_ver_seller", "%name%", auctionToView.getSellerName(), "%uuid%", auctionToView.getSellerUUID().toString());
                    msgManager.sendMessage(sender, "command_admin_ver_item", "%item%", InventoryUtil.formatMaterialName(auctionToView.getItemStack().getType()), "%amount%", String.valueOf(auctionToView.getItemStack().getAmount()));
                    msgManager.sendMessage(sender, "command_admin_ver_status", "%status%", auctionToView.getStatus().name());
                    msgManager.sendMessage(sender, "command_admin_ver_bid", "%bid%", String.format("%.2f %s", auctionToView.getCurrentBid(), cfgManager.getCurrencySymbol()));
                    if(auctionToView.getHighestBidderName() != null) {
                        msgManager.sendMessage(sender, "command_admin_ver_hb", "%name%", auctionToView.getHighestBidderName(), "%uuid%", auctionToView.getHighestBidderUUID().toString());
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
        if (sender.hasPermission(MYSTERY_AUCTION_PERMISSION)) msgManager.sendMessage(sender, "help_subasta_crear_misteriosa"); // Nueva clave de mensaje
        if (sender.hasPermission("aetherauctions.command.cancelar")) msgManager.sendMessage(sender, "help_subasta_cancelar");
        if (sender.hasPermission("aetherauctions.command.mis")) msgManager.sendMessage(sender, "help_subasta_mis");
        if (sender.hasPermission("aetherauctions.command.historial")) msgManager.sendMessage(sender, "help_subasta_historial");
        if (sender.hasPermission("aetherauctions.command.reclamar")) msgManager.sendMessage(sender, "help_subasta_reclamar");
        if (sender.hasPermission("aetherauctions.admin")) {
            msgManager.sendMessage(sender, "help_subasta_admin");
        }
        sender.sendMessage(msgManager.getRawMessage("help_footer"));
    }

    private void sendAdminHelpMessage(CommandSender sender) {
        sender.sendMessage(msgManager.getRawMessage("help_header"));
        msgManager.sendMessage(sender, "admin_help_reload");
        msgManager.sendMessage(sender, "admin_help_ver");
        msgManager.sendMessage(sender, "admin_help_borrar");
        sender.sendMessage(msgManager.getRawMessage("help_footer"));
    }

    // Modificar findBestMatch para aceptar el umbral
    private String findBestMatch(String input, List<String> candidates, double threshold) {
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
        if (highestSimilarity >= threshold) { // Usar el umbral de config
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
            for (String sc : validUserSubCommands) {
                String permissionNode;
                if (sc.equals("ayuda")) {
                    permissionNode = "aetherauctions.user";
                } else if (sc.equals("crearmisteriosa")) {
                    permissionNode = MYSTERY_AUCTION_PERMISSION;
                } else {
                    permissionNode = "aetherauctions.command." + sc;
                }

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
