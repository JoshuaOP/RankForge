# 🛡️ RankForge (Beta)
**A Professional Rank Progression System for Minecraft Servers**
RankForge is a high-performance, feature-rich plugin designed for Spigot and Paper servers. It provides a cinematic and interactive way for players to advance through ranks using a live-tracking GUI and immersive celebration effects.
## ✨ Key Features
### 🎬 The Rank-Up Spectacle
Celebration is part of the progression! Every rank-up triggers:
 * **Dynamic Feedback:** Configurable sound effects (success/failure/cancel) with adjustable pitch.
 * **Pyrotechnics:** Automatic fireworks spawned at the player's location upon successful forging.
 * **Global Announcements:** Server-wide broadcast system to celebrate player achievements.
### 📊 Premium Dynamic GUI
A live dashboard that tracks requirements in real-time:
 * **Profile Header:** A dynamic Player Head showing the user's skin, current rank, and balance.
 * **Anti-Skip Logic:** Enforces linear progression (A → B → C) to ensure a balanced economy.
 * **Status Tracking:** Automatically identifies if a rank is Owned, Available, Locked, or Missing Resources.
 * **Adaptive Layout:** The GUI scales based on row configuration and centers the profile head automatically.
### 🛠️ Core Engine
 * **LuckPerms Sync:** Automatically updates player groups using the LP command engine.
 * **Smart Requirements:** Supports Money (Vault), and multiple Item types.
 * **Custom Artifact Support:** Scans for items with specific custom display names, perfect for RPG or unique server items.
## 📋 Commands & Permissions
| Command | Description | Permission |
|---|---|---|
| /ranks | Opens the main Rank Progression GUI. | rankforge.use |
| /rf help | Displays administrative help tips. | rankforge.admin |
| /rf reload | Reloads all configurations instantly. | rankforge.admin |
| /rf set <p> <r> | Manually sets a player's rank. | rankforge.admin |
| /rf create <id> | Generates a new rank template in ranks.yml. | rankforge.admin |
## ⚙️ Configuration Files
### ranks.yml
*Manages the hierarchy and specific rank properties.*
```yaml
rank-order:
  - "default"
  - "member"
  - "vip"

ranks:
  member:
    prefix: "&f[Member] "
    requirements:
      money: 1000
      items:
        - "IRON_INGOT:16"
    gui:
      material: "IRON_INGOT"
      slot: 10
      name: "&f&lMEMBER"
      lore:
        - "&7Status: {status}"
    commands:
      - "lp user {player} parent set member"

```
### config.yml
*Global settings and aesthetic effects.*
```yaml
lang: "en.yml"
storage-type: "YAML"

effects:
  sounds:
    success: "UI_TOAST_CHALLENGE_COMPLETE"
    success-pitch: 1.2
    failure: "ENTITY_VILLAGER_NO"
  fireworks:
    enabled: true
    type: "BALL_LARGE"
    colors: ["GOLD", "WHITE"]

gui:
  title: "&6&lRankForge"
  rows: 3
  stats-head:
    slot: 4

```
### messages/en.yml
*Customizable language and notification system.*
```yaml
prefix: "&6&lRankForge &8» "
notifications:
  broadcast-purchase: "&6&lRankForge &8» &f{player} &7has forged the {prefix} &7rank!"
  rank-skip: "&c&lWAIT! &7You must purchase the previous rank first."
  rank-already-owned: "&eYou already own this rank!"

```
## 🚀 Installation & Usage
 1. **Dependencies:** Ensure **Vault** and **LuckPerms** are installed.
 2. **Setup:** Drop RankForge.jar into your /plugins/ folder and restart the server.
 3. **Ordering:** Always define your rank sequence in the rank-order list inside ranks.yml.
 4. **Custom Items:** To require a specific named item, use the format: MATERIAL:AMOUNT:DISPLAY_NAME (e.g., GOLD_INGOT:1:&6Ancient Coin).
 5. **Synchronization:** Ensure your LuckPerms groups match the Rank IDs used in your configuration.
*Developed by **JoshuaOP***
