# Handoff: comprehensive 3D item replacer for The Boys

> Kept as received, 2026-10-25. Rusty amended it the same day: "only make the resolution (128x where
> possible, and utilize labpbr texturing techniques where possible). No pixlii textures or assets
> should be utilized, all assets and textures created for this mod must be house made." Where this
> document and `knowledge/decisions/` disagree, the decisions win (D-0001 to D-0003).

**For:** Rusty Shackleford (you built the live Set It Down, so most of §4 is a contract with your own code)
**From:** nfx, via Claude
**Date:** 2026-10-25
**Target:** NeoForge 1.21.1 / 21.1.248, Java 21 — the Prism instance `The Boys`
**Working name:** `solid` (change it; `threedee`, `realitem`, whatever)

---

## 1. What nfx is asking for

Every flat 2D item sprite in the pack becomes a real 3D model. Not a few hero
items — the whole item registry, vanilla and modded, including the home-grown
mods. Specifically:

- **In every context**: inventory/GUI icon, held in first and third person,
  dropped on the ground as an `ItemEntity`, in item frames, on armour stands,
  and set down with Set It Down.
- **Consistent within a class.** Every ingot is the same ingot geometry with a
  different texture. Every nugget, every gem, every dust pile, every rod. If
  iron and copper ingots have visibly different proportions, that's a bug.
- **Styled, not generic.** Chamfers, bevels, tapers, real thickness. The thing
  nfx explicitly does not want is the "extrude the sprite 1px and call it done"
  look, or a cube with a texture on it.
- **128x + LabPBR, in Pixlli's idiom.** The pack runs Pixlli V57 128x with
  `_n`/`_s` maps under Iris. New art has to carry proper normal/specular maps
  and sit in Pixlli's value ranges, or shaders make it look wrong (see §5).
- **Scale and pose correct under Set It Down**, which is the fussiest consumer
  of item models in the pack because it measures them and poses them itself.

### Two deliverables

1. **The mod** — a NeoForge jar that ships models (and only where unavoidable,
   textures) as built-in resource packs, plus datapack tags, plus config, plus a
   debug command. A mod rather than a plain resource pack because of ordering,
   config, the procedural fallback in §3.3, and the tag additions in §4.4.
2. **The generator** — the Python pipeline that emits the model JSON and the
   derived textures. The art is scripted, not hand-modelled; that's the house
   method and it's what makes ~1500 items tractable (§6).

---

## 2. The ground truth, measured 2026-10-25

Don't trust the wiki or the pack descriptions; these are counts off the actual
files in the instance.

### 2.1 Vanilla

| | count |
|---|---|
| Item models in the 1.21.1 client jar | 1754 |
| …whose parent is `item/generated` or `item/handheld` (i.e. flat sprites), trims excluded | **667** |
| …of those, covered by a *working* enabled 3D pack today | 404 |
| …**with no 3D model at all** | **384** |
| …after dropping `*_darker_trim`, smithing templates, `light_NN`, `barrier`, `structure_void`, `brush_brushing_N` | **325 real items** |

### 2.2 Which 3D packs are actually doing anything

Enabled in `options.txt`, in priority order (later = higher priority):

| pack | `pack_format` | item models | verdict |
|---|---|---|---|
| 3D Default 1.20+ v1.15.0 | 15, supports →34 | 306 | **works**. Doors, rails, boats, trapdoors, hoppers, chains, tools. |
| Refined Tools 3.0 (folder + zip, both present) | 15 | 128 | **works**. Tools and weapons are largely done. |
| crops3d 1.21.1 | 34 | 4 | works, tiny |
| Farmers3D_1.21.1_fixed | 34 | 1 | works, ~nothing |
| FarmersDelight_Crops3D_1.21.1_fixed | 34 | 0 item models | block-only |
| Pixlli V57 128x | 34 | 45 | doors / lanterns / campfires / chains only |
| **Fresh Food** | **46, min_format 46** | 528 | **DEAD.** Needs 1.21.5+. It is in `incompatibleResourcePacks`, and its models are `apple_3d.json` style, selected through the 1.21.4+ `assets/minecraft/items/` definitions that do not exist in 1.21.1. **Food is flat today.** |

So the actual holes, in rough order of how much nfx will notice them:

- **All metals and minerals.** `iron_ingot`, `gold_ingot`, `copper_ingot`,
  `netherite_ingot`, `iron_nugget`, `gold_nugget`, `raw_iron`, `raw_copper`,
  `raw_gold`, `diamond`, `emerald`, `lapis_lazuli`, `quartz`, `coal`,
  `charcoal`, `redstone`, `glowstone_dust`, `gunpowder`, `sugar`, `brick`,
  `nether_brick`, `netherite_scrap`, `amethyst_shard`, `prismarine_shard`,
  `prismarine_crystals`, `echo_shard`, `nether_star`, `heart_of_the_sea`.
  This is the category nfx called out by name for consistency.
