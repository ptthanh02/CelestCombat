# CelestCombat

[![Version](https://img.shields.io/badge/version-1.0.7-blue.svg)](https://github.com/ptthanh02/CelestCombat)
[![API](https://img.shields.io/badge/API-1.21-green.svg)](https://www.spigotmc.org/)
[![Folia](https://img.shields.io/badge/Folia-supported-brightgreen.svg)](https://github.com/PaperMC/Folia)
[![bStats](https://img.shields.io/bstats/servers/25387)](https://bstats.org/plugin/bukkit/CelestCombat/25387)

**CelestCombat** is a lightweight yet powerful combat management plugin specially designed for SwordPvP & CrystalPVP, preventing combat logging and ensuring fair PvP battles on Minecraft servers.

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
- **WorldGuard Integration**: Prevents players from entering safe zones during combat with customizable barriers
- **Barrier Customization**: Choose barrier materials and configure detection radius and height
- **Anti-Exploit Measures**: Performance-optimized protection with visual barriers fully client-side for better player feedback

### 🏹 Enhanced Weapon Control
- **Ender Pearl Management**: Optional combat timer refresh when landing an Ender Pearl with configurable cooldowns
- **Trident Restrictions**: NEW! Control trident usage with cooldowns and world-specific bans
- **Per-World Settings**: Customize weapon restrictions per world for different gameplay experiences

### 🏆 Combat Rewards
- **Kill Rewards**: Reward players with commands when they defeat opponents
- **Advanced Cooldown System**: Prevent reward farming with global or per-player cooldowns
- **Permission-Based Cooldowns**: Different cooldown durations based on player permissions

### 🌐 Multilingual Support
- Built-in English (en_US) and Vietnamese (vi_VN) language files
- Custom language system for personalized translations
- Easy-to-use language folder structure for custom translations

## 📋 Commands & Aliases

**Main Command**: `/celestcombat` with aliases `/cc` and `/combat`

| Command | Description | Permission |
|---------|-------------|------------|
| `/celestcombat` | Shows plugin command help | `celestcombat.command.use` |
| `/celestcombat reload` | Reloads the plugin configuration | `celestcombat.command.use` |
| `/celestcombat tag <player>` | Tags a single player in combat | `celestcombat.command.use` |
| `/celestcombat tag <player1> <player2>` | Tags two players in mutual combat | `celestcombat.command.use` |

## 🔧 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `celestcombat.command.use` | Allows players to use all commands of CelestCombat | OP |
| `celestcombat.update.notify` | Receive update notifications | OP |
| `celestcombat.bypass.tag` | Allows players to bypass being tagged in combat | False |
| `celestcombat.cooldown.[key]` | Custom kill reward cooldown (vip, mvp, elite) | None |

## ⚙️ Configuration

CelestCombat offers extensive configuration options with enhanced time-format support:

- **Time Format**: Support for simple (20s, 5m) and complex (1d_2h_30m_15s) formats
- **Command Block Mode**: Choose between blacklist or whitelist mode for command blocking
- **Toggleable Features**: Enable/disable systems like item restrictions and death animations
- **WorldGuard Integration**: Enhanced barrier system with customizable materials and detection
- **Custom Language System**: Create your own translation files for personalized messaging
- **Per-World Settings**: Configure features differently across different worlds
- **Debug Mode**: Comprehensive logging for troubleshooting and optimization

<details>
<summary>📄 Click to view sample config.yml</summary>

```yaml
# TIME FORMAT GUIDE
# Simple formats: 20s (20 seconds), 5m (5 minutes), 1h (1 hour)
# Complex format: 1d_2h_30m_15s (1 day, 2 hours, 30 minutes, 15 seconds)
# Units: s = seconds, m = minutes, h = hours, d = days, w = weeks, mo = months, y = years

#---------------------------------------------------
#               Language Settings
#---------------------------------------------------
# Language setting (available: en_US, vi_VN)
language: en_US

# Enable or disable debug mode (provides verbose console output)
debug: false

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

  # Commands allowed during combat (used in whitelist mode)
  allowed_commands:
    - "msg"
    - "r"
    - "reply"
    - "tell"
    - "w"
    - "whisper"
    - "shop"
    - "buy"
    - "sell"
    - "ah"

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
#              ENDER PEARL RESTRICTIONS
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
#              TRIDENT RESTRICTIONS (NEW!)
#---------------------------------------------------
trident:
  # Refresh combat timer when trident lands
  refresh_combat_on_land: false
  # Worlds where tridents are completely banned
  banned_worlds:
    world_nether: true

trident_cooldown:
  # Enable/disable trident cooldowns
  enabled: true
  # Cooldown duration
  duration: "10s"
  # Only apply cooldown when player is in combat
  in_combat_only: true
  # Per-world cooldown settings
  worlds:
    world: true
    world_nether: false
    world_the_end: true

#---------------------------------------------------
#              WORLDGUARD INTEGRATION
#---------------------------------------------------
safezone_protection:
  # Enable/disable WorldGuard integration for safezone barriers
  enabled: true
  # Barrier material (BARRIER for invisible, RED_STAINED_GLASS for visible)
  barrier_material: "RED_STAINED_GLASS"
  # Detection radius for barriers
  barrier_detection_radius: 5
  # Barrier height
  barrier_height: 3

#---------------------------------------------------
#                  DEATH EFFECTS
#---------------------------------------------------
death_animation:
  # Master toggle for death animations
  enabled: true
  # Only show animations for player kills
  only_player_kill: true
  # Animation types
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
  commands:
    - donutcratecore shards give %killer% 10
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

1. **Download** the latest version of CelestCombat
2. **Stop** your server
3. **Place** the JAR file in your plugins folder
4. **Start** your server to generate configuration files
5. **Edit** the configuration files to your liking
6. **Use** `/celestcombat reload` to apply changes without restart

## 📊 Plugin Statistics

[<img src="https://bstats.org/signatures/bukkit/CelestCombat.svg" alt="bStats Chart"/>](https://bstats.org/plugin/bukkit/CelestCombat/25387)

## 💬 Support & Community

Need help with CelestCombat? Join our community!

- 🐛 **Bug Reports**: [GitHub Issue Tracker](https://github.com/ptthanh02/CelestCombat/issues)
- 💬 **Discord Support**: [Join our Discord Server](https://discord.com/invite/FJN7hJKPyb)
- ⭐ **Rate Us**: Leave a review on [SpigotMC](https://www.spigotmc.org/resources/celestcombat.118669/)

## 📜 License

CelestCombat is open-source software. Please check the repository for license details.