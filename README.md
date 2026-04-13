# Better Baby Pets

Baby animal pet companions for Paper servers.

## What it does

Players collect pet eggs from structure chests (or via admin commands), hatch them in a craftable **Pet Incubator**, and raise baby animal companions that follow them around and apply real player attribute bonuses that scale with level.

22 pet types across 5 rarities. Time-based leveling. A mood system. Pet storage. A full advancement tree.

## Requirements

- Paper `1.21+`
- Java `21+`
- Soft dependency: FishRework (optional — eggs can drop in FishRework treasure chests if installed)

## Install

1. Build or download `BabyPets-<version>.jar`.
2. Put it into `plugins/`.
3. Start the server once to generate configs.
4. Configure `plugins/BabyPets/config.yml` and `pets.yml`.
5. Restart or run `/pets reload` for config-only changes.

## Pet Incubator

Craft a Pet Incubator (recipe discoverable in-game), place it, and right-click it with a pet egg to begin incubation. Default duration is 20 minutes. The incubator is built from display entities — it renders as a custom furniture piece. Breaking it mid-incubation drops the egg and the incubator item.

Recipe: Copper Block / Lightning Rod / Copper Block on top, Iron Ingot / Glass / Iron Ingot in the middle, Iron Block / Golden Dandelion / Iron Block on the bottom.

## Leveling

Pets gain XP passively while active — default every 60 seconds. Cap is level 10. XP curve is configurable. Every level increases the pet's player attribute bonus (or storage slots for storage pets).

## Mood

Pets have 5 mood states: Ecstatic → Happy → Content → Hungry → Sad. Mood drifts down over time. Feed your pet or right-click it to improve mood.

Food is grouped by movement type:
- Ground pets: wheat, carrot, apple, bread
- Flying pets: seeds (wheat, melon, pumpkin, beetroot, torchflower, pitcher pod)
- Water pets: cod, salmon, tropical fish, kelp

## Pet Storage

Storage pets (Horse, Donkey, Mule, Llama, Camel, Trader Llama) open a personal inventory GUI instead of providing a stat bonus. Available slot count scales with pet level. Slots are shared per storage group, not per individual pet.

## Main Command

- `/pets`

Permissions:

- `pets.use` — default: true
- `pets.admin` — default: op

## Configuration

Two main files:

- `config.yml` — incubation duration, leveling curve, XP interval, follow/teleport distances, food lists, messages, loot injection settings, ability toggle
- `pets.yml` — full pet roster (entity type, rarity, description, player attribute, storage config)

Individual systems (advancements, abilities, loot injection) can be toggled off independently.

## Compatibility

- No hard dependencies outside Paper.
- FishRework soft dependency: if FishRework is installed, pet eggs can appear in its treasure chest loot pool.
- SQLite database is managed automatically on startup.

## Notes

- Paper plugin, not a client mod.
- Pets use display entities for hover names. They're fully server-side.
- Pet state (level, XP, mood, name, follow mode) persists across restarts via SQLite.
