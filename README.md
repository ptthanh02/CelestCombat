# 🌟 **CelestCombat**

A robust combat logging prevention plugin designed for the **CelestinalSMP** server, compatible with **Minecraft versions 1.21 - 1.21.4**.

## 📖 **Overview**

**CelestCombat** prevents combat logging by automatically tagging players during PvP encounters. If a tagged player attempts to disconnect, they will be automatically killed—ensuring fair gameplay and maintaining server integrity.

## ✨ **Key Features**

- ⚔️ **Advanced Combat Tagging**: Players are tagged during combat for a configurable duration
- ⚡ **Effective Logout Punishment**: Automatically kills players who disconnect while combat-tagged
- 🛑 **Smart Command Blocking**: Prevents use of escape commands like teleportation during active combat
- 🎯 **Ender Pearl Cooldown**: Configure special cooldowns for ender pearls during combat
- 🏆 **Kill Rewards System**: Automatically reward players for successful PvP kills
- ✨ **Visual & Audio Effects**: Configurable lightning strikes and immersive sounds when players combat log
- 🔧 **Extensive Permissions**: Fine-tune who can bypass combat restrictions
- 🚀 **Multi-Platform Support**: Works seamlessly across **Bukkit**, **Spigot**, and **Paper**
- 🌿 **Folia Compatibility**: Fully optimized for **Folia** servers

## 📋 **Technical Requirements**

- **Minecraft**: Supports versions **1.21 - 1.21.4**
- **Java**: Requires **Java 21** or higher
- **Server Platforms**: Compatible with Bukkit, Spigot, Paper, and Folia

## 🚀 **Installation**

1. Download the latest **CelestCombat** plugin JAR file
2. Place the JAR in your server's `plugins` folder
3. Restart your server or load the plugin using a plugin manager
4. The plugin will generate a default `config.yml` file
5. Customize settings to match your server's needs

## ⚙️ **Configuration**

CelestCombat offers extensive configuration options through its `config.yml` file:

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
    - "enderchest"
    - "ec"
    - "vanish"
    - "v"

# Combat logout punishment effects
logout_effects:
  # Strike lightning at player location (visual only)
  lightning: true

  # Common sounds: ENTITY_LIGHTNING_BOLT_THUNDER, ENTITY_GENERIC_EXPLODE,
  # ENTITY_WITHER_DEATH, ENTITY_ENDER_DRAGON_GROWL
  # Or NONE to disable sound
  sound: "ENTITY_LIGHTNING_BOLT_THUNDER"

# Ender pearl cooldown (while in combat)
enderpearl_cooldown:
  enabled: true
  # Ender pearl cooldown duration in seconds
  duration: 10

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
    notify: true
```

## 🔨 **Commands**

| Command                              | Description |
|--------------------------------------|-------------|
| `/celestcombat`, `/cc`, `/combatlog` | Main plugin command |
| `/celestcombat reload`               | Reloads the plugin configuration |

## 🔑 **Permissions**

| Permission                     | Description | Default |
|--------------------------------|-------------|---------|
| `celestcombat.bypass.commands` | Allows using commands during combat | `op` |
| `celestcombat.command.reload`  | Allows reloading plugin configuration | `op` |

## 💬 **Support & Community**

- **GitHub Issues**: Report bugs or suggest features on our repository

---