- **All food** (Fresh Food is dead): every meat raw and cooked, bread, cookie,
  cake, all the stews, apples, carrots, potatoes, melon, berries, pumpkin pie.
- **Signs and hanging signs** — 22 items, all flat.
- **Paper-likes**: `paper`, `book`, `writable_book`, `written_book`,
  `enchanted_book`, `knowledge_book`, `map`, `filled_map`, `leather`,
  `rabbit_hide`, `phantom_membrane`.
- **Rods and sticks**: `stick`, `blaze_rod`, `breeze_rod`, `bone`, `sugar_cane`,
  `arrow`, `spectral_arrow`, `tipped_arrow`, `string`, `lead`.
- **Odds that deserve real models**: `bundle`, `spyglass`, `goat_horn`,
  `totem_of_undying`, `trident`, `elytra`, `saddle`, all four horse armours,
  `wolf_armor`, `flint_and_steel`, `brush`, `shulker_shell`, `nautilus_shell`,
  `turtle_egg`, `sniffer_egg`, `firework_rocket`, `firework_star`.
- **Plants** (~56): saplings, flowers, grasses, vines, propagules, lily pad,
  pink petals. These want cross-plane or layered-plane models, not extrusions.
- **Armour pieces** (25): see §4.5 — Set It Down shows these as the *worn*
  Armored Legacy piece, so item models only matter in GUI / hand / ground.
- **Dyes** (16), **pottery sherds** (23), **banner patterns** (8), **buckets**
  (11), **music discs**, **spawn eggs**.

### 2.3 Modded

**1125 item textures across 38 mods.** The big ones:

| mod | item textures |
|---|---|
| VanillaBackport | 187 |
| Alex's Mobs | 175 |
| Farmer's Delight | 143 |
| Create | 122 |
| Block Factory's Bosses | 65 |
| Lucky's Wardrobe | 64 |
| Dusty Decorations | 63 |
| Mowzie's Mobs | 52 |
| Backport | 39 |
| Immersive Aircraft | 30 |
| Critters and Companions | 28 |
| rangedweaponsmod | 24 |
| survivalistarmor | 22 |
| Shippy Ships | 19 |

Plus nfx's own: `galestone` (4), `voidsteel` (4), `vanillawheels` (6+4),
`submersibles` (3), `metalsandmaterials` (2). **Do the home-grown mods'
materials in the same pass as vanilla's** — galestone and voidsteel ingots must
match iron's geometry exactly, or the whole consistency argument collapses.

### 2.4 Pixlli's actual item coverage — the important one

Pixlli ships **212 item albedos** at 128x, 211 of them with full `_n` + `_s`.
Vanilla has 605 item sprites. So **Pixlli covers ~35% of vanilla items**, and it
covers *none* of the metals and minerals:

```
iron_ingot      NO     redstone        yes
gold_ingot      NO     stick           NO
copper_ingot    NO     bread           NO
netherite_ingot NO     apple           NO
diamond         NO     iron_nugget     NO
emerald         NO     raw_iron        NO
coal            NO     brick           NO
lapis_lazuli    NO     quartz          NO
```

Of the 384 uncovered flat items, **74 have Pixlli 128x art and 310 do not.**

**This is the single most consequential fact in the document.** "Use Pixlli's
128x PBR" cannot mean "point the UVs at Pixlli's item texture" for the items nfx
cares most about, because Pixlli does not have them. It has to mean: *author new
128x art in Pixlli's idiom, with the material sampled from Pixlli's
corresponding **block** texture.* Pixlli's block tree is 2680 files and has
everything you need:

```
iron_block  gold_block  diamond_block  netherite_block  copper_block
emerald_block  lapis_block  coal_block  redstone_block  amethyst_block
raw_iron_block  raw_copper_block  raw_gold_block  bricks    — all with _n and _s
quartz_block — NOT PRESENT; find another source (quartz_bricks / chiseled), verify first
```

An iron ingot's faces are Pixlli's `iron_block` metal, relit for the ingot's own
geometry. That is both the correct answer aesthetically and the established
house method (§5.2).

---

## 3. Architecture

### 3.1 Why a mod and not just a resource pack

A resource pack could do 90% of this (3D Default proves it). The mod buys:

- **Deterministic pack ordering** without asking nfx to drag entries in a list.
- **Config** — per-category toggles, so "turn the 3D food off" is one click
  rather than a repack. `yet_another_config_lib_v3` and `cloth-config` are both
  already in the pack; use YACL.
- **The procedural fallback** (§3.3), which is the only way to honestly claim
  "any and all" coverage including mods nobody hand-authored for.
- **Tag additions** for Set It Down size/stack classes (§4.4).
- **A debug command** (§7.2) that makes the model/pose pairing checkable.

### 3.2 Two built-in packs, and the rule that matters

Register both in `AddPackFindersEvent` (client dist only), hidden and always-on:

