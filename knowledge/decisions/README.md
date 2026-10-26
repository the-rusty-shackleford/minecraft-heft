---
title: Heft — decisions log
type: index
layer: store
tags: [index]
---

# Heft — decisions log

**Append-only.** Never edit an entry's rationale; supersede via a new entry with
`supersedes: D-NNNN`. Every entry has a status: `Active` / `Superseded` / `Rejected`.

| Id | Topic |
|----|-------|
| D-0001 | Only our own art is drawn: a clean room against Pixlli (measured numbers cross, its pixels never do), no model face mapped onto a resource pack's sprite, the long tail repainted on the client from each mod's own sprite |
| D-0002 | Every solid item's sheet is on Heft's own atlas, registered with the model manager (stitched with mipmaps, sprites there at bake), drawn by a render type of its own; one model for every view, put in place at ModifyBakingResult |
| D-0003 | The class canon: one function per class, the same elements for every item of it; centred on the block; the `fixed` turn is the rest pose, since Set It Down lays an item's own model down by it (the handoff's §4.1-4.2 described the hand-model path, not this one) |
| D-0004 | The long tail (Tier B) is the game's own flat model thickened to two sixteenths, drawn from each sprite repainted at 64x (Scale2x, our grain, flat maps) on the client from its own mod's jar; overrides kept; translucent as a flat item |
| D-0005 | Plants drawn crossed at runtime (bush and coral block items); every spawn egg one kind tinted 0 and 1; dyes, sherds, patterns reuse classes; record, bucket, orb, star, shell, rocket, horn, totem, saddle; armour left to the packs |
