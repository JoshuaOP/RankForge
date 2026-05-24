package com.joshuaop.rankforge.lang;

import com.joshuaop.rankforge.RankForge;
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

    private static final String[] AVAILABLE = {"en", "fil", "es", "id"};

    private final RankForge plugin;
    private final Map<String, YamlConfiguration> langs  = new HashMap<>();
    private final ConcurrentHashMap<UUID, String> prefs  = new ConcurrentHashMap<>();
    private String defaultLang;

    public LangManager(RankForge plugin) {
        this.plugin = plugin;
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    public void loadAll() {
        defaultLang = plugin.getConfig().getString("language.default", "en");
        langs.clear();
        for (String lang : AVAILABLE) loadLang(lang);
        plugin.getLogger().info("[Lang] Loaded: " + Arrays.toString(AVAILABLE));
    }

    private void loadLang(String lang) {
        File dir  = new File(plugin.getDataFolder(), "lang");
        dir.mkdirs();
        File file = new File(dir, lang + ".yml");
        if (!file.exists()) plugin.saveResource("lang/" + lang + ".yml", false);
        langs.put(lang, YamlConfiguration.loadConfiguration(file));
    }

    // ── Message Retrieval ─────────────────────────────────────────────────────

    /**
     * Get a raw message for a player (in their preferred language, with prefix applied).
     * Pass null UUID to use the default language.
     */
    public String get(UUID uuid, String key) {
        boolean perPlayer = plugin.getConfig().getBoolean("language.per-player", true);
        String lang = (perPlayer && uuid != null) ? prefs.getOrDefault(uuid, defaultLang) : defaultLang;
        return resolve(lang, key);
    }

    private String resolve(String lang, String key) {
        YamlConfiguration cfg = langs.getOrDefault(lang, langs.get(defaultLang));
        if (cfg == null) return "§c[Missing lang: " + lang + "]";

        String prefix = cfg.getString("prefix", "§6RankForge §8»");
        String msg    = cfg.getString("messages." + key);

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

    public void setPlayerLang(UUID uuid, String lang) {
        if (langs.containsKey(lang)) prefs.put(uuid, lang);
    }

    public void resetPlayerLang(UUID uuid) { prefs.remove(uuid); }

    public String getPlayerLang(UUID uuid) { return prefs.getOrDefault(uuid, defaultLang); }

    public List<String> getAvailableLangs() { return Arrays.asList(AVAILABLE); }

    public boolean isValidLang(String lang) { return langs.containsKey(lang); }
}
