# Beastmastery: first pass (PROF-6)

A draft for review: tames that grow with you, pet armor you earn, and one companion that comes back when it dies.
The review answers are at the end.

## Built so far (2026-11-03)

- **Built:** taming, breeding and tame-hit XP; the tree and every node's effect; barding for wolves and the horse family;
  collars; the companion, with bonding, saving on death, calling back and fetching; Ice and Fire's tames in the tags;
  and the following-tames list for Dungeon Delving (DD-118). Code in the `beast` package, tests in `BeastmasteryTests`.
- **Not yet:**
  - Call Companion as an Iron's Spells spell: for now the Bonding Charm does the calling.
  - The paw icon by a companion's nameplate, and listing it on the character screen.
  - Real art: barding, collars and the charm use vanilla textures, and collars don't show on the tame.
  - Ice and Fire isn't in the dev pack, so its tames are untested.

## Goals

- **Fighting alongside animals is a playstyle.** A wolf pack or a warhorse should keep up with the gear a player has,
  not fall behind after the first dungeon tier.
- **Earned by doing.** The skill levels from taming and from your tames fighting, like every other skill.
- **One animal that matters.** Your companion survives death, so investing in it (a name, armor, a long history) is
  never wasted.

## What counts as a tame

- **Counts:** any mob a player owns that's listed in the entity tag `ddprofessions:beastmastery_pets`, so the pack can tune
  the list. Default: wolves, cats, parrots, the horse family (horses, donkeys, mules, llamas, camels), and Ice and Fire's
  dragons, hippogryphs and amphitheres when it's installed.
- **Ice and Fire's mounts count fully.** They're far stronger than a wolf already, so a dragon is the end of the climb:
  the tree's bonuses are percentages, and they wear Ice and Fire's own armor rather than our barding.
- **Not counted:** summons from Iron's Spells and Ars Nouveau. They're temporary and belong to the magic schools.

## Gaining XP

- **Taming:** scaled by the mob, from entity tags (`ddprofessions:taming_xp/small`, `mount`, `great` and `dragon`) so the
  pack can move a mob between tiers. Tames that skip vanilla's taming (Ice and Fire's hatched dragons) pay out the first
  time their owner is near them.

| Tame | XP |
|---|---|
| Parrot, cat | 30 |
| Wolf | 40 |
| Horse, donkey, mule, llama, camel | 60 |
| Hippogryph, amphithere | 150 |
| Dragon | 400 |
| Anything else in the pet tag | 40 |
- **Your tames doing damage:** each hit a tame lands trains Beastmastery for its owner, using the same rules as the
  combat styles: XP from the damage dealt, at most 20 a hit, scaled down against mobs well below the skill's level
  (PROF-3), and nothing from armor stands. It also counts only while the owner is within 32 blocks, so a pen of
  wolves can't train you while you're away.
- **Breeding:** 10 XP, as now. A small extra source for players who keep horses.

## The tree

Uses the shared five-row shape from [skill-trees.md](skill-trees.md). Bonuses apply to every tame you own unless a node
says companion.

