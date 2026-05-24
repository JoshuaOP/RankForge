package com.joshuaop.rankforge;

import com.joshuaop.rankforge.api.RankForgeAPI;
import com.joshuaop.rankforge.api.expansion.ExpansionRegistry;
import com.joshuaop.rankforge.api.gui.ExternalGUIRegistry;
import com.joshuaop.rankforge.api.hook.HookRegistry;
import com.joshuaop.rankforge.api.requirement.CustomRequirementRegistry;
import com.joshuaop.rankforge.api.rest.RestAPIServer;
import com.joshuaop.rankforge.api.reward.CustomRewardRegistry;
import com.joshuaop.rankforge.challenge.RankChallengeManager;
import com.joshuaop.rankforge.command.RankCommand;
import com.joshuaop.rankforge.cosmetic.CosmeticManager;
import com.joshuaop.rankforge.db.DatabaseManager;
import com.joshuaop.rankforge.db.SyncService;
import com.joshuaop.rankforge.db.YamlPlayerDataStorage;
import com.joshuaop.rankforge.experience.ExperienceManager;
import com.joshuaop.rankforge.experience.LeaderboardManager;
import com.joshuaop.rankforge.experience.RankHistoryManager;
import com.joshuaop.rankforge.gui.GUIListener;
import com.joshuaop.rankforge.lang.LangManager;
import com.joshuaop.rankforge.manager.AnnouncementManager;
import com.joshuaop.rankforge.manager.AntiBypassManager;
import com.joshuaop.rankforge.manager.GitHubUpdateNotifier;
import com.joshuaop.rankforge.manager.GuiClickShieldManager;
import com.joshuaop.rankforge.manager.RequirementManager;
import com.joshuaop.rankforge.manager.SoundManager;
import com.joshuaop.rankforge.performance.PerformanceManager;
import com.joshuaop.rankforge.performance.TaskScheduler;
import com.joshuaop.rankforge.permission.PermissionNodeGenerator;
import com.joshuaop.rankforge.placeholder.RankForgePlaceholders;
import com.joshuaop.rankforge.protection.AntiAbuseManager;
import com.joshuaop.rankforge.protection.RankupQueue;
import com.joshuaop.rankforge.quest.RankQuestManager;
import com.joshuaop.rankforge.rank.RankManager;
import com.joshuaop.rankforge.reward.DailyRewardManager;
import com.joshuaop.rankforge.softdep.SoftDependency;
import com.joshuaop.rankforge.yaml.ConfigUpdater;
import com.joshuaop.rankforge.yaml.RankYamlManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * RankForge main plugin class — wires all systems together.
 *
 * <p>Startup order:
 * <ol>
 *   <li>Config migration (ConfigUpdater)</li>
 *   <li>Language files</li>
 *   <li>Database / YAML storage</li>
 *   <li>Rank YAML loading</li>
 *   <li>Core managers (cache, requirements, sounds, cosmetics, etc.)</li>
 *   <li>New systems (XP, history, leaderboard, daily rewards, challenges, quests)</li>
 *   <li>Developer API ecosystem (expansion registry, hook registry, GUI registry, REST)</li>
 *   <li>Soft dependencies (Vault, LuckPerms, PAPI)</li>
 *   <li>Permissions + command registration</li>
 *   <li>Event listener registration</li>
 *   <li>Post-init (task scheduling, PAPI expansion, update checker)</li>
 * </ol>
 */
public final class RankForge extends JavaPlugin {

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

    // ── Classic Managers ─────────────────────────────────────────────────────
    private AntiBypassManager         antiBypassManager;
    private GuiClickShieldManager     guiClickShieldManager;
    private SoundManager              soundManager;
    private AnnouncementManager       announcementManager;
    private RequirementManager        requirementManager;
    private PermissionNodeGenerator   permissionNodeGenerator;

    // ── v2.1 Systems ─────────────────────────────────────────────────────────
    private SoftDependency            softDependency;
    private PerformanceManager        performanceManager;
    private TaskScheduler             taskScheduler;
    private CosmeticManager           cosmeticManager;
    private AntiAbuseManager          antiAbuseManager;
    private RankupQueue               rankupQueue;

