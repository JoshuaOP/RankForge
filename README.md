# ✦ RankForge

> Production-ready rank management plugin for Spigot/Paper servers with vanilla XP progression, advanced GUIs, crossplay compatibility, and developer APIs.

![Spigot](https://img.shields.io/badge/Spigot-1.20.1--1.21.x-orange)
![Java](https://img.shields.io/badge/Java-21-blue)
![Version](https://img.shields.io/badge/Version-3.0-brightgreen)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## 📚 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Requirements](#-requirements)
- [Installation](#-installation)
- [Commands](#-commands)
- [Permissions](#-permissions)
- [PlaceholderAPI](#-placeholderapi-placeholders)
- [Configuration](#-configuration)
- [Player Data](#-player-data)
- [Crossplay Support](#-crossplay-support)
- [Developer API](#-developer-api)
- [Storage](#-storage)
- [Release Notes](#-release-notes-v30)
- [Building](#-building)
- [License](#-license)

---

# 📖 Overview

RankForge is a modern rank progression plugin built for **Spigot/Paper 1.20.1–1.21.x**.

### Designed for:

- Small survival servers
- Large networks
- Prison servers
- Economy servers
- Crossplay communities

### Core Goals:

- Native vanilla XP progression
- Highly configurable ranks
- Production-ready performance
- Easy administration
- Developer extensibility

---

# ✨ Features

## Core Systems

- Vanilla Minecraft XP progression
- YAML + MySQL storage
- Async processing
- Auto migrations
- Rank history tracking
- Hot reload system
- Caching optimizations

## GUI Systems

- Animated Rank Tree GUI
- Search bar support
- Pagination support
- Preview mode
- GUI themes
- Player data editor
- Requirement status indicators

## Requirement Types

Supports:

- Money requirements
- XP requirements
- Playtime requirements
- Mob kills
- Block breaks
- Permission requirements
- World restrictions
- Item requirements
- Quest requirements
- Bukkit statistics
- Custom API requirements

## Integrations

- Vault
- PlaceholderAPI
- LuckPerms
- Geyser
- Floodgate
- bStats

## Crossplay Features

- Bedrock-safe layouts
- Platform detection API
- Floodgate integration
- Touch-friendly GUIs
- Crossplay-safe menus

---

# 📦 Requirements

| Dependency | Required | Version |
|------------|----------|---------|
| Paper / Spigot | Yes | 1.20.1–1.21.x |
| Java | Yes | 21+ |
| Vault | Optional | Latest |
| LuckPerms | Optional | Latest |
| PlaceholderAPI | Optional | Latest |
| MySQL | Optional | Latest |
| Floodgate | Optional | Latest |

---

# 🚀 Installation

## Step 1: Install Plugin

Place:

```text
RankForge-3.0.jar
```

Inside:

```text
/plugins/
```

## Step 2: Start Server

Generated files:

```text
plugins/
└── RankForge/
    ├── config.yml
    ├── gui.yml
    ├── ranks.yml
    ├── playerdata.yml
    └── data/
```

## Step 3: Configure

Edit:

- `config.yml`
- `gui.yml`
- `ranks.yml`

## Step 4: Reload

```text
/rank reload
```

---

# ⚡ Commands

## Player Commands

| Command | Description |
|---------|-------------|
| `/rank` | Open rank GUI |
| `/rank up` | Rank up |
| `/rank progress` | Show progress |
| `/rank next` | Show next rank |
| `/rank current` | Current rank |
| `/rank requirements` | View requirements |
| `/rank xp` | Vanilla XP information |
| `/rank history` | Rank history |
| `/rank help` | Help menu |

## Admin Commands

| Command | Description |
|---------|-------------|
| `/rank editor` | Open editor |
| `/rank create` | Create rank |
| `/rank delete` | Delete rank |
| `/rank set` | Set rank |
| `/rank reset` | Reset rank |
| `/rank force` | Force rank |
| `/rank reload` | Reload plugin |
| `/rank playerlist` | Player editor |

---

# 🔐 Permissions

```text
rankforge.*                     -> All permissions
rankforge.rank.use             -> Basic commands
rankforge.rank.up              -> Rank up
rankforge.rank.editor          -> Editor GUI
rankforge.rank.create          -> Create ranks
rankforge.rank.delete          -> Delete ranks
rankforge.rank.reload          -> Reload plugin
rankforge.rank.set             -> Set ranks
rankforge.rank.reset           -> Reset ranks
rankforge.rank.force           -> Force ranks
rankforge.rank.playerlist      -> Player editor
rankforge.rank.xp.admin        -> Manage XP
```

---

# 🧩 PlaceholderAPI Placeholders

## Rank

```text
%rankforge_rank%
%rankforge_rank_name%
%rankforge_next_rank%
%rankforge_rank_prefix%
```

## Progress

```text
%rankforge_progress%
%rankforge_progress_bar%
%rankforge_progress_percent%
```

## Vanilla XP

```text
%rankforge_xp_level%
%rankforge_xp_progress%
```

## Economy

```text
%rankforge_money%
%rankforge_missing_money%
```

---

# ⚙ Configuration

## config.yml

```yaml
database:
  type: mysql
  host: localhost
  port: 3306
  name: rankforge
  user: root
  password: password

crossplay:
  bedrock-prefix: "."
  clean-names: true

timezone: UTC
```

## ranks.yml

```yaml
ranks:
  Member:
    display-name: "&aMember"

    requirements:
      money: 5000
      xp-level: 10
      block-breaks: 200
      playtime-minutes: 60

    next-rank: Builder
```

## gui.yml

```yaml
themes:
  default:
    border: CYAN_STAINED_GLASS_PANE

search:
  enabled: true

pagination:
  enabled: true
```

---

# 👤 Player Data

Stored at:

```text
plugins/RankForge/playerdata.yml
```

Example:

```yaml
players:

  uuid:

    rank: Member

    block-breaks: 230

    settings:
      language: en
```

---

# 🌐 Crossplay Support

Automatic support for:

- Geyser
- Floodgate
- Bedrock players
- Crossplay GUIs
- Mobile-friendly layouts

API:

```java
isBedrockPlayer()

getCleanName()

hasFloodgate()
```

---

# 🛠 Developer API

```java
RankForgeAPI api = RankForgeAPI.getInstance();

api.rankUp(player);

api.setRank(player, "VIP");

api.resetRank(player);
```

Events:

```text
RankupEvent
RankSetEvent
RankResetEvent
```

---

# 💾 Storage

## YAML

```text
plugins/RankForge/playerdata.yml
```

## MySQL

Supports:

- Automatic migrations
- Fallback mode
- Caching
- Async saves

---

# 📋 Release Notes v3.0

### Added

- Crossplay support
- Search system
- Pagination
- Vanilla XP support

### Improved

- Performance
- Async safety
- GUI stability

### Removed

- Leaderboard system
- Dead code
- Unused managers

# 📄 License

RankForge is a proprietary plugin developed by **JoshuaOP**.

All rights reserved unless explicitly stated in the download source or release page.

You may use this plugin on your server, but redistribution or modification without permission is not allowed.
---

# ❤️ Credits

Developed by **JoshuaOP**