| pack | position | contains |
|---|---|---|
| `solid_models` | `Pack.Position.TOP` | **models only.** Every `models/item/*.json`, across `minecraft:` and every mod namespace. **Zero `.png` files.** |
| `solid_art` | `Pack.Position.BOTTOM` | **new textures only**, for items whose new geometry needs pixels no existing pack has. |

**The rule: models go on top, art goes on the bottom.** The models pack must
beat Pixlli so our geometry wins. The art pack must *lose* to Pixlli so that for
any item Pixlli does cover, Pixlli's 128x art is still what you see — we only
fill in where Pixlli is silent. Ship one `.png` in the TOP pack and you have
started overwriting Pixlli, which is the one thing nfx will not forgive.

Corollary: **UV-reference the existing texture path wherever possible.** Model
UVs are in 0–16 model space and are resolution-independent, so a model that UVs
onto `item/iron_pickaxe` automatically renders at whatever resolution the
winning pack supplies — 128x Pixlli today, 256x tomorrow. This is exactly how 3D
Default composes with Pixlli and why it still looks right. Only introduce a new
texture path when the geometry exposes faces the sprite cannot describe.

### 3.3 Hand-authored tier + procedural tier

~1500 item models is too many to hand-author and too many to leave to a script.
Split it:

- **Tier A — hand-authored (scripted, see §6), ~250 items.** Everything in the
  §2.2 hit list, every mod material (ingots / nuggets / dusts / gems from
  Create, galestone, voidsteel, metalsandmaterials, VanillaBackport), and
  anything a player holds constantly. These get real geometry per the §4.3
  canon.
- **Tier B — procedural extrusion at bake time, everything else.** Hook
  `ModelEvent.ModifyBakingResult`; for every item still resolving to a
  `generated` / `handheld` parent and not in Tier A, build an extruded model
  from its sprite.

  Mechanics that matter for Tier B:
  - Read the PNG through `ResourceManager`, **not** off the atlas — that way you
    get whichever pack won, at full 128x, and you respect pack order.
  - **Build geometry from a 16×16 mask, texture at full res.** Downsample the
    alpha mask to 16×16 (any-opaque, or >50% coverage) and greedy-mesh *that*
    into boxes. Mesh a 128×128 mask and you get thousands of elements per item
    and the game dies.
  - Front and back faces UV onto the sprite normally. Side faces UV onto the
    edge column/row of pixels — a 1px-wide strip of the sprite at the
    silhouette — so the extruded sides pick up the right colour and Pixlli's own
    `_n`/`_s`.
  - Thickness by size class: 1.5px for paper-likes, 2px default, 3px for tools.
  - Cap it: if the greedy mesh exceeds ~40 boxes, fall back to a single
    flat-plus-rim model rather than shipping a monster.
  - Cache by (item, pack reload) and clear on `ModelEvent.BakingCompleted`.

  Tier B is what makes "no 2D sprites left" true for Alex's Mobs' 175 items
  without anyone drawing 175 models.

- **Config**: `tierB.enabled`, and a `tierB.denylist` for items where extrusion
  looks worse than the sprite (flat maps, GUI-ish icons).

### 3.4 1.21.1 model format — read this before you follow any tutorial

1.21.1 is **before** the item-model rewrite. That means:

- Item models live at `assets/<ns>/models/item/<id>.json` and variants are
  selected with `"overrides"` + `"predicate"` (`custom_model_data`, `damaged`,
  `pulling`, etc.).
- There is **no** `assets/<ns>/items/<id>.json` client item definition, no
  `minecraft:model` / `minecraft:select` / `minecraft:condition` tree, no
  `minecraft:range_dispatch`. Every modern tutorial and most current packs
  (Fresh Food included) are written for that system and will not work here.
- `pack_format` for 1.21.1 is **34**.
- Display contexts are the old seven: `thirdperson_righthand`,
  `thirdperson_lefthand`, `firstperson_righthand`, `firstperson_lefthand`,
  `gui`, `head`, `ground`, `fixed`.

---

## 4. The Set It Down contract

This is your code, so this section is less "how it works" and more "here is
exactly which of your constants the model geometry has to respect." The live jar
in the instance is `setitdown-1.1.1.jar`, `com.chunkworks.setitdown`, AGPL-3.0,
`the-rusty-shackleford/minecraft-set-it-down`, dated 2026-10-23. (nfx also has
an older, separate `com.nfx.setitdown` 1.3.0 tree at
`C:\Users\nfx73\Dev mods\setitdown` — different implementation, not what is
running. Do not read it as the spec.)

### 4.1 Set It Down poses the model itself

`BoundsCache.item(stack, level, seed)` measures the baked model into a
`domain.Box`; `Pose` and `Rest` decide the resting orientation; and
`mixin.ItemRendererMixin#setitdown$atRest` wraps `BakedModel.applyTransform` so
the display-context transform is substituted at render time.

