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
| D-0006 | Set It Down's size and stacking tags written from the classes (data, so the jar goes on the server too); where its own tags say more, they win; meats now pile |
| D-0007 | Never override another mod's chosen model; at draw time (after Modefite decides) a flat model, whoever chose it, is extruded; Fresh Food is alive in this pack and keeps its 3D; Backport's eggs stay its own |
| D-0008 | Heft draws over a pack's own plain model where it does not read as the thing (Fresh Food's mutton, pork chops, bread, potatoes) |
| D-0009 | Every item drawn solid from the sprite the game already draws it with (the pack's, at its resolution); no hand shapes, no sheets, no atlas of Heft's; supersedes D-0001, D-0002, D-0004 |
| D-0010 | A relief per class (a fruit round, a loaf domed on a flat bottom, a cookie a disc), not puffy, at vanilla's sizes; solid in the world, flat in every slot and frame; supersedes D-0003's geometry |
| D-0011 | A raised item merged into as few quads as keep it within 0.1 sixteenth, at three levels of detail by distance: the showcase's stutter (33.7 to 56.7 fps facing the gallery) |
