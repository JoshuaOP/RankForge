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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the running Minecraft version and warns admins if it is unsupported.
 * Fetches the supported version list from the PaperMC API on startup (async)
 * so the list stays current without requiring config changes.
 */
public class McVersionNotifier implements Listener {

    private static final String PAPER_API_URL = "https://api.papermc.io/v2/projects/paper";
    private static final Pattern VERSION_PATTERN = Pattern.compile("\"([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)\"");

    private final RankForge      plugin;
    private final Set<String>    supportedVersions = new HashSet<>();
    private String               detectedVersion;
    private boolean              supported;

    public McVersionNotifier(RankForge plugin) {
        this.plugin = plugin;
        loadFallbackVersions();
        detect();
    }

    // ── Startup Check ─────────────────────────────────────────────────────────

    public void checkOnStartup() {
        if (!plugin.getConfig().getBoolean("mc-version-notifier.enabled", true)) return;

        Logger log = plugin.getLogger();
        log.info("[MC Version] Detected: " + detectedVersion);

        if (supported) {
            log.info("[MC Version] ✔ Version " + detectedVersion + " is supported.");
        } else {
            log.warning("[MC Version] ✘ Version " + detectedVersion + " is not in the offline supported list.");
            log.info("[MC Version] Fetching latest supported versions from PaperMC API…");
        }

        // Always fetch the latest list async — updates the in-memory set
        fetchSupportedVersionsAsync();

        if (plugin.getConfig().getBoolean("mc-version-notifier.warn-ops-on-join", true)) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
    }

    // ── PaperMC API fetch ─────────────────────────────────────────────────────

    private void fetchSupportedVersionsAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(PAPER_API_URL))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    parseVersions(response.body());
                    // Re-evaluate after fetching
                    supported = supportedVersions.contains(detectedVersion);
                    if (supported) {
                        plugin.getLogger().info("[MC Version] ✔ PaperMC API confirms version "
                                + detectedVersion + " is supported.");
                    } else {
                        plugin.getLogger().warning("[MC Version] ✘ PaperMC API does not list version "
                                + detectedVersion + ". Plugin may still work.");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().info("[MC Version] Could not reach PaperMC API — using offline list.");
            }
        });
    }

    private void parseVersions(String json) {
        // Extract the "versions" array from the JSON response
        int start = json.indexOf("\"versions\"");
        if (start < 0) return;
        int arrStart = json.indexOf('[', start);
        int arrEnd   = json.indexOf(']', arrStart);
        if (arrStart < 0 || arrEnd < 0) return;

        String versionsSection = json.substring(arrStart, arrEnd + 1);
        Matcher m = VERSION_PATTERN.matcher(versionsSection);
        while (m.find()) {
            supportedVersions.add(m.group(1));
        }
    }

    // ── Version Detection ─────────────────────────────────────────────────────

    private void detect() {
        String raw = Bukkit.getBukkitVersion();
        detectedVersion = raw.split("-")[0];
        supported = supportedVersions.contains(detectedVersion);
    }

    @SuppressWarnings("unchecked")
    private void loadFallbackVersions() {
        List<String> fromConfig = (List<String>) plugin.getConfig().getList(
                "mc-version-notifier.supported-versions", List.of());
        supportedVersions.addAll(fromConfig);
    }

    // ── Player Join Warn ──────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp() && !player.hasPermission("rankforge.rank.system.mcversion")) return;
        if (supported) return;

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                player.sendMessage("§6[RankForge] §eWarning: §7MC version §e" + detectedVersion
                        + " §7is not confirmed supported. Plugin may still work normally."), 40L);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void reload() {
        loadFallbackVersions();
        detect();
    }

    public String  getDetectedVersion() { return detectedVersion; }
    public boolean isSupported()        { return supported; }

    public String getStatusLine() {
        return supported
                ? "§a✔ MC " + detectedVersion + " is supported."
                : "§e⚠ MC " + detectedVersion + " not confirmed — may still work.";
    }
}
