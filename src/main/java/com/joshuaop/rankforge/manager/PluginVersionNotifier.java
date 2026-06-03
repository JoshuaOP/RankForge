package com.joshuaop.rankforge.manager;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks SpigotMC and Modrinth for newer RankForge plugin versions.
 * Resource IDs are hardcoded — no config.yml entries required from the user.
 * Runs fully asynchronously so the server thread is never blocked.
 *
 * To change the resource IDs update the two constants below and recompile.
 */
public class PluginVersionNotifier implements Listener {

    // ── Resource IDs (set these to your actual IDs before publishing) ─────────
    private static final String SPIGOT_RESOURCE_ID   = "134929";   // e.g. "12345"
    private static final String MODRINTH_PROJECT_ID  = "rankforge";   // e.g. "rankforge"
    // ──────────────────────────────────────────────────────────────────────────

    private static final String SPIGOT_API   = "https://api.spigotmc.org/legacy/update.php?resource=";
    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/%s/version?loaders=[%%22bukkit%%22,%%22spigot%%22,%%22paper%%22]&game_versions=[]";

    // Direct Website Resource Download URLs
    private static final String SPIGOT_URL    = "https://www.spigotmc.org/resources/%E2%9C%A6-rankforge-%E2%9A%A1.134929/";
    private static final String MODRINTH_URL  = "https://modrinth.com/plugin/";

    private static final Pattern SEMVER = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");

    private final RankForge plugin;
    private final String    currentVersion;

    private volatile String  latestVersion  = null;
    private volatile boolean updateAvailable = false;
    private volatile String  updateSource   = "";
    private volatile String  updateUrl      = "";

    public PluginVersionNotifier(RankForge plugin) {
        this.plugin         = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    // ── Startup ───────────────────────────────────────────────────────────────

    public void checkOnStartup() {
        if (!plugin.getConfig().getBoolean("plugin-version-notifier.enabled", true)) return;

        plugin.getLogger().info("[PluginUpdate] Current version: v" + currentVersion + " — checking for updates...");
        fetchLatestVersionAsync();

        if (plugin.getConfig().getBoolean("plugin-version-notifier.warn-ops-on-join", true)) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    // ── Async fetch ───────────────────────────────────────────────────────────

    private void fetchLatestVersionAsync() {
        // Run via localized async scheduler to keep startup execution tracked
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // 1. Try SpigotMC
            if (!SPIGOT_RESOURCE_ID.isEmpty()) {
                String ver = fetchSpigot(SPIGOT_RESOURCE_ID);
                if (ver != null) { 
                    evaluateUpdate(ver, "SpigotMC", SPIGOT_URL + SPIGOT_RESOURCE_ID); 
                    return; 
                }
            }

            // 2. Fallback to Modrinth if Spigot fails or is empty
            if (!MODRINTH_PROJECT_ID.isEmpty()) {
                String ver = fetchModrinth(MODRINTH_PROJECT_ID);
                if (ver != null) { 
                    evaluateUpdate(ver, "Modrinth", MODRINTH_URL + MODRINTH_PROJECT_ID); 
                    return; 
                }
            }

            // Both options exhausted with no version returned
            if (!SPIGOT_RESOURCE_ID.isEmpty() || !MODRINTH_PROJECT_ID.isEmpty()) {
                plugin.getLogger().info("[PluginUpdate] Could not reach update sources — skipping version check.");
            }
        });
    }

    private String fetchSpigot(String resourceId) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SPIGOT_API + resourceId))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "RankForge/" + currentVersion)
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body().trim();
                Matcher m = SEMVER.matcher(body);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String fetchModrinth(String projectId) {
        try {
            String url = String.format(MODRINTH_API, projectId);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "RankForge/" + currentVersion + " (contact: github.com/JoshuaOP/RankForge)")
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Pattern p = Pattern.compile("\"version_number\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(resp.body());
                if (m.find()) {
                    String raw = m.group(1);
                    Matcher sv = SEMVER.matcher(raw);
                    if (sv.find()) return sv.group(1);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void evaluateUpdate(String remote, String source, String url) {
        latestVersion = remote;
        updateSource  = source;
        updateUrl     = url;

        if (isNewerVersion(remote, currentVersion)) {
            updateAvailable = true;
            plugin.getLogger().warning("[PluginUpdate] A new version is available on " + source + "! Current: v"
                    + currentVersion + " → Latest: v" + remote);
            plugin.getLogger().warning("[PluginUpdate] Download the update here: " + url);
        } else {
            plugin.getLogger().info("[PluginUpdate] You are running the latest version: v" + currentVersion);
        }
    }

    private boolean isNewerVersion(String remote, String current) {
        int[] r = parseSemver(remote);
        int[] c = parseSemver(current);
        for (int i = 0; i < 3; i++) {
            if (r[i] > c[i]) return true;
            if (r[i] < c[i]) return false;
        }
        return false;
    }

    private int[] parseSemver(String ver) {
        String[] parts = ver.split("\\.");
        int[] out = new int[3];
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try { out[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    // ── Player Join Notify ────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission("rankforge.rank.system.update")) return;
        if (!updateAvailable) return;

        // Routed directly through Bukkit main thread tasks safely
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) { // Extra structural guard block for late connections
                player.sendMessage("§6[RankForge] §ePlugin Update Available! §7v" + currentVersion + " §8→ §av" + latestVersion);
                player.sendMessage("§7  Download from " + updateSource + ": §b§n" + updateUrl);
            }
        }, 40L);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void reload() {
        updateAvailable = false;
        latestVersion   = null;
        updateSource    = "";
        updateUrl       = "";
        fetchLatestVersionAsync();
    }

    public boolean isUpdateAvailable() { return updateAvailable; }
    public String  getLatestVersion()  { return latestVersion != null ? latestVersion : currentVersion; }
    public String  getCurrentVersion() { return currentVersion; }
    public String  getUpdateSource()   { return updateSource; }
    public String  getUpdateUrl()      { return updateUrl; }

    public String getStatusLine() {
        if (updateAvailable) {
            return "§e⚠ Update available: v" + latestVersion + " §7(Link: §b§n" + updateUrl + "§7)";
        }
        return "§a✔ RankForge v" + currentVersion + " is up to date.";
    }
}
