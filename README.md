# 🛡️ RankForge
**A Professional Rank Progression System for Minecraft Servers**

RankForge is a high-performance, feature-rich plugin designed for Spigot and Paper servers. It provides a cinematic and interactive way for players to advance through ranks (A, B, C, D) using a live-tracking GUI and immersive celebration effects.

---

## ✨ Key Features

### 🎬 The Rank-Up Spectacle
Celebration is part of the progression! Every rank-up triggers:
* **Dynamic Titles:** Custom titles and subtitles flash on the player's screen.
* **Audio Feedback:** Triumphant sound effects (configurable via config).
* **Pyrotechnics:** Automatic fireworks spawned at the player's feet upon success.

### 📊 Premium Dynamic GUI
A live dashboard that tracks requirements in real-time:
* **Requirement Checklist:** Scans inventory and balance automatically.
* **Live Progress:** Shows `(Current/Total)` for money, EXP, and items (e.g., `10/16 Iron Ingots`).
* **Visual Status:** Status icons (✔/✘) and enchanted glows for available ranks.
* **Clean Formatting:** Currency is formatted with commas (e.g., $15,000).

### 🛠️ Core Engine
* **LuckPerms Sync:** Automatically updates player groups using the LP command engine.
* **Smart Requirements:** Supports Money (Vault), EXP Levels, and multiple Item types.
* **Error Handling:** Gracefully handles typos in material names and sound configurations.

---

## 📋 Commands

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/rank` | Opens the Rank Progression GUI. | `rankforge.use` |
| `/rank help` | Displays user help tips. | `rankforge.use` |
| `/rf reload` | Reloads configurations. | `rankforge.admin` |
| `/rf set <player> <rank>` | Manually sets a player's rank. | `rankforge.admin` |
| `/rf reset <player>` | Resets player to Rank A. | `rankforge.admin` |

---

## 💡 Help & Development Tips

### 1. The Rank Chain
Ensure your `ranks.yml` defines the sequence correctly using the `next-rank` node.
* *Example:* Rank `a` → `next-rank: "b"`. Rank `b` → `next-rank: "c"`.

### 2. Item Material Names
Use official **Bukkit Material** names (e.g., `IRON_INGOT`, `DIAMOND_BLOCK`). Incorrect names will default to `PAPER` to prevent crashes.

### 3. Social Prestige
Ensure your LuckPerms groups match your Rank IDs (`a`, `b`, `c`, `d`). The plugin runs `lp user <name> parent set <rankID>` automatically.

---

## 🔧 Installation
1. Drop `RankForge.jar` into your `/plugins` folder.
2. Ensure **Vault**, **LuckPerms**, and an Economy plugin (like EssentialsX) are installed.
3. Restart the server to generate configurations.
4. Customize `ranks.yml` and run `/rf reload`.

---
*Developed with ❤️ by JoshuaOP*
