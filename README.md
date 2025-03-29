# CelestCombat

[![Version](https://img.shields.io/badge/version-latest-blue.svg)](https://github.com/ptthanh02/CelestCombat)
[![API](https://img.shields.io/badge/API-1.21-green.svg)](https://www.spigotmc.org/)
[![Folia](https://img.shields.io/badge/Folia-supported-brightgreen.svg)](https://github.com/PaperMC/Folia)

**CelestCombat** is a lightweight yet powerful combat management plugin mainly designed for SwordPvP, preventing combat logging and ensuring fair PvP battles on Minecraft servers.

## ✨ Features

### 🛡️ Core Combat System
- **Combat Tagging**: Automatically tags players in combat for a configurable duration
- **Command Blocking**: Prevents usage of teleportation and utility commands during combat
- **Item Restrictions**: Disable specific items like Chorus Fruit and Elytra during combat
- **Ender Pearl Cooldown**: Configure specific cooldowns for Ender Pearls during combat

<br>
<div align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/dd288f9b5efd82a88605c4dd81ab18b06e032443.png" alt="Ender Pearl cooldown" width="750" />

<strong>Ender Pearl cooldown during PvP.</strong>
</div>
<br>

### 💥 Visual Effects
- **Death Animations**: Customizable lightning strikes and particle effects for player deaths
- **Combat Logout Punishment**: Visual and sound effects when players log out during combat
- **Combat Timers**: Clean action bar countdown display for remaining combat time

<br>
<div align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/28626acdd4a6ad343b88e95983b691db74400e26.png" alt="In Combat" />

<strong>In Combat Indicator during PvP.</strong>
</div>
<br>

### 🌍 Safe Zone Protection (WorldGuard)
- **WorldGuard Integration**: Prevents players from entering safe zones during combat
- **Barrier System**: Visual and physical barriers to prevent safe zone abuse
- **Anti-Exploit Measures**: Death penalties for attempting to escape into safe zones

<br>
<div align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/62811f25f8b3d2f5056320b49819cfd733171cb0.png" alt="Safe Zone Barrier" width="750" />

<strong>Safe Zone Barrier during PvP.</strong>
</div>
<br>

### 🏆 Combat Rewards
- **Kill Rewards**: Reward players with commands when they defeat opponents
- **Cooldown System**: Prevent farming rewards from the same player with configurable cooldowns
- **Customizable Messages**: Full control over all notifications and rewards

### 🌐 Multilingual Support
- Includes both English (en_US) and Vietnamese (vi_VN) language files
- Easily add your own language translations

### 🛠️ Administrative Tools
- **Manual Combat Tagging**: Force players into combat with simple commands
- **Reload Configuration**: Update settings without restarting your server
- **Update Notifications**: Stay informed about new versions

## 📋 Commands

| Command | Aliases | Description | Permission |
|---------|---------|-------------|------------|
| `/celestcombat` | `/cc`, `/combatlog` | Shows plugin command help | None |
| `/celestcombat reload` | `/cc reload` | Reloads the plugin configuration | `celestcombat.command.reload` |
| `/celestcombat tag <player>` | `/cc tag <player>` | Tags a single player in combat | `celestcombat.command.tag` |
| `/celestcombat tag <player1> <player2>` | `/cc tag <player1> <player2>` | Tags two players in mutual combat | `celestcombat.command.tag` |

## 🔧 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `celestcombat.command.reload` | Allows reloading the plugin configuration | OP |
| `celestcombat.command.tag` | Allows manual combat tagging of players | OP |
| `celestcombat.update.notify` | Receive update notifications | OP |

## ⚙️ Configuration

CelestCombat offers extensive configuration options:

- Combat duration and command blacklist
- Customizable punishment effects and rewards
- WorldGuard integration for safe zone protection
- Fully customizable messages with RGB color support
- Easy to understand YAML configuration files

<details>
<summary>📄 Click to view sample config.yml</summary>

```yaml
# Language settings (en_US, vi_VN)
language: en_US

# Combat settings
combat:
  # Duration of combat tag in seconds
  duration: 20

  # Commands that are blocked during combat
  blocked_commands:
    - "logout"
    - "tpa"
    - "tpahere"
    - "afk"
    - "spawn"
    - "tpaccept"
    - "tpacancel"
    - "rtp"
    - "warp"
    - "warp spawn"
    - "warp shop"
    - "warp worlds"
    - "warp crates"
    - "warp afk"
    - "warp auction"
    - "home"
    - "team home"
    - "enderchest"
    - "ec"
    - "vanish"
    - "v"

  # Items that are blocked during combat
  # Available items: Foods, Potions and Elytra
  disabled_items:
    - CHORUS_FRUIT
    - ELYTRA

# Ender pearl cooldown (while in combat)
enderpearl_cooldown:
  enabled: true
  # Ender pearl cooldown duration in seconds
  duration: 10

# Combat logout punishment effects
logout_effects:
  # Strike lightning at player location (visual only)
  lightning: true

  # Common sounds: ENTITY_LIGHTNING_BOLT_THUNDER, ENTITY_GENERIC_EXPLODE,
  # ENTITY_WITHER_DEATH, ENTITY_ENDER_DRAGON_GROWL
  # Or NONE to disable sound
  sound: "ENTITY_LIGHTNING_BOLT_THUNDER"

death_animation:
  enabled: true
  # Only play death animation for player kills
  only_player_kill: true
  # Enable/disable each
  # If multiple are enabled, random one will play each time
  animation:
    lightning: true
    fire_particles: false

# Combat kill rewards
kill_rewards:
  enabled: true
  commands:
    - "donutcratecore shards give %killer% 10"
  cooldown:
    # Number of days before a player can receive rewards for killing the same player again
    # Set to 0 to disable cooldown
    days: 1
    # Whether to notify players when they kill someone on cooldown
    notify: false

# Config barrier for WorldGuard no-pvp region
safezone_barrier:
  duration: 5          # How long the barrier stays in seconds
  height: 4            # How tall the barrier is
  width: 5             # How wide the barrier is
```
</details>

## 📦 Installation

1. Download the latest version of CelestCombat
2. Place the JAR file in your plugins folder
3. Start or restart your server
4. Edit the configuration files to your liking
5. Use `/celestcombat reload` to apply changes

## 💬 Support

Need help with CelestCombat?
- [GitHub Issue](https://github.com/ptthanh02/CelestCombat/issues)
- [Discord Support](https://discord.com/invite/FJN7hJKPyb)