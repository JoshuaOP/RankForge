# RankForge

**Version:** 2.2 | **Author:** JoshuaOP | **API:** Spigot/Paper 1.20.1–1.21.x | **Java:** 21, Maven

---

## What's New in v2.0

| Change | Detail |
|---|---|
| **GitHub Update Notifier** | Async check against GitHub Releases API; notifies ops in console and on join. Config-gated, zero-dependency, zero startup lag |
| **XP / Experience System** | Players earn RankForge XP on every rank-up. Award, deduct, set per player. `/rank xp` and `/rank xp set\|add` commands |
| **Rank History** | Full per-player log of every rank change (RANKUP / SET / RESET) persisted to `data/rank-history.yml`. View with `/rank history` |
| **Leaderboard** | `/rank leaderboard [xp\|rank]` — top-10 by rank position or XP. Draws from cache + YAML storage |
| **Daily Rewards** | `/rank daily` — claim per-rank or global daily rewards (XP + money + commands). 24 h cooldown, configurable in `config.yml` |
| **Challenges** | `challenges.yml` — MINE_BLOCK, KILL_ENTITY, CRAFT_ITEM, RANKUP, MANUAL types. Tracked via Bukkit events. Repeatable with cooldowns |
| **Quests** | `quests.yml` — multi-step challenge sequences with completion rewards. `/rank quests` lists active quests |
| **Custom Requirement API** | `CustomRequirementRegistry` — third-party plugins register requirement types; map per-rank values via `RequirementManager.addRankRequirement()` |
| **Custom Reward API** | `CustomRewardRegistry` — inject custom rank-up reward logic; applied after built-in pipeline |
| **Expansion System** | `RankForgeExpansion` abstract class with onEnable / onDisable / onReload lifecycle. `ExpansionRegistry` manages all registered expansions |
| **Hook Registry** | `PluginHook` interface — lightweight callbacks for onRankup, onRankSet, onRankReset, onPlayerLoad |
| **External GUI Override** | `ExternalGUIRegistry` — replace any built-in GUI (RANK_TREE, ADMIN_EDITOR, LEADERBOARD, CHALLENGES, QUESTS) with a custom `Inventory` |
| **REST API** | Optional embedded HTTP server (plain `ServerSocket`, no extra deps). Endpoints: `/api/status`, `/api/ranks`, `/api/player/{uuid}`, `/api/leaderboard`. Bearer token auth |
| **Cancellable Events** | `RankupEvent`, `RankSetEvent`, `RankResetEvent`, `DailyRewardClaimEvent` — all cancellable Bukkit events fire before mutations |
| **Enhanced ConfigUpdater** | Now backs up files before migration (`<file>.bak.<timestamp>`); migrates `gui.yml`, `challenges.yml`, and `quests.yml` in addition to `config.yml` and lang files |
| **gui.yml expansion** | All GUI layout settings (titles, border materials, slot numbers, leaderboard, challenges, quests, history GUIs) now live in `gui.yml` |
| **OOP throughout** | All new systems use Builder pattern, immutable records, registry pattern, and interface-based contracts |

---

## What's New in v2.1

| Change | Detail |
|---|---|
| **YAML Player Data Storage** | Default fallback storage at `plugins/RankForge/data/playerdata.yml` — no H2, no extra database required |
| **Player List GUI** | `/rank playerlist` — Admin GUI with player heads showing all known players and their data |
| **Player Data Editor GUI** | Click any player in the list to edit their rank, experience, money, and language |
| **ConfigUpdater** | Config files auto-updated on plugin version change — missing keys are added without overwriting your settings |
| **H2 Removed** | Replaced by lightweight YAML file storage; MySQL is fully optional |

---

## Feature Overview

