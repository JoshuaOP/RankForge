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
import com.joshuaop.rankforge.manager.BypassRegistry;
import com.joshuaop.rankforge.manager.GuiClickShieldManager;
import com.joshuaop.rankforge.manager.RequirementManager;
import com.joshuaop.rankforge.manager.SoundManager;
import com.joshuaop.rankforge.manager.UpdateChecker;
import com.joshuaop.rankforge.performance.PerformanceManager;
import com.joshuaop.rankforge.performance.TaskScheduler;
import com.joshuaop.rankforge.permission.PermissionNodeGenerator;
import com.joshuaop.rankforge.placeholder.RankForgePlaceholders;
import com.joshuaop.rankforge.protection.AntiAbuseManager;
import com.joshuaop.rankforge.protection.RankupQueue;
import com.joshuaop.rankforge.rank.RankManager;
import com.joshuaop.rankforge.softdep.SoftDependency;
import com.joshuaop.rankforge.tracker.BlockBreakTracker;
import com.joshuaop.rankforge.tracker.PlaytimeTracker;
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
    private PlaytimeTracker           playtimeTracker;

    // ── Experience & History ──────────────────────────────────────────────────
    private ExperienceManager         experienceManager;
    private RankHistoryManager        historyManager;

    // ── Admin Bypass ──────────────────────────────────────────────────────────
    private BypassRegistry            bypassRegistry;

    // ── Developer API Ecosystem ───────────────────────────────────────────────
    private CustomRequirementRegistry customRequirementRegistry;
    private ExpansionRegistry         expansionRegistry;
    private HookRegistry              hookRegistry;
    private ExternalGUIRegistry       externalGUIRegistry;
    private RestAPIServer             restAPIServer;

    // ── Update Checker ────────────────────────────────────────────────────────
    private UpdateChecker             updateChecker;

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
                + (rankManager != null ? rankManager.getRankCount() : 0) + " ranks compiled)");
    }

    @Override
    public void onDisable() {
        // Shutdown networking and schedules immediately to prevent async state corruption
        if (restAPIServer      != null) restAPIServer.stop();
        if (expansionRegistry  != null) expansionRegistry.disableAll();
        if (performanceManager != null) performanceManager.stop();
        if (taskScheduler      != null) taskScheduler.cancelAll();
        if (cosmeticManager    != null) cosmeticManager.shutdown();

        // Flush live counters into cache before saving
        if (blockBreakTracker  != null) blockBreakTracker.flushAll();
        if (playtimeTracker    != null) playtimeTracker.flushAll();

        if (rankManager != null && rankManager.getCacheManager() != null) {
            if (syncService != null && databaseManager != null && databaseManager.isConnected()) {
                try {
                    syncService.flushNow();
                    syncService.stop();
                } catch (Exception e) {
                    getLogger().severe("SQL pipeline flush failed, attempting emergency YAML writeback: " + e.getMessage());
                    if (yamlPlayerDataStorage != null) {
                        yamlPlayerDataStorage.saveAll(rankManager.getCacheManager().getOnlineAndUnexpired());
                    }
                }
            } else if (yamlPlayerDataStorage != null) {
                yamlPlayerDataStorage.saveAll(rankManager.getCacheManager().getOnlineAndUnexpired());
            }
        }

        if (rankYamlManager != null) rankYamlManager.saveSync();
        if (databaseManager != null) databaseManager.disconnect();
        getLogger().info("RankForge disabled safely.");
    }

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

    }

    private void initYaml() {
        rankYamlManager = new RankYamlManager(this);
        rankYamlManager.initialize();
        guiConfig = new GUIConfig(this);
    }

    private void initCoreManagers() {
        rankManager = new RankManager(this);
        rankManager.loadRanks();

        if (databaseManager != null && !databaseManager.isConnected() && yamlPlayerDataStorage != null) {
            var stored = yamlPlayerDataStorage.loadAll();
            if (stored != null) {
                for (var pd : stored) {
                    rankManager.getCacheManager().put(pd.uuid(), pd);
                }
            }
        }

        antiBypassManager     = new AntiBypassManager(this);
        guiClickShieldManager = new GuiClickShieldManager(this);
        soundManager          = new SoundManager(this);
        announcementManager   = new AnnouncementManager(this);
        requirementManager    = new RequirementManager(this);
        bypassRegistry        = new BypassRegistry();
        syncService           = new SyncService(this);

        performanceManager    = new PerformanceManager(this);
        taskScheduler         = new TaskScheduler(this);
        antiAbuseManager      = new AntiAbuseManager(this);
        rankupQueue           = new RankupQueue();
        cosmeticManager       = new CosmeticManager(this);
        blockBreakTracker     = new BlockBreakTracker(this);
        playtimeTracker       = new PlaytimeTracker(this);
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

    private void initMetrics() {
        try {
            new Metrics(this, BSTATS_PLUGIN_ID);
        } catch (Exception e) {
            if (isDebug()) {
                getLogger().warning("bStats failed to initialize: " + e.getMessage());
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
        getServer().getPluginManager().registerEvents(blockBreakTracker, this);
        getServer().getPluginManager().registerEvents(playtimeTracker, this);
        if (cosmeticManager != null) cosmeticManager.registerListeners();
    }

    private void postInit() {
        if (databaseManager != null && databaseManager.isConnected() && syncService != null) {
            syncService.start();
        }

        if (performanceManager != null) performanceManager.start();

        if (softDependency != null && softDependency.hasPapi()) {
            new RankForgePlaceholders(this).register();
        }

        startRepeatingTasks();

        if (restAPIServer != null) restAPIServer.start();

        updateChecker = new UpdateChecker(this);
        updateChecker.checkOnStartup();
    }

    private void startRepeatingTasks() {
        if (taskScheduler == null) return;

        taskScheduler.repeatAsync(() -> {
            if (rankManager != null && rankManager.getCacheManager() != null) {
                rankManager.getCacheManager().purgeExpired();
            }
        }, 6000L, 6000L);

        taskScheduler.repeatAsync(() -> {
            if (antiAbuseManager != null) antiAbuseManager.purge();
        }, 2400L, 2400L);

        long blockFlushInterval = getConfig() != null
                ? getConfig().getLong("tracker.block-break-flush-ticks", 100L) : 100L;
        taskScheduler.repeatAsync(() -> {
            if (blockBreakTracker != null) blockBreakTracker.flushAll();
        }, blockFlushInterval, blockFlushInterval);

        // Flush playtime on the same interval as block-breaks for consistency
        taskScheduler.repeatAsync(() -> {
            if (playtimeTracker != null) playtimeTracker.flushAll();
        }, blockFlushInterval, blockFlushInterval);

        if (databaseManager != null && !databaseManager.isConnected()) {
            long yamlSyncInterval = getConfig() != null
                    ? getConfig().getLong("sync.interval-ticks", 200L) : 200L;
            taskScheduler.repeatAsync(() -> {
                if (yamlPlayerDataStorage != null && rankManager != null
                        && rankManager.getCacheManager() != null) {
                    var snapshot = rankManager.getCacheManager().getOnlineAndUnexpired();
                    if (!snapshot.isEmpty()) yamlPlayerDataStorage.saveAll(snapshot);
                }
            }, yamlSyncInterval, yamlSyncInterval);
        }
    }

    // ── Hot-reload ────────────────────────────────────────────────────────────

    public void reload() {
        if (taskScheduler != null) taskScheduler.cancelAll();
        if (cosmeticManager != null) cosmeticManager.shutdown();

        configUpdater.updateAll();
        reloadConfig();
        if (langManager != null) langManager.loadAll();
        if (rankYamlManager != null) rankYamlManager.hotReload();
        if (rankManager != null) {
            rankManager.loadRanks();
            rankManager.repairOrphanedRanks();
        }

        if (soundManager != null) soundManager.reload();
        if (antiBypassManager != null) antiBypassManager.reload();
        if (updateChecker != null) updateChecker.reload();
        if (expansionRegistry != null) expansionRegistry.reloadAll();
        if (guiConfig != null) guiConfig.load();

        com.joshuaop.rankforge.gui.PlayerListGUI.clearHeadCache();

        startRepeatingTasks();

        if (cosmeticManager != null && rankManager != null) {
            var cache = rankManager.getCacheManager();
            if (cache != null) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    String rankId = cache.contains(online.getUniqueId())
                            ? cache.get(online.getUniqueId()).rankId()
                            : rankManager.getDefaultRankId();
                    cosmeticManager.onLogin(online, rankId);
                }
            }
        }

        getLogger().info("RankForge configuration reloaded.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isDebug() {
        return getConfig() != null && getConfig().getBoolean("debug", false);
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
    public BypassRegistry                getBypassRegistry()               { return bypassRegistry; }
    public SoftDependency                getSoftDependency()               { return softDependency; }
    public PerformanceManager            getPerformanceManager()           { return performanceManager; }
    public TaskScheduler                 getTaskScheduler()                { return taskScheduler; }
    public CosmeticManager               getCosmeticManager()              { return cosmeticManager; }
    public AntiAbuseManager              getAntiAbuseManager()             { return antiAbuseManager; }
    public RankupQueue                   getRankupQueue()                  { return rankupQueue; }
    public BlockBreakTracker             getBlockBreakTracker()            { return blockBreakTracker; }
    public PlaytimeTracker               getPlaytimeTracker()              { return playtimeTracker; }
    public GUIConfig                     getGuiConfig()                    { return guiConfig; }
    public ExperienceManager             getExperienceManager()            { return experienceManager; }
    public RankHistoryManager            getHistoryManager()               { return historyManager; }
    public CustomRequirementRegistry     getCustomRequirementRegistry()    { return customRequirementRegistry; }
    public ExpansionRegistry             getExpansionRegistry()            { return expansionRegistry; }
    public HookRegistry                  getHookRegistry()                 { return hookRegistry; }
    public ExternalGUIRegistry           getExternalGUIRegistry()          { return externalGUIRegistry; }
    public RestAPIServer                 getRestAPIServer()                 { return restAPIServer; }
    public UpdateChecker                 getUpdateChecker()                { return updateChecker; }
}
