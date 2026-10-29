# Heft

Every flat item becomes a solid one, made from the art your resource packs already give it. In the hand, on the
ground and set down with Set It Down, each item is raised by what it is: a fruit round, a loaf domed on a flat
bottom, a cookie a thin disc, a steak a slab, an ingot a bevelled bar. Everything else flat is given depth. Slots
and item frames keep the flat icon, and every item keeps vanilla's size.

NeoForge 1.21.1, client side. On a server the jar only adds item tags, and a player without it can still join.

## How it works

- **From the sprite the game draws.** As models bake, every flat item model (`item/generated`, `item/handheld`,
  any mod's) is made solid from its own quads' sprites, on the game's block atlas: the resource pack's sprite where
  one replaces it, at its resolution, with its LabPBR maps. Nothing is repainted, and no texture is shipped
  (D-0009). Only a model wholly inside the game's flat slab is touched: a pack's own 3D model is drawn as the pack
  made it.
- **What each item is.** `assets/heft/classes.json` gives about 260 items a class (ingot, fruit, loaf, steak, dust,
  gem, ...); every spawn egg is of class `spawn_egg`. The class picks the relief; a food with no class goes by a
  word of its name (catfish a fish, sweet roll a loaf), else a modest rounded one; anything else with no class is
  extruded.
- **Raised** (`domain/Relief`, D-0010): a grid of 32 cells over the sprite, each cell raised by its distance from the
  silhouette's edge on the class's section, front and back each its own (a loaf's back flat, so it lies flat when set
  down), the sprite laid over both, a thin rim between. **Extruded**: the game's own flat model made two sixteenths
  thick; a bow's draw and other overrides still work.
- **Cheap to draw** (`domain/Patches`, D-0011): each side drawn in as few quads as keep it within 0.1 sixteenth of
  its heights, with no cracks; past 6 blocks from the camera on a 16-cell grid, past 12 on an 8-cell one. A raised
  item takes about 460 quads up close.
- **Where it is solid.** Held in either hand (first and third person), dropped, set down, worn on the head. In every
  GUI slot and in an item frame the flat model is drawn, as the pack draws it.
- **Plants** (a flower, sapling, grass or coral of any mod with no class) are drawn as two crossed planes, as they
  grow when placed.
- **Other mods' choices are kept** (D-0007). A model another mod chooses at draw time (a 1.21.4 item definition
  through Modefite: Fresh Food, Refined Tools) is left as it is if it is 3D; if it is flat, it is made solid then, by
  the same relief. A mod that draws an item with code of its own is left to it.
- **Except where a pack's model does not read as the thing** (D-0008). For the items `classes.json` lists under
  `over_packs` (mutton raw and cooked, the pork chops, bread, the potatoes raw and baked), Heft raises the item's
  own flat sprite in place of another mod's *plain* model of it (every quad on `item/<id>` or `item/<id>_3d`), over
  Fresh Food's. A pack's eating frames and plated variants stay its own.
- **Set It Down** (D-0006). The jar carries Set It Down's size and stacking tags for the classed items, written by
  `devtools/tags.py` from `classes.json`: ingots, chops and books pile, raw ore, dusts and gems heap, a whole joint of
  meat is one to a spot. Set It Down lays a raised item face up on its flat back.
- **Armour** is left as your resource packs draw it.

## Settings

The mod list's config button (or `config/heft-client.toml`):

- `enabled`: draw flat items as solid ones;
- `flatClasses`: classes left flat (`ingot`, `spawn_egg`, ...);
- `denylist`: items left flat, by id (`minecraft:filled_map`) or a whole mod (`somemod:*`).

They take effect on the next resource reload (F3+T).

## Diagnosing

- After every resource reload the log has `Heft: N items made solid from their own sprites, N other mods' spawn egg
  models, N drawn over a pack's own (D-0008), in N ms` and `Heft: N raised, N extruded, N crossed, N flat, N 3D, N
  custom, N empty`. `logs/heft-report.txt` lists every item by how it is drawn, per mod; `logs/heft-live.txt` lists
  every item Heft made solid with its class, relief, sprite, sprite width and quads near/mid/far.
- `/heft inspect` (client command): for the item in your main hand, how it is drawn, its class and relief, the sprite
  it is made from, its quads at each level of detail, what Heft did in each view it was last drawn in (its own
  model, made solid at draw time, a pack's 3D left, custom code, flat), and its Set It Down tags.
- `-Dheft.perf=<seconds>` on the game's command line measures what drawing costs: after a 20-second warm-up in a
  world, `logs/heft-perf.txt` gets frames a second, frame times (mean, p50, p95, p99, max), Heft's quads and draws a
  frame and the collector's pauses, every five seconds and in all; then the game quits (`-Dheft.perfStay=true` keeps
  it open, `-Dheft.perfAt=x,y,z,yaw,pitch` measures from there). `-Dheft.tolerance` and `-Dheft.detail=6,12` change
  the merge and the distances, for comparison.

## Building

`./gradlew clean build` runs the JUnit tests, the check that Set It Down's tags match `classes.json` (under uv), the
gametest server and the booth (display `:7`). After changing `classes.json`, run
`uv run --no-project python devtools/tags.py`. `knowledge/PROJECT.md` has the details.

## Licence

Copyright (C) 2026 Chunkworks. AGPL-3.0-or-later; see `LICENSE`.
