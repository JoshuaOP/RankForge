# RankForge
**Version:** 2.0 | **Author:** JoshuaOP | **API:** Spigot/Paper 1.20.1–1.21.x | **Java:** 21, Maven
## Feature Overview
| Category | Features |
|---|---|
| **Core** | YAML-defined ranks, hot-reload, async backup, immutable RankModel |
| **GUI** | Animated rank tree, player-head info panel, admin editor, drag-drop slot editor, per-rank detail view, player list + data editor |
| **Commands** | Per-rank console commands on rank-up (%player%), full admin suite |
| **Soft Dependencies** | Vault, LuckPerms, PlaceholderAPI — all with live event hooks (no hard dependencies) |
| **Placeholders** | 30+ %rankforge_xxx% PAPI placeholders + {rankforge_xxx} config format |
| **Cosmetics** | Particle trails, rank boss bars, tablist formatting, custom join/quit messages |
| **Performance** | TPS monitor, auto performance mode (HIGH/MEDIUM/LOW), centralized task scheduler |
| **Caching** | ConcurrentHashMap + TTL expiry, periodic purge, top-player query |
| **Protection** | RankupQueue (prevents simultaneous processing), RateLimiter, macro detection, anti-bypass |
| **Storage** | MySQL + HikariCP (optional) — YAML file fallback by default |
| **ConfigUpdater** | Auto-migrates config/lang files on plugin update — no manual edits needed |
| **API** | Full public RankForgeAPI for external plugins |
| **Languages** | en, es, fil, id — per-player preference |
## Soft Dependency Hooks
All three soft dependencies are handled by SoftDependency, which also implements Listener for full player lifecycle integration:
| Plugin | Hook | What it does |
|---|---|---|
| **Vault** | Setup on enable | Provides getBalance() / withdraw() for money requirements |
| **LuckPerms** | PlayerJoinEvent | Grants rank permission nodes to the player on every login |
| **PlaceholderAPI** | Setup on enable | Registers the %rankforge_xxx% expansion |
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
## Storage System
RankForge uses a two-tier storage system:
| Priority | Type | Location |
|---|---|---|
| **1st** | MySQL | Remote database (configured in config.yml) |
| **2nd (default)** | YAML File | plugins/RankForge/data/playerdata.yml |
If MySQL is configured and reachable, it is used. Otherwise, all player data is automatically stored in playerdata.yml — no setup required.
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
## Commands
### Player
| Command | Permission | Description |
|---|---|---|
| /rank | rankforge.rank.use | Open rank GUI |
| /rank up | rankforge.rank.up | Attempt rank-up |
| /rank progress | rankforge.rank.progress | Show progress bar |
| /rank next | rankforge.rank.next | Show next rank info |
| /rank current | rankforge.rank.current | Show current rank |
| /rank requirements | rankforge.rank.requirements | List unmet requirements |
| /rank version | rankforge.rank.system.version | Plugin info + soft-dep status |
| /rank lang <set|list|reset> | rankforge.rank.lang | Change language |
| /rank help | — | Context-aware help |
### Admin
| Command | Permission | Description |
|---|---|---|
| /rank editor | rankforge.rank.editor | Admin overview GUI |
| /rank editor <rankId> | rankforge.rank.editor | Edit specific rank |
| /rank editor drag | rankforge.rank.editor.drag | Drag-drop slot editor |
| /rank editor save | rankforge.rank.editor.save | Save to ranks.yml |
| /rank editor reload | rankforge.rank.reload | Hot-reload ranks.yml |
| /rank playerlist | rankforge.rank.playerlist | View & edit all player data (GUI) |
| /rank set <player> <rank> | rankforge.rank.set | Set player rank |
| /rank reset <player> | rankforge.rank.reset | Reset to default rank |
| /rank force <player> <rank> | rankforge.rank.force | Force rank (no checks) |
| /rank reload | rankforge.rank.reload | Full plugin reload |
| /rank stats | rankforge.rank.stats | System stats + soft-dep status |
| /rank security | rankforge.rank.security | Anti-abuse status |
| /rank debug | rankforge.rank.debug | Your rank debug info |
## Player List GUI (/rank playerlist)
Opens a paginated admin GUI showing all known players with their player heads.
 * **Page navigation** — previous/next arrows (45 players per page)
 * **Click a player head** — opens the **Player Data Editor GUI**
### Player Data Editor GUI
Edit any player's data directly from the GUI:
| Field | Description |
|---|---|
| **Rank** | Change the player's current rank (must be a valid rank ID) |
| **Experience** | Set the player's experience value |
| **Money** | Set the player's money balance |
| **Language** | Change the player's language (en, es, fil, id) |
Click any field to edit via chat input. Type cancel to abort. Changes are saved immediately to YAML and/or MySQL.
## ConfigUpdater
On every plugin start or /rank reload, RankForge automatically checks all config and language files for missing keys introduced by plugin updates. Missing keys are added with their default values — **your existing settings are never overwritten**.
Files checked:
 * config.yml
 * lang/en.yml, lang/es.yml, lang/fil.yml, lang/id.yml
## Placeholders
### PlaceholderAPI Format (%rankforge_xxx%)
Requires PlaceholderAPI to be installed.
| Placeholder | Description |
|---|---|
| %rankforge_rank% | Current rank ID |
| %rankforge_rank_name% | Current rank display name |
| %rankforge_rank_prefix% | Rank chat prefix |
| %rankforge_rank_position% | Rank position in chain (1, 2, 3…) |
| %rankforge_next_rank% | Next rank ID (or MAX) |
| %rankforge_next_cost% | Next rank money requirement |
| %rankforge_is_max_rank% | true/false |
| %rankforge_progress% | Progress value 0.0–100.0 |
| %rankforge_progress_bar% | ██████░░░░ bar string |
| %rankforge_progress_percent% | "73.4%" |
| %rankforge_money% | Player's current balance |
| %rankforge_player% | Player name |
| %rankforge_uuid% | Player UUID |
| %rankforge_lang% | Player language code |
| %rankforge_version% | Plugin version |
| %rankforge_gui_title% | GUI title string |
| %rankforge_top_rank_1/2/3% | Top ranked players |
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
## Supported Versions
1.20.1 · 1.20.2 · 1.20.4 · 1.20.6 · 1.21 · 1.21.1 · 1.21.2 · 1.21.3 · 1.21.4 · 1.21.5+
## License
MIT — free to use and modify with attribution to JoshuaOP.
