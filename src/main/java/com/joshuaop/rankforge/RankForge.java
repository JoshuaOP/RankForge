package com.joshuaop.rankforge;

import com.joshuaop.rankforge.api.RankForgeAPI;
import com.joshuaop.rankforge.api.expansion.ExpansionRegistry;
import com.joshuaop.rankforge.api.gui.ExternalGUIRegistry;
import com.joshuaop.rankforge.api.hook.HookRegistry;
import com.joshuaop.rankforge.api.requirement.CustomRequirementRegistry;
import com.joshuaop.rankforge.api.rest.RestAPIServer;
import com.joshuaop.rankforge.command.RankCommand;
import com.joshuaop.rankforge.cosmetic.CosmeticManager;
import com.joshuaop.rankforge.db.DatabaseManager;
import com.joshuaop.rankforge.db.SyncService;
import com.joshuaop.rankforge.db.YamlPlayerDataStorage;
import com.joshuaop.rankforge.experience.ExperienceManager;
import com.joshuaop.rankforge.experience.RankHistoryManager;
import com.joshuaop.rankforge.gui.GUIConfig;
import com.joshuaop.rankforge.gui.GUIListener;
import com.joshuaop.rankforge.lang.LangManager;
import com.joshuaop.rankforge.manager.AnnouncementManager;
import com.joshuaop.rankforge.manager.AntiBypassManager;
import com.joshuaop.rankforge.manager.GuiClickShieldManager;
import com.joshuaop.rankforge.manager.RequirementManager;
import com.joshuaop.rankforge.manager.SoundManager;
import com.joshuaop.rankforge.performance.PerformanceManager;
import com.joshuaop.rankforge.performance.TaskScheduler;
import com.joshuaop.rankforge.permission.PermissionNodeGenerator;
import com.joshuaop.rankforge.placeholder.RankForgePlaceholders;
import com.joshuaop.rankforge.protection.AntiAbuseManager;
import com.joshuaop.rankforge.protection.RankupQueue;
import com.joshuaop.rankforge.rank.RankManager;
import com.joshuaop.rankforge.softdep.SoftDependency;
import com.joshuaop.rankforge.tracker.BlockBreakTracker;
import com.joshuaop.rankforge.yaml.ConfigUpdater;
import com.joshuaop.rankforge.yaml.RankYamlManager;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RankForge main plugin class — wires all systems together.
 */
public final class RankForge extends JavaPlugin {

    /**
     * bStats plugin ID — registered at https://bstats.org/plugin/bukkit/RankForge
     * Metrics are always enabled and cannot be disabled.
     */
    private static final int BSTATS_PLUGIN_ID = 31704;

    private static RankForge instance;

    // ── Core ──────────────────────────────────────────────────────────────────
    private DatabaseManager           databaseManager;
    private YamlPlayerDataStorage     yamlPlayerDataStorage;
    private RankYamlManager           rankYamlManager;
    private RankManager               rankManager;
    private LangManager               langManager;
    private SyncService               syncService;
    private RankForgeAPI              api;
    private ConfigUpdater             configUpdater;
    private GUIConfig                 guiConfig;

    // ── Classic Managers ─────────────────────────────────────────────────────
    private AntiBypassManager         antiBypassManager;
    private GuiClickShieldManager     guiClickShieldManager;
    private SoundManager              soundManager;
    private AnnouncementManager       announcementManager;
    private RequirementManager        requirementManager;
    private PermissionNodeGenerator   permissionNodeGenerator;

    // ── v2.x Systems ─────────────────────────────────────────────────────────
    private SoftDependency            softDependency;
    private PerformanceManager        performanceManager;
    private TaskScheduler             taskScheduler;
    private CosmeticManager           cosmeticManager;
    private AntiAbuseManager          antiAbuseManager;
    private RankupQueue               rankupQueue;
    private BlockBreakTracker         blockBreakTracker;

    // ── Experience & History ──────────────────────────────────────────────────
    private ExperienceManager         experienceManager;
    private RankHistoryManager        historyManager;

    // ── Developer API Ecosystem ───────────────────────────────────────────────
    private CustomRequirementRegistry customRequirementRegistry;
    private ExpansionRegistry         expansionRegistry;
    private HookRegistry              hookRegistry;
    private ExternalGUIRegistry       externalGUIRegistry;
    private RestAPIServer             restAPIServer;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        runConfigUpdater();
        initLang();
        initDatabase();
        initYaml();
        initCoreManagers();
        initExperienceSystems();
        initAPIEcosystem();
        initSoftDep();
        initPermissions();
        registerCommands();
        registerListeners();
        postInit();
        initMetrics();

