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
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks the official SpigotMC resource for newer RankForge versions.
 * Runs fully asynchronously — the main server thread is never blocked.
 * The result is cached after the first check; call reload() to re-fetch.
 */
public class UpdateChecker implements Listener {

    private static final int    SPIGOT_RESOURCE_ID = 134929;
    private static final String SPIGOT_API_URL     = "https://api.spigotmc.org/legacy/update.php?resource=" + SPIGOT_RESOURCE_ID;
    private static final String SPIGOT_PAGE_URL    = "https://www.spigotmc.org/resources/%E2%9C%A6-rankforge-%E2%9A%A1.134929/";

    private static final Pattern SEMVER = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");

    private final RankForge plugin;
    private final String    currentVersion;
    private final Logger    log;

    private volatile String  latestVersion  = null;
    private volatile boolean updateAvailable = false;

    public UpdateChecker(RankForge plugin) {
        this.plugin         = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        this.log            = plugin.getLogger();
    }

    public void checkOnStartup() {
        if (!plugin.getConfig().getBoolean("plugin-version-notifier.enabled", true)) return;

        fetchAsync();

        if (plugin.getConfig().getBoolean("plugin-version-notifier.warn-ops-on-join", true)) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    private void fetchAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String fetched = fetchFromSpigot();
            if (fetched == null) {
                log.info("Could not reach SpigotMC — skipping version check.");
                return;
            }

            latestVersion = fetched;

            if (isNewer(fetched, currentVersion)) {
                updateAvailable = true;
                log.warning("A new version of RankForge is available!");
                log.warning("Current Version: v" + currentVersion);
                log.warning("Latest Version: v" + fetched);
                log.warning("Download: " + SPIGOT_PAGE_URL);
            } else {
                log.info("You are running the latest version of RankForge (v" + currentVersion + ").");
            }
        });
    }

    private String fetchFromSpigot() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SPIGOT_API_URL))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "RankForge/" + currentVersion)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body().trim();
                Matcher m = SEMVER.matcher(body);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private boolean isNewer(String remote, String current) {
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!updateAvailable) return;
        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission("rankforge.rank.system.update")) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage("§6[RankForge] §eA new version is available! §7v" + currentVersion + " §8→ §av" + latestVersion);
            player.sendMessage("§7  Download: §b§n" + SPIGOT_PAGE_URL);
        }, 40L);
    }

    public void reload() {
        updateAvailable = false;
        latestVersion   = null;
        fetchAsync();
    }

    public boolean isUpdateAvailable() { return updateAvailable; }
    public String  getLatestVersion()  { return latestVersion != null ? latestVersion : currentVersion; }
    public String  getCurrentVersion() { return currentVersion; }

    public String getStatusLine() {
        if (updateAvailable) {
            return "§e⚠ Update available: v" + latestVersion + " §7(§b§n" + SPIGOT_PAGE_URL + "§7)";
        }
        return "§a✔ RankForge v" + currentVersion + " is up to date.";
    }
}