| Row | Level | Nodes |
|---|---|---|
| Root | 0 | **Animal Friend:** tames get +10% max health. |
| Choices | 5 | **Pack Leader** (tames +10% damage) / **Thick Hide** (tames +2 armor) |
| Signature | 10, 20, 35 | **Companion Bond I–III**: designate a companion and recall it (below). Each rank shortens the recall cooldown and raises the health it returns with. |
| Refinements | 25 | **Mender** (tames regenerate out of combat) / **Rally** (when you're hit, nearby tames get Speed and Strength for 5 s) |
| Capstone | 50 | **Alpha:** your companion gets every tame bonus twice. |

- **Outer columns:** tame max health on one side and tame damage on the other, five ranks each at levels 2, 8, 15, 30
  and 45.
- **Mastery:** +tame stats, cap +25%.
- **How the bonuses reach the tame:** as attribute modifiers on the tame itself, worked out from its owner's tree and
  refreshed when the tame loads or the owner levels. They also stay on while the owner is offline, so a horse left in a
  stable keeps its bonuses.

## Pet armor

- **What gets armor:** wolves and the horse family. Vanilla has wolf armor (armadillo scutes) and four horse armors.
  Ours add starred barding made at the guild workshops from the same parts as player armor:

| Barding | Made from | Profession |
|---|---|---|
| Leather barding | Leather Panels and Straps | Leatherworking |
| Mail barding | Chain Links over Leather Panels | Forging |
| Plate barding | Armor Plates over Mail | Forging |

- **Its ★ comes from its parts,** like forged gear, and scales its protection the same way.
- **Beastmastery sets the best ★ you can make,** the same way Forging level caps ingot ★:

| Beastmastery level | Best barding ★ |
|---|---|
| 0 | ★2 |
| 10 | ★3 |
| 20 | ★4 |
| 30 | ★5 |
| 40 | ★6 |
| 50 | ★7 |

- Crafting with better parts than your cap makes the barding at your cap, the same way the crafting skills cap other
  gear. Putting it on your tame isn't restricted, so traded barding still works.
- **The crafting cap is the only gate for now.** Revisit after playtesting: if it feels too free, higher barding can
  also need a tree node.

### Collars for cats and parrots

- **A starred Collar** from Leatherworking straps and Goldsmithing fittings. Right-click a cat or parrot you own to put
  it on, and sneak-right-click with an empty hand to take it off.
- **What it gives:** +1 max health per ★, and +1 armor at ★4 and up. Parrots keep it on while riding your shoulder.
- **Same ★ cap** as barding, from Beastmastery level.
- **Looks:** none yet. Collars need art, then a coloured band on cats.

## Your companion

- **Designate:** right-click one of your tames with a **Bonding Charm**, a cheap crafted item: a lead, a gold nugget
  and a bone. You can only have one companion. Bonding a new one releases the old one, which stays your tame.
  Designating needs Companion Bond I (level 10).
- **It's marked (not built yet):** a small paw icon by its nameplate, and it's listed on the character screen (J).
- **When it dies:** we save it whole: its name, its armor and saddle, its attributes, its colour and variant. Its armor
  and saddle don't drop; they come back with it. A pack animal's chest contents do drop as normal, and are left out of
  the save so nothing duplicates.
- **Recalling it:** cast **Call Companion** to bring it back at your side. Without costing stats means it returns
  exactly as it was, not as a fresh wild animal.

| Rank | Cooldown | Returns with |
|---|---|---|
| Companion Bond I | 10 min | Half health |
| Companion Bond II | 5 min | Three-quarters health |
| Companion Bond III | 2 min | Full health |

- **The spell:** for now, hold right-click with the Bonding Charm for 3 s. Still to come: an Iron's Spells spell in the
  Nature school, so it uses mana and sits on the spell bar, with the charm kept for when Iron's Spells isn't installed.
- **Other tames that die stay dead,** as in vanilla. The companion is the one you don't lose.
- **It also fetches a living companion.** If it's alive, Call Companion teleports it to you, on the same cooldown. We
  keep its last known position and dimension, so it can be fetched from an unloaded chunk or another dimension: the
  chunk loads just long enough to move it.

## Tames in dungeons

- **All your tames can follow you in.** When you enter a Dungeon Delving dungeon, your tames that are following you
  (not sitting) and within 16 blocks go in with you, and come back out with you the same way.
- **Death works as everywhere else:** a tame that dies in a dungeon stays dead, and your companion can be recalled.
- **This is Dungeon Delving's work (DD-118):** its dungeon entry and exit have to carry the tames. This mod only provides the list
  of what's following you.

## How it's built

- **XP:** the tame-damage hook goes into `ActivitySkills.onDamage`, next to the combat styles: the damage source's
  entity is a tame in the pet tag, and its owner gets the XP through `awardAgainst`.
- **Tame bonuses:** a new `Beastmastery` class applies the attribute modifiers on `EntityJoinLevelEvent` and when the
  owner's tree changes. The owner's bonus totals are stored on the tame in a data attachment, so they work while the
  owner is offline.
- **Barding and collars:** new starred items and recipes, with the ★ cap checked in the starred recipes against
  Beastmastery level. Collars are stored on the cat or parrot in a data attachment.
- **Taming XP:** `Beastmastery.tamingXp` reads the `taming_xp` tags instead of the flat 40.
- **Ice and Fire:** optional entries in the pet and taming tags; no code needed.
- **Wolves:** vanilla only counts its own wolf armor, so a mixin (`WolfMixin`) lets barding count too: it takes the
  wolf's hits, shows on the wolf, and can be sheared off and repaired.
- **The companion:** kept in the world's saved data by owner, so one that dies while its owner is offline is saved too.
- **Dungeons:** a public method listing a player's following tames, for Dungeon Delving to call.
- **Tests:** game tests for tame-damage XP, scaled taming XP, bonuses on a tame, the companion surviving death and
  returning with its armor, fetching a living companion, collars, and the barding cap.

## Decided (2026-11-03)

1. Ice and Fire's mounts count fully.
2. Taming XP scales with the mob.
3. The crafting cap gates higher barding for now; revisit after playtesting.
4. Cats and parrots get collars.
5. Call Companion also fetches a living companion.
6. All following tames can go into dungeons.
