# Changelog

## 1.2.5

- Remove legacy duplicated pet mobs when their previously unloaded chunks are loaded, allowing servers affected by the old death-loop bug to clean themselves up automatically.

## 1.2.4

- Fixed pets repeatedly spawning at a dead AFK player's death location. Pet entities now despawn on death, wait for the respawn event, deduplicate delayed spawn jobs, and are never saved as persistent chunk entities.
- Completed the care system: active-pet mood decays on a configurable interval and now scales attribute strength and passive XP. Feeding at maximum mood no longer consumes food.
- Hardened multi-attribute pets with legacy attribute-name aliases, list and named-map YAML formats, combined duplicate attribute values, complete modifier cleanup, and warnings for attributes unavailable on players.
- Updated menus and `/pets info` to show mood-adjusted attribute values.
