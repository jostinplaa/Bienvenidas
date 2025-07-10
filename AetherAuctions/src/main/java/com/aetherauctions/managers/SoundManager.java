package com.aetherauctions.managers;

import com.aetherauctions.AetherAuctions;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
// No se necesita Level aquí si solo se usa plugin.getLogger().warning/severe

public class SoundManager {
    private final AetherAuctions plugin;
    private boolean soundsEnabled;
    private final Map<String, Sound> soundMap = new HashMap<>();
    private final Map<String, Float> volumeMap = new HashMap<>();
    private final Map<String, Float> pitchMap = new HashMap<>();

    private static final class DefaultSound {
        String bukkitSound;
        float volume;
        float pitch;
        DefaultSound(String bukkitSound, float volume, float pitch) {
            this.bukkitSound = bukkitSound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private final Map<String, DefaultSound> defaultSounds = new HashMap<String, DefaultSound>() {{
        put("click", new DefaultSound("UI_BUTTON_CLICK", 1.0f, 1.0f));
        put("open_gui", new DefaultSound("BLOCK_CHEST_OPEN", 0.8f, 1.1f));
        put("close_gui", new DefaultSound("BLOCK_CHEST_CLOSE", 0.8f, 1.1f));
        put("page_turn", new DefaultSound("ITEM_BOOK_PAGE_TURN", 0.8f, 1.0f));
        put("bid_placed_success", new DefaultSound("ENTITY_EXPERIENCE_ORB_PICKUP", 0.8f, 1.2f));
        put("bid_outbid_notification", new DefaultSound("ENTITY_VILLAGER_NO", 1.0f, 0.9f));
        put("auction_won", new DefaultSound("ENTITY_PLAYER_LEVELUP", 0.9f, 1.0f));
        put("auction_sold_for_seller", new DefaultSound("ENTITY_VILLAGER_YES", 1.0f, 1.0f));
        put("auction_item_claimed", new DefaultSound("ENTITY_ITEM_PICKUP", 0.8f, 1.1f));
        put("error", new DefaultSound("BLOCK_NOTE_BLOCK_PLING", 1.0f, 0.7f));
        put("inventory_full", new DefaultSound("ENTITY_VILLAGER_NO", 1.0f, 0.8f));
        put("auction_created", new DefaultSound("ENTITY_ITEM_FRAME_ADD_ITEM", 1.0f, 1.0f));
        put("auction_cancelled", new DefaultSound("BLOCK_LEVER_CLICK", 1.0f, 0.8f));
        put("reward_pending_notification", new DefaultSound("ENTITY_EXPERIENCE_ORB_PICKUP", 0.7f, 1.5f));
    }};

    public SoundManager(AetherAuctions plugin) {
        this.plugin = plugin;
        loadSounds();
    }

    public void loadSounds() {
        soundMap.clear();
        volumeMap.clear();
        pitchMap.clear();

        com.aetherauctions.config.ConfigManager cfgManager = plugin.getConfigManager();
        soundsEnabled = cfgManager.isGuiSoundsEnabled(); // Usar ConfigManager

        if (!soundsEnabled) {
            plugin.getLogger().info("GUI sounds are disabled in config.yml.");
            return;
        }

        // ConfigManager debería idealmente exponer un método para obtener esta sección,
        // o SoundManager podría seguir accediendo directamente a plugin.getConfig() para esta sección específica
        // si se considera que la estructura de sonidos es demasiado compleja para getters individuales.
        // Por ahora, mantendremos el acceso directo a la sección, pero a través de la instancia de config de ConfigManager.
        FileConfiguration currentConfig = plugin.getConfig(); // O idealmente cfgManager.getRawConfig().
        ConfigurationSection soundsSection = currentConfig.getConfigurationSection("sounds"); // Ruta actualizada

        if (soundsSection == null) {
            plugin.getLogger().warning("'sounds' section is missing in config.yml. Using default sounds.");
            for (Map.Entry<String, DefaultSound> entry : defaultSounds.entrySet()) {
                try {
                    soundMap.put(entry.getKey(), Sound.valueOf(entry.getValue().bukkitSound.toUpperCase()));
                    volumeMap.put(entry.getKey(), entry.getValue().volume);
                    pitchMap.put(entry.getKey(), entry.getValue().pitch);
                } catch (IllegalArgumentException ex) {
                     plugin.getLogger().severe("Default sound name '" + entry.getValue().bukkitSound + "' for key '" + entry.getKey() + "' is invalid.");
                }
            }
            return;
        }

        for (Map.Entry<String, DefaultSound> entry : defaultSounds.entrySet()) {
            String key = entry.getKey();
            DefaultSound defaultSoundInfo = entry.getValue();

            // Usar soundsSection para obtener valores específicos de la sub-sección
            String soundName = soundsSection.getString(key + ".sound", defaultSoundInfo.bukkitSound);
            float volume = (float) soundsSection.getDouble(key + ".volume", defaultSoundInfo.volume);
            float pitch = (float) soundsSection.getDouble(key + ".pitch", defaultSoundInfo.pitch);

            try {
                Sound bukkitSound = Sound.valueOf(soundName.toUpperCase());
                soundMap.put(key, bukkitSound);
                volumeMap.put(key, volume);
                pitchMap.put(key, pitch);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound name '" + soundName + "' for sound key '" + key + "' in config.yml. Using default: " + defaultSoundInfo.bukkitSound);
                try {
                    soundMap.put(key, Sound.valueOf(defaultSoundInfo.bukkitSound.toUpperCase()));
                    volumeMap.put(key, defaultSoundInfo.volume);
                    pitchMap.put(key, defaultSoundInfo.pitch);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().severe("Default sound name '" + defaultSoundInfo.bukkitSound + "' for key '" + key + "' is also invalid. This sound will not play.");
                }
            }
        }
        plugin.getLogger().info("Sound configurations loaded. Sounds enabled: " + soundsEnabled);
    }

    public void playSound(Player player, String soundKey) {
        if (!soundsEnabled || player == null || soundKey == null || soundKey.isEmpty()) {
            return;
        }

        String keyLowerCase = soundKey.toLowerCase();
        Sound sound = soundMap.get(keyLowerCase);
        Float volume = volumeMap.get(keyLowerCase);
        Float pitch = pitchMap.get(keyLowerCase);

        if (sound != null && volume != null && pitch != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } else {
            plugin.getLogger().warning("Attempted to play undefined sound key: '" + soundKey + "'. Please check config.yml or SoundManager defaults.");
        }
    }
}