| Category | Features |
|---|---|
| **Core** | YAML-defined ranks, hot-reload, async backup, immutable RankModel |
| **GUI** | Animated rank tree, player-head info panel, admin editor, drag-drop slot editor, per-rank detail view, player list + data editor |
| **Commands** | Per-rank console commands on rank-up (`%player%`), full admin suite |
| **Soft Dependencies** | Vault, LuckPerms, PlaceholderAPI — all with live event hooks (no hard dependencies) |
| **Placeholders** | 30+ `%rankforge_xxx%` PAPI placeholders + `{rankforge_xxx}` config format |
| **Cosmetics** | Particle trails, rank boss bars, tablist formatting, custom join/quit messages |
| **Performance** | TPS monitor, auto performance mode (HIGH/MEDIUM/LOW), centralized task scheduler |
| **Caching** | ConcurrentHashMap + TTL expiry, periodic purge, top-player query |
| **Protection** | RankupQueue (prevents simultaneous processing), RateLimiter, macro detection, anti-bypass |
| **Storage** | MySQL + HikariCP (optional) — YAML file fallback by default |
| **ConfigUpdater** | Auto-migrates config/lang files on plugin update — no manual edits needed |
| **API** | Full public `RankForgeAPI` for external plugins |
| **Languages** | `en`, `es`, `fil`, `id` — per-player preference |

---

## Soft Dependency Hooks

All three soft dependencies are handled by `SoftDependency`, which also implements `Listener` for full player lifecycle integration:

| Plugin | Hook | What it does |
|---|---|---|
| **Vault** | Setup on enable | Provides `getBalance()` / `withdraw()` for money requirements |
| **LuckPerms** | `PlayerJoinEvent` | Grants rank permission nodes to the player on every login |
| **PlaceholderAPI** | Setup on enable | Registers the `%rankforge_xxx%` expansion |

### Player Event Flow

```
PlayerJoinEvent (MONITOR priority)
  ├─ Load player data into cache (from startup YAML load or create defaults)
  ├─ Refresh player name if changed
  ├─ Apply rank permissions via LuckPerms (or Bukkit attachment fallback)
  └─ Restore cosmetics: particle trail + tablist prefix

PlayerQuitEvent (MONITOR priority)
  ├─ Remove cosmetics (boss bar, particle trail, tablist reset)
  └─ Persist player data to YAML immediately (if MySQL not connected)
```

If a soft dep is absent, that feature is gracefully skipped — no errors, no crashes.

---

## Storage System

RankForge uses a two-tier storage system:

| Priority | Type | Location |
|---|---|---|
| **1st** | MySQL | Remote database (configured in `config.yml`) |
| **2nd (default)** | YAML File | `plugins/RankForge/data/playerdata.yml` |

If MySQL is configured and reachable, it is used. Otherwise, all player data is automatically stored in `playerdata.yml` — no setup required.

**YAML format:**
```yaml
players:
  <uuid>:
    name: "PlayerName"
    rank: "Guest"
    experience: 0
    money: 0.0
    language: "en"
```

---

## Package Structure

