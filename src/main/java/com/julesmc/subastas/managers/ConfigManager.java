package com.julesmc.subastas.managers;

import com.julesmc.subastas.SubastasPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final SubastasPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(SubastasPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        // Guarda el config.yml por defecto desde resources si no existe
        plugin.saveDefaultConfig();
        // Carga la configuración
        config = plugin.getConfig();
        // Asegura que plugin.getConfig() no sea nulo después de saveDefaultConfig()
        // y recarga por si acaso.
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public String getString(String path, String defaultValue) {
        if (config == null) {
            plugin.getLogger().warning("Config not loaded yet, but getString was called. Path: " + path);
            return defaultValue;
        }
        return config.getString(path, defaultValue);
    }

    public int getInt(String path, int defaultValue) {
        if (config == null) {
            plugin.getLogger().warning("Config not loaded yet, but getInt was called. Path: " + path);
            return defaultValue;
        }
        return config.getInt(path, defaultValue);
    }

    public double getDouble(String path, double defaultValue) {
        if (config == null) {
            plugin.getLogger().warning("Config not loaded yet, but getDouble was called. Path: " + path);
            return defaultValue;
        }
        return config.getDouble(path, defaultValue);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        if (config == null) {
            plugin.getLogger().warning("Config not loaded yet, but getBoolean was called. Path: " + path);
            return defaultValue;
        }
        return config.getBoolean(path, defaultValue);
    }

    // Podrías añadir un método para recargar la configuración si es necesario
    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        plugin.getLogger().info("Configuration reloaded.");
    }
}
