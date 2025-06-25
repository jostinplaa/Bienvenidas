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
        if (messagesFile == null) {
            messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        }
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        try (InputStream defaultConfigStream = plugin.getResource("messages.yml")){
            if (defaultConfigStream != null) {
                 YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream, StandardCharsets.UTF_8));
                messagesConfig.setDefaults(defaultConfig);
                messagesConfig.options().copyDefaults(true);
                messagesConfig.save(messagesFile);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar messages.yml con los valores por defecto.", e);
        }

        if (plugin.getConfigManager() != null) {
             this.prefix = plugin.getConfigManager().getPluginPrefix();
        } else {
            this.prefix = ChatColor.translateAlternateColorCodes('&', "&6[&eAetherAuctions&6] &r");
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
            return message;
        }

        for (int i = 0; i < placeholderPairs.length; i += 2) {
            if (placeholderPairs[i] == null || placeholderPairs[i+1] == null) {
                 plugin.getLogger().warning("[MessageManager] Par de placeholder nulo detectado para mensaje: '" + rawMessage + "'. Placeholder: " + placeholderPairs[i]);
                 continue;
            }
            message = message.replace(placeholderPairs[i], placeholderPairs[i + 1]);
        }
        return message;
    }

    public String getMessage(String key, String... placeholderPairs) {
        String rawMessage = messagesConfig.getString(key);
        if (rawMessage == null) {
            plugin.getLogger().warning("[MessageManager] Clave de mensaje no encontrada en messages.yml: '" + key + "'.");
            String errorFormat = plugin.getConfigManager().getMessagesMissingKeyFormat(); // Get format from ConfigManager
            return ChatColor.translateAlternateColorCodes('&', errorFormat.replace("%key%", key));
        }
        return formatMessage(rawMessage, placeholderPairs);
    }

    public String getPrefixedMessage(String key, String... placeholderPairs) {
        String message = getMessage(key, placeholderPairs);
        // Check if the message is the "missing key" error message
        String missingKeyErrorFormat = ChatColor.translateAlternateColorCodes('&', plugin.getConfigManager().getMessagesMissingKeyFormat().replace("%key%", key));
        if (message.equals(missingKeyErrorFormat)) {
            return message; // Don't add prefix to "key missing" errors
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
            plugin.getLogger().warning("[MessageManager] Lista de mensajes no encontrada o vacía para la clave: '" + key + "'.");
            List<String> errorList = new ArrayList<>();
            String errorFormat = plugin.getConfigManager().getMessagesMissingKeyFormat();
            errorList.add(ChatColor.translateAlternateColorCodes('&', errorFormat.replace("%key%", "lista:" + key)));
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
