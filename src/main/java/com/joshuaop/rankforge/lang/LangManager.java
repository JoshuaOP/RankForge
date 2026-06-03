package com.joshuaop.rankforge.lang;

import com.joshuaop.rankforge.RankForge;
import com.joshuaop.rankforge.db.PlayerData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multi-language support with per-player language preferences.
 * Falls back to the default language when a key is missing.
 */
public class LangManager {

    private final RankForge plugin;
    private final Map<String, YamlConfiguration> langs = new HashMap<>();
    private final ConcurrentHashMap<UUID, String> prefs = new ConcurrentHashMap<>();
    private String defaultLang;

    public LangManager(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    /**
     * Scans the data directory and dynamically indexes available language configuration files.
     */
    public void loadAll() {
        defaultLang = plugin.getConfig().getString("language.default", "en");
        langs.clear();

        File dir = new File(plugin.getDataFolder(), "lang");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Pre-seed core bundle resources cleanly 
        for (String baseLang : new String[]{"en", "fil", "es", "id"}) {
            File file = new File(dir, baseLang + ".yml");
            if (!file.exists()) {
                plugin.saveResource("lang/" + baseLang + ".yml", false);
            }
        }

        // Dynamically discover all valid translation assets inside the lang folder
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String langCode = file.getName().substring(0, file.getName().length() - 4);
                langs.put(langCode, YamlConfiguration.loadConfiguration(file));
            }
        }

        plugin.getLogger().info("[Lang] Dynamically indexed " + langs.size() + " language profiles: " + langs.keySet());
    }

    // ── Message Retrieval ─────────────────────────────────────────────────────

    /**
     * Get a raw message for a player (in their preferred language, with prefix applied).
     * Pass null UUID to use the default language.
     */
    public String get(UUID uuid, String key) {
        boolean perPlayer = plugin.getConfig().getBoolean("language.per-player", true);
        String lang = defaultLang;

        if (perPlayer && uuid != null) {
            // First check dynamic runtime cache maps
            if (prefs.containsKey(uuid)) {
                lang = prefs.get(uuid);
            } else {
                // Intercept data context repository layers to fetch verified profiles cleanly
                var cache = plugin.getRankManager().getCacheManager();
                PlayerData cachedRecord = cache.get(uuid);
                if (cachedRecord != null) {
                    lang = cachedRecord.language();
                    prefs.put(uuid, lang);
                }
            }
        }
        return resolve(lang, key);
    }

    private String resolve(String lang, String key) {
        YamlConfiguration cfg = langs.getOrDefault(lang, langs.get(defaultLang));
        if (cfg == null) return "§c[Missing lang: " + lang + "]";

        String prefix = cfg.getString("prefix", "§6RankForge §8»");
        String msg = cfg.getString("messages." + key);

        if (msg == null) {
            YamlConfiguration def = langs.get(defaultLang);
            msg = def != null ? def.getString("messages." + key, "§c[Missing: " + key + "]") : "§c[Missing: " + key + "]";
        }
        return msg.replace("%prefix%", prefix);
    }

    /** Apply placeholder map to a message string. */
    public String format(String msg, Map<String, String> placeholders) {
        for (var e : placeholders.entrySet())
            msg = msg.replace("%" + e.getKey() + "%", e.getValue());
        return msg;
    }

    // ── Send Shortcuts ────────────────────────────────────────────────────────

    public void send(Player player, String key) {
        player.sendMessage(get(player.getUniqueId(), key));
    }

    public void send(Player player, String key, Map<String, String> phs) {
        player.sendMessage(format(get(player.getUniqueId(), key), phs));
    }

    // ── Language Preferences ──────────────────────────────────────────────────

    /**
     * Assigns the preferred language code and pushes structural sync actions down to persistence modules.
     */
    public void setPlayerLang(UUID uuid, String lang) {
        if (!langs.containsKey(lang)) return;
        
        prefs.put(uuid, lang);

        // Update the core data record structure so UI operations and saves reflect changes in real time
        var cache = plugin.getRankManager().getCacheManager();
        PlayerData current = cache.get(uuid);
        if (current != null && !current.language().equalsIgnoreCase(lang)) {
            PlayerData updated = current.withLanguage(lang);
            cache.put(uuid, updated);
            
            // Asynchronously sync modified data state back to persistent storage units
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (plugin.getYamlPlayerDataStorage() != null) {
                    plugin.getYamlPlayerDataStorage().savePlayer(updated);
                }
            });
        }
    }

    public void resetPlayerLang(UUID uuid) { 
        prefs.remove(uuid); 
        setPlayerLang(uuid, defaultLang);
    }

    public String getPlayerLang(UUID uuid) { 
        return prefs.getOrDefault(uuid, defaultLang); 
    }

    public List<String> getAvailableLangs() { 
        return new ArrayList<>(langs.keySet()); 
    }

    public boolean isValidLang(String lang) { 
        return langs.containsKey(lang); 
    }
}