        getLogger().info("RankForge v" + getDescription().getVersion() + " successfully loaded! ("
                + rankManager.getRankCount() + " ranks compiled)");
    }

    @Override
    public void onDisable() {
        if (restAPIServer      != null) restAPIServer.stop();
        if (expansionRegistry  != null) expansionRegistry.disableAll();
        if (performanceManager != null) performanceManager.stop();
        if (taskScheduler      != null) taskScheduler.cancelAll();
        if (cosmeticManager    != null) cosmeticManager.shutdown();

        // Flush block-break counters before saving so no break counts are lost.
        if (blockBreakTracker != null) blockBreakTracker.flushAll();

        if (rankManager != null && rankManager.getCacheManager() != null) {
            if (databaseManager != null && databaseManager.isConnected() && syncService != null) {
                syncService.flushNow();
                syncService.stop();
            } else if (yamlPlayerDataStorage != null) {
                yamlPlayerDataStorage.saveAll(rankManager.getCacheManager().getOnlineAndUnexpired());
            }
        }

        if (rankYamlManager != null) rankYamlManager.saveSync();
        if (databaseManager != null) databaseManager.disconnect();
        getLogger().info("RankForge disabled safely.");
    }

    // ── Init phases ───────────────────────────────────────────────────────────

    private void runConfigUpdater() {
        configUpdater = new ConfigUpdater(this);
        configUpdater.updateAll();
        reloadConfig();
    }

    private void initLang() {
        langManager = new LangManager(this);
        langManager.loadAll();
    }

    private void initDatabase() {
        databaseManager       = new DatabaseManager(this);
        boolean mysqlOk       = databaseManager.connect();
        yamlPlayerDataStorage = new YamlPlayerDataStorage(this);

        if (!mysqlOk && isDebug()) {
            getLogger().info("[DB] Defaulting to fallback local storage tracking.");
        }
    }

    private void initYaml() {
        rankYamlManager = new RankYamlManager(this);
        rankYamlManager.initialize();
        guiConfig = new GUIConfig(this);
    }

    private void initCoreManagers() {
        rankManager = new RankManager(this);
        rankManager.loadRanks();

        if (!databaseManager.isConnected() && yamlPlayerDataStorage != null) {
            var stored = yamlPlayerDataStorage.loadAll();
            for (var pd : stored) {
                rankManager.getCacheManager().put(pd.uuid(), pd);
            }
        }

        antiBypassManager     = new AntiBypassManager(this);
        guiClickShieldManager = new GuiClickShieldManager(this);
        soundManager          = new SoundManager(this);
        announcementManager   = new AnnouncementManager(this);
        requirementManager    = new RequirementManager(this);
        syncService           = new SyncService(this);

        performanceManager    = new PerformanceManager(this);
        taskScheduler         = new TaskScheduler(this);
        antiAbuseManager      = new AntiAbuseManager(this);
        rankupQueue           = new RankupQueue();
        cosmeticManager       = new CosmeticManager(this);

        // BlockBreakTracker must be initialised before registerListeners()
        // so the join event populates counters before the first break occurs.
        blockBreakTracker = new BlockBreakTracker(this);
    }

    private void initExperienceSystems() {
        experienceManager = new ExperienceManager(this);
        historyManager    = new RankHistoryManager(this);
    }

    private void initAPIEcosystem() {
        customRequirementRegistry = new CustomRequirementRegistry();
        expansionRegistry         = new ExpansionRegistry(getLogger());
        hookRegistry              = new HookRegistry(getLogger());
        externalGUIRegistry       = new ExternalGUIRegistry(getLogger());
        api                       = new RankForgeAPI(this);
        restAPIServer             = new RestAPIServer(this);
    }

    private void initSoftDep() {
        softDependency = new SoftDependency(this);
        softDependency.initialize();
    }

    private void initPermissions() {
        permissionNodeGenerator = new PermissionNodeGenerator(this);
        permissionNodeGenerator.generateAll();
    }

    /**
     * Initialise bStats metrics.
     * Metrics are always enabled — no config toggle.
     * bStats respects the global opt-out at plugins/bStats/config.yml.
     */
    private void initMetrics() {
        try {
            new Metrics(this, BSTATS_PLUGIN_ID);
        } catch (Exception e) {
            if (isDebug()) {
                getLogger().warning("[Metrics] bStats failed to initialize: " + e.getMessage());
            }
        }
    }

    private void registerCommands() {
        var rankCommand = new RankCommand(this);
        var cmd = getCommand("rank");
        if (cmd != null) {
            cmd.setExecutor(rankCommand);
            cmd.setTabCompleter(rankCommand);
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(softDependency, this);
        // BlockBreakTracker handles PlayerJoinEvent, PlayerQuitEvent, and BlockBreakEvent.
        getServer().getPluginManager().registerEvents(blockBreakTracker, this);
        cosmeticManager.registerListeners();
    }

    private void postInit() {
        if (databaseManager.isConnected()) syncService.start();

        performanceManager.start();

        if (softDependency.hasPapi()) {
            new RankForgePlaceholders(this).register();
        }

        taskScheduler.repeatAsync(() -> rankManager.getCacheManager().purgeExpired(), 6000L, 6000L);
        taskScheduler.repeatAsync(() -> antiAbuseManager.purge(), 2400L, 2400L);

        // Periodic block-break flush to keep cache entries current between syncs.
        long blockFlushInterval = getConfig().getLong("tracker.block-break-flush-ticks", 100L);
        taskScheduler.repeatAsync(() -> {
            if (blockBreakTracker != null) blockBreakTracker.flushAll();
        }, blockFlushInterval, blockFlushInterval);

        if (!databaseManager.isConnected()) {
            long yamlSyncInterval = getConfig().getLong("sync.interval-ticks", 200L);
            taskScheduler.repeatAsync(() -> {
                if (yamlPlayerDataStorage != null) {
                    var snapshot = rankManager.getCacheManager().getOnlineAndUnexpired();
                    if (!snapshot.isEmpty()) yamlPlayerDataStorage.saveAll(snapshot);
                }
            }, yamlSyncInterval, yamlSyncInterval);
        }

        restAPIServer.start();
    }

    // ── Hot-reload ────────────────────────────────────────────────────────────

    public void reload() {
        // Stop all active particle tasks before reloading so no orphaned schedulers survive.
        if (cosmeticManager != null) cosmeticManager.shutdown();

        configUpdater.updateAll();
        reloadConfig();
        langManager.loadAll();
        rankYamlManager.hotReload();
        rankManager.loadRanks();

        // Repair any cached player data whose rank no longer exists after the reload.
        rankManager.repairOrphanedRanks();

        soundManager.reload();
        antiBypassManager.reload();
        expansionRegistry.reloadAll();
        if (guiConfig != null) guiConfig.load();
        // Clear offline player head reference cache so stale skin data is not served.
        com.joshuaop.rankforge.gui.PlayerListGUI.clearHeadCache();

        // Restart cosmetics for all currently online players using their (possibly repaired) rank.
        if (cosmeticManager != null) {
            var cache = rankManager.getCacheManager();
            for (Player online : Bukkit.getOnlinePlayers()) {
                String rankId = cache.contains(online.getUniqueId())
                        ? cache.get(online.getUniqueId()).rankId()
                        : rankManager.getDefaultRankId();
                cosmeticManager.onLogin(online, rankId);
            }
        }

        getLogger().info("RankForge configuration reloaded.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** @return true if debug logging is enabled in config.yml */
    public boolean isDebug() {
        return getConfig().getBoolean("debug", false);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static RankForge              getInstance()                     { return instance; }
    public DatabaseManager               getDatabaseManager()              { return databaseManager; }
    public YamlPlayerDataStorage         getYamlPlayerDataStorage()        { return yamlPlayerDataStorage; }
    public RankYamlManager               getRankYamlManager()              { return rankYamlManager; }
    public RankManager                   getRankManager()                  { return rankManager; }
    public LangManager                   getLangManager()                  { return langManager; }
    public SyncService                   getSyncService()                  { return syncService; }
    public RankForgeAPI                  getApi()                          { return api; }
    public ConfigUpdater                 getConfigUpdater()                { return configUpdater; }
    public AntiBypassManager             getAntiBypassManager()            { return antiBypassManager; }
    public GuiClickShieldManager         getGuiClickShieldManager()        { return guiClickShieldManager; }
    public SoundManager                  getSoundManager()                 { return soundManager; }
    public AnnouncementManager           getAnnouncementManager()          { return announcementManager; }
    public RequirementManager            getRequirementManager()           { return requirementManager; }
    public SoftDependency                getSoftDependency()               { return softDependency; }
    public PerformanceManager            getPerformanceManager()           { return performanceManager; }
    public TaskScheduler                 getTaskScheduler()                { return taskScheduler; }
    public CosmeticManager               getCosmeticManager()              { return cosmeticManager; }
    public AntiAbuseManager              getAntiAbuseManager()             { return antiAbuseManager; }
    public RankupQueue                   getRankupQueue()                  { return rankupQueue; }
    public BlockBreakTracker             getBlockBreakTracker()            { return blockBreakTracker; }
    public GUIConfig                     getGuiConfig()                    { return guiConfig; }
    public ExperienceManager             getExperienceManager()            { return experienceManager; }
    public RankHistoryManager            getHistoryManager()               { return historyManager; }
    public CustomRequirementRegistry     getCustomRequirementRegistry()    { return customRequirementRegistry; }
    public ExpansionRegistry             getExpansionRegistry()            { return expansionRegistry; }
    public HookRegistry                  getHookRegistry()                 { return hookRegistry; }
    public ExternalGUIRegistry           getExternalGUIRegistry()          { return externalGUIRegistry; }
    public RestAPIServer                 getRestAPIServer()                 { return restAPIServer; }
}
