# Better Baby Pets

Baby animal pet companions for Paper servers.

## What it does

Players collect pet eggs from structure chests (or via admin commands), hatch them in a craftable **Pet Incubator**, and raise baby animal companions that follow them around and apply real player attribute bonuses that scale with level.

22 pet types across 5 rarities. Time-based leveling with adult stages. A mood system. Pet storage. A full advancement tree.

## Requirements

- Paper `1.21–1.21.11` or current `26.x` releases (one JAR)
- Java `21` for 1.21.x servers; Java `25` for 26.x servers
- Soft dependency: FishRework (optional — eggs can drop in FishRework treasure chests if installed)

## Install

1. Run `./gradlew build` or download `baby-pets-<version>.jar`; the same file supports both version families.
2. Put it into `plugins/`.
3. Start the server once to generate configs.
4. Configure `plugins/BabyPets/config.yml` and `pets.yml`.
5. Restart or run `/pets reload` for config-only changes.

## Compatibility

The build uses the Paper 1.21 API and Java 21 bytecode. Keep `api-version: '1.21'` in
`plugin.yml` so older servers can load it. On publishing platforms, select all supported
Minecraft versions for the same uploaded file. Newer baby models, climate variants, sounds,
and armadillo poses are used when available; older servers keep their vanilla appearances.
The incubator uses a regular dandelion where golden dandelions do not exist.

## Pet Incubator

Craft a Pet Incubator (recipe discoverable in-game), place it, and right-click it with a pet egg to begin incubation. Default duration is 20 minutes. The incubator is built from display entities — it renders as a custom furniture piece. Breaking it mid-incubation drops the egg and the incubator item.

Recipe: Copper Block / Lightning Rod / Copper Block on top, Iron Ingot / Glass / Iron Ingot in the middle, Iron Block / Golden Dandelion / Iron Block on the bottom.

## Leveling

Pets gain XP passively while active — default every 60 seconds. Cap is level 10. XP curve is configurable. Every level increases the pet's player attribute bonus (or storage slots for storage pets). Mood scales passive XP between 50% and 125% by default.

At level 7 by default, a pet reaches its adult stage and can unlock a species-specific second ability. The unlock level is configurable with `leveling.adult_level`.

Every pet keeps its baby appearance by default. Once it reaches the adult stage, **Pet Details** provides a per-pet toggle between the baby and adult models. Species without a separate vanilla baby model report that the cosmetic option is unavailable.

Horse, Mule, and Llama currently unlock **Riding** as their adult ability. The adult model must be visible for Riding to work. Right-click an eligible adult to ride it; sneak-right-click opens its pet storage. Horses and Mules control like saddled vanilla mounts, while Llamas preserve vanilla's mountable-but-unsteerable behavior.

## Mood

Pets have 5 mood states: Ecstatic → Happy → Content → Hungry → Sad. An active pet loses one tier every 30 minutes by default. Feed your pet or right-click it to improve mood. Mood scales player attribute bonuses from 50% at Sad to 120% at Ecstatic and passive XP from 50% to 125%; every value and the decay interval are configurable.

Food is grouped by movement type:
- Ground pets: wheat, carrot, apple, bread
- Flying pets: seeds (wheat, melon, pumpkin, beetroot, torchflower, pitcher pod)
- Water pets: cod, salmon, tropical fish, kelp

## Pet Storage

Storage pets (Horse, Donkey, Mule, Llama, Camel, Trader Llama) open a personal inventory GUI instead of providing a stat bonus. Available slot count scales with pet level. Slots are shared per storage group, not per individual pet. For a Horse, Mule, or Llama with active Riding, sneak-right-click opens the bag; other storage pets use normal empty-hand right-click.

## Main Command

- `/pets`

Permissions:

- `pets.use` — default: true
- `pets.admin` — default: op

## Configuration

Two main files:

- `config.yml` — incubation duration, leveling curve, XP interval, mood decay/multipliers, follow/teleport distances, food lists, loot injection settings, ability toggle
- `pets.yml` — full pet roster (entity type, rarity, description, one or more player attributes, potion effects, storage config)

Individual systems (advancements, abilities, loot injection) can be toggled off independently.

## Compatibility

- No hard dependencies outside Paper.
- FishRework soft dependency: if FishRework is installed, pet eggs can appear in its treasure chest loot pool.
- SQLite database is managed automatically on startup.

## Notes

- Paper plugin, not a client mod.
- Pets use display entities for hover names. They're fully server-side.
- Pet state (level, XP, mood, name, growth appearance, follow mode) persists across restarts via SQLite.
