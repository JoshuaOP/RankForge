<div align="center">

# ⚒️ RankForge

**A modular, production-ready Minecraft rank management plugin.**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.17--1.21.4-brightgreen?style=for-the-badge&logo=minecraft)](https://www.minecraft.net/)
[![Java](https://img.shields.io/badge/Java-21%2B-orange?style=for-the-badge&logo=openjdk)](https://adoptium.net/)
[![Version](https://img.shields.io/badge/Version-2.8-purple?style=for-the-badge)](https://github.com/JoshuaOP/RankForge/releases)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)
[![bStats](https://img.shields.io/badge/bStats-31704-informational?style=for-the-badge)](https://bstats.org/plugin/bukkit/RankForge/31704)
[![Issues](https://img.shields.io/github/issues/JoshuaOP/RankForge?style=for-the-badge)](https://github.com/JoshuaOP/RankForge/issues)

<br/>

*Build the rank system your server deserves — with flexible requirements, powerful rewards, animated GUIs, cosmetics, multi-language support, a REST API, and a full developer API.*

[🚀 Installation](#-installation) · [⌨️ Commands](#️-commands) · [📋 Requirements](#-rank-requirements) · [🎁 Rewards](#-rank-rewards) · [🧩 API](#-developer-api) · [💬 Support](#-support)

</div>

---

## 📖 Table of Contents

- [✨ Introduction](#-introduction)
- [🎯 Features at a Glance](#-features-at-a-glance)
- [⚙️ Requirements](#️-requirements)
- [📦 Dependencies](#-dependencies)
- [🚀 Installation](#-installation)
- [🗂️ Folder Structure](#️-folder-structure)
- [🔧 First Startup](#-first-startup)
- [📄 Configuration Files](#-configuration-files)
  - [config.yml](#configyml)
  - [ranks.yml](#ranksyml)
  - [Language Files](#language-files)
- [⌨️ Commands](#️-commands)
- [🔑 Permissions](#-permissions)
- [🏅 Managing Ranks](#-managing-ranks)
- [📋 Rank Requirements](#-rank-requirements)
- [🎁 Rank Rewards](#-rank-rewards)
- [🖥️ GUI System](#️-gui-system)
- [💄 Cosmetics](#-cosmetics)
- [📊 PlaceholderAPI Support](#-placeholderapi-support)
- [💳 Vault Economy Integration](#-vault-economy-integration)
- [🔒 LuckPerms Integration](#-luckperms-integration)
- [🌍 Crossplay Support (Geyser / Floodgate)](#-crossplay-support-geyser--floodgate)
- [🌐 Multi-Language Support](#-multi-language-support)
- [🗄️ Data Storage](#️-data-storage)
- [⚡ Performance System](#-performance-system)
- [🛡️ Anti-Abuse & Security](#️-anti-abuse--security)
- [🌐 REST API](#-rest-api)
- [📈 Experience & Rank History](#-experience--rank-history)
- [🧩 Developer API](#-developer-api)
- [📝 Full Configuration Examples](#-full-configuration-examples)
- [💡 Best Practices](#-best-practices)
- [❓ Frequently Asked Questions](#-frequently-asked-questions)
- [🔍 Troubleshooting](#-troubleshooting)
- [🤝 Contributing](#-contributing)
- [💬 Support](#-support)
- [📄 License](#-license)
- [🙏 Credits](#-credits)
- [⏱️ Real Playtime Tracking (v2.8)](#️-real-playtime-tracking-v28)
- [🖥️ GUI Configuration (gui.yml)](#️-gui-configuration-guiyml)
- [❓ Frequently Asked Questions (Extended)](#-frequently-asked-questions-1)
- [🔧 Troubleshooting (Extended)](#-troubleshooting-1)

---

## ✨ Introduction

**RankForge** is a comprehensive, enterprise-grade rank plugin for **Paper** and **Spigot** servers. It replaces brittle, script-based rankup setups with a clean YAML-driven system that is beginner-friendly yet exposes a full Java API for developers.

RankForge handles the entire lifecycle of rank progression:

- Checking a player's requirements (money, XP, playtime, mob kills, block breaks, items, quests, worlds, custom logic)
- Executing rewards when a player ranks up (commands, permissions, cosmetic effects)
- Displaying progress through animated GUIs, PlaceholderAPI placeholders, and boss bars
- Persisting data safely to YAML or MySQL with automatic cache management
- Protecting against exploits with a built-in anti-abuse system
- Serving rank data over an optional embedded REST API

---

## 🎯 Features at a Glance

<details>
<summary><strong>Click to expand</strong></summary>

### Core
- 🏅 Unlimited ranks defined in a single `ranks.yml`
- 🔗 Linked-list rank chain: each rank points to the next via `next-rank`
- 🛡️ Slot-based GUI positioning — each rank occupies a configurable inventory slot
- 💾 Hot-reload without server restart (`/rank reload`)
- 🔧 In-game rank creation, editing, and deletion via chat prompts and GUI

### Requirements
- 💰 **Money** — Vault economy balance
- ⭐ **XP Level** — Vanilla Minecraft experience level
- ⏱️ **Playtime** — Tracked via real wall-clock time (independent of server TPS)
- ⚔️ **Mob Kills** — Tracked via vanilla `MOB_KILLS` statistic
- 🪨 **Block Breaks** — Exact per-player counter via a dedicated event listener
- 📊 **Bukkit Statistics** — Any untyped `Statistic` enum value
- 🔐 **Permission** — A single required permission node
- 🧩 **Quests** — Quest IDs checked as `rankforge.quest.completed.<id>` permissions
- 🗺️ **World Lock** — Player must be in a specific world
- 🎒 **Items** — Specific materials and quantities in inventory
- 🔧 **Custom** — Register your own logic via `CustomRequirementRegistry` API

### Rewards
- 💻 **Commands** — Execute any console command on rankup
- 🔐 **Permissions** — Auto-apply LuckPerms groups or Bukkit permission attachments
- 📣 **Server Broadcast** — Configurable chat broadcast to all players
- 🎬 **Title & Subtitle** — Full-screen rank-up title with configurable timing
- 🔊 **Action Bar** — Persistent action bar notification on rankup
- 🌟 **Boss Bar** — Rank-up and progress boss bars with auto-dismiss
- ✨ **Particle Trail** — Timed particle effect around the player on rankup
- 🪧 **Tablist Prefix** — Player's tab-list name updated with their rank prefix

### Administration
- 🖥️ **Animated Rank Tree GUI** — Players browse all ranks visually
- ✏️ **Admin Editor GUI** — Create and edit ranks in-game
- 🔀 **Drag-and-Drop Slot Editor** — Reorder ranks in the GUI without editing YAML
- 👥 **Player List GUI** — Browse and edit any player's rank data in-game
- 🌍 **Multi-Language** — Built-in: English, Spanish, Filipino, Indonesian; fully extensible
- 🌐 **REST API** — Optional embedded HTTP server for external dashboard integrations
- 📈 **Rank History** — Full per-player audit log of every rank change
- 🎮 **XP Management** — Custom experience tracking with admin set/add commands
- 📦 **MySQL + HikariCP** — Connection-pooled MySQL backend; YAML fallback
- 🔄 **Auto-Repair** — Orphaned rank IDs repaired automatically on reload/join
- ⚡ **TPS-Aware Performance** — Automatically degrades heavy effects under server load
- 🛡️ **Anti-Abuse** — Macro detection, command rate limiting, admin rollback tracking
- 🎮 **Crossplay** — Native Geyser/Floodgate Bedrock prefix stripping
- 📊 **bStats** — Anonymous usage metrics (plugin ID `31704`)

</details>

---

## ⚙️ Requirements

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **Server Software** | Spigot 1.17 | Paper 1.21+ |
| **Java Version** | Java 21 | Java 21 |
| **Minecraft Version** | 1.17.x | 1.21.4 |

> [!IMPORTANT]
> RankForge is compiled targeting **Java 21** and **Spigot API 1.21.4**. It will not load on servers running Java 17 or older. Paper is strongly recommended over Spigot for best compatibility with async features and GUI handling.

> [!WARNING]
> Do **not** use the `/reload` command (vanilla server reload). Always use `/rank reload` instead. Using `/reload` can corrupt player data and cause RankForge to behave unpredictably.

---

## 📦 Dependencies

### Required
None. RankForge works out of the box with zero required dependencies.

### Optional (Soft Dependencies)

| Plugin | Version | What it enables |
|--------|---------|----------------|
| **Vault** | 1.7.1+ | Money requirements, money balance tracking |
| **LuckPerms** | 5.4+ | Automatic permission group management on rankup |
| **PlaceholderAPI** | 2.11.6+ | `%rankforge_*%` placeholders in other plugins |
| **Floodgate / Geyser** | Any | Bedrock crossplay player name handling |

> [!TIP]
> Install only the soft dependencies that match features you plan to use. A server with no economy plugin can still use XP, playtime, mob kills, and block-break requirements without Vault.

---

## 🚀 Installation

1. **Download** `RankForge-2.8.jar` from the [Spigot page](https://www.spigotmc.org/resources/%E2%9C%A6-rankforge-%E2%9A%A1.134929/).

2. **Drop the JAR** into your `plugins/` directory:
   ```
   server/
   └── plugins/
       └── RankForge-2.8.jar
   ```

3. **(Optional)** Install Vault, LuckPerms, and/or PlaceholderAPI for extra features.

4. **Start your server.** RankForge auto-generates all config files.

5. **Edit** `plugins/RankForge/ranks.yml` to define your ranks.

6. **Reload** the plugin after changes:
   ```
   /rank reload
   ```

---

## 🗂️ Folder Structure

```
plugins/
└── RankForge/
    ├── config.yml          # Main settings (storage, cosmetics, performance, REST API…)
    ├── ranks.yml           # All rank definitions (requirements, rewards, GUI layout)
    └── lang/               # Language files
        ├── en.yml          # English (default)
        ├── es.yml          # Spanish
        ├── fil.yml         # Filipino
        └── id.yml          # Indonesian
```

> [!NOTE]
> Player data is stored in a MySQL database (if configured) or in-memory with a YAML fallback file. There is no separate `data.yml` to manage manually — the plugin handles all persistence automatically.

---

## 🔧 First Startup

On first launch, RankForge:

1. Saves default `config.yml`, `ranks.yml`, and all four bundled language files
2. Detects installed soft dependencies and prints a summary:
   ```
   [RankForge] [SoftDep] Vault=✓  LuckPerms=✓  PlaceholderAPI=✓  Floodgate=✗
   [RankForge] [Lang] Dynamically indexed 4 language profiles: [en, es, fil, id]
   [RankForge] RankForge v2.8 successfully loaded! (5 ranks compiled)
   ```
3. Connects to MySQL (if configured) or initializes the YAML storage fallback
4. Starts the TPS monitor and performance evaluator
5. Starts the REST API server (if enabled in `config.yml`)
6. Registers all PlaceholderAPI placeholders (if PAPI is present)

---

## 📄 Configuration Files

### `config.yml`

The master configuration file. Every section is explained below.

```yaml
# ─────────────────────────────────────────────────────────────
#  RankForge — config.yml
#  Reload at any time with: /rank reload
# ─────────────────────────────────────────────────────────────

# ── Language ────────────────────────────────────────────────
language:
  # Default language code. Must match a file in plugins/RankForge/lang/.
  # Bundled: en, es, fil, id
  default: "en"
  # Allow players to set their own language with /rank lang set <code>
  per-player: true

# ── Announcements (rankup notifications) ────────────────────
announcements:
  enabled: true
  rankup:
    # Server-wide chat broadcast
    broadcast: true
    message: "&6[RankForge] &e%player% &aranked up to &6%rank%&a!"

    # Full-screen title shown to the ranking-up player
    title:
      enabled: true
      title: "&6✦ Rank Up! ✦"
      subtitle: "&eYou are now &a%rank%"
      fade-in: 10    # ticks (20 ticks = 1 second)
      stay: 60
      fade-out: 10

    # Action bar shown to the ranking-up player
    action-bar:
      enabled: true
      message: "&a✦ You ranked up to &e%rank% &a✦"

# ── Cosmetics ────────────────────────────────────────────────
cosmetic:
  # Boss bar shown to the player on rankup and progress checks
  bossbar:
    enabled: true
    duration-ticks: 100   # How long the boss bar stays visible (100 ticks = 5 seconds)

  # Tablist prefix: shows the player's rank prefix in the player list
  tablist:
    enabled: true
    # Available tokens: {prefix} {player}
    format: "{prefix}{player}"

  # Particle trail spawned around the player on rankup (auto-expires)
  particles:
    enabled: true

# ── Data Storage ─────────────────────────────────────────────
# Options: mysql, yaml (yaml = local file fallback)
storage:
  type: yaml
  mysql:
    host: "localhost"
    port: 3306
    database: "rankforge"
    username: "root"
    password: "password"
    pool-size: 10

# ── Sync (YAML mode only) ────────────────────────────────────
sync:
  # How often online player data is flushed to the YAML file (in ticks)
  interval-ticks: 200

# ── Block Break Tracker ──────────────────────────────────────
tracker:
  # How often the in-memory block-break counts are flushed to storage (ticks)
  block-break-flush-ticks: 100

# ── Performance ──────────────────────────────────────────────
performance:
  modes:
    # TPS at or above this → HIGH mode (all cosmetics enabled)
    high-tps-threshold: 18.0
    # TPS at or above this → MEDIUM mode (reduced cosmetics)
    medium-tps-threshold: 14.0
    # TPS below medium threshold → LOW mode (cosmetics disabled)

# ── Crossplay (Geyser / Floodgate) ──────────────────────────
crossplay:
  # Bedrock player names are prefixed with this character by Floodgate.
  # RankForge strips this prefix from display names automatically.
  bedrock-prefix: "."

# ── REST API ─────────────────────────────────────────────────
rest-api:
  enabled: false
  port: 4567
  # Bearer token for authentication. Leave empty to disable auth (dev only!).
  token: ""

# ── Debug ────────────────────────────────────────────────────
debug: false
```

---

### `ranks.yml`

Defines every rank. Each rank is a YAML key (the **rank ID**) containing its display properties, GUI position, requirements, and rewards.

```yaml
# ─────────────────────────────────────────────────────────────
#  RankForge — ranks.yml
# ─────────────────────────────────────────────────────────────

# The rank ID assigned to players who have no stored data.
default-rank: Guest

ranks:

  # ── Rank ID ─────────────────────────────────────────────
  Guest:
    # Text shown in GUIs and messages. Supports & color codes.
    display-name: "&7[Guest]"
    # The rank ID this rank promotes to. Leave empty for the final rank.
    next-rank: "Member"
    # GUI inventory slot (0–53). Use the drag editor to reorder visually.
    slot: 11
    # Material for the GUI icon.
    material: GRAY_WOOL
    # Lore lines on the GUI icon. Supports & color codes.
    lore:
      - "&7Starting rank."
    # Chat prefix injected into the tablist and cosmetics.
    chat-prefix: "&7[Guest]"
    # Permission nodes granted to the player when they hold this rank.
    permissions:
      - "essentials.chat.color"
    # Console commands executed when this rank's requirements are met and player ranks up.
    commands: []
    # ── Requirements (all must be met to rank up FROM this rank) ──
    required-money: 0
    required-xp-level: 0
    required-permission: ""
    required-playtime-minutes: 0
    required-mob-kills: 0
    required-block-breaks: 0
    required-statistic-id: ""
    required-statistic-value: 0
    required-quests: []
    required-worlds: []
    required-items: {}

  Member:
    display-name: "&a[Member]"
    next-rank: "Veteran"
    slot: 13
    material: GREEN_WOOL
    lore:
      - "&7A trusted community member."
      - ""
      - "&eRequirements:"
      - "  &7$1,000"
      - "  &710 XP levels"
      - "  &760 minutes playtime"
    chat-prefix: "&a[Member]"
    permissions:
      - "essentials.sethome.multiple.member"
    commands:
      - "lp user %player% group set member"
    required-money: 1000
    required-xp-level: 10
    required-playtime-minutes: 60
    required-mob-kills: 0
    required-block-breaks: 0
    required-items: {}
    required-quests: []
    required-worlds: []

  Veteran:
    display-name: "&2[Veteran]"
    next-rank: "Elite"
    slot: 15
    material: EMERALD
    lore:
      - "&7A seasoned survivor."
    chat-prefix: "&2[Veteran]"
    permissions:
      - "essentials.sethome.multiple.veteran"
    commands:
      - "lp user %player% group set veteran"
      - "give %player% GOLDEN_APPLE 5"
    required-money: 10000
    required-xp-level: 30
    required-playtime-minutes: 600
    required-mob-kills: 100
    required-block-breaks: 5000
    required-items:
      DIAMOND: 10
    required-quests:
      - "first_quest"
    required-worlds: []

  Elite:
    display-name: "&b[Elite]"
    next-rank: ""
    slot: 31
    material: DIAMOND
    lore:
      - "&7The pinnacle of achievement."
    chat-prefix: "&b[Elite]"
    permissions:
      - "essentials.sethome.multiple.elite"
    commands:
      - "lp user %player% group set elite"
    required-money: 100000
    required-xp-level: 75
    required-playtime-minutes: 3000
    required-mob-kills: 1000
    required-block-breaks: 50000
    required-statistic-id: "FISH_CAUGHT"
    required-statistic-value: 50
    required-items:
      NETHERITE_INGOT: 5
      DIAMOND: 32
    required-quests:
      - "main_quest"
      - "side_quest_a"
    required-worlds:
      - "world"
```

**Rank Property Reference**

| Property | Type | Description |
|---------|------|-------------|
| `display-name` | String | Name shown to players. Supports `&` color codes. |
| `next-rank` | String | The rank ID this rank promotes to. Empty string = final rank. |
| `slot` | Integer | Inventory slot (0–53) in the rank tree GUI. |
| `material` | String | Bukkit `Material` name for the GUI icon. |
| `lore` | List\<String\> | Lore lines on the GUI icon. |
| `chat-prefix` | String | Prefix shown in the tablist. |
| `permissions` | List\<String\> | Permissions granted when the player holds this rank. |
| `commands` | List\<String\> | Console commands run on rankup. Use `%player%` for the player's name. |
| `required-money` | Double | Vault balance required. `0` = no requirement. |
| `required-xp-level` | Integer | Vanilla XP level required. |
| `required-permission` | String | A single permission node required to rank up. |
| `required-playtime-minutes` | Long | Total server playtime required (minutes). |
| `required-mob-kills` | Integer | Total mob kills required (`MOB_KILLS` statistic). |
| `required-block-breaks` | Integer | Exact block breaks tracked by RankForge's counter. |
| `required-statistic-id` | String | Any untyped `Bukkit.Statistic` enum name. |
| `required-statistic-value` | Integer | The required value for the above statistic. |
| `required-quests` | List\<String\> | Quest IDs. Each is checked as `rankforge.quest.completed.<id>`. |
| `required-worlds` | List\<String\> | Player must currently be in one of these world names. |
| `required-items` | Map | `MATERIAL_NAME: amount` pairs. Items must be in inventory. |

---

### Language Files

Language files live in `plugins/RankForge/lang/`. RankForge ships with four built-in files and dynamically loads any `.yml` file placed in that folder.

| File | Language |
|------|---------|
| `en.yml` | English (default) |
| `es.yml` | Spanish |
| `fil.yml` | Filipino |
| `id.yml` | Indonesian |

**Adding a custom language:**

1. Create `plugins/RankForge/lang/de.yml` (or any ISO code)
2. Copy `en.yml` as a template and translate the `messages` section
3. Run `/rank reload` — RankForge discovers the file automatically
4. Players can switch with `/rank lang set de`

---

## ⌨️ Commands

All commands use the `/rank` base (aliased as `/ranks`).

### Player Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/rank` | `rankforge.rank.use` | Open the animated rank tree GUI |
| `/rank up` | `rankforge.rank.up` | Attempt to rank up to the next rank |
| `/rank progress` | `rankforge.rank.progress` | Show a progress bar and requirement breakdown |
| `/rank next` | `rankforge.rank.next` | Show your next rank's display name |
| `/rank current` | `rankforge.rank.current` | Show your current rank |
| `/rank requirements` | `rankforge.rank.requirements` | List unmet requirements for the next rank |
| `/rank history` | `rankforge.rank.history` | View your last 10 rank changes |
| `/rank xp` | `rankforge.rank.use` | View your vanilla XP level and progress |
| `/rank lang set <code>` | `rankforge.rank.lang` | Set your preferred language |
| `/rank lang list` | `rankforge.rank.lang` | List all available languages |
| `/rank lang reset` | `rankforge.rank.lang` | Reset to the server default language |
| `/rank version` | `rankforge.rank.system.version` | Show plugin and system information |
| `/rank help` | *(none)* | Show the help screen |

### Admin Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/rank editor` | `rankforge.rank.editor` | Open the admin rank editor GUI |
| `/rank editor <rankId>` | `rankforge.rank.editor` | Open the editor for a specific rank |
| `/rank editor drag` | `rankforge.rank.editor.drag` | Open the drag-and-drop slot editor |
| `/rank editor reload` | `rankforge.rank.editor` | Hot-reload `ranks.yml` from the editor |
| `/rank create` | `rankforge.rank.create` | Create a new rank via chat prompts |
| `/rank create <id>` | `rankforge.rank.create` | Create a new rank with a specific ID |
| `/rank delete <id>` | `rankforge.rank.delete` | Delete a rank (prompts for confirmation) |
| `/rank remove <id>` | `rankforge.rank.delete` | Alias for `/rank delete` |
| `/rank set <player> <rank>` | `rankforge.rank.set` | Set a player's rank (online or offline) |
| `/rank force <player> <rank>` | `rankforge.rank.force` | Force-set a rank bypassing all checks |
| `/rank reset <player>` | `rankforge.rank.reset` | Reset a player's rank to the default |
| `/rank xp set <player> <amount>` | `rankforge.rank.xp.admin` | Set a player's tracked experience |
| `/rank xp add <player> <amount>` | `rankforge.rank.xp.admin` | Add to a player's tracked experience |
| `/rank playerlist` | `rankforge.rank.playerlist` | Open the player data GUI (browse all players) |
| `/rank reload` | `rankforge.rank.reload` | Full plugin reload (config + ranks + lang) |
| `/rank stats` | `rankforge.rank.stats` | Show system statistics in console |
| `/rank security` | `rankforge.rank.security` | Show anti-abuse system status |
| `/rank debug` | `rankforge.rank.debug` | Show your personal rank debug information |
| `/rank sound <name>` | `rankforge.rank.sound` | Test a sound effect by name |

### Command Examples

```bash
# Player ranks up
/rank up

# Admin views Steve's rank info
/rank debug     # (run as Steve, or use /rank set to inspect)

# Admin sets Steve's rank
/rank set Steve Veteran

# Admin force-sets rank without checking requirements
/rank force Steve Elite

# Admin resets Steve to the default rank
/rank reset Steve

# Admin gives Steve 500 tracked XP
/rank xp add Steve 500

# Admin creates a new rank
/rank create
/rank create Builder

# Admin deletes a rank
/rank delete Builder

# Reload config and ranks after editing files
/rank reload
```

> [!TIP]
> `/rank set` and `/rank reset` support **offline players** — the change is stored in the database and applied the next time that player joins.

---

## 🔑 Permissions

### Player Permissions

| Permission Node | Default | Description |
|----------------|---------|-------------|
| `rankforge.rank.use` | `true` | Open the rank GUI (`/rank`) |
| `rankforge.rank.up` | `true` | Use `/rank up` |
| `rankforge.rank.progress` | `true` | Use `/rank progress` |
| `rankforge.rank.next` | `true` | Use `/rank next` |
| `rankforge.rank.current` | `true` | Use `/rank current` |
| `rankforge.rank.requirements` | `true` | Use `/rank requirements` |
| `rankforge.rank.history` | `true` | Use `/rank history` |
| `rankforge.rank.lang` | `true` | Use `/rank lang` |
| `rankforge.rank.system.version` | `true` | Use `/rank version` |

### Admin Permissions

| Permission Node | Default | Description |
|----------------|---------|-------------|
| `rankforge.rank.editor` | `op` | Open and use the admin editor GUI |
| `rankforge.rank.editor.save` | `op` | Save changes from the editor |
| `rankforge.rank.editor.drag` | `op` | Use the drag-and-drop slot editor |
| `rankforge.rank.create` | `op` | Create new ranks |
| `rankforge.rank.delete` | `op` | Delete ranks |
| `rankforge.rank.set` | `op` | Set any player's rank |
| `rankforge.rank.force` | `op` | Force-set a rank without requirement checks |
| `rankforge.rank.reset` | `op` | Reset a player's rank |
| `rankforge.rank.xp.admin` | `op` | Modify tracked XP for players |
| `rankforge.rank.playerlist` | `op` | Open the player list GUI |
| `rankforge.rank.reload` | `op` | Reload the plugin |
| `rankforge.rank.stats` | `op` | View system stats |
| `rankforge.rank.security` | `op` | View anti-abuse status |
| `rankforge.rank.debug` | `op` | View debug information |
| `rankforge.rank.sound` | `op` | Test sounds |
| `rankforge.*` | `op` | Wildcard — grants all permissions |

### Quest Integration Permission

Quest requirements are gated behind a special permission pattern:

```
rankforge.quest.completed.<questId>
```

When a player completes a quest, your quest plugin should grant this permission. RankForge checks it automatically when evaluating the `required-quests` field.

```bash
# Grant a completed quest via LuckPerms
/lp user Steve permission set rankforge.quest.completed.first_quest true
```

---

## 🏅 Managing Ranks

### Creating a Rank

**Method 1 — In-game editor (recommended):**
```
/rank create
```
RankForge will prompt you via chat to type the rank ID. You can also provide the ID directly:
```
/rank create Builder
```
The new rank is opened immediately in the `RankDetailEditorGUI` for further configuration.

**Method 2 — Edit `ranks.yml` directly:**

Add a new entry to `ranks.yml`, then run `/rank reload`.

> [!NOTE]
> Rank IDs must use only letters, numbers, and underscores. The following IDs are reserved and cannot be used: `null`, `none`, `default`, `cancel`, `all`, `reset`, `admin`.

### Editing a Rank

Open the admin GUI:
```
/rank editor
/rank editor Veteran    ← edit a specific rank directly
```

Or edit `ranks.yml` and run `/rank reload`.

### Deleting a Rank

```
/rank delete Veteran
```

RankForge will ask you to confirm by typing `yes`. This action is irreversible — players whose current rank is deleted will be automatically repaired to the default rank on their next join.

### Reordering Ranks in the GUI

```
/rank editor drag
```

Opens the drag-and-drop slot editor. Click and drag rank icons to change their GUI slot positions. Changes are saved automatically with a debounced write (no save spam).

---

## 📋 Rank Requirements

Requirements are defined directly on each rank in `ranks.yml`. A player must satisfy **all** requirements simultaneously to rank up.

### 💰 Money

Requires a Vault economy balance. The balance is checked at rankup time; **money is not automatically deducted** — use a `commands` entry to call your economy plugin's withdrawal command if needed.

```yaml
required-money: 10000
```

> [!TIP]
> To deduct money on rankup, add a command reward: `"eco take %player% 10000"` (EssentialsX) or `"vault take %player% 10000"`.

---

### ⭐ XP Level

Requires the player to hold at least this many vanilla Minecraft XP levels.

```yaml
required-xp-level: 30
```

XP levels are checked but **not consumed** by RankForge. To consume levels, add a command like `"xp set %player% 0l"`.

---

### 🔐 Permission

Requires the player to hold a specific Bukkit permission node.

```yaml
required-permission: "server.vip"
```

Only one permission can be specified per rank using this built-in field. For multiple permission checks, use `required-quests` (each quest maps to one permission) or the Custom Requirements API.

---

### ⏱️ Playtime

Requires a minimum number of minutes of server playtime. Tracked via the vanilla `PLAY_ONE_MINUTE` statistic (1 minute = 1 200 ticks).

```yaml
required-playtime-minutes: 300    # 5 hours
```

> [!NOTE]
> This statistic resets if a player's server statistics are wiped. It is cross-world (counts all time on the server, not per world).

---

### ⚔️ Mob Kills

Requires a minimum total mob kill count, read from the vanilla `MOB_KILLS` statistic.

```yaml
required-mob-kills: 500
```

---

### 🪨 Block Breaks

Requires a minimum number of block breaks. This is tracked by RankForge's own `BlockBreakTracker` — an exact atomic counter incremented on every `BlockBreakEvent`. It is **not** based on the approximated `MINE_BLOCK` vanilla statistic.

```yaml
required-block-breaks: 10000
```

> [!TIP]
> Block break counts are flushed to storage at the interval set by `tracker.block-break-flush-ticks` in `config.yml`. Data is always safe on server shutdown (a final flush is performed).

---

### 📊 Bukkit Statistic

Requires any untyped `Bukkit.Statistic` enum value to meet a minimum.

```yaml
required-statistic-id: "FISH_CAUGHT"
required-statistic-value: 100
```

A full list of valid statistic names is available in the [Bukkit Statistic Javadoc](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Statistic.html). Only **untyped** statistics (those that don't require a material or entity type parameter) are supported by this built-in field. For typed statistics, use the Custom Requirements API.

---

### 🧩 Quests

Requires completion of one or more quest IDs. Each quest ID is translated to a permission node: `rankforge.quest.completed.<questId>`. If the player has that permission, the quest is considered complete.

```yaml
required-quests:
  - "tutorial_complete"
  - "first_dungeon"
```

Grant the permission when a player finishes a quest in your quest plugin:
```bash
/lp user Steve permission set rankforge.quest.completed.tutorial_complete true
```

---

### 🗺️ World Lock

Requires the player to currently be in one of the listed worlds when they attempt to rank up.

```yaml
required-worlds:
  - "world"
  - "world_nether"
```

Leave the list empty (or omit the field) to allow ranking up from any world.

---

### 🎒 Items

Requires specific items and quantities to be present in the player's inventory. Items are **not consumed** by RankForge — add a command reward to consume them if desired.

```yaml
required-items:
  DIAMOND: 10
  IRON_INGOT: 64
  NETHERITE_INGOT: 1
```

The key is a `Bukkit.Material` name (uppercase). Only material and amount are checked — item names, lore, and NBT are ignored.

---

### 🔧 Custom Requirements (API)

Third-party plugins can register custom requirement types using the `CustomRequirementRegistry`. See [Developer API](#-developer-api) for implementation details.

```yaml
# In ranks.yml — after registering via the API
# (Custom requirements are evaluated programmatically;
#  see CustomRequirementRegistry.addRankRequirement())
```

---

## 🎁 Rank Rewards

Rewards are applied the moment a player successfully ranks up. Multiple reward types are applied simultaneously.

### 💻 Command Rewards

A list of console commands executed at rankup. Use `%player%` as the placeholder for the player's name.

```yaml
commands:
  - "lp user %player% group set veteran"
  - "give %player% GOLDEN_APPLE 5"
  - "eco give %player% 500"
  - "broadcast %player% just ranked up!"
```

> [!TIP]
> Commands are run synchronously on the main thread. For commands from plugins that support async dispatch, this is always safe.

---

### 🔐 Permission Rewards

Permissions listed in the `permissions` field are applied to the player when they hold that rank, and removed when they move to the next rank. With LuckPerms installed, they are applied as transient (session) nodes via the LuckPerms API. Without LuckPerms, they are granted via a Bukkit `PermissionAttachment`.

```yaml
permissions:
  - "essentials.sethome.multiple.3"
  - "server.veteran"
```

> [!IMPORTANT]
> These permissions represent what the player has **while at this rank**, not one-time grants. For permanent grants (e.g., LuckPerms group membership), use a `commands` entry instead: `"lp user %player% group add veteran"`.

---

### 📣 Announcement Rewards

Configured globally in `config.yml` under `announcements.rankup`:

- **Chat broadcast** — sent to all online players
- **Full-screen title** — shown to the ranking-up player
- **Action bar** — shown to the ranking-up player

All three are configurable and can be individually disabled.

---

### 🌟 Boss Bar

A boss bar is automatically shown to the player on rankup (configurable in `cosmetic.bossbar`). A progress variant is also available via the API.

---

### ✨ Particle Trail

A timed particle trail spawns around the player immediately after a rankup. It auto-expires after a short duration. The particle type and behavior are controlled by `cosmetic.particles` in `config.yml`.

Under server load (LOW performance mode), particles are suppressed automatically by the TPS monitor.

---

### 🪧 Tablist Update

The player's tab-list display name is updated to include their new rank's `chat-prefix` as soon as they rank up.

---

## 🖥️ GUI System

RankForge includes five distinct GUIs.

### Animated Rank Tree GUI (`/rank`)

The primary player-facing GUI. Opens when a player runs `/rank` (or clicks with no arguments). Displays all ranks in the chain, with the player's current position highlighted. Icons use the rank's configured `material`, `display-name`, and `lore`. The GUI is animated for visual appeal.

### Admin Rank Editor GUI (`/rank editor`)

Lets admins browse all ranks and click into the detail editor for any rank. Changes made in the GUI are auto-saved to `ranks.yml` via a debounced async write (saves fire at most once per second even during rapid edits).

### Rank Detail Editor GUI (`/rank editor <rankId>`)

A per-rank editor that lets you modify:
- Display name
- Next rank link
- Material / icon
- Required money, XP, playtime, mob kills, block breaks
- And more — via in-game chat prompts

### Drag-and-Drop Slot Editor (`/rank editor drag`)

A visual editor for rearranging rank icons in the GUI. Drag an icon from one slot to another to update its `slot` value without touching `ranks.yml`. Changes persist immediately.

### Player List GUI (`/rank playerlist`)

Lists all players whose data is in the cache, with their rank shown on the icon. Clicking a player opens the `PlayerDataEditorGUI` where an admin can view and change that player's rank data directly.

### Player Data Editor GUI (`PlayerDataEditorGUI`)

A 54-slot editor opened by clicking any player in the Player List GUI. Provides the following buttons:

| Slot | Button | Action |
|------|--------|--------|
| 4 | Player Head | Summary (UUID, rank, XP, balance, status) |
| 10 | Rank | Click to change the player's rank (chat input) |
| 12 | Experience | Click to set the player's experience (chat input) |
| 14 | Balance | Click to set the player's economy balance (chat input) |
| 16 | **Rank History** | Displays the selected player's last 10 rank changes directly in the administrator's chat |
| 45 | ← Back | Return to the Player List GUI |
| 49 | Reset | Reset the player's data to server defaults |
| 53 | Close | Close the GUI |

#### Rank History Button (Slot 16)

Clicking the **Rank History** button sends the selected player's rank history directly to the administrator's chat — no new GUI or inventory is opened. The output is identical to running `/rank history` as that player: it uses the same `RankHistoryManager`, formatter, and display logic. Up to 10 entries are shown, newest first, including the timestamp, change type (Rankup / Set / Reset), and the from/to rank IDs. Works for both online and offline players.

### External GUI Registry (API)

Developers can replace any GUI with their own implementation using `ExternalGUIRegistry`. See [Developer API](#-developer-api).

---

## 💄 Cosmetics

Cosmetics are managed by `CosmeticManager`, which coordinates four sub-systems.

### Boss Bar

| Config Key | Default | Description |
|-----------|---------|-------------|
| `cosmetic.bossbar.enabled` | `true` | Enable/disable boss bars entirely |
| `cosmetic.bossbar.duration-ticks` | `100` | Ticks before the bar auto-dismisses |

- Shows a **yellow SOLID** bar on rankup with the new rank name
- Shows a **green SEGMENTED_10** bar for progress checks (via API)
- Concurrent bars per player are handled correctly — a new bar cancels the old dismiss timer

### Particle Trail

| Config Key | Default | Description |
|-----------|---------|-------------|
| `cosmetic.particles.enabled` | `true` | Enable/disable particle effects |

A timed particle trail is spawned around the player immediately on rankup. It expires automatically — no permanent persistent trails. The trail is suppressed in LOW performance mode.

### Tablist Prefix

| Config Key | Default | Description |
|-----------|---------|-------------|
| `cosmetic.tablist.enabled` | `true` | Enable/disable tablist formatting |
| `cosmetic.tablist.format` | `{prefix}{player}` | Format string. Tokens: `{prefix}`, `{player}` |

The player's tab-list name is updated on:
- Login
- Rankup
- Plugin reload (applied to all online players)

### Join / Quit

`JoinQuitManager` handles restoring cosmetics when players reconnect and cleaning them up on disconnect (removes boss bars, stops particle tasks, resets tablist).

---

## 📊 PlaceholderAPI Support

With PlaceholderAPI installed, RankForge registers all `%rankforge_*%` placeholders automatically. Use them in any PAPI-compatible plugin (scoreboards, chat, tab lists, holograms, etc.).

### Available Placeholders

| Placeholder | Example Output | Description |
|------------|---------------|-------------|
| `%rankforge_rank%` | `Veteran` | Current rank ID |
| `%rankforge_rank_name%` | `&2[Veteran]` | Current rank display name |
| `%rankforge_rank_display%` | `&2[Veteran]` | Alias for `rank_name` |
| `%rankforge_rank_prefix%` | `&2[Veteran]` | Current rank's `chat-prefix` |
| `%rankforge_rank_position%` | `3` | Position of current rank in the chain (1-based) |
| `%rankforge_is_max_rank%` | `false` | `true` if player is at the final rank |
| `%rankforge_next_rank%` | `Elite` | Next rank ID, or `MAX` |
| `%rankforge_next_cost%` | `$100,000` | Next rank's money requirement formatted |
| `%rankforge_cost%` | `$10,000` | Current rank's money requirement formatted |
| `%rankforge_progress%` | `62.5` | Overall requirement progress as a decimal |
| `%rankforge_progress_percent%` | `62.5%` | Same with `%` appended |
| `%rankforge_progress_bar%` | `██████░░░░` | Visual ASCII progress bar |
| `%rankforge_required_progress%` | `$100,000 Lv75` | All requirements for the next rank |
| `%rankforge_remaining_progress%` | `$37,500 Lv12` | What remains to meet requirements |
| `%rankforge_money%` | `$62,500` | Player's current Vault balance |
| `%rankforge_has_money%` | `false` | `true` if player can afford the next rank |
| `%rankforge_missing_money%` | `$37,500` | Money still needed for next rank |
| `%rankforge_requirements_status%` | `§c✘ Requirements Unmet` | Met/unmet status label |
| `%rankforge_requirements_detail%` | *(multiline)* | Full requirement breakdown |
| `%rankforge_xp_level%` | `30` | Player's current Minecraft XP level |
| `%rankforge_xp_progress%` | `73.2%` | Fraction to next XP level |
| `%rankforge_player%` | `Steve` | Player's name |
| `%rankforge_uuid%` | `xxxxxxxx-...` | Player's UUID |
| `%rankforge_lang%` | `en` | Player's active language code |
| `%rankforge_version%` | `2.8` | Plugin version |

### Scoreboard Example (CMI)

```yaml
scoreboard:
  title: "&6&lMy Server"
  lines:
    - "&7Rank: %rankforge_rank_display%"
    - "&7Progress: %rankforge_progress_bar%"
    - "&7Next: %rankforge_next_rank%"
    - "&7Cost: %rankforge_next_cost%"
```

---

## 💳 Vault Economy Integration

RankForge hooks into **Vault** to read economy balances. Any Vault-compatible economy plugin works automatically.

**Common compatible plugins:** EssentialsX Economy, CMI Economy, iConomy, BOSEconomy, TheNewEconomy.

**What RankForge does with Vault:**
- Reads the player's balance to evaluate `required-money`
- Saves the live balance snapshot to the cache on player logout for accurate offline reads
- Exposes the balance via `%rankforge_money%` and related PAPI placeholders

**What RankForge does NOT do:**
- Automatically deduct money — add a command reward for withdrawals

```yaml
# Example: deduct money on rankup via EssentialsX command
commands:
  - "eco take %player% 10000"
```

---

## 🔒 LuckPerms Integration

With LuckPerms installed, RankForge manages `permissions` entries via the LuckPerms API instead of Bukkit `PermissionAttachment`, providing more reliable session-based permission handling.

**What happens on login:**
- RankForge reads the player's stored rank
- Calls `luckPermsHook.applyPermissions(player, rankModel)` with the rank's `permissions` list
- Without LuckPerms, a Bukkit `PermissionAttachment` is used as fallback

**What happens on rankup:**
- Old rank's permissions are removed via `removeRankPermissions(player, oldRankId)`
- New rank's permissions are applied via `applyRankPermissions(player, newRankId)`

**For permanent group membership**, use `commands`:
```yaml
commands:
  - "lp user %player% group set veteran"
  - "lp user %player% group remove member"
```

---

## 🌍 Crossplay Support (Geyser / Floodgate)

RankForge automatically detects Floodgate and handles Bedrock player names gracefully.

**Configuration:**
```yaml
crossplay:
  bedrock-prefix: "."    # Floodgate's default prefix for Bedrock usernames
```

**What this affects:**
- `RankForgeAPI.getCleanName(player)` strips the prefix for display purposes
- The `%rankforge_player%` placeholder returns the clean name
- Rank checks and storage still use the full UUID, so Bedrock players are handled identically to Java players internally

---

## 🌐 Multi-Language Support

RankForge ships with four bundled languages and supports unlimited custom ones.

### Bundled Languages

| Code | Language |
|------|---------|
| `en` | English |
| `es` | Spanish |
| `fil` | Filipino |
| `id` | Indonesian |

### Per-Player Language

Players change their language with:
```
/rank lang list             ← see available codes
/rank lang set es           ← switch to Spanish
/rank lang reset            ← revert to server default
```

The preference is saved to the player's data record and persists across sessions. The `%rankforge_lang%` placeholder shows a player's current language code.

### Adding a Language

1. Copy `plugins/RankForge/lang/en.yml` to `plugins/RankForge/lang/de.yml`
2. Translate the `messages` section
3. Run `/rank reload`
4. The new language is available immediately

The `prefix` key at the top of each language file sets the message prefix for that language.

---

## 🗄️ Data Storage

### YAML (Default)

When `storage.type: yaml` is set (or MySQL connection fails), player data is stored in memory and periodically flushed to YAML files. This is suitable for single-server setups.

```yaml
storage:
  type: yaml

sync:
  interval-ticks: 200    # Flush every 200 ticks (10 seconds)
```

On shutdown, a final synchronous flush is always performed.

### MySQL

For networks or servers that need persistent, relational storage:

```yaml
storage:
  type: mysql
  mysql:
    host: "localhost"
    port: 3306
    database: "rankforge"
    username: "rankforge_user"
    password: "your_secure_password"
    pool-size: 10
```

RankForge uses **HikariCP** for connection pooling. Tables are created automatically on first connection.

> [!WARNING]
> Never commit `config.yml` to a public repository if it contains your MySQL credentials. Use environment variables or a secrets manager on production servers.

### Fallback Behavior

If the MySQL connection fails at startup, RankForge automatically falls back to YAML storage and logs a warning. This prevents the plugin from failing to load due to database unavailability.

### Cache

An in-memory `CacheManager` holds data for all online players plus recently-offline players. Entries expire and are purged on a schedule (every 6 000 ticks by default). On player logout, data is saved asynchronously and the cache entry is scheduled for cleanup.

**Orphan Repair:** If a player's stored rank ID no longer exists in `ranks.yml` (e.g., after a rank was deleted), the rank is automatically repaired to the `default-rank` both on join and after every reload.

---

## ⚡ Performance System

RankForge monitors server TPS and automatically adjusts behavior to protect server performance.

### TPS Modes

| Mode | TPS Range | Behavior |
|------|-----------|---------|
| **HIGH** | ≥ 18.0 | All cosmetics enabled |
| **MEDIUM** | ≥ 14.0 | Reduced cosmetics |
| **LOW** | < 14.0 | Cosmetics (particles) suppressed |

Thresholds are configurable:
```yaml
performance:
  modes:
    high-tps-threshold: 18.0
    medium-tps-threshold: 14.0
```

Mode transitions are logged to console:
```
[RankForge] [Perf] Mode switched HIGH → MEDIUM (TPS: 16.4)
```

The TPS monitor runs as a repeating server task and evaluates every 200 ticks (10 seconds).

---

## 🛡️ Anti-Abuse & Security

`AntiAbuseManager` provides three layers of exploit protection.

### 1. Macro Detection

GUI clicks are timestamped per player. If two consecutive clicks arrive within 20 ms of each other, the second click is blocked and a warning is logged. This prevents automated macro clicking in the rank GUI.

### 2. Command Rate Limiting

`/rank up` and similar commands are subject to a 1 000 ms (1 second) per-player cooldown. Attempts within the cooldown window are silently ignored.

### 3. Admin Rollback Tracking

Admin rank changes (via `/rank set`, `/rank force`, `/rank reset`) are logged in `AntiAbuseManager` as pending rollback actions. This creates an audit trail that can be reviewed with `/rank security`.

### Anti-Bypass Manager

`AntiBypassManager` provides additional protection against players finding exploits to bypass rank requirements. Configuration is in `config.yml`.

### Rankup Queue

`RankupQueue` prevents concurrent rankup processing for the same player — if a rankup is already being processed for a player, subsequent attempts are queued or rejected until the first completes. This prevents double-rankup race conditions.

---

## 🌐 REST API

RankForge includes an optional lightweight embedded HTTP server for external dashboard and monitoring integrations.

### Enabling

```yaml
rest-api:
  enabled: true
  port: 4567
  token: "my_secret_token"    # Leave empty to disable auth (development only)
```

Run `/rank reload` after changing these settings.

### Endpoints

All endpoints are `GET` only. Responses are `application/json`.

#### `GET /api/status`
Plugin status and rank count.
```json
{
  "plugin": "RankForge",
  "version": "2.8",
  "ranks": 5,
  "players": 12
}
```

#### `GET /api/ranks`
Array of all loaded rank definitions.
```json
[
  {
    "id": "Guest",
    "displayName": "&7[Guest]",
    "nextRank": "Member",
    "slot": 11,
    "requiredMoney": 0,
    "requiredXpLevel": 0
  },
  ...
]
```

#### `GET /api/player/{uuid}`
Rank data for a specific player (must be in the active cache).
```json
{
  "uuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "name": "Steve",
  "rank": "Veteran",
  "experience": 1500,
  "money": 62500.0
}
```

### Authentication

When `token` is set, every request must include:
```
Authorization: Bearer my_secret_token
```

Requests without a valid token receive `401 Unauthorized`.

> [!WARNING]
> **Never expose the REST API port to the public internet.** Place it behind a reverse proxy (nginx, Caddy) with TLS, or restrict it to localhost/VPN access only. The embedded server has no TLS support.

---

## 📈 Experience & Rank History

### Experience Manager

RankForge tracks a **custom experience value** per player (separate from vanilla Minecraft XP). This can be used in conjunction with external systems.

```bash
# Admin commands
/rank xp                          ← view your own XP
/rank xp set Steve 1000           ← set Steve's XP to 1000
/rank xp add Steve 250            ← add 250 XP to Steve
```

The experience value is stored in the player's data record and accessible via the API:
```java
long xp = plugin.getExperienceManager().getXp(player);
plugin.getExperienceManager().set(player, 500L);
plugin.getExperienceManager().award(player, 100L);
```

### Rank History

Every rank change (rankup, admin set, force, reset) is recorded by `RankHistoryManager` with a timestamp.

Players can view their own history:
```
/rank history
```

Output shows the last 10 entries (older entries are shown as a count). History is stored persistently and loaded asynchronously to avoid blocking the main thread.

---

## 🧩 Developer API

RankForge exposes a public API at `com.joshuaop.rankforge.api.RankForgeAPI`.

### Adding as a Dependency

**Maven:**
```xml
<repository>
  <id>jitpack.io</id>
  <url>https://jitpack.io</url>
</repository>

<dependency>
  <groupId>com.github.JoshuaOP</groupId>
  <artifactId>RankForge</artifactId>
  <version>2.8</version>
  <scope>provided</scope>
</dependency>
```

**Gradle:**
```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    compileOnly 'com.github.JoshuaOP:RankForge:2.8'
}
```

**`plugin.yml`:**
```yaml
softdepend: [RankForge]
```

### Getting the API Instance

```java
import com.joshuaop.rankforge.api.RankForgeAPI;

public class MyPlugin extends JavaPlugin {
    private RankForgeAPI rankForge;

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("RankForge") != null) {
            rankForge = RankForgeAPI.getInstance();
        }
    }
}
```

### Core API Methods

```java
RankForgeAPI api = RankForgeAPI.getInstance();

// ── Read player data ──────────────────────────────────
PlayerRank rank = api.getPlayerRank(player);
String rankId   = rank.getRankId();

// ── Trigger rankup ────────────────────────────────────
boolean success = api.rankUp(player);

// ── Set rank ──────────────────────────────────────────
api.setRank(player, "Veteran");
api.setRank(player, "Veteran", commandSenderForAudit);

// ── Reset rank ────────────────────────────────────────
api.resetRank(player);
api.resetRank(player, commandSenderForAudit);

// ── Progress ──────────────────────────────────────────
double progressPercent = api.getProgress(player);    // 0.0–100.0

// ── Crossplay-safe name ───────────────────────────────
String cleanName = api.getCleanName(player);         // Strips Bedrock prefix

// ── Experience ────────────────────────────────────────
long xp = api.getExperienceManager().getXp(player);
api.getExperienceManager().award(player, 100L);
api.getExperienceManager().set(player, 500L);

// ── History ───────────────────────────────────────────
List<RankHistoryEntry> history = api.getHistoryManager().getHistory(uuid);
```

### Listening to RankForge Events

All events are in `com.joshuaop.rankforge.api.event`.

#### `RankupEvent` (Cancellable)

Fired when a player is about to rank up via `/rank up` or the API.

```java
@EventHandler
public void onRankup(RankupEvent event) {
    Player player   = event.getPlayer();
    String oldRank  = event.getOldRankId();
    String newRank  = event.getNewRankId();

    // Cancel the rankup
    if (someCondition) {
        event.setCancelled(true);
        player.sendMessage("Rankup blocked!");
        return;
    }

    // Custom logic
    player.sendMessage("Congrats on reaching " + newRank + "!");
}
```

#### `RankSetEvent`

Fired when a rank is set via `/rank set` or `api.setRank()`.

```java
@EventHandler
public void onRankSet(RankSetEvent event) {
    String newRank = event.getNewRankId();
    // Log or react to admin rank changes
}
```

#### `RankResetEvent`

Fired when a rank is reset via `/rank reset` or `api.resetRank()`.

```java
@EventHandler
public void onRankReset(RankResetEvent event) {
    Player player = event.getPlayer();
    // React to rank resets
}
```

### Registering a Custom Requirement

```java
import com.joshuaop.rankforge.api.requirement.CustomRequirement;
import com.joshuaop.rankforge.rank.RankModel;
import org.bukkit.entity.Player;

public class QuestsRequirement implements CustomRequirement {

    @Override
    public String getId() {
        return "myplugin:quests_completed";
    }

    @Override
    public boolean check(Player player, RankModel rank, String configValue) {
        int required = Integer.parseInt(configValue);
        int completed = MyQuestPlugin.getCompleted(player);
        return completed >= required;
    }

    @Override
    public String getUnmetMessage(Player player, RankModel rank, String configValue) {
        int required  = Integer.parseInt(configValue);
        int completed = MyQuestPlugin.getCompleted(player);
        return "§7Quests: §c" + completed + "/" + required;
    }
}

// Register during onEnable — after RankForge is loaded:
RankForgeAPI api = RankForgeAPI.getInstance();
if (api != null) {
    api.getCustomRequirementRegistry().register(new QuestsRequirement());
}

// Then configure per-rank in Java (not YAML):
plugin.getRequirementManager().addRankRequirement("Elite", "myplugin:quests_completed", "10");
```

### Replacing the GUI

```java
import com.joshuaop.rankforge.api.gui.ExternalGUIProvider;

public class MyGUIProvider implements ExternalGUIProvider {

    @Override
    public GuiType getType() {
        return GuiType.RANK_TREE;
    }

    @Override
    public void open(Player player) {
        // Open your custom GUI
        MyCustomGUI.open(player);
    }
}

// Register:
api.getExternalGUIRegistry().register(new MyGUIProvider());
```

### Registering a Plugin Hook

```java
import com.joshuaop.rankforge.api.hook.PluginHook;

public class MyHook implements PluginHook {
    @Override
    public String getId() { return "myplugin"; }

    @Override
    public void onEnable()  { /* hook setup */ }

    @Override
    public void onDisable() { /* hook teardown */ }
}

api.getHookRegistry().register(new MyHook());
```

---

## 📝 Full Configuration Examples

### Survival Server (5 Ranks)

<details>
<summary><strong>Click to expand</strong></summary>

```yaml
# ranks.yml
default-rank: Guest

ranks:
  Guest:
    display-name: "&7[Guest]"
    next-rank: "Member"
    slot: 10
    material: GRAY_WOOL
    lore:
      - "&7Welcome to the server!"
    chat-prefix: "&7[G]"
    permissions: []
    commands: []
    required-money: 0
    required-xp-level: 0
    required-playtime-minutes: 0
    required-mob-kills: 0
    required-block-breaks: 0
    required-items: {}

  Member:
    display-name: "&a[Member]"
    next-rank: "Veteran"
    slot: 13
    material: GREEN_WOOL
    lore:
      - "&7A trusted member."
      - ""
      - "&eRequires:"
      - "  &7$500 | 5 XP | 1 hour"
    chat-prefix: "&a[M]"
    permissions:
      - "essentials.sethome.multiple.2"
    commands:
      - "lp user %player% group set member"
      - "eco take %player% 500"
    required-money: 500
    required-xp-level: 5
    required-playtime-minutes: 60
    required-mob-kills: 0
    required-block-breaks: 0
    required-items: {}

  Veteran:
    display-name: "&2[Veteran]"
    next-rank: "Elite"
    slot: 16
    material: EMERALD
    lore:
      - "&7A seasoned player."
    chat-prefix: "&2[V]"
    permissions:
      - "essentials.sethome.multiple.4"
    commands:
      - "lp user %player% group set veteran"
      - "eco take %player% 10000"
      - "give %player% GOLDEN_APPLE 3"
    required-money: 10000
    required-xp-level: 30
    required-playtime-minutes: 600
    required-mob-kills: 200
    required-block-breaks: 10000
    required-items:
      DIAMOND: 5

  Elite:
    display-name: "&b[Elite]"
    next-rank: "Legend"
    slot: 22
    material: DIAMOND
    lore:
      - "&7Among the best."
    chat-prefix: "&b[E]"
    permissions:
      - "essentials.sethome.multiple.6"
    commands:
      - "lp user %player% group set elite"
      - "eco take %player% 50000"
    required-money: 50000
    required-xp-level: 60
    required-playtime-minutes: 3000
    required-mob-kills: 1000
    required-block-breaks: 100000
    required-items:
      DIAMOND: 32
      NETHERITE_INGOT: 2

  Legend:
    display-name: "&6&l[Legend]"
    next-rank: ""
    slot: 31
    material: NETHER_STAR
    lore:
      - "&6The highest rank."
      - "&7Only the truly dedicated reach this."
    chat-prefix: "&6&l[L]"
    permissions:
      - "essentials.sethome.multiple.10"
    commands:
      - "lp user %player% group set legend"
      - "eco take %player% 200000"
      - "broadcast &6✦ %player% &rhas become a LEGEND! &6✦"
    required-money: 200000
    required-xp-level: 100
    required-playtime-minutes: 10000
    required-mob-kills: 5000
    required-block-breaks: 500000
    required-items:
      NETHERITE_INGOT: 10
      DRAGON_EGG: 1
    required-quests:
      - "ender_dragon"
```

</details>

### Prison Server (A→Z)

<details>
<summary><strong>Click to expand</strong></summary>

```yaml
# ranks.yml
default-rank: A

ranks:
  A:
    display-name: "&7[A]"
    next-rank: "B"
    slot: 0
    material: GRAY_WOOL
    lore: ["&7Mine the A mine."]
    chat-prefix: "&7[A]"
    permissions: ["mines.a"]
    commands: []
    required-money: 0
    required-xp-level: 0
    required-playtime-minutes: 0
    required-mob-kills: 0
    required-block-breaks: 0
    required-items: {}

  B:
    display-name: "&f[B]"
    next-rank: "C"
    slot: 1
    material: WHITE_WOOL
    lore: ["&7Mine the B mine."]
    chat-prefix: "&f[B]"
    permissions: ["mines.b"]
    commands:
      - "lp user %player% group set B"
      - "eco take %player% 5000"
    required-money: 5000
    required-xp-level: 0
    required-playtime-minutes: 0
    required-mob-kills: 0
    required-block-breaks: 500
    required-items: {}

  # ... repeat the pattern through Z ...

  Z:
    display-name: "&6&l[Z] FREE"
    next-rank: ""
    slot: 25
    material: GOLD_BLOCK
    lore:
      - "&6You've earned your freedom!"
    chat-prefix: "&6[FREE]"
    permissions: ["mines.free", "prison.free"]
    commands:
      - "lp user %player% group set free"
      - "eco take %player% 10000000"
      - "broadcast &6%player% has earned their FREEDOM from prison!"
    required-money: 10000000
    required-xp-level: 0
    required-playtime-minutes: 0
    required-mob-kills: 0
    required-block-breaks: 1000000
    required-items: {}
```

</details>

---

## 💡 Best Practices

### Rank ID Naming
- Use `PascalCase` or `UPPERCASE`: `Guest`, `Member`, `Veteran` or `RANK_A`, `RANK_B`
- Avoid spaces and special characters
- Keep IDs short — they appear in logs, commands, and API calls

### Requirement Scaling
- Scale requirements **exponentially**, not linearly. Each rank should be noticeably harder than the last
- Combine multiple requirement types to prevent any single grind path from being exploitable (e.g., money + playtime together prevent AFK farming)
- Use `required-block-breaks` on survival/skyblock servers; use `required-mob-kills` on combat servers

### LuckPerms Groups
- Create a matching LuckPerms group for every rank before configuring RankForge
- Use `commands` entries to call `lp user %player% group set <group>` — this is permanent, unlike the `permissions` field which is session-based

### YAML Editing Safety
- Always run `/rank reload` after editing `ranks.yml`, never `/reload`
- Use a YAML validator before applying changes on a live server
- Take a backup of `ranks.yml` before major restructuring

### MySQL on Networks
- Use MySQL if you run more than one server (BungeeCord/Velocity) and want shared rank data
- Ensure the MySQL user has only `SELECT`, `INSERT`, `UPDATE`, `DELETE` on the `rankforge` database (no `DROP` or `CREATE`)

---

## ❓ Frequently Asked Questions

<details>
<summary><strong>Why isn't my rank saving between restarts?</strong></summary>

If using YAML storage, ensure the server is shutting down gracefully (not SIGKILL). RankForge performs a synchronous flush on `onDisable()`. If the server is killed abruptly, the last sync interval's changes may be lost. Use MySQL for stronger durability guarantees.

</details>

<details>
<summary><strong>The money requirement isn't being checked.</strong></summary>

Vault must be installed and an economy plugin must be registered with it. Check the startup log:
```
[RankForge] [SoftDep] Vault=✓ ...
```
If Vault shows `✗`, install Vault. If Vault shows `✓` but economy is still not working, check whether your economy plugin registers with Vault on startup.

</details>

<details>
<summary><strong>Permissions aren't being applied on login.</strong></summary>

The `permissions` field grants session-based permissions. With LuckPerms, these are applied as transient nodes when the player joins. Check:
1. LuckPerms shows `✓` in the startup log
2. The permission node in `permissions` is correctly spelled
3. There are no LuckPerms policy restrictions blocking transient nodes

For permanent permissions, use `commands` with `lp user %player% permission set <node> true`.

</details>

<details>
<summary><strong>Players can't rank up even though they meet all requirements.</strong></summary>

Run `/rank requirements` as the player to see which requirements are unmet. Also check:
- The `next-rank` field on their current rank is set correctly
- The player's rank ID exists in `ranks.yml`
- `debug: true` in `config.yml` for detailed logs

</details>

<details>
<summary><strong>%rankforge_*% placeholders show as literal text.</strong></summary>

PlaceholderAPI must be installed. Check the startup log for `PlaceholderAPI=✓`. If PAPI is installed but placeholders still don't resolve, run `/papi reload` and verify the plugin that uses them supports PAPI (check its documentation).

</details>

<details>
<summary><strong>My rank GUI is blank / shows wrong items.</strong></summary>

Each rank's icon is determined by its `material` field. If an invalid material name is used, the item defaults to `GRAY_WOOL`. The `slot` field must be unique per rank (0–53). Run `/rank editor drag` to visually verify and correct slot assignments.

</details>

<details>
<summary><strong>Block breaks aren't being counted.</strong></summary>

RankForge's `BlockBreakTracker` uses a `BlockBreakEvent` listener. Ensure no other plugin is cancelling this event before RankForge processes it. In `debug: true` mode, block break events are logged for each player.

</details>

<details>
<summary><strong>Can I have the same player on multiple rank systems?</strong></summary>

RankForge uses a single linear rank chain. If you need separate progression paths (e.g., survival and donor), create distinct rank IDs and manage them via separate `commands` entries and LuckPerms groups. Multi-track support is not built in but can be simulated via the API.

</details>

---

## 🔍 Troubleshooting

### Enable Debug Mode

```yaml
# config.yml
debug: true
```

Reload with `/rank reload`. Debug mode prints detailed logs for:
- Requirement checks per player
- Block-break counter updates
- Cache hits/misses
- Storage read/write operations
- Performance mode transitions

### Common Errors

| Error Message | Cause | Fix |
|--------------|-------|-----|
| `[SoftDep] Vault=✗` | Vault not installed | Install Vault |
| `[DB] Defaulting to fallback local storage` | MySQL connection failed | Check `config.yml` credentials; verify MySQL is running |
| `[RankYaml] Failed to save ranks.yml` | File permissions issue | Check the server process has write access to `plugins/RankForge/` |
| `Unknown subcommand` | Typo in command | Run `/rank help` to see all valid subcommands |
| `Rank 'X' does not exist` | Rank ID not found in `ranks.yml` | Check spelling; run `/rank reload` after adding the rank |
| `[Requirements] Unknown statistic` | Invalid `required-statistic-id` | Check the Bukkit Statistic enum name (must be uppercase, untyped) |
| `[AntiAbuse] Macro-like click detected` | Player clicking GUI too fast | Normal if player has no macro; report if persistent on legitimate players |

### Checking the Startup Log

A healthy startup looks like:
```
[RankForge] [SoftDep] Vault=✓  LuckPerms=✓  PlaceholderAPI=✓  Floodgate=✗
[RankForge] [Lang] Dynamically indexed 4 language profiles: [en, es, fil, id]
[RankForge] [REST] API server listening on port 4567   ← (if enabled)
[RankForge] RankForge v2.8 successfully loaded! (5 ranks compiled)
```

If the rank count shows `(0 ranks compiled)`, `ranks.yml` failed to parse. Check for YAML syntax errors (indentation, missing colons, tab characters).

### Checking Player Data

Use `/rank debug` (run as the player) or the Player List GUI (`/rank playerlist`) to inspect a player's current rank, XP, and cached data.

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. **Fork** the repository on GitHub
2. **Branch** from `main`: `git checkout -b feature/my-feature`
3. **Code** your changes following the existing style (Java 21, no wildcard imports, Javadoc on all public API methods)
4. **Test** on a local Paper 1.21.4 server
5. **Commit** with a clear message: `git commit -m "feat: add custom statistic requirement support"`
6. **Open a Pull Request** targeting `main`

### Bug Reports

Use [GitHub Issues](https://github.com/JoshuaOP/RankForge/issues). Include:
- Server software and exact version (e.g., `Paper 1.21.4 #123`)
- Java version (`java -version`)
- Full error from `logs/latest.log`
- Your `config.yml` and `ranks.yml` (remove credentials)
- Steps to reproduce

---

## 💬 Support

| Channel | Link |
|---------|------|
| 🐛 **Bug Reports** | [GitHub Issues](https://github.com/JoshuaOP/RankForge/issues) |
| 💡 **Feature Requests** | [GitHub Discussions](https://github.com/JoshuaOP/RankForge/discussions) |
| 📖 **Source Code** | [github.com/JoshuaOP/RankForge](https://github.com/JoshuaOP/RankForge) |

> [!NOTE]
> Before opening an issue, enable `debug: true`, reproduce the problem, and include the relevant log section. Issues without reproduction steps may be closed without resolution.

---

## 📄 License

```
MIT License

Copyright (c) 2024 JoshuaOP

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

See [LICENSE](LICENSE) for the full text.

---

## 🙏 Credits

| Name / Project | Role |
|---------------|------|
| **JoshuaOP** | Author & lead developer |
| **Vault** ([MilkBowl](https://github.com/MilkBowl/Vault)) | Economy abstraction API |
| **LuckPerms** ([Luck](https://luckperms.net/)) | Permission management API |
| **PlaceholderAPI** ([extended_clip](https://github.com/PlaceholderAPI/PlaceholderAPI)) | Placeholder framework |
| **HikariCP** ([brettwooldridge](https://github.com/brettwooldridge/HikariCP)) | High-performance JDBC connection pooling |
| **bStats** ([Bastian](https://bstats.org/)) | Anonymous plugin analytics |
| **Paper Team** | High-performance Bukkit fork |
| **Community** | Bug reports, translations, feature suggestions |

---

## ⏱️ Real Playtime Tracking (v2.8)

> **New in v2.8** — Playtime requirements now use real elapsed wall-clock time, not Minecraft ticks.

### How It Works

RankForge v2.8 introduces `PlaytimeTracker`, a dedicated listener that records the exact moment each player joins and calculates elapsed time using `System.currentTimeMillis()`. This is completely independent of the server's tick rate (TPS).

**Previous behaviour (v2.8 and earlier):**
```
playtime = PLAY_ONE_MINUTE_stat / 1200   ← tick-based, affected by TPS drops
```

**New behaviour (v2.8+):**
```
playtime = (currentTimeMillis - joinTimeMillis) / 60_000   ← real wall-clock time
```

**Why this matters:**
- On a lagging server (10 TPS), the old system would credit only **10 minutes** for every 20 minutes of real time.
- A server reboot or GC pause would skew the tick counter permanently.
- `PlaytimeTracker` is immune to all of this — if a player is online for 60 real minutes, they earn exactly 60 minutes.

### Configuring `playtime-minutes`

The configuration key is **unchanged** from previous versions. Set `playtime-minutes` inside a rank's `requirements` block:

```yaml
ranks:
  Member:
    requirements:
      playtime-minutes: 60    # 60 real minutes of server playtime
```

```yaml
ranks:
  Veteran:
    requirements:
      playtime-minutes: 600   # 10 real hours
```

The value is always in **whole minutes**. Sub-minute precision (seconds) is intentionally discarded when saving — sessions shorter than 60 seconds do not count as a full minute.

### Configuration Examples

**Beginner server — short requirements:**
```yaml
ranks:
  Newcomer:
    requirements:
      playtime-minutes: 0     # No playtime required

  Member:
    requirements:
      playtime-minutes: 30    # 30 real minutes

  Regular:
    requirements:
      playtime-minutes: 300   # 5 real hours
```

**Survival / vanilla server:**
```yaml
ranks:
  Settler:
    requirements:
      playtime-minutes: 60    # 1 real hour

  Veteran:
    requirements:
      playtime-minutes: 1200  # 20 real hours

  Elder:
    requirements:
      playtime-minutes: 6000  # 100 real hours
```

**Prison server — combined with block-breaks:**
```yaml
ranks:
  B:
    requirements:
      playtime-minutes: 60
      block-breaks: 5000

  C:
    requirements:
      playtime-minutes: 180
      block-breaks: 25000
```

### Migration from v2.7

No configuration changes are required. The `playtime-minutes` key in `ranks.yml` is identical. When upgrading:

1. RankForge automatically migrates the YAML playerdata schema from v3 → v4, adding `playtime-minutes: 0` to all existing records.
2. For MySQL, a `playtime_minutes` column is added automatically via `ALTER TABLE` on startup.
3. All existing players start at `0` real minutes and accumulate from the point of upgrade onward.

> **Note:** Previous tick-based values are **not** converted. Servers that want to credit existing players for time already played can use an admin command or manually edit `playerdata.yml`. This is intentional — converting inaccurate tick counts would propagate the existing error.

### Tips and Best Practices

- **Start low.** Begin with shorter playtime requirements (30–60 minutes) and increase them based on your player feedback.
- **Combine with other requirements.** Playtime alone doesn't show engagement. Pair it with `block-breaks` or `mob-kills` for a richer progression curve.
- **AFK players.** Playtime counts even while AFK. If you want to exclude AFK time, use a compatible AFK plugin that removes players from the server (which stops the timer), or use an activity-based requirement like `block-breaks` as the primary gate.
- **Test before going live.** Use `/rank set <yourself> <rank>` and `/rank progress` to verify requirements display correctly.
- **Reload safely.** Always use `/rank reload` — never `/reload`. A server reload can corrupt the tracker's session state.

### Troubleshooting Playtime

| Symptom | Cause | Fix |
|---------|-------|-----|
| Playtime shows 0 after upgrade | Normal — existing data migrates to 0, accumulation begins fresh | No action needed; time will accumulate naturally |
| Playtime not advancing | PlaytimeTracker not initialized | Check console for errors on startup; use `/rank version` to verify plugin loaded |
| Playtime resets on relog | Data not saving before quit | Check storage errors in console; ensure `playerdata.yml` is writable |
| Requirement shows wrong time | Stale cache | Run `/rank reload` to flush the cache |
| MySQL column missing | Very old database, migration failed | Check console for `[DB] Column migration check failed` warnings |

---

## 🖥️ GUI Configuration (`gui.yml`)

All GUI layout, appearance, and slot settings are controlled by `plugins/RankForge/gui.yml`. The file is auto-generated on first run and supports full customization without editing Java code.

### File Location

```
plugins/
└── RankForge/
    └── gui.yml
```

### Structure Overview

```yaml
# Player-facing rank tree GUI (/rank)
player-gui:
  title: "&8✦ &6RankForge &8✦"       # GUI window title
  border-material: CYAN_STAINED_GLASS_PANE  # Border block type
  head-slot: 4                         # Slot for the player's head item
  info-slot: 49                        # Slot for the info/progress item

# Admin rank editor (/rank editor)
admin-gui:
  title: "&8✦ &cAdmin Rank Editor &8✦"
  border-material: RED_STAINED_GLASS_PANE

# Per-rank detail editor (click a rank in the admin editor)
detail-editor:
  title-prefix: "&8✦ &6Editing: "
  border-top: LIME_STAINED_GLASS_PANE
  border-bottom: GRAY_STAINED_GLASS_PANE

# Drag-and-drop slot reassignment editor
drag-drop:
  title: "&8✦ &bSlot Editor &8✦"

# Player list GUI (/rank playerlist)
player-list:
  title: "&8✦ &9Player List &8✦"
  border-material: BLUE_STAINED_GLASS_PANE
  prev-page-slot: 45
  close-slot: 49
  next-page-slot: 53

# Player data editor (click a player in the player list)
player-data-editor:
  title-prefix: "&8✦ &9Editing: "
  border-material: LIGHT_BLUE_STAINED_GLASS_PANE
  block-breaks:
    slot: 30
    material: IRON_PICKAXE
    name: "&7&lBlock Breaks"
    lore:
      - "&7Exact blocks broken (tracked by RankForge)."

# Block-break stat panel settings
block-break-stats:
  show-in-rank-gui: true
  show-in-player-data-editor: true
  label: "&7Blocks Broken"
  material: IRON_PICKAXE
  value-format: "&a%count%"
  unmet-format: "&cNeed &e%required% &cblocks &8(have &7%current%&8)"
  met-format: "&a✔ &e%required% &ablocks broken"

# Shared elements used across all GUIs
common:
  filler-material: GRAY_STAINED_GLASS_PANE
  close-material: BARRIER
  close-name: "&cClose"
  back-material: ARROW
  back-name: "&7Back"

# Requirement status icons on the next-rank GUI item
requirement-icons:
  enabled: true
  met-symbol: "&a✔"
  unmet-symbol: "&c✘"
  show-details: true

# GUI themes (border colors)
themes:
  default:
    player-border: CYAN_STAINED_GLASS_PANE
    admin-border: RED_STAINED_GLASS_PANE
    list-border: BLUE_STAINED_GLASS_PANE
  dark:
    player-border: BLACK_STAINED_GLASS_PANE
    admin-border: RED_STAINED_GLASS_PANE
    list-border: BLUE_STAINED_GLASS_PANE
  gold:
    player-border: YELLOW_STAINED_GLASS_PANE
    admin-border: ORANGE_STAINED_GLASS_PANE
    list-border: YELLOW_STAINED_GLASS_PANE
```

### Customization Tips

- **Titles** support `&` color codes and most Bukkit formatting codes.
- **Materials** must be valid Bukkit `Material` enum names (all uppercase, underscores).
- **Slots** are 0-indexed inventory slots (0–53 for a 6-row chest).
- Apply changes with `/rank reload` — no server restart needed.

### GUI Reference

| GUI | Command | Description |
|-----|---------|-------------|
| Rank Tree | `/rank` | Player-facing animated rank progression view |
| Admin Editor | `/rank editor` | Create, edit, and delete ranks in-game |
| Slot Editor | `/rank editor drag` | Drag-and-drop rank reordering |
| Player List | `/rank playerlist` | Browse and edit all player data |
| Player Data Editor | *(click player in list)* | Edit individual player rank/XP/money |

---

## ❓ Frequently Asked Questions

### General

**Q: Does RankForge work without Vault, LuckPerms, or PlaceholderAPI?**
A: Yes. All three are optional. Without Vault, money requirements are skipped. Without LuckPerms, a Bukkit `PermissionAttachment` is used as fallback. Without PlaceholderAPI, the `%rankforge_*%` placeholders are simply unavailable.

**Q: Can I have unlimited ranks?**
A: Yes. RankForge loads every rank defined in `ranks.yml` with no hardcoded limit.

**Q: Does RankForge support Bedrock players (Geyser/Floodgate)?**
A: Yes. All storage and checks use UUID, not player name. The Bedrock prefix (`.` by default) is stripped from display names automatically.

**Q: Will `/reload` (vanilla server reload) break the plugin?**
A: It can. Always use `/rank reload` instead. The vanilla reload can corrupt in-memory state and discard unsaved playtime data.

### Playtime

**Q: How is playtime measured in v2.8?**
A: Using `System.currentTimeMillis()` — real wall-clock time measured in milliseconds and stored as whole minutes. This is completely independent of server TPS.

**Q: Does playtime count while the player is AFK?**
A: Yes, by default. The timer runs from join to quit regardless of activity. If you need AFK detection, use an external AFK plugin that disconnects inactive players, or gate progression on activity-based requirements like `block-breaks`.

**Q: Why did all players reset to 0 playtime after upgrading?**
A: This is expected. Previous versions used the vanilla `PLAY_ONE_MINUTE` statistic (tick-based). Those inaccurate values are not migrated to avoid propagating existing errors. Players accumulate accurate real-world playtime from the upgrade point onward.

**Q: What happens to playtime if the server crashes?**
A: Playtime is periodically flushed to storage (every `sync.interval-ticks`, default 10 seconds). On a crash, up to 10 seconds of playtime may be lost. Clean shutdowns always flush everything before stopping.

**Q: Can I manually set a player's playtime?**
A: Currently, this must be done by editing `playerdata.yml` while the server is stopped (or via a MySQL UPDATE). A `/rank playtime set` admin command may be added in a future version.

**Q: My playtime requirement shows "0min" in `/rank progress` even though I've been online for an hour.**
A: This can happen if the tracker didn't initialize on join (e.g., the plugin reloaded while you were online). Rejoin the server — the tracker will re-register your session start. If the issue persists, check console for errors.

**Q: Does playtime-minutes in ranks.yml still work without changes?**
A: Yes. The configuration key is identical. No migration of `ranks.yml` is required — only the underlying implementation changed.

### Storage

**Q: Which storage backend should I use?**
A: YAML is suitable for single servers. MySQL is recommended for networks, bungeecord/velocity proxies, or servers with more than a few hundred players. MySQL is also required if you want to query player data from external tools (dashboards, Discord bots, etc.).

**Q: My MySQL connection fails. Will the server error out?**
A: No. RankForge automatically falls back to YAML storage when MySQL is unavailable, logs a single info-level message, and continues loading normally.

**Q: How do I migrate from YAML to MySQL?**
A: Start the server with MySQL configured. RankForge will create the tables. Then manually export data from `playerdata.yml` and insert it into `rf_players`. A built-in migration tool may be added in a future version.

---

## 🔧 Troubleshooting

### Plugin fails to load

1. Check Java version: `java -version` must be 21 or higher.
2. Confirm you are running Spigot or Paper 1.17+.
3. Review the console for `[SEVERE]` messages from RankForge on startup.

### Ranks not loading

1. Ensure `ranks.yml` is valid YAML. Use [yaml-online-parser.appspot.com](https://yaml-online-parser.appspot.com/) to validate.
2. Verify the `default-rank` value matches an actual rank key.
3. Run `/rank reload` after editing the file.

### `/rank up` does nothing

1. Run `/rank progress` — this shows which requirements are unmet.
2. Confirm the current rank has a `next-rank` set.
3. Ensure you are not at the final rank (empty `next-rank`).

### Playtime not advancing

1. Check startup log — PlaytimeTracker registers on load. If there are errors, address them.
2. Relog: the join event re-registers your session start.
3. Check if `/rank requirements` shows a playtime line. If absent, the rank has `playtime-minutes: 0` (no requirement).

### Playtime not saving after restart

1. Check write permissions on `plugins/RankForge/data/playerdata.yml`.
2. Confirm no `[YamlStorage] Failed to save` errors appear in console.
3. For MySQL: check for `[DB] MySQL save failed` warnings.

### GUI not opening

1. Ensure no other plugin is cancelling inventory open events.
2. Check for `GuiClickShieldManager` warnings in console.
3. Confirm the player has `rankforge.rank.use` permission.

### LuckPerms permissions not applying

1. LuckPerms must be loaded **before** RankForge. Add `depend: [LuckPerms]` to LuckPerms's plugin.yml or ensure load order.
2. Verify the permission nodes in `ranks.yml` are valid (no spaces, valid format).
3. Use `/rank set <player> <rank>` to force a re-apply after fixing configuration.

### MySQL tables not created

1. Verify credentials in `config.yml` are correct.
2. Ensure the database user has `CREATE TABLE`, `ALTER`, and `INSERT` privileges.
3. Check console for `[DB] Failed to create MySQL tables` with the specific SQL error.

---

---

## 🛠️ Setup & Configuration Guide

This guide walks through every step of installing and configuring RankForge from scratch.

---

### 📥 Installation Guide

#### Step 1 — Download RankForge

Download the latest `RankForge-*.jar` from the [SpigotMC resource page](https://www.spigotmc.org/resources/%E2%9C%A6-rankforge-%E2%9A%A1.134929/).

#### Step 2 — Install the plugin

Drop the JAR into your server's `plugins/` folder:

```
server/
└── plugins/
    └── RankForge-2.8.jar
```

#### Step 3 — Install optional dependencies

Install any of the following soft dependencies for additional features:

| Plugin | Purpose |
|--------|---------|
| **Vault** | Money requirements and economy balance tracking |
| **LuckPerms** | Automatic permission group management on rankup |
| **PlaceholderAPI** | `%rankforge_*%` placeholders in other plugins |
| **Floodgate / Geyser** | Bedrock crossplay player name handling |
| **MySQL** | (Configure in `config.yml`) Persistent relational storage for networks |

None are required — RankForge works out of the box with no dependencies.

#### Step 4 — Start the server

Start your server once. RankForge auto-generates all configuration files:

```
plugins/RankForge/
├── config.yml
├── ranks.yml
├── gui.yml
└── lang/
    ├── en.yml
    ├── es.yml
    ├── fil.yml
    └── id.yml
```

#### Step 5 — Configure the plugin

Edit the generated files (see the [Configuration Guide](#-configuration-guide-1) below).

#### Step 6 — Reload or restart

Apply your changes without a full restart:

```
/rank reload
```

> [!WARNING]
> Never use the vanilla `/reload` command. It can corrupt in-memory player data and break the playtime tracker. Always use `/rank reload`.

---

### ⚙️ Configuration Guide

#### `config.yml`

The master settings file. Key sections:

| Section | Key | Description |
|---------|-----|-------------|
| `language` | `default` | Default language code (`en`, `es`, `fil`, `id`) |
| `language` | `per-player` | Allow players to pick their own language |
| `announcements` | `rankup.broadcast` | Broadcast message to all players on rankup |
| `announcements` | `rankup.title` | Full-screen title shown to the ranking-up player |
| `cosmetic` | `bossbar.enabled` | Boss bar on rankup and progress checks |
| `cosmetic` | `tablist.enabled` | Show rank prefix in the player list |
| `cosmetic` | `particles.enabled` | Particle trail around the player on rankup |
| `storage` | `type` | `yaml` (default) or `mysql` |
| `storage` | `mysql.*` | Host, port, database, username, password, pool-size |
| `performance` | `modes.*-tps-threshold` | TPS thresholds for HIGH / MEDIUM / LOW cosmetic modes |
| `crossplay` | `bedrock-prefix` | Floodgate prefix to strip from Bedrock player names |
| `rest-api` | `enabled` | Enable the optional embedded HTTP API |
| `debug` | *(boolean)* | Enable verbose debug logging |

#### `ranks.yml`

Defines every rank. Each top-level key under `ranks:` is a rank ID.

```yaml
default-rank: Guest   # Rank assigned to new / unknown players

ranks:
  RankId:
    display-name: "&aDisplay Name"
    next-rank: "NextRankId"       # Empty string = final rank
    slot: 11                      # GUI inventory slot (0–53)
    material: GREEN_WOOL          # Bukkit Material name
    lore:
      - "&7Description line"
    chat-prefix: "&a[Tag]"
    permissions:
      - "some.permission.node"
    commands:
      - "lp user %player% group set rankid"
    requirements:
      money: 1000
      xp-level: 10
      playtime: 1hr               # New unified format (see Requirements Guide)
      mob-kills: 50
      block-breaks: 500
      permission: "some.required.node"
      statistic-id: "FISH_CAUGHT"
      statistic-value: 10
      quests:
        - "quest_id"
      worlds:
        - "world"
      items:
        DIAMOND: 5
```

#### Language files (`lang/en.yml`, `lang/fil.yml`, `lang/id.yml`, `lang/es.yml`)

Located in `plugins/RankForge/lang/`. Each file contains:

- `prefix:` — the `[RankForge]` prefix shown before messages
- `messages:` — every translatable message string

To add a language, copy `en.yml` to a new file (e.g., `de.yml`), translate the `messages` block, and run `/rank reload`. Players switch languages with `/rank lang set de`.

#### `gui.yml`

Controls all GUI titles, border materials, slot positions, icons, and themes. Every GUI in RankForge is configurable here without touching any Java code. Apply changes with `/rank reload`.

#### YAML storage

Default. Player data is stored in `plugins/RankForge/data/playerdata.yml` and synced every `sync.interval-ticks` (default: 200 ticks = 10 seconds). A final flush always runs on clean shutdown.

#### MySQL storage

```yaml
storage:
  type: mysql
  mysql:
    host: "localhost"
    port: 3306
    database: "rankforge"
    username: "rankforge_user"
    password: "your_password"
    pool-size: 10
```

Tables are created automatically. HikariCP handles connection pooling. If the connection fails at startup, RankForge falls back to YAML automatically.

---

### 🏅 Rank Creation Guide

#### Creating a rank

Add a new key under `ranks:` in `ranks.yml`:

```yaml
ranks:
  Builder:
    display-name: "&e[Builder]"
    next-rank: "Veteran"
    slot: 13
    material: BRICKS
    lore:
      - "&7A creative builder."
    chat-prefix: "&e[Builder]"
```

Reload with `/rank reload`. You can also create ranks in-game with `/rank create <id>`.

#### Linking rank chains

Each rank points to the next via `next-rank`. Leave it empty for the final rank:

```
Guest → Member → Veteran → Elite → (empty, final rank)
```

#### Configuring rewards

```yaml
ranks:
  Member:
    permissions:
      - "essentials.sethome.multiple.2"   # Applied when player holds this rank
    commands:
      - "lp user %player% group set member"   # Runs on rankup
      - "eco take %player% 1000"
```

#### Configuring permissions

- **`permissions:`** — nodes granted while the player holds this rank (managed by LuckPerms or Bukkit attachment)
- **`commands:`** — console commands executed at the moment of rankup (`%player%` is replaced with the player's name)

#### Configuring requirements

See the [Requirements Guide](#-requirements-guide) below for all supported types and the new playtime format.

---

### 📋 Requirements Guide

All requirements are defined inside a `requirements:` block within each rank entry.

| Key | Type | Description |
|-----|------|-------------|
| `money` | Double | Vault balance required. `0` = no requirement. |
| `xp-level` | Integer | Vanilla Minecraft XP level. |
| `permission` | String | A single permission node the player must have. |
| `playtime` | String | Real wall-clock playtime (see format below). |
| `mob-kills` | Integer | Total mob kills via `MOB_KILLS` statistic. |
| `block-breaks` | Integer | Block breaks tracked by RankForge's counter. |
| `statistic-id` | String | Any untyped Bukkit `Statistic` enum name. |
| `statistic-value` | Integer | Required value for `statistic-id`. |
| `quests` | List\<String\> | Quest IDs checked as `rankforge.quest.completed.<id>`. |
| `worlds` | List\<String\> | Player must be in one of these world names. |
| `items` | Map | `MATERIAL_NAME: amount` pairs. Items must be in inventory. |

#### Playtime Requirement Format

The `playtime` key uses a single human-readable string. **This is the recommended format.**

**Supported units:**

| Unit | Meaning |
|------|---------|
| `d`  | Days (1d = 1440 minutes) |
| `hr` | Hours (1hr = 60 minutes) |
| `m`  | Minutes |
| `s`  | Seconds (truncated to whole minutes; < 60s = 0) |

Units are **case-insensitive** (`D`, `HR`, `M`, `S` all work). Extra whitespace is ignored. Any combination of units is accepted.

**Examples:**

```yaml
requirements:
  playtime: 30m           # 30 minutes

requirements:
  playtime: 2hr           # 2 hours (120 minutes)

requirements:
  playtime: 7d            # 7 days (10,080 minutes)

requirements:
  playtime: 1d 12hr       # 1 day and 12 hours (2,160 minutes)

requirements:
  playtime: 5d 5hr 5m 5s  # 5 days, 5 hours, 5 minutes (seconds truncated)

requirements:
  playtime: 45s           # 45 seconds → 0 whole minutes (no requirement in effect)
```

**Backward compatibility:**

Existing configurations using `playtime-minutes` continue to work without any changes:

```yaml
requirements:
  playtime-minutes: 60    # Still fully supported — equals "1hr"
```

The loader checks for `playtime` first; if absent, it falls back to `playtime-minutes`. No edits to existing `ranks.yml` files are required.

**Full example rank using the new format:**

```yaml
ranks:
  Veteran:
    display-name: "&2[Veteran]"
    next-rank: "Elite"
    slot: 15
    material: EMERALD
    chat-prefix: "&2[Veteran]"
    commands:
      - "lp user %player% group set veteran"
    requirements:
      money: 10000
      xp-level: 30
      playtime: 10hr
      mob-kills: 100
      block-breaks: 5000
      items:
        DIAMOND: 10
```

---

### ⌨️ Commands Guide

#### Player Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/rank` | `rankforge.rank.use` | Open the animated rank tree GUI |
| `/rank up` | `rankforge.rank.up` | Attempt to rank up to the next rank |
| `/rank progress` | `rankforge.rank.progress` | Show a progress bar and requirement breakdown |
| `/rank next` | `rankforge.rank.next` | Show your next rank's display name |
| `/rank current` | `rankforge.rank.current` | Show your current rank |
| `/rank requirements` | `rankforge.rank.requirements` | List unmet requirements for the next rank |
| `/rank history` | `rankforge.rank.history` | View your last 10 rank changes |
| `/rank xp` | `rankforge.rank.use` | View your custom RankForge XP |
| `/rank lang set <code>` | `rankforge.rank.lang` | Set your preferred language |
| `/rank lang list` | `rankforge.rank.lang` | List all available languages |
| `/rank lang reset` | `rankforge.rank.lang` | Reset to the server default language |
| `/rank version` | `rankforge.rank.system.version` | Show plugin and system information |
| `/rank help` | *(none)* | Show the help screen |

#### Admin Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/rank editor` | `rankforge.rank.editor` | Open the admin rank editor GUI |
| `/rank editor <rankId>` | `rankforge.rank.editor` | Open the editor for a specific rank |
| `/rank editor drag` | `rankforge.rank.editor.drag` | Open the drag-and-drop slot editor |
| `/rank create <id>` | `rankforge.rank.create` | Create a new rank with a specific ID |
| `/rank delete <id>` | `rankforge.rank.delete` | Delete a rank (prompts for confirmation) |
| `/rank set <player> <rank>` | `rankforge.rank.set` | Set a player's rank (online or offline) |
| `/rank force <player> <rank>` | `rankforge.rank.force` | Force-set a rank bypassing all checks |
| `/rank reset <player>` | `rankforge.rank.reset` | Reset a player to the default rank |
| `/rank reload` | `rankforge.rank.reload` | Hot-reload all configuration files |
| `/rank playerlist` | `rankforge.rank.playerlist` | Browse all player rank data in a GUI |
| `/rank xp set <player> <amount>` | `rankforge.rank.xp.set` | Set a player's custom XP |
| `/rank xp add <player> <amount>` | `rankforge.rank.xp.add` | Add custom XP to a player |
| `/rank security` | `rankforge.rank.security` | View the admin rollback audit log |
| `/rank history <player>` | `rankforge.rank.admin` | View another player's rank history |

---

### 🔑 Permissions Guide

| Permission | Description |
|-----------|-------------|
| `rankforge.rank.use` | Open the rank GUI and view XP |
| `rankforge.rank.up` | Use `/rank up` to attempt rankup |
| `rankforge.rank.progress` | View requirement progress |
| `rankforge.rank.next` | View next rank |
| `rankforge.rank.current` | View current rank |
| `rankforge.rank.requirements` | List unmet requirements |
| `rankforge.rank.history` | View own rank history |
| `rankforge.rank.lang` | Change personal language |
| `rankforge.rank.system.version` | View plugin version info |
| `rankforge.rank.editor` | Access the admin rank editor GUI |
| `rankforge.rank.editor.drag` | Access the drag-and-drop slot editor |
| `rankforge.rank.create` | Create new ranks |
| `rankforge.rank.delete` | Delete ranks |
| `rankforge.rank.set` | Set any player's rank |
| `rankforge.rank.force` | Force-set a rank bypassing requirements |
| `rankforge.rank.reset` | Reset any player to the default rank |
| `rankforge.rank.reload` | Reload plugin configuration |
| `rankforge.rank.playerlist` | Browse the player list GUI |
| `rankforge.rank.xp.set` | Set a player's custom XP |
| `rankforge.rank.xp.add` | Add custom XP to a player |
| `rankforge.rank.security` | View the anti-abuse audit log |
| `rankforge.rank.admin` | General admin access (view other players' history) |
| `rankforge.quest.completed.<id>` | Marks a quest as complete for requirement checks |

---

### 📊 PlaceholderAPI Guide

With PlaceholderAPI installed, all `%rankforge_*%` placeholders are available in any PAPI-compatible plugin (scoreboards, chat formatters, holograms, tab lists, etc.).

| Placeholder | Example Output | Description |
|------------|---------------|-------------|
| `%rankforge_rank%` | `Veteran` | Current rank ID |
| `%rankforge_rank_name%` | `&2[Veteran]` | Current rank display name |
| `%rankforge_rank_display%` | `&2[Veteran]` | Alias for `rank_name` |
| `%rankforge_rank_prefix%` | `&2[Veteran]` | Current rank's `chat-prefix` |
| `%rankforge_rank_position%` | `3` | Position in the rank chain (1-based) |
| `%rankforge_is_max_rank%` | `false` | `true` if player is at the final rank |
| `%rankforge_next_rank%` | `Elite` | Next rank ID, or `MAX` |
| `%rankforge_next_cost%` | `$100,000` | Next rank's money requirement |
| `%rankforge_cost%` | `$10,000` | Current rank's money requirement |
| `%rankforge_progress%` | `62.5` | Overall requirement progress (decimal) |
| `%rankforge_progress_percent%` | `62.5%` | Same with `%` appended |
| `%rankforge_progress_bar%` | `██████░░░░` | Visual ASCII progress bar |
| `%rankforge_required_progress%` | `$100,000 Lv75` | All requirements for the next rank |
| `%rankforge_remaining_progress%` | `$37,500 Lv12` | What remains unmet |
| `%rankforge_money%` | `$62,500` | Player's current Vault balance |
| `%rankforge_has_money%` | `false` | `true` if player can afford the next rank |
| `%rankforge_missing_money%` | `$37,500` | Money still needed |
| `%rankforge_requirements_status%` | `§c✘ Requirements Unmet` | Met / unmet status label |
| `%rankforge_requirements_detail%` | *(multiline)* | Full requirement breakdown |
| `%rankforge_xp_level%` | `30` | Player's current Minecraft XP level |
| `%rankforge_xp_progress%` | `73.2%` | Fraction to next XP level |
| `%rankforge_player%` | `Steve` | Player's display name (Bedrock-safe) |
| `%rankforge_uuid%` | `xxxxxxxx-...` | Player's UUID |
| `%rankforge_lang%` | `en` | Player's active language code |
| `%rankforge_version%` | `2.8` | Plugin version |

---

### ❓ FAQ / Troubleshooting

**Rank not updating after `/rank up`**
Run `/rank progress` to see which requirements are unmet. Confirm `next-rank` is set correctly on the current rank.

**PlaceholderAPI not working**
Ensure PlaceholderAPI is installed and loaded before RankForge. Run `/papi list` to confirm `rankforge` is registered. Use `/rank reload` after installing PAPI.

**Vault not detected**
Vault must be installed *and* an economy provider (e.g., EssentialsX) must also be present. Check startup log for `[SoftDep] Vault=✓`.

**LuckPerms integration not applying permissions**
LuckPerms must load before RankForge. Verify with `/rank version` that the plugin loaded successfully. Check that permission nodes in `ranks.yml` have no spaces or typos.

**MySQL connection problems**
Verify credentials in `config.yml`. Ensure the database user has `CREATE`, `ALTER`, and `INSERT` privileges. Check console for `[DB] Failed to create MySQL tables` and the specific SQL error. RankForge automatically falls back to YAML if MySQL is unreachable.

**Playtime requirement not progressing**
Check that `PlaytimeTracker` initialized on startup (look for errors in the console). Relog — the `PlayerJoinEvent` re-registers your session start. Confirm the rank's `playtime` value is greater than zero. Use `/rank requirements` to see the playtime line.

**Old `playtime-minutes` config stopped working**
It has not stopped working. The key is fully supported. Add `playtime: 1hr` to new ranks going forward; existing `playtime-minutes` entries require no changes.

**Invalid playtime format error**
Ensure the value uses only the supported units (`d`, `hr`, `m`, `s`) with whole numbers and no special characters. Valid: `1d 12hr 30m`. Invalid: `1.5d`, `2hours`, `90`.

**Configuration mistakes**
Use a YAML validator (e.g., [yaml-online-parser.appspot.com](https://yaml-online-parser.appspot.com/)) to check `ranks.yml` for syntax errors. All invalid ranks are logged and skipped — look for `[YamlLoader] Skipped rank` warnings in the console.

---

### 💡 Best Practices

#### Organizing rank chains

- Keep rank IDs simple and consistent (`Guest`, `Member`, `Veteran`) — they are stored in player data.
- Always set `next-rank` for every non-final rank to avoid silent dead-ends.
- Use the `slot` field to arrange ranks visually in the GUI from left to right.

#### Optimizing performance

- Use YAML storage for single servers; MySQL for networks with many concurrent players.
- Keep `sync.interval-ticks` at `200` (10 seconds) or higher to reduce disk I/O.
- Disable cosmetics you don't use (`particles`, `bossbar`) to reduce per-player overhead on busy servers.
- The TPS-aware performance system automatically suppresses heavy effects when the server is under load.

#### Creating balanced progression

- Start playtime requirements low (30m–1hr for the first rank) and increase gradually.
- Combine `playtime` with activity-based requirements (`block-breaks`, `mob-kills`) so idle AFK players don't auto-rank.
- Test all requirements on yourself with `/rank set <yourself> <rank>` and `/rank progress` before opening to players.

#### Maintaining configuration files

- Always use `/rank reload` — never `/reload`.
- Back up `ranks.yml` and `playerdata.yml` (or your MySQL database) before making major changes.
- When renaming a rank ID, update `default-rank` and all `next-rank` references, then use `/rank reload`. Players with the old ID are automatically repaired to `default-rank`.
- Use the in-game admin editor (`/rank editor`) for safe edits that automatically validate and save the file.

---

<div align="center">

**Made with ❤️ by [JoshuaOP](https://github.com/JoshuaOP)**

⭐ If RankForge helps your server, please **star the repository** — it helps others find the project!

[![GitHub Stars](https://img.shields.io/github/stars/JoshuaOP/RankForge?style=social)](https://github.com/JoshuaOP/RankForge)
[![GitHub Forks](https://img.shields.io/github/forks/JoshuaOP/RankForge?style=social)](https://github.com/JoshuaOP/RankForge/fork)

</div>
