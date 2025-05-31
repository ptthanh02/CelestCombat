# CelestCombat

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/celest-combat-plugin?logo=modrinth&logoColor=white&label=downloads&labelColor=%23139549&color=%2318c25f)](https://modrinth.com/plugin/celest-combat-plugin)
[![Spigot Downloads](https://img.shields.io/spiget/downloads/123515?logo=spigotmc&logoColor=white&label=spigot%20downloads&labelColor=%23ED8106&color=%23FF994C)](https://www.spigotmc.org/resources/celest-combat-combat-log-%E2%9C%A8-1-21-1-21-4-%EF%B8%8F.123515/)
[![Folia](https://img.shields.io/badge/Folia-Supported-brightgreen.svg?logo=papermc&logoColor=white&labelColor=%23139549&color=%2318c25f)](https://github.com/PaperMC/Folia)

[![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg)](https://modrinth.com/plugin/celest-combat-plugin)
[![Spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg)](https://www.spigotmc.org/resources/celest-combat-combat-log-%E2%9C%A8-1-21-1-21-4-%EF%B8%8F.123515/)
[![Hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg)](https://hangar.papermc.io/Nighter/CelestCombat)

A comprehensive combat management plugin for Minecraft servers specializing in PvP environments.

## Requirements

- **Minecraft Version:** 1.21 - 1.21.4
- **Server Software:** Paper, Purpur, Folia
- **Java Version:** 21+

### Optional Dependencies
- **WorldGuard** - For safe zone integration and region protection

## Installation

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/celest-combat-plugin)
2. Place the `.jar` file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin in `plugins/CelestCombat/config.yml`
5. Reload with `/cc reload`

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/cc help` | `celestcombat.command.use` | Display command help |
| `/cc reload` | `celestcombat.command.use` | Reload plugin configuration |
| `/cc tag <player1> [player2]` | `celestcombat.command.use` | Manually tag players in combat |
| `/cc removetag <player/world/all>` | `celestcombat.command.use` | Remove combat tags |

**Aliases:** `/cc`, `/combat`, `/celestcombat`

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `celestcombat.command.use` | OP | Access to all plugin commands |
| `celestcombat.update.notify` | OP | Receive update notifications |
| `celestcombat.bypass.tag` | false | Bypass combat tagging |

## Building

```bash
git clone https://github.com/ptthanh02/CelestCombat.git
cd CelestCombat
./gradlew build
```

The compiled JAR will be available in `build/libs/`

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## Support

- **Issues & Bug Reports:** [GitHub Issues](https://github.com/ptthanh02/CelestCombat/issues)
- **Discord Community:** [Join our Discord](https://discord.com/invite/FJN7hJKPyb)

## Statistics

[![bStats](https://bstats.org/signatures/bukkit/CelestCombat.svg)](https://bstats.org/plugin/bukkit/CelestCombat/25387)

## License

This project is licensed under the CC BY-NC-SA 4.0 License - see the [LICENSE](LICENSE) file for details.