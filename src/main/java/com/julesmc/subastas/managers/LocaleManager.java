package com.julesmc.subastas.managers;

import com.julesmc.subastas.SubastasPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class LocaleManager {

    private final SubastasPlugin plugin;
    private final ConfigManager configManager;
    private FileConfiguration langConfig;
    private String pluginPrefix;
    private final Map<String, String> fallbackMessages = new HashMap<>();

    public LocaleManager(SubastasPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        loadFallbackMessages();
    }

    private void loadFallbackMessages() {
        // Fallback messages in Spanish (as per requirements)
        // Command messages
        fallbackMessages.put("command.reload", "¡La configuración del plugin Subastas ha sido recargada!"); // This one is general, might be reused by reload-success
        fallbackMessages.put("command.help", "Usa /subastas <comando> para interactuar con el sistema de subastas.");
        fallbackMessages.put("command.player-only", "Este comando solo puede ser ejecutado por un jugador.");
        fallbackMessages.put("command.no-permission", "&cNo tienes permiso para ejecutar este comando.");
        fallbackMessages.put("command.unknown-command", "&cComando desconocido. Usa /subastas help para ver la lista de comandos.");
        fallbackMessages.put("command.reload-success", "&a¡La configuración y los mensajes del plugin Subastas han sido recargados!");
        fallbackMessages.put("command.reload-fail", "&cHubo un error al recargar la configuración.");
        fallbackMessages.put("command.help-header", "&e--- Ayuda de Subastas ---");
        fallbackMessages.put("command.help-footer", "&e-----------------------");
        fallbackMessages.put("command.help-format", "&6/subastas {subcommand} &7- {description}");
        fallbackMessages.put("command.help-description-help", "Muestra este mensaje de ayuda.");
        fallbackMessages.put("command.help-description-reload", "Recarga la configuración del plugin.");
        fallbackMessages.put("command.help-description-crear", "Crea una nueva subasta con el ítem en tu mano.");
        fallbackMessages.put("command.help-description-gui", "Abre la interfaz gráfica de subastas."); // Changed from ver
        fallbackMessages.put("command.help-description-main", "Abre la interfaz gráfica de subastas."); // New

        fallbackMessages.put("command.player-only-gui", "&cEste comando solo puede ser usado por un jugador para abrir la GUI."); // New

        fallbackMessages.put("command.crear.usage", "&cIncorrecto! Usa: {usage}");
        fallbackMessages.put("command.crear.no-item", "&cDebes tener un ítem en tu mano para crear una subasta.");
        fallbackMessages.put("command.crear.invalid-initial-price", "&cEl precio inicial debe ser mayor que 0.");
        fallbackMessages.put("command.crear.invalid-price-format", "&cEl formato del precio no es válido.");
        fallbackMessages.put("command.crear.buynow-too-low", "&cEl precio de compra directa debe ser mayor que el precio inicial, o 0 para deshabilitar.");
        fallbackMessages.put("command.crear.invalid-duration", "&cLa duración especificada no es válida. Ejemplo: 1h, 30m, 2d.");
        fallbackMessages.put("command.crear.max-auctions-reached", "&cHas alcanzado el límite de {limit} subastas activas.");
        fallbackMessages.put("command.crear.creation-failed", "&cNo se pudo crear la subasta debido a un error interno.");
        fallbackMessages.put("command.crear.fee-charged", "&eSe te ha cobrado una tarifa de {amount} por crear la subasta.");
        fallbackMessages.put("command.crear.fee-refunded", "&aSe te ha reembolsado la tarifa de creación de {amount} debido a un error.");

        fallbackMessages.put("command.ver.invalid-page", "&cEl número de página '{input}' no es válido.");

        // Auction messages (general and errors)
        fallbackMessages.put("auction.created", "¡Has puesto en subasta {item} por {price}!"); // Already existed, ensure it's consistent
        fallbackMessages.put("auction.error.not-active-or-expired", "&cEsta subasta ya no está activa o ha expirado.");

        // Bidding messages
        fallbackMessages.put("auction.enter-bid-amount", "Ingresa tu puja:");
        fallbackMessages.put("auction.bid-accepted", "&a¡Has pujado {amount} por {item}!");
        fallbackMessages.put("auction.bid-too-low", "&cTu puja debe ser mayor que la puja actual o el precio inicial.");
        fallbackMessages.put("auction.bid-too-low-concurrent", "&cAlguien más ha pujado más alto mientras ingresabas tu puja. Inténtalo de nuevo.");
        fallbackMessages.put("auction.bid-higher-than-buynow", "&cTu puja no puede ser igual o mayor al precio de compra directa.");
        fallbackMessages.put("auction.not-enough-funds-bid", "&cNo tienes suficientes fondos para realizar esa puja.");
        fallbackMessages.put("auction.cannot-bid-on-own", "&cNo puedes pujar en tu propia subasta.");
        fallbackMessages.put("auction.already-highest-bidder", "&eYa eres el pujador más alto para esta subasta.");
        fallbackMessages.put("auction.invalid-bid-amount", "&cLa cantidad de puja ingresada no es un número válido.");
        // fallbackMessages.put("auction.bid-increment-too-small", "&cDebes aumentar la puja en al menos {amount}.");

        // Notifications related to bidding
        fallbackMessages.put("auction.outbid-notification", "&e¡Has sido superado en la subasta de {item}! Nueva puja: {new_bid}.");
        fallbackMessages.put("auction.new-bid-notification", "&e{player} ha pujado {amount} por tu subasta de {item}.");
        fallbackMessages.put("auction.sold-to-player-notification-seller", "&a¡Tu subasta de {item} ha sido vendida a {buyer} por {amount}!");
        fallbackMessages.put("auction.won-notification-buyer", "&a¡Has ganado la subasta de {item} por {price}!");
        fallbackMessages.put("auction.buyer-no-funds-notification-seller", "&cEl comprador {buyer} de tu subasta de {item} no tenía fondos suficientes. La subasta ha sido cancelada/expirada.");
        fallbackMessages.put("auction.payment-failed-notification-buyer", "&cNo se pudo procesar tu pago por la subasta de {item}. Asegúrate de tener fondos suficientes.");
        fallbackMessages.put("auction.expired-no-bids-notification-seller", "&eTu subasta de {item} ha expirado sin pujas.");
        fallbackMessages.put("auction.bought-directly-notification-buyer", "&a¡Compraste {item} directamente por {price}!");
        fallbackMessages.put("auction.sold-directly-notification-seller", "&a{buyer} compró tu {item} directamente por {price}!");
        fallbackMessages.put("auction.outbid-by-buyout-notification", "&eLa subasta de {item} en la que pujabas fue comprada directamente por {buyer}.");

        // Auction error messages (ensure this is merged correctly, "not-active-or-expired" already exists)
        fallbackMessages.put("auction.error.buy-now-not-available", "&cLa opción de compra directa no está disponible para esta subasta.");
        fallbackMessages.put("auction.error.cannot-buy-own", "&cNo puedes comprar tu propia subasta.");

        // Auction GUI messages
        fallbackMessages.put("auction.gui.no-auctions", "&eNo hay subastas activas en este momento.");
        fallbackMessages.put("auction.gui.item-name-prefix", "&b");
        // Specific lore lines
        fallbackMessages.put("auction.gui.lore.seller", "&7Vendedor: &e{seller_name}");
        fallbackMessages.put("auction.gui.lore.initial-price", "&7Precio Inicial: &c{amount}");
        fallbackMessages.put("auction.gui.lore.current-bid", "&7Puja Actual: &c{amount}");
        fallbackMessages.put("auction.gui.lore.highest-bidder", "&7Pujador Máximo: &d{player_name}");
        fallbackMessages.put("auction.gui.lore.no-current-bidder", "&7Pujador Máximo: &8Nadie");
        fallbackMessages.put("auction.gui.lore.buy-now-price", "&7Compra Directa: &6{amount}");
        fallbackMessages.put("auction.gui.lore.buy-now-not-available", "&7Compra Directa: &8No disponible");
        fallbackMessages.put("auction.gui.lore.time-left", "&7Tiempo Restante: &b{time}");
        fallbackMessages.put("auction.gui.lore.bid-instruction", "&eClick Izquierdo: Pujar");
        fallbackMessages.put("auction.gui.lore.buy-instruction", "&6Click Derecho: Comprar");
        // GUI Buttons
        fallbackMessages.put("auction.gui.button.previous-page-name", "&aPágina Anterior");
        fallbackMessages.put("auction.gui.button.next-page-name", "&aPágina Siguiente");
        fallbackMessages.put("auction.gui.button.refresh-name", "&eActualizar");
        fallbackMessages.put("auction.gui.button.close-gui-name", "&cCerrar");

        // Error messages
        fallbackMessages.put("error.generic", "Ha ocurrido un error inesperado.");
        fallbackMessages.put("error.vault-not-found", "&cVault no encontrado. El plugin Subastas se deshabilitará. Asegúrate de tener Vault instalado.");
        // Note: "error.database-connection" was already present, but the new one from lang_es.yml is "database-connect-error"
        // I will assume "database-connect-error" is the new standard and remove/update the old one if necessary.
        // For now, I'll add the new ones as specified.
        fallbackMessages.put("error.database-connect-error", "&cError al conectar con la base de datos SQLite: {error}");
        fallbackMessages.put("error.database-query-error", "&cError al ejecutar una consulta en la base de datos: {error}");
        fallbackMessages.put("error.item-serialization-error", "&cError al serializar/deserializar el ítem: {error}");
        fallbackMessages.put("error.config-load", "Error al cargar la configuración: {error}");
        fallbackMessages.put("error.messages-load", "Error al cargar los mensajes del idioma '{lang}': {error}");
        fallbackMessages.put("error.economy-provider-not-found", "&eVault está presente, pero no se encontró un proveedor de economía. Las funciones económicas están deshabilitadas.");
        fallbackMessages.put("error.insufficient-funds", "&cNo tienes suficientes fondos para realizar esta acción. Necesitas {amount}.");
        fallbackMessages.put("error.economy-error", "&cOcurrió un error con la economía al intentar {action}.");

        // Plugin status messages
        fallbackMessages.put("plugin.enabled", "&aEl plugin Subastas ha sido habilitado correctamente."); // Already existed
        fallbackMessages.put("plugin.disabled", "&cEl plugin Subastas ha sido deshabilitado.");

        // Economy messages
        fallbackMessages.put("economy.transaction-success", "&aTransacción económica exitosa.");

        // Log messages
        fallbackMessages.put("logs.task-auction-end-start", "Iniciando tarea de finalización de subastas...");
        fallbackMessages.put("logs.task-auction-end-processing", "Procesando {count} subastas activas...");
        fallbackMessages.put("logs.task-auction-end-summary", "Tarea de finalización: {processed} procesadas, {sold} vendidas, {expired} expiradas, {failed_payment} con pago fallido.");
        fallbackMessages.put("logs.auction-processed-sold", "Subasta ID {id} ({item}) vendida a {buyer} por {price}.");
        fallbackMessages.put("logs.auction-processed-expired", "Subasta ID {id} ({item}) expirada sin pujas.");
        fallbackMessages.put("logs.auction-processed-payment-failed", "Subasta ID {id} ({item}) finalizada, pero el pago de {buyer} por {price} falló.");
        fallbackMessages.put("logs.plugin-enabled-details", "Plugin Subastas v{version} habilitado. Managers: Config OK, Locale OK, DB {db_status}, Economy {econ_status}.");
        fallbackMessages.put("logs.plugin-disabled-details", "Plugin Subastas v{version} deshabilitado.");
    }

    public boolean loadMessages(String languageCode) {
        this.pluginPrefix = ChatColor.translateAlternateColorCodes('&', configManager.getString("plugin-prefix", "&e[Subastas]&r "));

        String targetFileName = "lang_" + languageCode + ".yml"; // e.g., "lang_es.yml"
        File targetDir = new File(plugin.getDataFolder(), "lang"); // e.g., plugins/Subastas/lang/
        File langFileOnDisk = new File(targetDir, targetFileName); // e.g., plugins/Subastas/lang/lang_es.yml

        String resourcePathInJar = "lang/" + targetFileName; // e.g., lang/lang_es.yml (path inside JAR)

        if (!langFileOnDisk.exists()) {
            plugin.getLogger().info("Language file " + langFileOnDisk.getPath() + " not found. Attempting to save from JAR resource: " + resourcePathInJar);
            try {
                // This will save the JAR's lang/lang_es.yml to plugins/Subastas/lang/lang_es.yml
                plugin.saveResource(resourcePathInJar, false);
                plugin.getLogger().info("Successfully saved " + resourcePathInJar + " from JAR to " + langFileOnDisk.getPath());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.SEVERE, "CRITICAL: Language resource " + resourcePathInJar + " not found in JAR. Using fallback messages.", e);
                langConfig = null; // Ensure fallback is used
                return false;
            } catch (Exception e) { // Catch other potential exceptions during saveResource
                plugin.getLogger().log(Level.WARNING, "Could not save default language file " + targetFileName + " from JAR. Resource path attempted: " + resourcePathInJar, e);
                // Still try to load fallback, but this is less critical than resource not found
                langConfig = null;
                return false; // Or handle differently, maybe allow plugin to run with fallback
            }
        }

        if (langFileOnDisk.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(langFileOnDisk);
            // Attempt to load defaults from JAR stream for completeness, using the correct resourcePathInJar
            try (InputStream defLangStream = plugin.getResource(resourcePathInJar)) {
                if (defLangStream != null) {
                    YamlConfiguration jarLangConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defLangStream, StandardCharsets.UTF_8));
                    // Set defaults from JAR config to the loaded disk config
                    // This ensures that new keys added to the JAR's lang file are available
                    // if the user's disk file is outdated, without overwriting their existing values.
                    langConfig.setDefaults(jarLangConfig);
                    langConfig.options().copyDefaults(true);
                } else {
                     plugin.getLogger().warning("Default language resource " + resourcePathInJar + " not found in JAR for setting defaults (this is unexpected if saveResource worked or file was present).");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error loading default messages from JAR stream for " + resourcePathInJar, e);
            }
             plugin.getLogger().info("Successfully loaded messages from " + langFileOnDisk.getPath());
            return true;
        } else {
            // This should ideally not be reached if saveResource failed with IllegalArgumentException (handled above)
            // or if the file existed. This is a fallback for other unexpected scenarios.
            plugin.getLogger().severe("Language file " + langFileOnDisk.getPath() + " could not be loaded or created. Using fallback messages.");
            langConfig = null;
            return false;
        }
    }

    public String getMessage(String key, String... replacements) {
        String message = "";
        if (langConfig != null) {
            message = langConfig.getString(key);
        }

        if (message == null || message.isEmpty()) {
            message = fallbackMessages.getOrDefault(key, "&cMissing message for key: " + key);
            if (langConfig != null) { // Only log if we expected to find it in a file
                 plugin.getLogger().warning("Missing message for key: " + key + ". Using fallback.");
            }
        }

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return pluginPrefix + ChatColor.translateAlternateColorCodes('&', message);
    }

     public String getRawMessage(String key, String... replacements) {
        String message = "";
        if (langConfig != null) {
            message = langConfig.getString(key);
        }

        if (message == null || message.isEmpty()) {
            message = fallbackMessages.getOrDefault(key, "&cMissing message for key: " + key);
             if (langConfig != null) {
                plugin.getLogger().warning("Missing message for key: " + key + ". Using fallback.");
            }
        }

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public void setPluginPrefix(String prefix) {
        this.pluginPrefix = ChatColor.translateAlternateColorCodes('&', prefix);
    }
}
