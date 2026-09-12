# Changelog

## 1.3.0

- One Java 21 JAR now targets Paper 1.21.x and current 26.x, with older attribute-name support and optional newer pet cosmetics.
- Added an adult growth stage at level 7, with a configurable unlock level and a second-ability definition for future pet expansion.
- Added a per-pet **Keep Baby Appearance** option in Pet Details. It defaults to enabled and persists through restarts; existing databases migrate automatically with the baby appearance preserved.
- Horse, Mule, and Llama pets now unlock riding as their adult ability. Riding requires the adult stage, enabled pet abilities, and the adult model.
- Grown mounts use vanilla behavior: Horses and Mules receive a protected virtual saddle for normal control, while Llamas remain mountable but unsteerable. Sneak-right-click opens their persistent pet storage.

## 1.2.5

- Remove legacy duplicated pet mobs when their previously unloaded chunks are loaded, allowing servers affected by the old death-loop bug to clean themselves up automatically.

## 1.2.4

- Fixed pets repeatedly spawning at a dead AFK player's death location. Pet entities now despawn on death, wait for the respawn event, deduplicate delayed spawn jobs, and are never saved as persistent chunk entities.
- Completed the care system: active-pet mood decays on a configurable interval and now scales attribute strength and passive XP. Feeding at maximum mood no longer consumes food.
- Hardened multi-attribute pets with legacy attribute-name aliases, list and named-map YAML formats, combined duplicate attribute values, complete modifier cleanup, and warnings for attributes unavailable on players.
- Updated menus and `/pets info` to show mood-adjusted attribute values.