**Consequence for model authors:** for the set-down case, a model's own
`display.fixed` transform is *not* what positions it — the measured box is. So
you cannot fix a badly-posed display by fiddling `fixed`. You fix it by changing
the model's proportions, by tagging it (§4.4), or by adding a case to `Rest`.

Author `fixed` correctly anyway — vanilla item frames and armour-stand / `head`
rendering still use it, and nfx has item frames all over the place.

### 4.2 `Rest.of(Box)`, decoded — the pose rule

With `w = maxX-minX`, `h = maxY-minY`, `d = maxZ-minZ` in model space:

```
if  h <= 2 * min(w, d) + 1e-4   ->  ON_BASE     (stands up as modelled)
else if  d <= w + 1e-4          ->  FACE_OUT    (lies flat, face away from surface)
else                            ->  ON_SIDE     (lies on its side)
```

**So: anything taller than twice its smallest horizontal extent gets laid
down.** Check your geometry against this before wondering why it is lying on its
face:

| intended model (w × h × d, in 16ths) | 2·min(w,d) | pose | right? |
|---|---|---|---|
| flat sprite 16 × 16 × 0 | 0 | FACE_OUT | ✓ lies flat |
| ingot 10 × 3 × 5 | 10 | ON_BASE | ✓ lies as an ingot |
| gem 7 × 7 × 7 | 14 | ON_BASE | ✓ sits |
| plate / paper 11 × 1 × 9 | 18 | ON_BASE | ✓ sits flat |
| stick modelled along X: 11 × 2 × 2 | 4 | ON_BASE | ✓ lies along the surface |
| tool, drawn diagonally: 14 × 14 × 2 | 4 | FACE_OUT | ✓ lies flat |
| bottle 6 × 12 × 6 | 12 | ON_BASE **by 1e-4** | ⚠ do not sit on the boundary |
| bottle 6 × 13 × 6 | 12 | FACE_OUT | ✗ a bottle lying on its face |

**Authoring rule:** anything that should *stand* must satisfy
`h <= 2 · min(w, d)` **with margin** — keep `h <= 1.8 · min(w, d)`. A 6-wide
bottle gets at most 11 high, or it needs a wider base, or it needs a `Rest`
case. Torch-proportioned models (2 × 14 × 2) will always be laid down; if
something needs to stand on a thin base, that is a code change on your side,
not a geometry trick.

### 4.3 The size canon

`domain.Size` normalises a displayed item to a target side length in blocks:

| `Size` | `side()` | tag |
|---|---|---|
| `TINY` | 0.300 | `setitdown:size/tiny` |
| `SMALL` | 0.375 | `setitdown:size/small` |
| `NORMAL` | 0.500 | *(default, untagged)* |
| `TOOL` | 0.700 | `setitdown:size/tool` |
| `LONG` | 0.800 | `setitdown:size/long` |

Because the display is normalised to a target side, **a model's absolute size
matters much less than its aspect ratio** (which drives §4.2) and its *relative*
proportions within a class (which is what makes a stack of four ingots look
machined instead of hand-whittled).

So here is the proposed geometry canon. All dimensions in 16ths of a block
(model space, `w × h × d`), modelled resting on `y = 0` where it stands:

| class | dims | geometry notes | size tag |
|---|---|---|---|
| **ingot** | 10 × 3 × 5 | trapezoidal prism: top face 8 × 3 inset 1 all round, 1px chamfer on the four long edges. **Identical for every metal** — iron, gold, copper, netherite, galestone, voidsteel, Create's brass/zinc, VanillaBackport's. | `size/small` (covers `#c:ingots`) |
| **nugget** | 4 × 2 × 3 | the ingot silhouette scaled and simplified to 2 boxes | `size/tiny` (`#c:nuggets`) |
| **raw chunk** | 7 × 5 × 6 | 3 offset boxes, deliberately irregular; same 3-box arrangement for every raw metal, only the texture changes | `size/small` (`#c:raw_materials`) |
| **gem** (diamond, emerald) | 7 × 7 × 7 | octahedron faked with 3 nested rotated slabs at 45°, each inset 0.05 to kill z-fighting | `size/small` (`#c:gems`) |
| **shard** (amethyst, prismarine, echo) | 3 × 2 × 9 | tapered 3-segment prism | `size/small` |
| **dust pile** (redstone, glowstone, gunpowder, sugar, bone meal, blaze powder) | 9 × 2 × 9 | two-tier mound: 9×1×9 base, 5×1×5 crown offset ~1 off-centre. **Same mound for every dust.** | `size/tiny` (`#c:dusts`) |
| **lump** (coal, charcoal) | 6 × 5 × 6 | 4-box irregular cluster | `size/small` |
| **rod** (stick, blaze, breeze, bone, sugar cane) | 11 × 2 × 2 | modelled along X; 1px taper at both ends | `size/small` |
| **plate** (paper, map, leather, rabbit hide, membrane) | 11 × 1 × 9 | one box, corners clipped by 1px; slight curl on one corner for paper | `size/small` |
| **book** | 10 × 2.5 × 7 | two cover boxes 10 × 1 × 7 with a 1px overhang on three sides, page block 8.5 × 1.5 × 6 inset | `NORMAL` |
| **bar** (brick, nether brick) | 8 × 3 × 4 | box with 0.5px chamfer | `size/small` |
| **loaf / round food** | 8 × 5 × 7 | per-food; keep one silhouette family across raw/cooked pairs so `beef` and `cooked_beef` differ only by texture | `size/small` |
| **sign** | 14 × 10 × 1.5 | board + post, mirroring the block model | `NORMAL` |

