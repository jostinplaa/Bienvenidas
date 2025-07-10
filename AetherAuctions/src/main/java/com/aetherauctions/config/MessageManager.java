package com.aetherauctions.config;

import com.aetherauctions.AetherAuctions;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets; // For UTF-8
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {
    private final AetherAuctions plugin;
    private FileConfiguration messagesConfig = null;
    private File messagesFile = null;
    private String prefix = "";

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public MessageManager(AetherAuctions plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    public void loadMessages() {
        String langFileName = plugin.getConfigManager().getLanguageFile();
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), langFileName);
        }
        if (!messagesFile.exists()) {
            plugin.saveResource(langFileName, false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        try (InputStream defaultConfigStream = plugin.getResource(langFileName)){
            if (defaultConfigStream != null) {
                 YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));
                messagesConfig.setDefaults(defaultConfig);
                messagesConfig.options().copyDefaults(true); // Copia los defaults al archivo si no existen
                messagesConfig.save(messagesFile); // Guarda para persistir los defaults copiados
            } else {
                plugin.getLogger().warning("El archivo de mensajes por defecto '" + langFileName + "' no se encontró en el JAR.");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar " + langFileName + " con los valores por defecto.", e);
        }

        // El prefijo se carga después de que ConfigManager esté completamente inicializado y haya cargado config.yml
        this.prefix = plugin.getConfigManager().getPluginPrefix();
        if (this.prefix == null || this.prefix.isEmpty()){
            this.prefix = ChatColor.translateAlternateColorCodes('&', "&6[&eAetherAuctions&6] &r"); // Fallback por si acaso
            plugin.getLogger().warning("ConfigManager no estaba disponible al cargar prefijo en MessageManager. Usando prefijo por defecto.");
        }
        plugin.getLogger().info("Mensajes cargados/recargados.");
    }

    private String translateHexColorCodes(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.COLOR_CHAR + "x"
                    + ChatColor.COLOR_CHAR + group.charAt(0) + ChatColor.COLOR_CHAR + group.charAt(1)
                    + ChatColor.COLOR_CHAR + group.charAt(2) + ChatColor.COLOR_CHAR + group.charAt(3)
                    + ChatColor.COLOR_CHAR + group.charAt(4) + ChatColor.COLOR_CHAR + group.charAt(5)
            );
        }
        return matcher.appendTail(buffer).toString();
    }

    private String formatMessage(String rawMessage, String... placeholderPairs) {
        if (rawMessage == null) {
            return null;
        }
        String message = translateHexColorCodes(ChatColor.translateAlternateColorCodes('&', rawMessage));

        if (placeholderPairs.length % 2 != 0) {
            plugin.getLogger().severe("[MessageManager] Error de placeholders para el mensaje: '" + rawMessage +
                                      "'. Se proporcionó un número impar de argumentos. Placeholders: " + Arrays.toString(placeholderPairs));
            return message; // Devuelve el mensaje parcialmente formateado o sin formatear si no hay placeholders
        }

        for (int i = 0; i < placeholderPairs.length; i += 2) {
            String placeholder = placeholderPairs[i];
            String value = placeholderPairs[i+1];

            if (placeholder == null || value == null) {
                 plugin.getLogger().warning("[MessageManager] Par de placeholder nulo detectado para mensaje: '" + rawMessage + "'. Placeholder: " + placeholder);
                 continue;
            }
            // Ya no se necesita manejo especial para %id_short% aquí.
            // Se asume que si se quiere un ID corto, el código que llama a formatMessage
            // preparará el substring y lo pasará con un placeholder como %id% o %auction_id_short_display%.
            message = message.replace(placeholder, value);
        }
        return message;
    }

    public String getMessage(String key, String... placeholderPairs) {
        String rawMessage = messagesConfig.getString(key);
        if (rawMessage == null) {
            plugin.getLogger().warning("[MessageManager] Clave de mensaje no encontrada en " + plugin.getConfigManager().getLanguageFile() + ": '" + key + "'.");
            // Usar un formato de error por defecto interno o una clave específica de messages.yml para este error
            String errorFormat = messagesConfig.getString("internal_error_format.missing_key", "&cError: Clave de mensaje '%key%' no encontrada.");
            return ChatColor.translateAlternateColorCodes('&', errorFormat.replace("%key%", key));
        }
        // Asegurarse de que los placeholders como "#id_short%" que podrían haber quedado en messages.yml
        // no causen problemas si no se pasan explícitamente. O mejor, eliminarlos de messages.yml.
        // Por ahora, la lógica de reemplazo simple no fallará, solo no reemplazará si no hay par.
        return formatMessage(rawMessage, placeholderPairs);
    }

    public String getPrefixedMessage(String key, String... placeholderPairs) {
        String message = getMessage(key, placeholderPairs);
        // Comprobar si el mensaje devuelto ES el mensaje de error por clave no encontrada.
        // Esto es un poco frágil si el formato del mensaje de error cambia.
        String missingKeyErrorMsg = ChatColor.translateAlternateColorCodes('&', messagesConfig.getString("internal_error_format.missing_key", "&cError: Clave de mensaje '%key%' no encontrada.").replace("%key%", key));
        if (message.equals(missingKeyErrorMsg)) {
            return message; // No añadir prefijo al mensaje de "clave no encontrada"
        }
        return this.prefix + message;
    }

    public void sendMessage(CommandSender sender, String key, String... placeholderPairs) {
        String message = getPrefixedMessage(key, placeholderPairs);
        sender.sendMessage(message);
    }

    public List<String> getStringList(String key, String... placeholderPairs) {
        List<String> rawList = messagesConfig.getStringList(key);
        if (rawList == null || rawList.isEmpty()) {
            plugin.getLogger().warning("[MessageManager] Lista de mensajes no encontrada o vacía para la clave: '" + key + "' en " + plugin.getConfigManager().getLanguageFile());
            List<String> errorList = new ArrayList<>();
            String errorFormat = messagesConfig.getString("internal_error_format.missing_list_key", "&cError: Lista de mensajes '%key%' no encontrada.");
            errorList.add(ChatColor.translateAlternateColorCodes('&', errorFormat.replace("%key%", key)));
            return errorList;
        }

        List<String> processedList = new ArrayList<>();
        for (String line : rawList) {
            processedList.add(formatMessage(line, placeholderPairs));
        }
        return processedList;
    }

    public String getRawMessage(String key, String... placeholderPairs) {
         return getMessage(key, placeholderPairs);
    }
}
