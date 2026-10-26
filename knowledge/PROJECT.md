---
title: Heft — project
type: overview
layer: store
tags: [overview]
---

# Heft

## What this is

Every flat item drawn as a solid one. From nfx's handoff of 2026-10-25
(`knowledge/sources/3D-ITEM-REPLACER-HANDOFF.md`), as Rusty amended it the same day: 128x and
LabPBR where possible, and no Pixlli asset used. Every texture is house-made (D-0001). Chunkworks,
AGPL-3.0-or-later, `com.chunkworks.heft`. Client-side: on a server the jar only adds item tags.

- **The plan.** `~/.claude/plans/sparkling-drifting-metcalfe.md`, approved by Rusty 2026-10-25, with
  his answers: numbers-only clean room; only our art drawn; leave 3D Default and Refined Tools' items;
  ship in the Mod Hub pack on his go; the handoff's §8 recommendations adopted.
- **How it draws** (D-0002): sheets on Heft's own atlas, one `SolidModel` per item in every view, put
  in place at `ModifyBakingResult` from the shape files `devtools/art/build.py` writes.
- **The canon** (D-0003): one function per class, the same elements for every item of it; the
  `fixed` turn is the rest pose Set It Down uses.
- **Shared tools**: `minecraft mods/tools/pbr/` (measure, LabPBR encoding and range checks, noise),
  lifted from Survivalist Armor's art pipeline.

## Status: phase 5 (plants, spawn eggs, odds and ends) built 2026-10-26, unreleased, no remote

**Phase 5** (D-0005): plants crossed and spawn eggs as one tinted kind, at runtime by what an item is; 97
more items by hand (dyes, discs, buckets, sherds, patterns, orbs, star, shell, rocket, horn, totem,
saddle). In the booth's game: 296 solid, 208 extruded, 63 crossed, 1 flat. Booth fix: NeoForge's early
loading window is off (`earlyWindowControl`), its handoff failed on Xwayland and hung the run on a dialog.

## Phase 4 (food) built 2026-10-25

**Phase 4**: 82 food items in 16 classes (D-0003), vanilla's and Farmer's Delight's; 167 solid items in all.
Heft's atlas in the booth's game: 4096x2048 (the solid sheets and the long tail together).

## Phase 3 (rods, plates, books, signs, arrows) built 2026-10-25