Two constraints behind those numbers: every one satisfies §4.2 for its intended
pose, and every "same class" entry is literally the same element list with a
different `textures` block — which is the consistency nfx asked for, enforced
structurally rather than by eye.

### 4.4 Tag work the mod must ship

The live jar already tags by convention tags (`#c:ingots`, `#c:nuggets`,
`#c:dusts`, `#c:gems`, `#c:raw_materials`, `#c:seeds`, …) with
`"required": false`, so most modded materials classify for free. What the new
mod should add as a datapack:

- **`setitdown:size/*` additions** for anything whose new 3D model changes its
  natural size class, and for modded items that carry no `c:` tag — Create's
  sheets and plates, Alex's Mobs' drops, VanillaBackport's new materials,
  rangedweaponsmod's parts beyond the five already listed.
- **`setitdown:stackable_pile`** — plates, books, signs, bars, ingots. Flat
  things that climb.
- **`setitdown:stackable_cluster`** — lumps, gems, raw chunks, round foods,
  shards. Lumpy things that spread.
- Re-check both against the new geometry: an item that read as "flat" as a
  sprite may now be a chunky 3D object and belong in the other tag.
- **`setitdown:undisplayable`** ships empty in 1.1.1 and that is your call, not
  this mod's — but if the new models make anything unplaceable-looking, note it
  rather than silently tagging it.

### 4.5 Armour is not an item-model problem

Set It Down renders wearable armour as the **worn** piece (`Mannequins`,
`BoundsCache.armour`), because nfx's 3D armour is not item models at all — it is
Armored Legacy's EMF `optifine/cem/armor_stand_<piece>.jem` /
`player_<piece>.jem` geometry, which only applies through the real armour layer.

So for the 25 armour items: their item models matter in **GUI, hand, ground and
item frames only**. Do those (Armored Legacy's base geometry at
`assets/minecraft/models/item/base/<mat>_<piece>.json` is standalone,
`texture_size` 128×64, no parent — a good starting point), and do not try to
"fix" how armour looks when set down by changing item models. Also note
`survivalistarmor` (22 textures) has its own art direction already decided —
check nfx's survivalist armour notes before touching it.

### 4.6 Other Set It Down facts worth holding

- Hitbox is half a block wide and a quarter deep (0.45 tilted). Models whose
  normalised footprint exceeds that stick out of their own hitbox.
- Clusters shrink as they fill up, so a 4-cluster item wants to read at ~0.35
  block across. Over-detailed geometry disappears at that size — keep cluster
  items' detail coarse.
- Set It Down keys off its own key (**Y** by default) in 1.1.1, not sneak +
  right-click. Good: that means none of the §2 items' own interactions are
  shadowed.
- A no-quad model (`builtin/entity`, BEWLR renderers) measures as a fallback
  box. `shield`, `trident`, banners, beds, skulls and all 11 `builtin/entity`
  items are in that bucket — if you give any of them real geometry, they start
  being measured for real, which can change their pose. Re-check them after.

---

## 5. Texturing: Pixlli and LabPBR

The pack is 128x Pixlli under Iris with parallax. These rules were learned the
hard way on the existing Pixlli patches (Farmer's Delight, Create's 230 stone
textures, Another Furniture, Dusty Decorations, SubWild) — every one of them was
rejected in-game at least once before it was right.

### 5.1 Hard rules

1. **LabPBR heights must stay in Pixlli's range.** Faces ~250, seams ~208,
   nothing below ~175. The FD cabinets and the FD stove were both rejected
   in-game ("looks really bad", "red stripes, can't even tell what the block
   is") because derived heights went down to 166 and spanned 60–255, and the
   shader's parallax turned the tone map into stacked slabs with white-rimmed
   mortar.
2. **Never derive height from albedo brightness.** Height comes from the
   *design* — the geometry you know you built — never from tone or grain. For
   art you did not author, write flat normal/height/AO and supply only
   smoothness and metal F0.
   - One exception: if the art's shading genuinely *is* its geometry (weave,
     lattice, rope, corrugation), shallow relief (~22 levels, about half a plank
     seam) is honest. Painted detail is not.
3. **Anchor the crest, not the median.** Centring height on the median and
   adding to the face level puts half the texels over 248 and clips at 255.
   Anchor the crest at ~253 and hang the relief below it.
