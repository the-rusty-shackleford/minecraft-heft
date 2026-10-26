# Heft

Every flat item becomes a solid one: ingots, gems, dusts, food, paper and the rest drawn as real
three-dimensional models in the inventory, in the hand, on the ground, in item frames and set down
with Set It Down. Every item of a kind has one shape (every ingot is the same ingot), with
house-made 128x textures and LabPBR maps for shaders.

NeoForge 1.21.1, client side. On a server the jar only adds item tags, and a player without it can
still join.

## How it works

- **Shapes.** `assets/heft/shapes/<namespace>/<item>.json` is a model file (elements, textures,
  display transforms) with a `heft` object naming the item's class, material and Set It Down size.
  `devtools/art/build.py` writes these from one function per class, so every item of a class has
  the same elements.
- **Its own atlas.** Every sheet is stitched on `heft:textures/atlas/items.png` (listed in
  `assets/heft/atlases/items.json`), never on the block atlas, which a 128x resource pack fills. The
  atlas is stitched with mipmaps. A shader mod with LabPBR finds each sheet's `_n` and `_s` beside it.
- **One model in every view.** As models bake, each item with a shape gets a `SolidModel` in place of
  its own, drawn on Heft's atlas in the inventory, both hands, on the ground, in frames, on the head
  and when set down. Its break particles stay the item's own.
- **The long tail.** Every other item still drawn as a flat sprite is drawn extruded: the game's own
  flat model of it made two sixteenths thick, its sprite repainted at 64x. The repaint is made on your
  client, as the game loads, from the sprite as its own mod ships it, never from a resource pack that
  replaces it: doubled by Scale2x, with a fine grain, and flat LabPBR maps. A bow's draw and other
  overrides still work. An item another resource pack already makes 3D is left as that pack draws it,
  and an animated sprite stays flat.
- **Set It Down.** A Heft model is the item's own, so Set It Down lays it down by its item-frame turn
  (`display.fixed`) and sizes it by its measured box. Every class's `fixed` turn lays it with its
  thinnest side out of the surface and its top towards you.
- **Art.** Painted from measured numbers only (value ranges, eight-colour palettes, grain, LabPBR
  ranges) so it sits beside a 128x pack under shaders. No resource pack's pixels are used or shipped.

## Settings

The mod list's config button (or `config/heft-client.toml`):

- `enabled`: draw flat items as solid ones;
- `flatClasses`: classes of shape left flat (`ingot`, ...);
- `tierB.enabled`: draw the long tail extruded;
- `tierB.denylist`: items the long tail leaves flat, by id (`minecraft:filled_map`) or a whole mod (`somemod:*`).

Both take effect on the next resource reload (F3+T).

## Diagnosing

- After every resource reload the log has the line `Heft: N sprites repainted for the extruded tier in
  N ms; ...` and one more, `Heft: N solid, N extruded, N flat, N 3D, N custom, N empty; N breaches, N
  shape files unread`, and `logs/heft-report.txt` lists every item by how it is drawn,
  per mod, with any shape that breaks the canon or names a sprite missing from the atlas.
- `/heft inspect` (client command): for the item in your main hand, how it is drawn, its class,
  material and size, its shape's box as built and as set down or framed, and its Set It Down tags.
- A solid item drawn as the purple-and-black missing texture: its sheet is not in
  `assets/heft/atlases/items.json`. The report names it.

## Building

`./gradlew clean build` runs the JUnit tests, the art pipeline's tests (under uv), the gametest
server and the booth (display `:7`). `knowledge/PROJECT.md` has the details.

## Licence

Copyright (C) 2026 Chunkworks. AGPL-3.0-or-later; see `LICENSE`.