```
com.joshuaop.rankforge
├── RankForge.java                  Main class — wires all systems
├── api/
│   ├── RankForgeAPI.java           Public API surface
│   ├── RankService.java            Core rank logic (RankupQueue, SoftDependency, Cosmetics)
│   ├── ProgressService.java        Progress % + bar (uses SoftDependency for balance)
│   └── PlayerRank.java             Immutable player rank snapshot
├── command/
│   ├── RankCommand.java            /rank router + /rank help (admin-sensitive)
│   ├── RankEditorCommand.java      /rank editor [<rankId>|drag|save|reload]
│   ├── RankVersionCommand.java     /rank version — plugin info + soft-dep status
│   └── RankReloadCommand.java      /rank reload
├── cosmetic/
│   ├── CosmeticManager.java        Central cosmetics coordinator
│   ├── BossBarManager.java         Rank-themed boss bars on rank-up/progress
│   ├── ParticleManager.java        Per-rank particle trail system
│   ├── TablistManager.java         Tablist rank prefix formatting
│   └── JoinQuitManager.java        Custom per-rank join/quit messages
├── db/
│   ├── DatabaseManager.java        HikariCP MySQL connection pool
│   ├── MySQLProvider.java          MySQL schema + prepared statements
│   ├── CacheManager.java           ConcurrentHashMap + TTL expiry + top-player query
│   ├── PlayerData.java             Immutable player record
│   ├── RankDataRepository.java     SQL/YAML CRUD (routes by storage type)
│   ├── FlatFileCache.java          Startup YAML loader (populates cache at boot)
│   ├── SyncService.java            Async periodic MySQL flush
│   └── YamlPlayerDataStorage.java  YAML file fallback storage (read/write per player)
├── gui/
│   ├── AnimatedRankTreeGUI.java    Player GUI with player-head panel
│   ├── AdminRankEditorGUI.java     Admin rank overview
│   ├── DragDropRankEditorGUI.java  Slot reassignment editor
│   ├── RankDetailEditorGUI.java    Per-rank property editor (chat input)
│   ├── PlayerListGUI.java          Admin player list (paginated, player heads)
│   ├── PlayerDataEditorGUI.java    Per-player data editor (rank/xp/money/language)
│   ├── RankItemBuilder.java        ItemStack factory
│   └── GUIListener.java            Inventory event router + chat edit handler
├── lang/
│   └── LangManager.java            Multi-lang messages, per-player preference
├── manager/
│   ├── AntiBypassManager.java      GUI cooldown anti-spam
│   ├── GuiClickShieldManager.java  Click debounce (server-wide)
│   ├── SoundManager.java           Configurable sounds
│   ├── AnnouncementManager.java    Rank-up broadcast / title / action-bar
│   └── RequirementManager.java     Requirement evaluation (uses SoftDependency)
├── performance/
│   ├── TpsMonitor.java             Rolling-average TPS tracker
│   ├── PerformanceMode.java        Enum: HIGH / MEDIUM / LOW
│   ├── PerformanceManager.java     Auto mode switching based on TPS
│   └── TaskScheduler.java          Centralized task registry (all tasks tracked)
├── permission/
│   ├── PermissionRegistry.java     All permission node constants
│   └── PermissionNodeGenerator.java Auto-register nodes on startup
├── placeholder/
│   ├── RankForgePlaceholders.java  PAPI PlaceholderExpansion (30+ placeholders)
│   └── PlaceholderUtil.java        {rankforge_xxx} string replacement utility
├── progress/
│   └── ProgressManager.java        Thin facade over ProgressService
├── protection/
│   ├── AntiAbuseManager.java       Macro detection, admin action logging
│   ├── RateLimiter.java            Token-bucket rate limiter (per-player cooldowns)
│   └── RankupQueue.java            Prevents simultaneous rank calculations per player
├── rank/
│   ├── RankModel.java              Immutable rank data + Builder
│   ├── RankManager.java            In-memory rank index
│   └── RankEditor.java             Runtime rank mutation utility
├── softdep/
│   ├── SoftDependency.java         Vault + LuckPerms + PAPI handler + PlayerJoin/Quit listener
│   └── LuckPermsHook.java          Isolated LuckPerms node application (package-private)
└── yaml/
    ├── RankYamlManager.java        Load / save / backup / hot-reload
    ├── YamlLoader.java             ranks.yml → RankModel list
    ├── YamlSerializer.java         RankModel list → ranks.yml
    └── ConfigUpdater.java          Auto-migrates config/lang files on update
```

---

## Commands

### Player
| Command | Permission | Description |
|---|---|---|
| `/rank` | `rankforge.rank.use` | Open rank GUI |
| `/rank up` | `rankforge.rank.up` | Attempt rank-up |
| `/rank progress` | `rankforge.rank.progress` | Show progress bar |
| `/rank next` | `rankforge.rank.next` | Show next rank info |
| `/rank current` | `rankforge.rank.current` | Show current rank |
| `/rank requirements` | `rankforge.rank.requirements` | List unmet requirements |
| `/rank version` | `rankforge.rank.system.version` | Plugin info + soft-dep status |
| `/rank lang <set\|list\|reset>` | `rankforge.rank.lang` | Change language |
| `/rank help` | — | Context-aware help |

### Admin
| Command | Permission | Description |
|---|---|---|
| `/rank editor` | `rankforge.rank.editor` | Admin overview GUI |
| `/rank editor <rankId>` | `rankforge.rank.editor` | Edit specific rank |
| `/rank editor drag` | `rankforge.rank.editor.drag` | Drag-drop slot editor |
| `/rank editor save` | `rankforge.rank.editor.save` | Save to ranks.yml |
| `/rank editor reload` | `rankforge.rank.reload` | Hot-reload ranks.yml |
| `/rank playerlist` | `rankforge.rank.playerlist` | View & edit all player data (GUI) |
| `/rank set <player> <rank>` | `rankforge.rank.set` | Set player rank |
| `/rank reset <player>` | `rankforge.rank.reset` | Reset to default rank |
| `/rank force <player> <rank>` | `rankforge.rank.force` | Force rank (no checks) |
| `/rank reload` | `rankforge.rank.reload` | Full plugin reload |
| `/rank stats` | `rankforge.rank.stats` | System stats + soft-dep status |
| `/rank security` | `rankforge.rank.security` | Anti-abuse status |
| `/rank debug` | `rankforge.rank.debug` | Your rank debug info |

