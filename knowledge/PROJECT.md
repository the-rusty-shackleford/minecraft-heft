---
title: Heft — project
type: overview
layer: store
tags: [overview]
---

# Heft

## What this is

Every flat item drawn as a solid one. From nfx's handoff of 2026-10-25
(`knowledge/sources/3D-ITEM-REPLACER-HANDOFF.md`). 0.1.0 drew house-made shapes and 128x textures (D-0001 to
D-0004, superseded); since 0.2.0 every item is made solid from the art the player's packs already give it (D-0009).
Chunkworks, AGPL-3.0-or-later, `com.chunkworks.heft`. Client-side: on a server the jar only adds item tags.

- **The plan.** `~/.claude/plans/sparkling-drifting-metcalfe.md`, approved by Rusty 2026-10-25 (0.1.0's).
- **How it draws** (D-0009 to D-0011): `LiveModel` made at `ModifyBakingResult` from each flat model's own sprites,
  raised by a `domain/Relief` chosen by the item's class (`assets/heft/classes.json`) or extruded, merged by
  `domain/Patches`, three levels of detail by distance; flat in slots and frames; made at draw time for a flat model
  another mod chooses (D-0007); D-0008's items drawn over a pack's own.
- **Set It Down's tags** (D-0006): `devtools/tags.py` from the class table.

## Status: 0.2.0 built and gated 2026-10-28, unreleased (release on Rusty's go)

The live rebuild (D-0009, D-0010, D-0011), from Rusty's "take the beautiful art ... and just 3D-ify them" on 0.1.0 in
his own game (2026-10-27): every flat item made solid on the client from the sprite the game draws it with (the pack's,
at its resolution, its LabPBR maps with it), raised by its class's relief or extruded; vanilla's sizes; flat in every slot
and frame; D-0008's seven drawn over Fresh Food. No hand shapes (Rusty kept none), no sheets, no atlas of Heft's: the
paint pipeline, the shape files, the generator and its art tests are gone; `assets/heft/classes.json` is the class table
(263 items), `devtools/tags.py` writes Set It Down's tags from it (byte for byte the 0.1.0 tags; the build checks them).

- **The stutter** Rusty saw in the showcase (D-0011): measured with `-Dheft.perf` (`client/Perf`), fixed by
  `domain/Patches` and three levels of detail by distance: facing the gallery 33.7 to 56.7 fps, facing the lineup 13.2
  to 42.9. Probe photos old against new in `run/review-perf/` (Pixlli in them: never published).
- **Fixed on the way**: a carrot or sweet berries (a classed item that plants a crop) was drawn crossed as a plant.
- **Gate 2026-10-28**: 27 JUnit tests (Relief 16, Patches 11), the tag check, 2 GameTests, the software booth's 16
  checks (raised or extruded by class; whole in every view in the world; flat in slot and frame; the far detail; the
  quad budget; D-0008; Set It Down's displays and poses; meat by size); the probe (a copy of Rusty's instance, the 4070)
  passes the same 16: 457 raised, 902 extruded, 88 crossed; 155 classed items drawn raised there average 332 quads up
  close.
- The wiki page and its three pictures (software booth, no Pixlli) are 0.2.0's.
- **Not yet seen by Rusty**: 0.2.0 after the stutter fix in his showcase (the instance carries the dev jar); the far
  level of detail item by item; the carrot fix.
- **Maybe** (Rusty's earlier nit): coal, raw iron and the shell from the side still a little pillowy.
- **The showcase** is the Prism instance "Chunkworks Showcase" (`chunkworks-showcase`, world "Heft Showcase"), dev jar
  `heft-0.2.0-dev.jar`. The prototype's history (inflate, extrude and mixed modes; sizing, dropped) is in D-0009 and
  D-0010.

## Status: 0.1.0 released 2026-10-27 in pack 1.94.0

nfx approved the review set as is. Released on Rusty's go with Set It Down 1.2.0: public at
`the-rusty-shackleford/minecraft-heft`, tag `v0.1.0` (`4e1c600`, adding the wiki page), jar `11f1e4a4` and
`wiki.zip` on the GitHub release, on both sides of the pack. Gate: 29 JUnit tests, 972 art tests, 2 GameTests,
19 booth checks. The server repo's `knowledge/releases/pack-1.94.0.md` is the deployment record. Not yet seen:
anyone playing with it on the box.

## Before release: phases 0 to 6 built and probed in a copy of Rusty's instance 2026-10-26

**The probe** (D-0007): Heft in a copy of Rusty's own instance (`run/probe`, `./gradlew runProbeBooth`;
copy his mods, resource packs, shaders, config and options there by hand, leaving out Old Cannons, a Fabric
jar the dev launcher refuses). 493 solid, 863 extruded, 88 crossed, 19 flat; block atlas unchanged at
8192x8192; all 17 checks pass. It found Modefite running Fresh Food's and Refined Tools' item definitions
and VanillaBackport drawing spawn eggs itself; Heft now gives depth to whatever would be drawn flat and
never overrides another mod's choice. The dev launcher sometimes crashes in this pack (Controlify's
mixin, then Sodium's config); a rerun loads.

Rusty's answers, applied 2026-10-26: Fresh Food keeps its own 3D; meat by its size, small cuts sharing a
spot and whole joints one to a spot (D-0006, with Set It Down 1.2.0's single tag, its D-0009; **Heft ships with
Set It Down 1.2.0**); the bone a knobbed class of its own; coal lifted and brass deepened (D-0003).

**Review for nfx** (2026-10-26): `run/review/` (review only, never published: Pixlli is in them), from the probe
on the 4070: `0-all-changes.png` and one close-up a pair (the bone by the stick, coal by charcoal, brass by gold, the
brass nugget and the coal shard; chops and bacon piled, cod slices piled, chicken cuts heaped; beef, chicken, mutton
and cod one each), the probe's overview shots beside them. The booth's review row (z 13) makes them; every run
clears the probe's save now (`prepareProbe`), since last run's displays stood where this run sets the bench.
The probe and the software booth carry Set It Down 1.2.0 from the sibling build (`run/probe-removed/` keeps the
1.1.1 jars they had). Gate 2026-10-26: 964 art tests, 2 GameTests, the booth's 18 checks; the probe's 18 pass.
**Drawn over Fresh Food** (D-0008, Rusty 2026-10-26: "anything that doesn't look like what it's supposed to"): of
Fresh Food's 43 models, judged by eye, mutton (raw, cooked), the pork chops, bread and the potatoes (raw, baked) are
Heft's now: a new leg class, a new chop class, the tuber made two rounded lumps, the loaf kept. Set It Down 1.2.0's
D-0010 lays Fresh Food's fish on their sides. Probe 2026-10-26 20:02: all seven swapped, Heft's in every view, 19 of
19 checks; review set rebuilt (`run/review/00-all-changes.png`, 01 to 11).
Before D-0008: every vanilla meat and fish in the close-ups was Fresh Food's model (run by Modefite; Heft leaves it, D-0007), not
Heft's: the cod propped on its fin, the wood-looking pork chop and the cooked mutton Rusty called "too much of a blob"
(its slices not distinct) are Fresh Food's. The report's "solid" section lists items Heft has a shape for, not who
draws them in a given pack.

**Phase 6** (D-0006): Set It Down's size and stacking tags from the classes; the jar goes on the server too.

## Phase 5 (plants, spawn eggs, odds and ends) built 2026-10-26

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

- `./gradlew clean build`: JUnit, the tag check (`tagsCheck`, under uv), the gametest server, the booth
  (`runPhotoBooth`, needs display `:7` and the software variables; `-PskipBooth` to leave it out). Fill the booth
  first: `uv run --no-project python devtools/booth/fetch.py` (and `--gpu` for `runGpuBooth`).
- The probe, Heft in a copy of Rusty's instance on the 4070: `DISPLAY=:0 tools/booth/run_iconified.sh <repo> run/probe
  runProbeBooth` (`-PheftShots=<prefix>` for some photos only, `none` for the checks alone; `-PheftTolerance`,
  `-PheftDetail` to compare).
- After changing `classes.json`: `uv run --no-project python devtools/tags.py`.
- After every model bake the client logs two `Heft:` lines and writes `logs/heft-report.txt` and `logs/heft-live.txt`;
  `-Dheft.perf=60` measures what drawing costs (README, Diagnosing).
