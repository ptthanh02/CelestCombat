## ✨ Highlight Features

**The ultimate combat management plugin for SwordPvP & CrystalPVP servers.** Prevent combat logging, ensure fair battles, and create an engaging PvP experience with powerful features and stunning visual effects.

### ⚔️ **Smart Combat System**
- **Intelligent Combat Tagging** - Tracks players in combat with customizable duration
- **Flexible Command Blocking** - Blacklist/whitelist modes for complete control
- **Staff-Friendly** - Admin actions (kick/ban) won't trigger combat kill punishments

<br>
<div align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/e4e7967047311b4f9a896f8beb8737d5c0c7e792_0.webp" alt="Combat Indicator during PvP" />
</div>
<br>
<div align="center">
  <strong>Combat Indicator during PvP</strong>
</div>
<br>

### 🎯 **Advanced PvP Restriction Control**
- **Ender Pearl Cooldowns** - Smart management with combat integration
- **Trident Restrictions** - Per-world bans and cooldown systems
- **Item Restrictions** - Block Chorus Fruit, Elytra, and more during fights

<br>
<div align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/9e981f36ed5230ce264e330d62db060b506d82c4_0.webp" alt="Multiple cooldowns display while PvP" />
</div>
<br>
<div align="center">
  <strong>Multiple configurable cooldowns display during PvP</strong>
</div>
<br>

### 👶 **Newbie Protection**
- **New Player Shield** - Configurable protection for newcomers
- **Smart Removal** - Auto-remove when dealing damage to others
- **Visual Indicators** - Boss bar and action bar displays

<br>
<div align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/5bea30421d89862ff4599c4e3cccba8d100c0f35_0.webp" alt="PvP Protection display for new players" />
</div>
<br>
<div align="center">
  <strong>PvP Protection display for new players</strong>
</div>
<br>

### 🛡️ **WorldGuard Integration**
- **SafeZone Barriers** - Visual barriers prevent entering safe zones during combat
- **Customizable Materials** - Choose from invisible barriers to colored glass
- **Performance Optimized** - Client-side barriers for smooth gameplay

<br>
<div align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/20070e831792011e4944218833fefcfe2115079a_0.webp" alt="Precise Safe Zone Barrier for WorldGuard" />
</div>
<br>
<div align="center">
  <strong>Precise Safe Zone Barrier for WorldGuard</strong>
</div>
<br>

### 🏆 **Reward System**
- **Kill Rewards** - Execute commands on player defeats
- **Smart Cooldowns** - Global or per-player cooldowns
- **Anti-Farming Protection** - Prevent reward exploitation

```yaml
kill_rewards:
  enabled: true
  commands:
    - "eco give %killer% 100"
    - "say %killer% eliminated %victim%!"
  cooldown:
    duration: "10h"
```
<br>

### 🌐 **Multi-Language Support**
Built-in English and Vietnamese with easy custom translation system.
```yaml
# Example messages
combat_countdown:
  action_bar: "&#4A90E2Combat: &#FFFFFF%time%s"
  
player_died_combat_logout:
  message: "&cYou previously logged out during combat and have been penalized."
  title: "&#E94E77COMBAT PENALTY"
  subtitle: "&#BF3A49Don't log out during fights!"
  sound: entity.wither.hurt

combat_expired:
  enabled: true
  message: "&#4CAF50You are no longer in combat."
  sound: entity.experience_orb.pickup
```
<br>

---

## 🚀 Quick Setup

1. **Download** and place in your plugins folder
2. **Restart** your server to generate configs
3. **Customize** settings with `/cc reload`
4. **Enjoy** enhanced PvP combat!

## 📋 Commands & Usage

**Main Command:** `/celestcombat` (aliases: `/cc`, `/combat`)

| Command | Description |
|---------|-------------|
| `/cc help` | Show command help |
| `/cc reload` | Reload plugin configuration |
| `/cc tag <player1> <player2>` | Tag player(s) in combat |
| `/cc removeTag <player>` | Remove combat tags (player/world/all) |
| `/cc killReward <action>` | Manage kill reward cooldowns |
| `/cc newbieProtection <action>` | Control newbie protection |

## 🔑 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `celestcombat.command.use` | Access all plugin commands | OP |
| `celestcombat.update.notify` | Receive update notifications | OP |
| `celestcombat.bypass.tag` | Bypass combat tagging | False |

---

## 📊 Plugin Statistics

[![bStats](https://bstats.org/signatures/bukkit/CelestCombat.svg)](https://bstats.org/plugin/bukkit/CelestCombat/25387)

## 🆘 Support & Community

**Need Help?**
- 🐛 **Bug Reports:** [GitHub Issues](https://github.com/ptthanh02/CelestCombat/issues)
- 💬 **Discord Support:** [Join Our Server](https://discord.com/invite/FJN7hJKPyb)
- ⭐ **Rate & Review:** Help others discover CelestCombat!