---

## Player List GUI (`/rank playerlist`)

Opens a paginated admin GUI showing all known players with their player heads.

- **Page navigation** — previous/next arrows (45 players per page)
- **Click a player head** — opens the **Player Data Editor GUI**

### Player Data Editor GUI

Edit any player's data directly from the GUI:

| Field | Description |
|---|---|
| **Rank** | Change the player's current rank (must be a valid rank ID) |
| **Experience** | Set the player's experience value |
| **Money** | Set the player's money balance |
| **Language** | Change the player's language (`en`, `es`, `fil`, `id`) |

Click any field to edit via chat input. Type `cancel` to abort. Changes are saved immediately to YAML and/or MySQL.

---

## ConfigUpdater

On every plugin start or `/rank reload`, RankForge automatically checks all config and language files for missing keys introduced by plugin updates. Missing keys are added with their default values — **your existing settings are never overwritten**.

Files checked:
- `config.yml`
- `lang/en.yml`, `lang/es.yml`, `lang/fil.yml`, `lang/id.yml`

---

## Placeholders

### PlaceholderAPI Format (`%rankforge_xxx%`)
Requires PlaceholderAPI to be installed.

| Placeholder | Description |
|---|---|
| `%rankforge_rank%` | Current rank ID |
| `%rankforge_rank_name%` | Current rank display name |
| `%rankforge_rank_prefix%` | Rank chat prefix |
| `%rankforge_rank_position%` | Rank position in chain (1, 2, 3…) |
| `%rankforge_next_rank%` | Next rank ID (or MAX) |
| `%rankforge_next_cost%` | Next rank money requirement |
| `%rankforge_is_max_rank%` | `true`/`false` |
| `%rankforge_progress%` | Progress value 0.0–100.0 |
| `%rankforge_progress_bar%` | `██████░░░░` bar string |
| `%rankforge_progress_percent%` | `"73.4%"` |
| `%rankforge_money%` | Player's current balance |
| `%rankforge_player%` | Player name |
| `%rankforge_uuid%` | Player UUID |
| `%rankforge_lang%` | Player language code |
| `%rankforge_version%` | Plugin version |
| `%rankforge_gui_title%` | GUI title string |
| `%rankforge_top_rank_1/2/3%` | Top ranked players |

---

## ranks.yml Format

```yaml
ranks:
  RankId:
    display-name: "§aDisplay Name"    # Color codes supported
    next-rank: "NextRankId"            # Empty = max rank
    slot: 12                           # GUI slot 9–44
    material: GREEN_WOOL               # Bukkit Material name
    chat-prefix: "§a[Rank]"
    permissions: []                    # Granted on rank-up via LuckPerms (or Bukkit fallback)
    lore:
      - "§7Description line"
    requirements:
      money: 5000                      # Vault balance required
      xp-level: 10                     # XP level required
      permission: ""                   # Permission gate (empty = none)
    commands:                          # Console commands on rank-up
      - "broadcast §e%player% ranked up!"
      - "give %player% diamond 1"
```

---

## External API

```java
RankForgeAPI api = RankForgeAPI.getInstance();

PlayerRank rank = api.getPlayerRank(player);
String id       = rank.rankId();
String display  = rank.displayName();
boolean isMax   = rank.isMaxRank();
double progress = rank.progress();   // 0.0–100.0

api.rankUp(player);
api.setRank(player, "Builder");
api.resetRank(player);
double pct = api.getProgress(player);
```

---

## Building

```bash
cd RankForge
mvn clean package -q
# Output: target/RankForge-2.1.jar
```

---

## Supported Versions

1.20.1 · 1.20.2 · 1.20.4 · 1.20.6 · 1.21 · 1.21.1 · 1.21.2 · 1.21.3 · 1.21.4 · 1.21.5+

---

## License

MIT — free to use and modify with attribution to JoshuaOP.
