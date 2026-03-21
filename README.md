# EcoLibrarianFinder

A Fabric 1.21.x client-side mod that automatically rerolls librarian villager trades
to find **EcoEnchants** custom enchantments — like Librarian Trade Finder, but built
specifically to read the `ecoenchants:` namespace from server-side trade NBT.

---

## Why this mod exists

Existing mods like [Librarian Trade Finder](https://modrinth.com/mod/librarian-trade-finder)
and [LibrGetter](https://modrinth.com/mod/libr-getter) only scan for enchantments that are
registered on the **client**. EcoEnchants is a server-side Paper plugin — its hundreds of
custom enchantments are **not** in the vanilla client registry, so those mods silently skip
every EcoEnchants book.

EcoLibrarianFinder solves this with a three-layer parser:

1. **Component scan** — checks the `stored_enchantments` component for `ecoenchants:*` IDs  
   (works on servers that sync the custom registry).
2. **Custom NBT scan** — checks `CustomData` compound for `StoredEnchantments` lists with
   namespaced IDs (EcoEnchants' legacy/fallback storage).
3. **String fallback** — serialises the entire item NBT to a string and searches for the
   `ecoenchants:<id>` substring (catches any other storage format).

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 1.21.x (tested on 1.21.4) |
| Fabric Loader | ≥ 0.16.0 |
| Fabric API | any matching 1.21.x build |
| Server plugin | EcoEnchants (Paper) |

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) and
   [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.x.
2. Drop `ecolibrarianfinder-1.0.0.jar` into your `.minecraft/mods/` folder.
3. Launch the game and join your EcoEnchants server.

---

## Building

```bash
git clone <this repo>
cd ecolibrarianfinder
./gradlew build
# Output: build/libs/ecolibrarianfinder-1.0.0.jar
```

---

## Usage

### Step 1 — Set up your villager

1. Trap a **librarian villager** in a 1×1 enclosure.
2. Place a **lectern** directly in front of it.
3. Make sure the villager is **untouched** (hasn't traded yet — trading locks their profession).

### Step 2 — Load your inventory

- **Offhand or hotbar**: a stack of **Lecterns** (more = longer unattended run)
- **Main hand**: an **axe** (faster = more rerolls per minute)

### Step 3 — Select targets

Look at the lectern and press **I** (default) or run:
```
/ecofind select
```
The mod will auto-find the nearest librarian.

### Step 4 — Set your goals

```
/ecofind add <enchant_id> <level> [maxPrice]
```

Examples:
```
/ecofind add abrasion 3 32
/ecofind add ecoenchants:fiery_arrows 1 20
/ecofind add trickster 2
```

- `enchant_id` — the EcoEnchants ID. You can use just `abrasion` or the full `ecoenchants:abrasion`.
- `level` — the **minimum** level you'll accept.
- `maxPrice` — maximum emerald cost (default: 64).

List your goals:
```
/ecofind list
```

### Step 5 — Start

Press **O** (default) or:
```
/ecofind start
```

The mod will:
- Open the villager trade screen
- Read the trade list
- Close the screen
- If no match → break the lectern, place a new one, repeat
- If match → **stop and alert you** (chat message + level-up sound)

### Step 6 — Stop

Press **O** again, or:
```
/ecofind stop
```

---

## Commands

| Command | Description |
|---|---|
| `/ecofind select` | Select the lectern you're looking at + nearest librarian |
| `/ecofind start` | Start the search loop |
| `/ecofind stop` | Stop the search loop |
| `/ecofind add <id> <lvl> [price]` | Add an enchantment goal |
| `/ecofind remove <id> <lvl>` | Remove a goal |
| `/ecofind list` | List current goals |
| `/ecofind clear` | Clear all goals |
| `/ecofind status` | Show current state and attempt count |

---

## Keybindings

| Keybind | Default | Action |
|---|---|---|
| Start/Stop Search | `O` | Toggle the search loop |
| Select Lectern | `I` | Select lectern + villager |

Rebind via **Options → Controls → EcoLibrarianFinder**.

---

## Configuration

Saved to `.minecraft/config/ecolibrarianfinder.json` automatically.

```json
{
  "goals": [
    { "enchantId": "ecoenchants:abrasion", "level": 3, "maxPrice": 32 }
  ],
  "notifyOnFind": true,
  "stopOnFind": true
}
```

---

## Finding EcoEnchants Enchantment IDs

The ID is the config file name from the EcoEnchants plugin, lowercased and with spaces
replaced by underscores. E.g.:
- `Abrasion` → `abrasion`
- `Fiery Arrows` → `fiery_arrows`
- `Sand Veil` → `sand_veil`

Ask your server admin for the full list, or check the server's
`plugins/EcoEnchants/enchants/` folder.

You can also run `/ecofind add` with a guess and the fallback parser will catch it even
if you don't know the exact namespace.

---

## Troubleshooting

**"No librarian found nearby"**  
→ The mod looks for villagers with the librarian profession within 8 blocks of the lectern.
Make sure the villager has actually picked up the librarian profession (they need to have
slept and path-found to the lectern at least once).

**Mod stops immediately / no match ever found**  
→ EcoEnchants may be storing enchantments in an unexpected format on your server version.
Run `/data get entity @e[type=villager,limit=1,sort=nearest]` in-game and paste the output
in a bug report so the parser can be updated.

**Lecterns aren't breaking**  
→ Some server anti-cheats block block-break packets when not looking directly at the block.
The mod auto-aims but if your server is very strict, you may need to stand closer.

**Mod found the wrong enchantment**  
→ The fallback string search can theoretically cause false positives if one enchant ID is
a substring of another (e.g. `fire` matching `fiery_arrows`). Use the full ID including
namespace to be precise: `/ecofind add ecoenchants:fire 1`.

---

## License

MIT
