# CelestCombat

[![Version](https://img.shields.io/badge/version-1.0.6-blue.svg)](https://github.com/ptthanh02/CelestCombat)
[![API](https://img.shields.io/badge/API-1.21-green.svg)](https://www.spigotmc.org/)
[![Folia](https://img.shields.io/badge/Folia-supported-brightgreen.svg)](https://github.com/PaperMC/Folia)
[![bStats](https://img.shields.io/bstats/servers/25387)](https://bstats.org/plugin/bukkit/CelestCombat/25387)

**CelestCombat** is a lightweight yet powerful combat management plugin specially designed for SwordPvP, preventing combat logging and ensuring fair PvP battles on Minecraft servers.

## ✨ Features

### 🛡️ Core Combat System
- **Combat Tagging**: Automatically tags players in combat for a configurable duration
- **Command Blocking**: Configurable command blocking (blacklist/whitelist mode)
- **Item Restrictions**: Disable specific items like Chorus Fruit and Elytra during combat
- **Smart Ban**: Configurable staff kicks/bans won't trigger combat logout punishments

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
- **Push Force Control**: Configurable repulsion strength when players try to enter safe zones
- **Anti-Exploit Measures**: Performance-optimized protection without visual barriers

### 🏹 Enhanced Ender Pearl Control
- **Pearl Combat Reset**: Optional combat timer refresh when landing an Ender Pearl
- **Configurable Cooldowns**: Control Ender Pearl usage with customizable cooldowns
- **Per-World Settings**: Customize Ender Pearl restrictions per world

### 🏆 Combat Rewards
- **Kill Rewards**: Reward players with commands when they defeat opponents
- **Advanced Cooldown System**: Prevent reward farming with global or per-player cooldowns
- **Permission-Based Cooldowns**: Different cooldown durations based on player permissions
- **Customizable Messages**: Full control over all notifications and rewards

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
| `celestcombat.bypass.tag` | Allows players to bypass being tagged in combat | False |
| `celestcombat.cooldown.[key]` | Custom kill reward cooldown (vip, mvp, elite) | None |

## ⚙️ Configuration

CelestCombat offers extensive configuration options with time-format support:

- **Time Format**: Support for simple (20s, 5m) and complex (1d_2h_30m_15s) formats
- **Command Block Mode**: Choose between blacklist or whitelist mode for command blocking
- **Toggleable Features**: Enable/disable systems like item restrictions and death animations
- **WorldGuard Integration**: Performance-optimized safe zone protection with push force control
- **Custom Language System**: Create your own translation files for personalized messaging
- **Per-World Settings**: Configure features differently across different worlds

<details>
<summary>📄 Click to view sample config.yml</summary>

```yaml
#---------------------------------------------------
#               Language Settings
#---------------------------------------------------
# Available: en_US, vi_VN
language: en_US

#---------------------------------------------------
#              CORE COMBAT SETTINGS
#---------------------------------------------------
combat:
  # Combat tag duration
  duration: 20s

  # Command blocking mode: "blacklist" or "whitelist"
  command_block_mode: "blacklist"

  # Commands blocked during combat (used in blacklist mode)
  blocked_commands:
    - "logout"
    - "tpa"
    - "tpahere"
    - "afk"
    - "spawn"
    # ...more commands...

  # Commands allowed during combat (used in whitelist mode)
  allowed_commands:
    - "msg"
    - "r"
    - "reply"
    - "tell"
    # ...more commands...

  # If true, players kicked/banned by admins won't be punished for combat logging
  exempt_admin_kick: true

  # Disable flight during combat
  disable_flight: false

  # Items blocked during combat
  item_restrictions:
    enabled: false
    disabled_items:
      - CHORUS_FRUIT
      - ELYTRA

#---------------------------------------------------
#              MOVEMENT RESTRICTIONS
#---------------------------------------------------
enderpearl:
  # Refresh combat timer when player lands an ender pearl
  refresh_combat_on_land: false

enderpearl_cooldown:
  # Enable/disable ender pearl cooldowns
  enabled: true
  # Cooldown duration
  duration: 10s
  # Only apply cooldowns during combat
  in_combat_only: true
  # Per-world settings
  worlds:
    minigames: false

#---------------------------------------------------
#              WORLDGUARD INTEGRATION
#---------------------------------------------------
safezone_protection:
  # Enable/disable WorldGuard integration for safezone protection
  enabled: true
  # Push force controls how strongly players are knocked back
  push_force: 1.5

#---------------------------------------------------
#                  KILL REWARDS
#---------------------------------------------------
kill_rewards:
  # Advanced cooldown system
  cooldown:
    use_global_cooldown: false
    duration: 1d
    use_same_player_cooldown: true
    same_player_duration: 1d
    use_permission_cooldowns: false
    permissions:
      vip: 12h
      mvp: 6h
      elite: 3h
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
