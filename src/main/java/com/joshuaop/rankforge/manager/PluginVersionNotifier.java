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

    private static final Pattern SEMVER = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");

    private final RankForge plugin;
    private final String    currentVersion;

    private volatile String  latestVersion  = null;
    private volatile boolean updateAvailable = false;
    private volatile String  updateSource   = "";

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
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Try SpigotMC first
            if (!SPIGOT_RESOURCE_ID.isEmpty()) {
                String ver = fetchSpigot(SPIGOT_RESOURCE_ID);
                if (ver != null) { evaluateUpdate(ver, "SpigotMC"); return; }
            }

            // Try Modrinth
            if (!MODRINTH_PROJECT_ID.isEmpty()) {
                String ver = fetchModrinth(MODRINTH_PROJECT_ID);
                if (ver != null) { evaluateUpdate(ver, "Modrinth"); return; }
            }

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

    private void evaluateUpdate(String remote, String source) {
        latestVersion = remote;
        updateSource  = source;

        if (isNewerVersion(remote, currentVersion)) {
            updateAvailable = true;
            plugin.getLogger().warning("[PluginUpdate] A new version is available on " + source + "! Current: v"
                    + currentVersion + " → Latest: v" + remote);
            plugin.getLogger().warning("[PluginUpdate] Download the update to get the latest fixes and features.");
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

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage("§6[RankForge] §ePlugin Update Available! §7v" + currentVersion + " §8→ §av" + latestVersion);
            player.sendMessage("§7  Download the latest version from §b" + updateSource + "§7.");
        }, 40L);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void reload() {
        updateAvailable = false;
        latestVersion   = null;
        updateSource    = "";
        fetchLatestVersionAsync();
    }

    public boolean isUpdateAvailable() { return updateAvailable; }
    public String  getLatestVersion()  { return latestVersion != null ? latestVersion : currentVersion; }
    public String  getCurrentVersion() { return currentVersion; }
    public String  getUpdateSource()   { return updateSource; }

    public String getStatusLine() {
        if (updateAvailable) {
            return "§e⚠ Update available: v" + latestVersion + " (via " + updateSource + ")";
        }
        return "§a✔ RankForge v" + currentVersion + " is up to date.";
    }
}