4. **Rotating a material rotates its normal map.** For `np.rot90` CCW:
   `R' = G, G' = 255 − R`. Mirroring flips the matching component (and its
   sign). Forget this and the lighting runs backwards on half the model.
5. **Transparent pixels hold black.** Blurring or upscaling a cutout drags black
   into every visible edge — curtains lost 18% edge brightness this way. Flood
   the opaque colours outward *first*, then compute weights and modulations from
   the filled image.
6. **Do not bake a highlight into a UV patch shared by two faces.** The
   generator should de-duplicate patches by (material, w, h), and a cube's
   opposite faces then share one rect — which Blockbench and MC mirror.
   See-through or two-sided faces need their own clean patch.
7. **Scale depth against one reference, not per-texture percentiles.**
   Normalising each item by its own span stretches a nearly-flat thing to full
   depth. Pick one reference (an ingot) and cap total depth (~62 levels).
8. **`_s` alpha 255 means no emission.** Emit only where something actually
   glows (glowstone dust, blaze powder, magma cream, nether star, echo shard).
   `_s` green > 200 is the metal F0 flag (~230 on real metal).

### 5.2 The derivation method for new art

For every item in the "no Pixlli art" bucket (310 of them), the material comes
from Pixlli's corresponding **block**:

```
iron_ingot      <- iron_block         gold_ingot      <- gold_block
copper_ingot    <- copper_block       netherite_ingot <- netherite_block
diamond         <- diamond_block      emerald         <- emerald_block
lapis_lazuli    <- lapis_block        coal            <- coal_block
amethyst_shard  <- amethyst_block     brick           <- bricks
raw_iron        <- raw_iron_block     raw_copper      <- raw_copper_block
raw_gold        <- raw_gold_block     redstone        <- (item art already exists)
quartz          <- NO quartz_block in Pixlli; pick and verify a source
```

The method that works (three variants failed before it):

```
out = Pixlli_base(x, y) * M        where  M = design_texel / median(base)
```

with gamma 1.7 on the darkening so frame and edge lines stay bold, blended by a
per-texel "is this the base material" weight from palette distance. Non-base
texels keep their own colour plus ~35% of the base grain. `_n` = Pixlli's
normals plus height/AO from the *design* structure. `_s` = Pixlli's smoothness,
F0 230 on low-saturation grey texels.

Three source-selection traps:

- **A palette match alone must never pick a base.** `derive()` paints the base's
  *pattern* through, so sharing a colour is not enough — palette-only matching
  put a dried ghast on an iron block and a cake top on a froglight. Accept a
  base only on (a) ≥45% identical texels, (b) ≥30% when another face of the same
  object matched the same family, or (c) the filename names the material *and*
  the palette agrees. A colour word in a name does not vouch for a material.
- **Match material names on whole words, not substrings.** `red_raked_sand`
  does not contain `red_sand`, so a substring test saw `sand`, rejected it, and
  *silently skipped the texture* — nfx spotted it in game. Tokenise on `_`,
  require every word present, most specific first. **And log every rejection:**
  a bad base is visible, a missed base just looks untouched.
- **Pixlli's `polished_*` blocks carry a raised frame around the whole face.**
  Use one as a material and you print that frame inside every ingot. Sample
  from the flat tiling stones only.

### 5.3 Where new texture pixels are actually needed

Most of the time: front and back faces UV onto the existing sprite, side faces
UV onto an edge strip of it. No new art. Write new art only when:

- The geometry exposes a face the sprite genuinely does not describe — an
  ingot's top and side faces, a book's spine, a loaf's cut end.
- The sprite is a 2D icon rather than a view of an object (maps, dyes, some mod
  icons). Those may be better left to Tier B or denylisted.

Every new texture ships into `solid_art` (BOTTOM), with `_n` and `_s`, at 128x,
in the correct Pixlli range. A new texture without its maps is a bug: under Iris
it renders flat and dead next to everything around it.

---

## 6. Pipeline and tooling

### 6.1 Environment, verified today

```
Python            3.13.15
numpy             2.5.3        (available — any older "numpy not installed" note is stale)
PIL               12.3.0
javap             C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\   (for jar spelunking)
Decompiled 1.21.1 sources
   C:\Users\nfx73\.gradle\caches\neoformruntime\intermediate_results\sourcesAndCompiledWithNeoForge_*.jar
Client jar (vanilla textures)
   ...\PrismLauncher\libraries\com\mojang\minecraft\1.21.1\minecraft-1.21.1-client.jar
Pixlli
   ...\instances\The Boys\minecraft\resourcepacks\Pixlli V57 26.3-1.13 128x.zip
Unpacked Pixlli subset (block/ + item/ only)
   C:\Users\nfx73\Mincecraft - Claude modding\create-vanilla-reskin\src\pixlli\assets\minecraft\textures\
Armored Legacy
   ...\resourcepacks\Armored Legacy 1.5.1.zip   (use base assets/, NOT overlay_55 / overlay_63)
Scaffold for a new NeoForge mod
   C:\Users\nfx73\Dev mods\cavestalker\  — copy build.gradle, settings.gradle, gradlew*, gradle/wrapper/
```

