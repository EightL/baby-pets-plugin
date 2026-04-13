# Better Baby Pets

Collect and raise baby animal companions that follow you around and give real, scaling stat bonuses. **22 pets** across **5 rarity tiers**, egg hatching via a custom **Pet Incubator**, time-based leveling, a mood system, pet storage, and a full **advancement tree** — all on a single command (`/pets`).

---

## How it works

Get a pet egg — either from structure chest loot, or via `/pets give` as an admin. Craft a **Pet Incubator** and right-click it with the egg to start incubation. After 20 minutes, your egg hatches into a random pet of that rarity.

Open your collection with `/pets`, click a pet to select it, and it'll spawn at your side. Feed it, pet it, level it up — its stat bonus scales with level up to **level 10**.

>**TIP #1:** Eggs drop into vanilla structure chests automatically (8% base chance, configurable). Rarity of the egg determines the rarity pool the pet is rolled from on hatch.

>**TIP #2:** Right-click your active pet to give it affection, or feed it the right food for its type — ground pets eat wheat/carrots/apples/bread, flying pets eat seeds, water pets eat fish.

---

## Features

### Pets and rarities
- 22 pet types across 5 rarity tiers: Common, Uncommon, Rare, Epic, Legendary
- Every stat-boosting pet provides a unique **player attribute bonus** that scales linearly with level
- Storage pets (Horse, Donkey, Mule, Llama, Camel, Trader Llama) unlock inventory slots that scale with level instead

### Pet leveling
- Time-based XP — pets gain XP passively while active, every 60 seconds
- Scaling XP curve up to level 10
- Attribute bonuses and storage slots increase every level

### Mood and care
- 5 mood states: Ecstatic → Happy → Content → Hungry → Sad
- Mood drifts down over time; recover it by feeding or petting
- Food type depends on the pet's movement category (ground / flying / water)

### Egg and incubation system
- Custom **Pet Incubator** block: crafted, visually composed of display entities, fully persistent
- Particle effects scale with incubation progress — starts subtle, glows near completion
- Broken incubators drop the egg and the incubator item back
- Loot injection into vanilla structure chests (togglable, per-rarity thresholds configurable)

### Pet storage
- Storage pets open a personal inventory GUI that scales slot count with pet level
- Separate storage groups: horse-tier (5 slots), llama (9), camel (14), trader llama (18)

### GUIs
- Pet Collection: all owned pets as spawn egg icons with level, rarity, mood, and stat info. Sortable by level, name, or movement type
- Pet Detail: per-pet stats, rename via name tag, delete with confirmation prompt
- Pet Settings: per-player toggles (follow/stay, hide others' pets, sounds, notifications)
- Pet Storage: inventory GUI for storage-ability pets

### Advancement tree
- 12 advancements tracking the full progression arc — from first incubation to legendary hatch to completing the full collection

### Everything else
- Follow and Stay modes
- Cross-dimension teleport when the player switches worlds
- Respawn on join — active pet re-appears when the player logs back in
- Hover name displayed above the pet entity
- Idle emote sounds
- Per-player toggles: hide other players' pets, pet sounds, chat notifications
- Rename pets with a name tag in-hand while right-clicking
- Soft dependency on FishRework (optional integration)
- SQLite-backed persistence

### Nearly everything is tunable in the YAML configs

---

## Pet Roster

| Rarity | Pets | Bonus |
|--------|------|-------|
| Common | Chicken, Pig, Cow, Sheep, Squid | Fall resistance, Max health, Max absorption, Safe fall distance, Oxygen bonus |
| Uncommon | Bee, Bunny, Horse, Donkey, Mule | Knockback resistance, Jump height, Storage (5 slots) ×3 |
| Rare | Dolphin, Armadillo, Mooshroom, Turtle, Llama | Oxygen bonus, Armor, Gravity reduction, Water movement, Storage (9 slots) |
| Epic | Fox, Ocelot, Polar Bear, Camel | Movement speed, Block break speed, Sweep damage, Storage (14 slots) |
| Legendary | Goat, Panda, Trader Llama | Attack damage, Block reach, Storage (18 slots) |

---

## Requirements

|                   |              |
|-------------------|--------------|
| Server software   | Paper 1.21+  |
| Java              | 21+          |
| Hard dependencies | None         |
| Soft dependencies | FishRework   |

---

## Installation

1. Drop the jar into your `/plugins` folder
2. Start the server once to generate configs
3. Tune `config.yml` and `pets.yml` to your liking

---

## Commands

| Command | What it does |
|---------|--------------|
| `/pets` | Open your pet collection |
| `/pets info` | Show active pet stats |
| `/pets settings` | Open per-player settings GUI |
| `/pets follow` | Set active pet to follow mode |
| `/pets stay` | Set active pet to stay mode |
| `/pets hideothers [on\|off\|toggle]` | Hide other players' pets |
| `/pets sounds [on\|off\|toggle]` | Toggle pet sounds |
| `/pets notifications [on\|off\|toggle]` | Toggle pet chat notifications |
| `/pets select <id>` | Select a pet by DB ID |
| `/pets deselect` | Deselect current pet |
| `/pets help` | Full command list |

**Admin commands** (requires `pets.admin`):

| Command | What it does |
|---------|--------------|
| `/pets give <player> <rarity>` | Give a pet egg of a specific rarity |
| `/pets givepet <pet_type>` | Add a pet directly to a player's collection |
| `/pets setlevel <level>` | Set the active pet's level |
| `/pets incubator` | Give yourself a Pet Incubator item |
| `/pets hatch` | Instantly hatch all incubating eggs |
| `/pets reload` | Reload config without restart |

---

## Configuration

Two main config files:
- `config.yml` — incubation time, leveling curve, XP intervals, follow/teleport distances, mood food lists, messages, loot injection settings
- `pets.yml` — full pet roster definitions: entity type, rarity, description, player attribute, storage size, and more

Individual systems (advancements, abilities, loot injection) can each be toggled independently.

---

> ### Balance disclaimer
>
> This plugin has not been heavily playtested. Leveling speed, mood decay, and attribute values are rough starting points — some bonuses are probably too weak, some too strong.
>
> If something feels off, I'd genuinely like to know.

---

## Feedback and support

Bug reports, balance feedback, and suggestions are all welcome.

Discord: **[discord.gg/axZRQ5Sy](https://discord.gg/d8pZUe5TbP)**