    // ── v2.2 Experience & Progression Systems ────────────────────────────────
    private ExperienceManager         experienceManager;
    private RankHistoryManager        historyManager;
    private LeaderboardManager        leaderboardManager;
    private DailyRewardManager        dailyRewardManager;
    private RankChallengeManager      challengeManager;
    private RankQuestManager          questManager;

    // ── v2.2 Developer API Ecosystem ─────────────────────────────────────────
    private CustomRequirementRegistry customRequirementRegistry;
    private CustomRewardRegistry      customRewardRegistry;
    private ExpansionRegistry         expansionRegistry;
    private HookRegistry              hookRegistry;
    private ExternalGUIRegistry       externalGUIRegistry;
    private RestAPIServer             restAPIServer;
    private GitHubUpdateNotifier      updateNotifier;

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

        getLogger().info("RankForge v" + getDescription().getVersion() + " enabled! "
                + "(" + rankManager.getRankCount() + " ranks, "
                + challengeManager.getAllChallenges().size() + " challenges, "
                + questManager.getAllQuests().size() + " quests)");
    }

    @Override
    public void onDisable() {
        if (restAPIServer        != null) restAPIServer.stop();
        if (expansionRegistry    != null) expansionRegistry.disableAll();
        if (performanceManager   != null) performanceManager.stop();
        if (taskScheduler        != null) taskScheduler.cancelAll();
        if (cosmeticManager      != null) cosmeticManager.shutdown();
        if (syncService          != null) { syncService.flushNow(); syncService.stop(); }
        if (rankYamlManager      != null) rankYamlManager.saveSync();
        if (databaseManager      != null) databaseManager.disconnect();
        getLogger().info("RankForge disabled.");
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
        if (!mysqlOk)
            getLogger().info("[RankForge] Using YAML file storage at plugins/RankForge/data/playerdata.yml");
    }

    private void initYaml() {
        rankYamlManager = new RankYamlManager(this);
        rankYamlManager.initialize();
    }

    private void initCoreManagers() {
        rankManager           = new RankManager(this);
        rankManager.loadRanks();

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
    }

    private void initExperienceSystems() {
        experienceManager  = new ExperienceManager(this);
        historyManager     = new RankHistoryManager(this);
        leaderboardManager = new LeaderboardManager(this);
        dailyRewardManager = new DailyRewardManager(this);
        challengeManager   = new RankChallengeManager(this);
        questManager       = new RankQuestManager(this);
        getLogger().info("[RankForge] Experience systems initialised.");
    }

    private void initAPIEcosystem() {
        customRequirementRegistry = new CustomRequirementRegistry();
        customRewardRegistry      = new CustomRewardRegistry();
        expansionRegistry         = new ExpansionRegistry(getLogger());
        hookRegistry              = new HookRegistry(getLogger());
        externalGUIRegistry       = new ExternalGUIRegistry(getLogger());

        // API singleton is created last (it holds references to the above)
        api = new RankForgeAPI(this);

        restAPIServer = new RestAPIServer(this);
        getLogger().info("[RankForge] Developer API ecosystem initialised.");
    }

    private void initSoftDep() {
        softDependency = new SoftDependency(this);
        softDependency.initialize();
    }

    private void initPermissions() {
        permissionNodeGenerator = new PermissionNodeGenerator(this);
        permissionNodeGenerator.generateAll();
    }

    private void registerCommands() {
        var rankCommand = new RankCommand(this);
        var cmd = getCommand("rank");
        if (cmd != null) { cmd.setExecutor(rankCommand); cmd.setTabCompleter(rankCommand); }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(softDependency, this);
        getServer().getPluginManager().registerEvents(challengeManager, this);
        cosmeticManager.registerListeners();
    }

    private void postInit() {
        // MySQL sync service
        if (databaseManager.isConnected()) {
            syncService.start();
        }

        // Performance monitor
        performanceManager.start();

        // PlaceholderAPI
        if (softDependency.hasPapi()) {
            new RankForgePlaceholders(this).register();
            getLogger().info("[RankForge] PlaceholderAPI expansion registered.");
        }

        // Periodic tasks
        taskScheduler.repeatAsync(() -> rankManager.getCacheManager().purgeExpired(), 6000L, 6000L);
        taskScheduler.repeatAsync(() -> antiAbuseManager.purge(), 2400L, 2400L);

        // YAML sync if no MySQL
        if (!databaseManager.isConnected()) {
            long yamlSyncInterval = getConfig().getLong("sync.interval-ticks", 200L);
            taskScheduler.repeatAsync(() -> {
                if (yamlPlayerDataStorage != null)
                    yamlPlayerDataStorage.saveAll(rankManager.getCacheManager().all());
            }, yamlSyncInterval, yamlSyncInterval);
            getLogger().info("[Sync] YAML file sync started (every " + yamlSyncInterval + " ticks).");
        }

        // REST API (optional)
        restAPIServer.start();

        // GitHub update checker
        updateNotifier = new GitHubUpdateNotifier(this);
        getServer().getPluginManager().registerEvents(updateNotifier, this);
        updateNotifier.start();
    }

    // ── Hot-reload ────────────────────────────────────────────────────────────

    public void reload() {
        configUpdater.updateAll();
        reloadConfig();
        langManager.loadAll();
        rankYamlManager.hotReload();
        rankManager.loadRanks();
        soundManager.reload();
        antiBypassManager.reload();
        dailyRewardManager.loadRewards();
        challengeManager.loadChallenges();
        questManager.loadQuests();
        expansionRegistry.reloadAll();
        getLogger().info("RankForge reloaded.");
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static RankForge              getInstance()                     { return instance; }

    // Core
    public DatabaseManager               getDatabaseManager()              { return databaseManager; }
    public YamlPlayerDataStorage         getYamlPlayerDataStorage()        { return yamlPlayerDataStorage; }
    public RankYamlManager               getRankYamlManager()              { return rankYamlManager; }
    public RankManager                   getRankManager()                  { return rankManager; }
    public LangManager                   getLangManager()                  { return langManager; }
    public SyncService                   getSyncService()                  { return syncService; }
    public RankForgeAPI                  getApi()                          { return api; }
    public ConfigUpdater                 getConfigUpdater()                { return configUpdater; }

    // Classic managers
    public AntiBypassManager             getAntiBypassManager()            { return antiBypassManager; }
    public GuiClickShieldManager         getGuiClickShieldManager()        { return guiClickShieldManager; }
    public SoundManager                  getSoundManager()                 { return soundManager; }
    public AnnouncementManager           getAnnouncementManager()          { return announcementManager; }
    public RequirementManager            getRequirementManager()           { return requirementManager; }

    // v2.1 systems
    public SoftDependency                getSoftDependency()               { return softDependency; }
    public PerformanceManager            getPerformanceManager()           { return performanceManager; }
    public TaskScheduler                 getTaskScheduler()                { return taskScheduler; }
    public CosmeticManager               getCosmeticManager()              { return cosmeticManager; }
    public AntiAbuseManager              getAntiAbuseManager()             { return antiAbuseManager; }
    public RankupQueue                   getRankupQueue()                  { return rankupQueue; }

    // v2.2 experience & progression
    public ExperienceManager             getExperienceManager()            { return experienceManager; }
    public RankHistoryManager            getHistoryManager()               { return historyManager; }
    public LeaderboardManager            getLeaderboardManager()           { return leaderboardManager; }
    public DailyRewardManager            getDailyRewardManager()           { return dailyRewardManager; }
    public RankChallengeManager          getChallengeManager()             { return challengeManager; }
    public RankQuestManager              getQuestManager()                 { return questManager; }

    // v2.2 Developer API
    public CustomRequirementRegistry     getCustomRequirementRegistry()    { return customRequirementRegistry; }
    public CustomRewardRegistry          getCustomRewardRegistry()         { return customRewardRegistry; }
    public ExpansionRegistry             getExpansionRegistry()            { return expansionRegistry; }
    public HookRegistry                  getHookRegistry()                 { return hookRegistry; }
    public ExternalGUIRegistry           getExternalGUIRegistry()          { return externalGUIRegistry; }
    public RestAPIServer                 getRestAPIServer()                 { return restAPIServer; }
    public GitHubUpdateNotifier          getUpdateNotifier()               { return updateNotifier; }
}
