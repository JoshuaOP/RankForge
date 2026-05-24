package com.joshuaop.rankforge.manager;

import com.joshuaop.rankforge.RankForge;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitHub Releases API for a newer version of RankForge and notifies
 * online operators when an update is available.
 *
 * <h3>config.yml options:</h3>
 * <pre>
 * update-checker:
 *   enabled: true
 *   github-repo: "JoshuaOP/RankForge"
 *   check-interval-hours: 24
 *   notify-ops-on-join: true
 * </pre>
 *
 * <p>The check is performed asynchronously on a background thread and never
 * blocks the main server thread. Results are cached until the next scheduled
 * check to avoid repeated HTTP calls.
 *
 * <p>GitHub's unauthenticated rate limit is 60 requests/hour per IP.
 * With a 24-hour interval this is well within limits.
 *
 * <p>This class implements {@link Listener} and must be registered via
 * {@code Bukkit.getPluginManager().registerEvents(notifier, plugin)}.
 */
public class GitHubUpdateNotifier implements Listener {

    private static final String API_URL_TEMPLATE =
            "https://api.github.com/repos/%s/releases/latest";
    private static final Pattern TAG_PATTERN =
            Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"");

    private final RankForge    plugin;
    private final Logger       log;
    private final String       currentVersion;
    private final String       githubRepo;

    /** Latest version string fetched from GitHub (null = not checked yet). */
    private volatile String    latestVersion  = null;
    /** Whether an update is available. */
    private volatile boolean   updateAvailable = false;
    /** Guards against concurrent checks. */
    private final    AtomicBoolean checking    = new AtomicBoolean(false);

    public GitHubUpdateNotifier(RankForge plugin) {
        this.plugin         = plugin;
        this.log            = plugin.getLogger();
        this.currentVersion = plugin.getDescription().getVersion();
        this.githubRepo     = plugin.getConfig()
                .getString("update-checker.github-repo", "JoshuaOP/RankForge");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Schedule periodic update checks.
     * Called from {@link RankForge#onEnable()}.
     */
    public void start() {
        if (!isEnabled()) {
            log.info("[UpdateChecker] Disabled in config.");
            return;
        }

        long intervalHours = plugin.getConfig().getLong("update-checker.check-interval-hours", 24L);
        long intervalTicks = intervalHours * 72_000L; // 20t/s × 3600s

        // First check — 60 ticks after startup (async)
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::check, 60L);

        // Subsequent periodic checks
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, intervalTicks, intervalTicks);
    }

    // ── Check ─────────────────────────────────────────────────────────────────

    /**
     * Perform an HTTP check against the GitHub Releases API.
     * Safe to call from any thread.
     */
    public void check() {
        if (!checking.compareAndSet(false, true)) return; // already running
        try {
            String apiUrl = String.format(API_URL_TEMPLATE, githubRepo);
            URL    url    = URI.create(apiUrl).toURL();

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.setRequestProperty("User-Agent", "RankForge/" + currentVersion);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.fine("[UpdateChecker] GitHub API returned " + responseCode);
                return;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }

            Matcher matcher = TAG_PATTERN.matcher(response.toString());
            if (!matcher.find()) {
                log.fine("[UpdateChecker] Could not parse tag_name from GitHub response.");
                return;
            }

            latestVersion   = matcher.group(1).trim();
            updateAvailable = isNewer(latestVersion, currentVersion);

            if (updateAvailable) {
                log.warning("[UpdateChecker] A new version of RankForge is available: v"
                        + latestVersion + " (you have v" + currentVersion + ")");
                log.warning("[UpdateChecker] https://github.com/" + githubRepo + "/releases/latest");

                // Notify online ops on the main thread
                Bukkit.getScheduler().runTask(plugin, this::notifyOnlineOps);
            } else {
                log.fine("[UpdateChecker] RankForge is up to date (v" + currentVersion + ").");
            }

        } catch (Exception e) {
            log.fine("[UpdateChecker] Check failed: " + e.getMessage());
        } finally {
            checking.set(false);
        }
    }

    // ── Listener ──────────────────────────────────────────────────────────────

    /**
     * Notify operators when they join if an update is available
     * and {@code update-checker.notify-ops-on-join} is true.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled()) return;
        if (!plugin.getConfig().getBoolean("update-checker.notify-ops-on-join", true)) return;
        if (!updateAvailable) return;
        if (!player.hasPermission("rankforge.rank.reload")) return;

        // Slight delay so the player's chat loads before the notification
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendMessage(ChatColor.GOLD + "[RankForge] " + ChatColor.YELLOW
                    + "A new update is available: "
                    + ChatColor.GREEN + "v" + latestVersion
                    + ChatColor.YELLOW + " (you have v" + currentVersion + ")");
            player.sendMessage(ChatColor.GOLD + "[RankForge] "
                    + ChatColor.AQUA + "https://github.com/" + githubRepo + "/releases/latest");
        }, 40L);
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    public boolean isUpdateAvailable() { return updateAvailable; }
    public String  getLatestVersion()  { return latestVersion; }
    public String  getCurrentVersion() { return currentVersion; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("update-checker.enabled", true);
    }

    /** Notify all currently-online operators. */
    private void notifyOnlineOps() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("rankforge.rank.reload")) {
                p.sendMessage(ChatColor.GOLD + "[RankForge] " + ChatColor.YELLOW
                        + "Update available: " + ChatColor.GREEN + "v" + latestVersion
                        + ChatColor.YELLOW + " → " + ChatColor.AQUA
                        + "https://github.com/" + githubRepo + "/releases/latest");
            }
        }
    }

    /**
     * Simple semver comparison.
     * Returns true if {@code candidate} is newer than {@code current}.
     * Handles formats: "1.2.3", "1.2", "1".
     */
    static boolean isNewer(String candidate, String current) {
        int[] c = parse(candidate);
        int[] b = parse(current);
        int len = Math.max(c.length, b.length);
        for (int i = 0; i < len; i++) {
            int cv = i < c.length ? c[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (cv > bv) return true;
            if (cv < bv) return false;
        }
        return false;
    }

    private static int[] parse(String version) {
        String[] parts = version.replaceAll("[^0-9.]", "").split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { result[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException e) { result[i] = 0; }
        }
        return result;
    }
}
