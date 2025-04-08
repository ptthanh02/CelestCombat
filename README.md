# CelestCombat

[![Version](https://img.shields.io/badge/version-1.0.4-blue.svg)](https://github.com/ptthanh02/CelestCombat)
[![API](https://img.shields.io/badge/API-1.21-green.svg)](https://www.spigotmc.org/)
[![Folia](https://img.shields.io/badge/Folia-supported-brightgreen.svg)](https://github.com/PaperMC/Folia)
[![bStats](https://img.shields.io/bstats/servers/25387)](https://bstats.org/plugin/bukkit/CelestCombat/25387)

**CelestCombat** is a lightweight yet powerful combat management plugin mainly designed for SwordPvP, preventing combat logging and ensuring fair PvP battles on Minecraft servers.

## ✨ Features

### 🛡️ Core Combat System
- **Combat Tagging**: Automatically tags players in combat for a configurable duration
- **Command Blocking**: Prevents usage of teleportation and utility commands during combat
- **Item Restrictions**: Disable specific items like Chorus Fruit and Elytra during combat
- **Smart Ban**: Configurable staff kicks/bans won't trigger combat logout punishments
- **Flight Permissions**: Configurable flight restrictions during combat

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
- **Customizable Barrier**: Visual and physical barriers with configurable block types
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

### 🧭 World-Specific Settings
- **Per-World Ender Pearl Cooldowns**: Customize ender pearl restrictions per world

### 🌐 Multilingual Support
- Built-in English (en_US) and Vietnamese (vi_VN) language files
- Custom language system for personalized translations

## 📋 Commands & Aliases

**Main Command**: `/celestcombat` with aliases `/cc` and `/combat`

| Command | Description | Permission |
|---------|-------------|------------|
| `/celestcombat` | Shows plugin command help | None |
| `/celestcombat reload` | Reloads the plugin configuration | `celestcombat.command.reload` |
| `/celestcombat tag <player>` | Tags a single player in combat | `celestcombat.command.tag` |
| `/celestcombat tag <player1> <player2>` | Tags two players in mutual combat | `celestcombat.command.tag` |

## 🔧 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `celestcombat.command.reload` | Allows reloading the plugin configuration | OP |
| `celestcombat.command.tag` | Allows manual combat tagging of players | OP |
| `celestcombat.update.notify` | Receive update notifications | OP |
| `celestcombat.combat.fly` | Allows players to fly while in combat | OP |

## ⚙️ Configuration

CelestCombat offers extensive configuration options with time-format support:

- **Time Format**: Support for simple (20s, 5m) and complex (1d_2h_30m_15s) formats
- **Toggleable Features**: Enable/disable systems like item restrictions and death animations
- **WorldGuard Integration**: Configurable safe zone protection with custom barrier blocks
- **Custom Language System**: Create your own translation files for personalized messaging
- **Per-World Settings**: Configure features differently across different worlds

<details>
<summary>📄 Click to view sample config.yml</summary>

```yaml
#---------------------------------------------------
#              LANGUAGE SETTINGS
#---------------------------------------------------
# Available: en_US, vi_VN
# For custom languages:
# 1. Create a new folder in the language directory
# 2. Use en_US/messages.yml as a template
# 3. Modify messages.yml as needed
# 4. Set language to your custom folder name

# ⚠️ WARNING: DO NOT modify the default language files (en_US, vi_VN) directly!
# These files will be overwritten with each plugin update.
# Always create a new custom language folder instead.

language: en_US

#---------------------------------------------------
#              CORE COMBAT SETTINGS
#---------------------------------------------------
# TIME FORMAT GUIDE
# Simple formats: 20s (20 seconds), 5m (5 minutes), 1h (1 hour)
# Complex format: 1d_2h_30m_15s (1 day, 2 hours, 30 minutes, 15 seconds)
# Units: s = seconds, m = minutes, h = hours, d = days, w = weeks, mo = months, y = years

combat:
  # Combat tag duration
  duration: 20s

  # Commands blocked during combat
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
    - "home"
    - "team"
    - "enderchest"
    - "ec"
    - "vanish"
    - "v"

  # If true, players kicked/banned by admins won't be punished (killed) for combat logging
  exempt_admin_kick: true

  # Disable flight (Creative fly) during combat
  disable_flight: true

  # Items blocked during combat (Foods, Potions, Elytra)
  item_restrictions:
    # Enable or disable item restrictions during combat
    enabled: true
    # Items blocked during combat (Foods, Potions, Elytra)
    disabled_items:
      - CHORUS_FRUIT
      - ELYTRA

#---------------------------------------------------
#              MOVEMENT RESTRICTIONS
#---------------------------------------------------
enderpearl_cooldown:
  # Enable/disable ender pearl cooldowns
  enabled: true

  # Cooldown duration
  duration: 10s

  # Only apply cooldowns during combat
  in_combat_only: true

  # Per-world settings (overrides global setting)
  worlds:
    minigames: false  # Example: cooldowns disabled in minigames world

#---------------------------------------------------
#                  DEATH EFFECTS
#---------------------------------------------------
death_animation:
  # Master toggle for death animations
  enabled: true

  # Only show animations for player kills
  only_player_kill: true

  # Animation types (random selection if multiple enabled)
  animation:
    lightning: true
    fire_particles: true

#---------------------------------------------------
#                  KILL REWARDS
#---------------------------------------------------
kill_rewards:
  # Master toggle for kill rewards
  enabled: true

  # Commands executed when player gets a kill
  # Variables: %killer% = killer's name, %victim% = victim's name
  commands:
    - "donutcratecore shards give %killer% 10"

  cooldown:
    # How often rewards can be earned (0s to disable)
    duration: 1d

    # Notify killers when victim is on cooldown
    notify: false

#---------------------------------------------------
#              WORLDGUARD INTEGRATION
#---------------------------------------------------
# WorldGuard's barrier for no-pvp regions
safezone_barrier:
  # Enable/disable WorldGuard integration for safezone barriers
  enabled: true

  # Barrier duration
  duration: 3s

  # Barrier block type
  block: RED_STAINED_GLASS_PANE

  # Barrier dimensions
  height: 4
  width: 8
```
</details>

## 📦 Installation

1. Download the latest version of CelestCombat
2. Place the JAR file in your plugins folder
3. Start or restart your server
4. Edit the configuration files to your liking
5. Use `/celestcombat reload` to apply changes

## 📊 Plugin Statistics

[<img src="https://bstats.org/signatures/bukkit/CelestCombat.svg" alt="bStats Chart"/>](https://bstats.org/plugin/bukkit/CelestCombat/25387)

## 💬 Support

Need help with CelestCombat?
- [GitHub Issue Tracker](https://github.com/ptthanh02/CelestCombat/issues)
- [Discord Support Server](https://discord.com/invite/FJN7hJKPyb)