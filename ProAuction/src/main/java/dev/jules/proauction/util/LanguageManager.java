package dev.jules.proauction.util;

import dev.jules.proauction.ProAuction;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageManager {

    private final ProAuction plugin;
    private FileConfiguration langConfig = null;
    private File langFile = null;
    private String langFileName;
    private final Map<String, String> messages = new HashMap<>();
    private final Pattern placeholderPattern = Pattern.compile("\\{(\\w+)\\}"); // Pattern for {placeholder}

    public LanguageManager(ProAuction plugin, String languageCode) {
        this.plugin = plugin;
        this.langFileName = "messages_" + languageCode + ".yml";
        saveDefaultLanguageFile();
        loadMessages();
    }

    private void saveDefaultLanguageFile() {
        langFile = new File(plugin.getDataFolder(), langFileName);
        if (!langFile.exists()) {
            plugin.saveResource(langFileName, false); // Saves from JAR if exists
        }
        // If it still doesn't exist (e.g. custom lang code not in JAR), try to save English as a fallback
        if (!langFile.exists() && !langFileName.equals("messages_en.yml")) {
            plugin.getLogger().warning("Language file " + langFileName + " not found in JAR. Attempting to use messages_en.yml as fallback template.");
            InputStream enStream = plugin.getResource("messages_en.yml");
            if (enStream != null) {
                // Ensure data folder exists before trying to save a new file there
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                YamlConfiguration enConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(enStream, StandardCharsets.UTF_8));
                 try {
                    enConfig.save(langFile); // Save the English content to the new langFile path
                    plugin.getLogger().info("Created " + langFileName + " based on messages_en.yml. Please translate this file.");
                } catch (Exception e) {
                    plugin.getLogger().severe("Could not save " + langFileName + " based on English template: " + e.getMessage());
                }
            } else {
                 plugin.getLogger().severe("Fallback messages_en.yml also not found in JAR. Language system may not work correctly.");
            }
        }
         // Final check for data folder, though above should handle it for new files
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
    }

    public void loadMessages() {
        messages.clear();
        if (langFile == null ) { // If langFile is null, try to initialize it
            langFile = new File(plugin.getDataFolder(), langFileName);
        }

        if (!langFile.exists()) {
            plugin.getLogger().warning(langFileName + " not found. Attempting to create/restore it.");
            saveDefaultLanguageFile(); // Attempt to save/create it
            if (!langFile.exists()){ // If still not there, severe issue
                 plugin.getLogger().severe("Failed to create or find " + langFileName + " after attempting restore. Messages will be missing or default to keys.");
                return;
            }
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        // Load defaults from JAR resource to ensure all keys are present if file is modified by user
        InputStream defaultStream = plugin.getResource(langFileName);
        // If specific lang file isn't in JAR, try en.yml as the ultimate fallback for defaults
        if (defaultStream == null && !langFileName.equals("messages_en.yml")) {
            plugin.getLogger().warning("Default language file " + langFileName + " is missing from JAR. Trying messages_en.yml for default keys.");
            defaultStream = plugin.getResource("messages_en.yml");
        }

        if (defaultStream == null && langFileName.equals("messages_en.yml")) { // Critical if en.yml itself is missing from jar
             plugin.getLogger().severe("CRITICAL: Default language file messages_en.yml is missing from JAR. This is a fundamental error.");
        }


        if (defaultStream != null) {
            FileConfiguration defaultLangConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            for (String key : defaultLangConfig.getKeys(true)) {
                if (!defaultLangConfig.isConfigurationSection(key)) { // Only load actual message keys
                    // Use value from user's file if it exists, otherwise use default from JAR
                    messages.put(key, ChatColor.translateAlternateColorCodes('&', langConfig.getString(key, defaultLangConfig.getString(key))));
                }
            }
        } else { // Fallback if no default stream at all (e.g. even en.yml missing from JAR)
             plugin.getLogger().severe("No default language stream found (not even messages_en.yml in JAR!). Loading directly from user file for " + langFileName + ", if it exists and is complete.");
            for (String key : langConfig.getKeys(true)) { // This will only load what's in the user's file
                 if (!langConfig.isConfigurationSection(key)) {
                    messages.put(key, ChatColor.translateAlternateColorCodes('&', langConfig.getString(key)));
                 }
            }
        }
        plugin.getLogger().info("Loaded " + messages.size() + " messages for language: " + langFileName.replace("messages_", "").replace(".yml", ""));
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, ChatColor.RED + "Missing message: " + key + " (" + langFileName.replace("messages_", "").replace(".yml", "") + ")");
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key); // This now includes the "Missing message" part if key not found
        if (placeholders == null || placeholders.isEmpty()) {
            return message;
        }
        Matcher matcher = placeholderPattern.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String placeholderKey = matcher.group(1);
            // Fallback to the original {placeholder} string if the key is not in the map.
            String replacement = placeholders.getOrDefault(placeholderKey, "{" + placeholderKey + "}");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // Overloaded helper for simple single placeholder replacement
    public String getMessage(String key, String placeholder, String value) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholder, value);
        return getMessage(key, placeholders);
    }

    public String getLangFileNameForLogging() {
        return this.langFileName;
    }
}