### 6.2 Existing generators worth reading before writing a new one

All under `C:\Users\nfx73\Dev mods\`:

| path | what to steal |
|---|---|
| `farmersdelight-pixlli-patch\fdgen.py` | the `derive(name, base)` method of §5.2 |
| `farmersdelight-pixlli-patch\cabgen.py` | `Mat` / `Canvas` / bevel / finish primitives — procedural surfaces in Pixlli's range |
| `farmersdelight-pixlli-patch\stovegen.py`, `models.py` | procedural rebuild after a derived version was rejected; `modelview.py` renders a model isometrically |
| `farmersdelight-pixlli-patch\afgen.py`, `drawergen.py`, `ddgen.py` | `clean_rect()` seam-free base sampling, `flood_colour()`, `own_art()` / `own_art_relief()`, symmetry preservation, grain voting |
| `farmersdelight-pixlli-patch\swgen.py` | borrowing a *mask* from Pixlli rather than upscaling the mod's own; splitting nugget core from socket ring |
| `create-stone-pixlli\material.py`, `stonegen.py`, `build.py` | design-field averaging; the depth-capping and crest-anchoring numbers |
| `create-vanilla-reskin\tools\zincgen.py` | the `_s` green == 230 nugget mask trick |

### 6.3 Model generation

Write the `.json` models from Python the way nfx's vehicle models are generated,
not by hand in Blockbench. House conventions, adapted to item models:

- Define each geometry **class once as a function** (`ingot()`, `nugget()`,
  `dust_pile()`, `gem()`, `rod()`, `plate()`), parameterised only by texture
  name. That is what guarantees §4.3 consistency — same element list, different
  `textures` block.
- Round shapes = nested rotated slabs. For `n` slabs at `180/n` steps, the
  correct half-dims are `R·cos(step/2)` × `R·sin(step/2)`, which puts the
  corners exactly on the polygon's vertices; slabs spanning the full diameter
  poke outside the circle. Inset each successive slab ~0.05 to stop z-fighting.
  Anything that must be see-through needs a ring of tangential segments, not
  full-diameter slabs.
- **Print the rotated bounding box, not `min(from)` / `max(to)`.** With rotated
  slabs the raw box over-reported length by ~2.5 units on the motorcycle. For
  §4.2 you need the *real* box or you will predict the wrong pose.
- Build a preview renderer. `render_bbmodel.py` (flat-shaded 3/4 PNG, samples
  per-face colour and alpha from the embedded texture, handles mesh elements,
  includes a full PNG filter 0–4 decoder) and `modelview.py` (isometric model
  render) both exist in the scratchpad lineage — port them to item models and
  look at every class before shipping it. **Checking geometry without launching
  the game is what makes this iterable.**
- Blockbench is a native Electron app here and cannot be driven by browser
  tooling. Edit `.bbmodel` on disk with Blockbench closed, or hand over a
  devtools-console script. Also: at `format_version 5.0` group names and pivots
  live in a separate top-level `groups` array, so a patch script that adds a
  bone must append to **both** the outliner and `groups`.

### 6.4 Element budget and performance

- ≤ 40 elements per item model; ~24 is plenty for the canon classes.
- The pack already runs Sodium, ImmediatelyFast, EntityCulling, ModernFix,
  FerriteCore, ScalableLux, C2ME and Distant Horizons. Item models are baked
  once, so the cost is memory and inventory draw calls, not world rendering —
  but EMI renders hundreds of item models in a grid, so a 200-box model per item
  is felt there first.
- Minecraft holds loaded pack zips open. **Write a new filename; never replace a
  zip in place** while the game is running.

---

## 7. Validation

### 7.1 Headless

`./gradlew runServer` (with `run/eula.txt` = `eula=true`) is the cheap pass and
catches bad JSON, tags and datapack errors. **Recipe parity check: vanilla loads
1290 recipes** — compare the "Loaded N recipes" line. A standing regression
datapack under `run/world/datapacks/` is the house pattern; note that datapack
functions get no command feedback, so assertions have to be conditional `say`
lines, not `data get`. Kill `runServer` with `timeout`.

It will **not** validate a single model, because models are client-side. So:

### 7.2 A startup validator and a debug command (please build both)

- **Validator**: on `ModelEvent.BakingCompleted`, walk the item registry and log
  (a) every item whose baked model has no quads, (b) every item still resolving
  to a `generated` / `handheld` parent — i.e. still flat — and (c) every model
  over the element budget. That list *is* the progress bar for this project, and
  it is how you prove "no 2D sprites left" instead of asserting it.
- **Debug command** (`/solid inspect`, client-side): for the held item, print
  the measured `Box`, the derived `Rest`, the `Size` class and the resolved
  tags. You already have `BoundsCache`, `Rest` and `Size`; exposing them turns
  every pose question in §4.2 into a one-command answer instead of a squint.

### 7.3 In game

Nothing in §4.3 or §5 is verified in game yet — treat the canon numbers as a
starting point that needs one pass with nfx looking at it. Checklist per batch:

1. **Inventory grid** — open a full creative tab and EMI. Icons must still be
   identifiable at 16px. This is where an over-ambitious `gui` rotation dies.
2. **Hand, first and third person**, moving and still.
3. **Dropped on the ground**, including a stack (vanilla draws 2–4 offset
   copies) and spinning.
4. **Item frame** (uses `fixed` — the one place the model's own transform still
   governs) and **glow item frame**.
5. **Set It Down**: place one of each class on floor, wall and ceiling; rotate;
   tip; stack to 4. Watch for sinking into the block, clipping out of the
   hitbox, and the wrong `Rest`.
6. **Under shaders**, Iris on, with parallax — this is where §5.1's height-range
   rule gets tested, and it is the step that caught both the FD cabinets and the
   FD stove.
7. If you want screenshots from in-game, take them **in game** (F2, or a
   programmatic capture of the game's own framebuffer). Do not grab desktop
   windows.

---

## 8. Open decisions for nfx

Worth getting answers before building the generator, because each one changes
the output broadly:

1. **GUI icon treatment.** Two options: (a) keep `gui` at identity so an
   extruded model's front view reads exactly like today's sprite — zero
   disruption, zero new depth in the inventory; or (b) give chunky classes an
   isometric `gui` rotation (~`[30, 225, 0]`) so ingots and gems show their
   depth in the hotbar. (b) is the "cosmetically pleasing" answer and the one
   that matches what nfx asked for, but it changes *every* icon silhouette and
   has to survive the EMI grid. **Recommendation: (b) for the material classes
   (ingot / nugget / gem / dust / lump / raw), (a) for everything else.** nfx
   should look at one tab of each before it is locked in.
2. **How far into the mods to go.** 1125 mod item textures. Tier B covers them
   all adequately; Tier A hand-authoring is the expensive part.
   **Recommendation: Tier A for every *material* item in every mod** (so
   consistency holds across Create, galestone, voidsteel, metalsandmaterials and
   VanillaBackport) **plus Farmer's Delight food**, and Tier B for the rest.
   Alex's Mobs' 175 icons are not worth hand-modelling.
3. **Food: replace Fresh Food, or port it?** Fresh Food is a well-liked pack
   that simply cannot run on 1.21.1. Porting its 528 models back to the old
   override system is mechanical, but it is someone else's art and licence.
   **Recommendation: author food in Tier A to our own canon** — it keeps the
   style consistent with the ingots and avoids the licence question. Confirm
   with nfx, since he enabled Fresh Food deliberately.
4. **Does this ship as one mod, or mod + optional art pack?** Shipping the art
   inside the jar means every client gets it on join; a separate pack means nfx
   can swap it. **Recommendation: in the jar, with config toggles.**
5. **Signs** (22 items) borrow their block model and are nearly free; confirm
   they are wanted, since a 3D sign in hand is noticeably bigger than the
   sprite.

---

## 9. Suggested order of work

| phase | scope | why in this position |
|---|---|---|
| 0 | Mod skeleton: two built-in packs, pack ordering, YACL config, the §7.2 validator and debug command | nothing else is checkable without it |
| 1 | **Metals and minerals** — ingot, nugget, raw, gem, shard, dust, lump, bar, across vanilla *and* every mod | the category nfx named; proves the §5.2 derivation and the §4.3 canon in one go; highest visibility |
| 2 | Tier B procedural extrusion + denylist | turns "384 flat items" into "no flat items" immediately, and tells you which items need Tier A |
| 3 | Paper-likes, rods, books, signs | cheap, high count, one geometry family each |
| 4 | Food | biggest art volume; needs decision #3 |
| 5 | Plants (cross / layered planes), odds and ends, armour GUI models | lowest risk, most fiddly |
| 6 | Tag sweep: `size/*`, `stackable_pile`, `stackable_cluster` re-checked against final geometry | must come after the models settle |

---

## 10. Licence and distribution

Pixlli, Armored Legacy, 3D Default (by GeForceLegend; there is a `LICENSE` in
the zip) and Refined Tools are all third-party. Deriving from them for nfx's own
server is fine; **publishing the derived art is not**, without checking each
licence. Set It Down itself is AGPL-3.0, which matters if this mod links against
it rather than just shipping tags for it. Keep the generator scripts and the
derived output separable, so the art can be rebuilt from sources on any machine
that has the packs.

## 11. One request from nfx

**Do not install anything into his instances uninvited.** Build the jar, hand
over the write-up, and let him install it. That has caused friction before.