**Phase 3**: six classes over 44 items (D-0003): rod (stick, blaze and breeze rods, bone), plate (paper,
map, leather, rabbit and kangaroo hide, membrane), book (five), sign and hanging sign (13 woods each,
Backport's pale oak and poplar among them), arrow (plain, spectral, tipped: the head takes the potion's
tint). The painter gained parts (a book's pages, a sign's chains, an arrow's head and fletching each
their own material) and grain (wood, a stack of pages). 85 solid items in all; the booth's game draws
80 solid and 474 extruded. Left in the long tail on purpose: filled maps (their markings are a tinted
layer), Farmer's Delight's canvas signs, string, lead. Open for Rusty: the bone is a rod (the
handoff's canon) and reads as a pale stick; a knobbed bone would be a class of its own.
- Booth: the camera is held on the client each shot. The server's flying flag did not hold it: the
  player fell from every camera position to the ground (the shake Rusty saw in the booth window, and
  every shot taken from the ground). Settle 60 ticks, for chunk meshes.

## Phase 2 (the long tail, Tier B, D-0004) built 2026-10-25

**Phase 2**: every other flat item extruded from its own sprite repainted at 64x (Rusty's call). In the
booth's game: 513 items extruded, 1 left flat (`sculk_vein`, animated), 470 sprites repainted in about
1.2 s, Heft's atlas 2048x2048. Both booths pass 15 checks, among them: the samples (stick, apple, bread,
bow, tipped arrow, dyed helmet, egg, feather, string, paper) extruded and drawn whole on Heft's atlas,
translucent; a bow's overrides kept; a sheet whose sprite Pixlli replaces is painted from the game's
own. Not yet run in Rusty's full instance (his ~1,400 flat items).
- Booth fixes: `pauseOnLostFocus:false` (the pause menu was in the GPU photos when the window lost
  focus) and `enableVsync:false` (a GPU window under others drew about a frame a second).

## Phase 1 (metals and minerals) built 2026-10-25

**Phase 1**: eight classes (ingot, nugget, bar, raw, lump, gem, shard, dust) over 41 items, vanilla and
every mod in the pack whose materials these are (Create's brass and zinc, steel, voidsteel, galestone,
rose quartz, Subterranean Wilds' coal shard). Both booths pass 12 checks over the 40 present in the
booth's game (Create, galestone, voidsteel and Metals and Materials added to it): every one drawn
whole on Heft's atlas in every view, in all 42 frames and in Set It Down's displays; 0 canon breaches as
the game reads the shapes. Photos: `run/gpubooth/screenshots/` (Complementary, Pixlli beside).
- Fixed on the way: the game keeps an element turn's origin in blocks, so `ShapeLibrary` read it 16
  times too small and the report showed false breaches (the booth now checks the canon as the game
  reads it); the left-hand displays were mirrored twice (the game mirrors them itself); a selected
  hotbar slot set on the server never reaches the client (the booth puts what it holds in slot 0).
- Not yet seen: the mod items in the real pack with Rusty's packs; anything by Rusty or nfx.

## Phase 0 (skeleton and spikes) done 2026-10-25

Built: the domain layer (`Box`, `Turn`, `Element`, `Shape`, `Canon`, 20 JUnit tests); the client
(`HeftAtlas`, `ShapeLibrary`, `SolidModel`, `HeftClient`, `Report`, `/heft inspect`, `HeftConfig` on
NeoForge's own config screen rather than YACL, which needs no dependency); the generator for one
class (the ingot, on iron, a placeholder look); the art tests (canon, LabPBR ranges, the clean-room
wall); one gametest (loads on a dedicated server); the booth (`HeftBooth`, 21 checks;
`devtools/booth/fetch.py`; the loud twin `devtools/booth/loud.py`).

The spikes, answered:
- **(a) every view draws our render type: yes.** Both booths: in every display context, in an item
  frame and a glow frame, and in Set It Down's displays (one, and a pile of four joined), the ingot is
  drawn whole on Heft's atlas and by nothing else; Set It Down measures it as the item's own model,
  posed by its `fixed` turn (10 x 5 x 3 at rest, 3 out of the surface).
- **(b) Iris reads Heft's LabPBR maps: yes.** On the 4070 under Complementary with Pixlli, the loud twin
  (the iron sheet's colour, ridged `_n`) shows its ridges in a frame
  (`run/gpubooth/screenshots/booth-frames.png`).
- **(c) Tier B's size** (a static count over Rusty's instance): about 1,367 item models still resolve
  to a flat sprite (vanilla's count includes variant models), 1,467 layers: 24 Mpx at 128x (an
  8192x4096 atlas, about 0.5 GiB with Iris's two copies), 6 Mpx at 64x. Recommended to Rusty: 64x for
  Tier B. His call before phase 2.
- **Found by the booth:** sheets under `textures/item/` were stitched onto the block atlas too (the
  game's `blocks.json` lists a `directory` source over `item` in every namespace). They live under
  `textures/solid/` (D-0002).

Seen in the photos, for phase 1: the ingot reads as a stepped plate; held in first person it is far
too big; the GUI icon wants a close look at 3x; the wall shot's camera missed the wall.

## Phases

0 skeleton and spikes; 1 metals and minerals across vanilla and every mod; 2 Tier B and the denylist;
3 paper-likes, rods, books, signs; 4 food; 5 plants, odds and ends, armour's held models; 6 the tag
sweep (Set It Down's size and stacking tags against the final shapes). Each phase ends with photos to
Rusty; nfx reviews before any release.

## Building

- `./gradlew clean build`: JUnit, the art tests (`artTests`, under uv), the gametest server, the booth
  (`runPhotoBooth`, needs display `:7`; `-PskipBooth` to leave it out). Fill the booth first:
  `uv run --no-project python devtools/booth/fetch.py` (and `--gpu` for `runGpuBooth`).
- `uv run --no-project --with numpy --with pillow python devtools/art/build.py` writes every shape,
  sheet and map; `devtools/art/measure/run.sh` re-measures the numbers.
- After every model bake the client logs one `Heft:` line (solid, flat, 3D, custom, empty; breaches)
  and writes `logs/heft-report.txt`: the progress bar.
