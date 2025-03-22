# 🌟 **CelesCombat**

A robust combat logging plugin designed for the **CelestSMP** server, compatible with **Minecraft versions 1.21 - 1.21.4**.

---

## 📖 **Overview**

**CelesCombat** is a powerful Minecraft plugin that prevents combat logging, ensuring fair PvP encounters. When a player tries to disconnect during combat, the plugin will automatically kill them—promoting fairness and maintaining server integrity.

---

## ✨ **Features**

✅ **Combat Tagging System**: Players are tagged during combat for a configurable duration.  
✅ **Logout Punishment**: Automatically kill players who disconnect while combat-tagged.  
✅ **Command Blocking**: Restricts key commands like teleportation during active combat.  
✅ **Visual Effects**: Configurable lightning strikes and immersive sounds when a player logs out.  
✅ **Comprehensive Permissions**: Fine-tune who can bypass combat restrictions.  
✅ **Folia Support**: Fully compatible with **Folia** for optimized server performance.  
✅ **Multi-Platform**: Works seamlessly across **Bukkit**, **Spigot**, and **Paper**.

---

## 📋 **Technical Requirements**

- **Minecraft**: Supports **1.21 - 1.21.4**.
- **Java**: Requires **Java 21** or higher.
- **Platforms**: Bukkit, Spigot, Paper, and Folia compatible.

---

## 🚀 **Installation**

1. Download the **CelesCombat** plugin JAR file.
2. Place the JAR in your server's `plugins` folder.
3. Restart your server or load the plugin using a plugin manager.
4. Customize settings by editing the `config.yml` file.

---

## ⚙️ **Configuration**

The plugin generates a `config.yml` file with customizable options:

```yaml
# Language settings
language: en_US

# Combat settings
combat:
  # Duration of combat tag in seconds
  duration: 20

  # Commands that are blocked during combat
  blocked_commands:
    - "rtp"
    - "tp"
    - "teleport"
    - "spawn"
    - "home"
    - "tpa"
    - "tpahere"
    - "tpaccept"
    - "warp"
    - "enderchest"
    - "ec"
    - "vanish"
    - "v"

# Combat logout punishment effects
logout_effects:
  # Strike lightning at player location (visual only)
  lightning: true

  # Sound effects (e.g., ENTITY_LIGHTNING_BOLT_THUNDER, ENTITY_GENERIC_EXPLODE)
  sound: "ENTITY_LIGHTNING_BOLT_THUNDER"
```

---

## 🔨 **Commands**

| Command                  | Description                      |
|--------------------------|----------------------------------|
| `/celescombat` `/cc` `/combatlog` | Main plugin command.            |
| `/celescombat reload`    | Reloads the plugin configuration. |

---

## 🔑 **Permissions**

| Permission                        | Description                                      | Default |
|-----------------------------------|--------------------------------------------------|---------|
| `celescombat.bypass.commands`     | Allows players to use commands during combat.    | `op`   |
| `celescombat.command.reload`      | Allows players to reload the plugin configuration.| `op`   |

---

## 📅 **Future Plans**

CelesCombat will soon be available on **Modrinth** and **Spigot** with enhanced features and additional improvements.

---

## 💬 **Support**

For issues, suggestions, or contributions, please reach out via the project's repository.

---

## 💙 **About**

CelesCombat is proudly developed for the CelestSMP server by Nighter.

