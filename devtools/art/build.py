"""build: writes every solid item's shape, sheet and maps, and the atlas that lists them.

    uv run --no-project --with numpy --with pillow python devtools/art/build.py

For each item in ITEMS: assets/heft/shapes/<ns>/<id>.json (the class's elements, its display, the
sheet's uvs), assets/heft/textures/solid/<ns>/<id>.png with _n and _s beside it (never under textures/item/, which the game's block atlas sweeps up in every namespace); then
assets/heft/atlases/items.json listing every sheet (the maps are found beside them by the shader mod,
never stitched themselves).

Copyright 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import paint  # noqa: E402  (puts tools/pbr on the path)
import labpbr  # noqa: E402
from layout import GAP, PPU, SHEET, patches  # noqa: E402
from shapes import CLASSES, DIRECTIONS, Klass  # noqa: E402

ASSETS = HERE.parents[1] / "src" / "main" / "resources" / "assets" / "heft"

# item -> (class, material). Every item of a class is its class's boxes in its material (D-0003).
ITEMS: dict[str, tuple[str, str]] = {
    # Phase 1: metals and minerals, vanilla and every mod in the pack.
    "minecraft:iron_ingot": ("ingot", "iron"), "minecraft:gold_ingot": ("ingot", "gold"),
    "minecraft:copper_ingot": ("ingot", "copper"), "minecraft:netherite_ingot": ("ingot", "netherite"),
    "create:brass_ingot": ("ingot", "brass"), "create:zinc_ingot": ("ingot", "zinc"),
    "metalsandmaterials:steel_ingot": ("ingot", "steel"), "voidsteel:voidsteel_ingot": ("ingot", "voidsteel"),
    "minecraft:iron_nugget": ("nugget", "iron"), "minecraft:gold_nugget": ("nugget", "gold"),
    "create:copper_nugget": ("nugget", "copper"), "create:brass_nugget": ("nugget", "brass"),
    "create:zinc_nugget": ("nugget", "zinc"), "metalsandmaterials:steel_nugget": ("nugget", "steel"),
    "minecraft:raw_iron": ("raw", "raw_iron"), "minecraft:raw_gold": ("raw", "raw_gold"),
    "minecraft:raw_copper": ("raw", "raw_copper"), "create:raw_zinc": ("raw", "raw_zinc"),
    "voidsteel:raw_voidsteel": ("raw", "raw_voidsteel"), "galestone:raw_galestone": ("raw", "raw_galestone"),
    "minecraft:diamond": ("gem", "diamond"), "minecraft:emerald": ("gem", "emerald"),
    "create:rose_quartz": ("gem", "rose_quartz"), "galestone:galestone_crystal": ("gem", "galestone"),
    "minecraft:amethyst_shard": ("shard", "amethyst"), "minecraft:prismarine_shard": ("shard", "prismarine"),
    "minecraft:echo_shard": ("shard", "echo"), "minecraft:quartz": ("shard", "quartz"),
    "subwild:coal_shard": ("shard", "coal_shard"),
    "minecraft:redstone": ("dust", "redstone"), "minecraft:glowstone_dust": ("dust", "glowstone"),
    "minecraft:gunpowder": ("dust", "gunpowder"), "minecraft:sugar": ("dust", "sugar"),
    "minecraft:bone_meal": ("dust", "bone_meal"), "minecraft:blaze_powder": ("dust", "blaze_powder"),
    "minecraft:coal": ("lump", "coal"), "minecraft:charcoal": ("lump", "charcoal"),
    "minecraft:lapis_lazuli": ("lump", "lapis"), "minecraft:netherite_scrap": ("lump", "netherite_scrap"),
    "minecraft:brick": ("bar", "brick"), "minecraft:nether_brick": ("bar", "nether_brick"),
    # Phase 3: rods, plates, books, signs, arrows.
    "minecraft:stick": ("rod", "stick"), "minecraft:blaze_rod": ("rod", "blaze_rod"),
    "minecraft:breeze_rod": ("rod", "breeze_rod"), "minecraft:bone": ("bone", "bone"),
    "minecraft:paper": ("plate", "paper"), "minecraft:map": ("plate", "map_paper"), "minecraft:leather": ("plate", "leather"),
    "minecraft:rabbit_hide": ("plate", "rabbit_hide"), "minecraft:phantom_membrane": ("plate", "phantom_membrane"),
    "alexsmobs:kangaroo_hide": ("plate", "kangaroo_hide"),
    "minecraft:book": ("book", "book"), "minecraft:writable_book": ("book", "writable_book"),
    "minecraft:written_book": ("book", "written_book"), "minecraft:enchanted_book": ("book", "enchanted_book"),
    "minecraft:knowledge_book": ("book", "knowledge_book"),
    **{f"minecraft:{w}_sign": ("sign", f"{w}_wood") for w in ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
                                                              "cherry", "bamboo", "crimson", "warped", "pale_oak")},
    **{f"minecraft:{w}_hanging_sign": ("hanging_sign", f"{w}_wood") for w in ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                                                                              "mangrove", "cherry", "bamboo", "crimson", "warped", "pale_oak")},
    "backport:poplar_sign": ("sign", "poplar_wood"), "backport:poplar_hanging_sign": ("hanging_sign", "poplar_wood"),
    "minecraft:arrow": ("arrow", "arrow"), "minecraft:spectral_arrow": ("arrow", "spectral_arrow"),
    "minecraft:tipped_arrow": ("arrow", "tipped_arrow"),
}
# Phase 4: food, each item its own material (paint._food_materials).
import food  # noqa: E402
ITEMS.update({item: (klass, item) for item, (klass, _) in food.FOOD.items()})
# Phase 5: odds and ends, each its own material (paint._odd_materials); every spawn egg is one kind.
import odds  # noqa: E402
ITEMS.update({item: (klass, item) for item, (klass, _) in odds.ODDS.items()})
ITEMS["_kind:spawn_egg"] = ("spawn_egg", "egg_white")

NORMAL_STRENGTH = 30.0  # a 4-level-per-texel bevel tilts its normal about 25 degrees; the face's own noise about 7


def sheet(klass: Klass, material: paint.Material):
    """effects: returns (albedo RGBA, _n, _s) as uint8 arrays for the class's sheet in material, and its patches"""
    tall, laid = patches(klass)
    shape = (round(tall * PPU), SHEET * PPU)
    albedo = np.zeros(shape + (3,))
    height = np.full(shape, float(labpbr.FACE))
    ao = np.ones(shape)
    nx = np.zeros(shape)
    ny = np.zeros(shape)
    smooth = np.zeros(shape)
    emission = np.zeros(shape)
    filled = np.zeros(shape, dtype=bool)
    f0 = np.full(shape, material.f0)
    porosity = np.zeros(shape)
    for (role, w, h), p in laid.items():
        x0, y0 = round(p.u * PPU), round(p.v * PPU)
        pw, ph = round(w * PPU), round(h * PPU)
        layers = paint.paint(role, pw, ph, material)
        sl = (slice(y0, y0 + ph), slice(x0, x0 + pw))
        albedo[sl] = layers.albedo
        height[sl] = layers.height
        ao[sl] = layers.ao
        smooth[sl] = layers.smooth
        porosity[sl] = layers.porosity
        f0[sl] = layers.f0
        if layers.emission is not None:
            emission[sl] = layers.emission
        pnx, pny = labpbr.normals(layers.height, NORMAL_STRENGTH)
        nx[sl], ny[sl] = pnx, pny
        filled[sl] = True
    steps = int(GAP * PPU)
    for a in (height, ao, nx, ny, smooth, emission, f0, porosity):
        a[...] = labpbr.spread(a, filled, steps)
    for c in range(3):
        albedo[..., c] = labpbr.spread(albedo[..., c], filled, steps)
    rgba = np.zeros(shape + (4,), dtype=np.uint8)
    rgba[..., :3] = np.clip(np.round(albedo * 255), 0, 255)
    rgba[..., 3] = np.where(filled, 255, 0)
    return rgba, labpbr.encode_n(nx, ny, ao, height), labpbr.encode_s(smooth, f0, porosity, emission if glows(material) else None), (tall, laid), filled


def glows(material: paint.Material) -> bool:
    """effects: returns whether material or any of its parts glows"""
    return material.glows or any(paint.MATERIALS[m].glows for _, m in material.parts)


# Items Heft draws in place of a pack's own plain model of them (D-0008; Rusty, 2026-10-26: "anything that doesn't look
# like what it's supposed to"). Judged by eye against Fresh Food's: its mutton a kinked slab, its cooked pork chop a
# striped plank, its bread a grooved board, its potato a box. A pair's other half goes with it, so raw and cooked match.
OVER_PACKS = {
    "minecraft:mutton", "minecraft:cooked_mutton", "minecraft:porkchop", "minecraft:cooked_porkchop", "minecraft:bread",
    "minecraft:potato", "minecraft:baked_potato",
}


# Roles drawn with a tint index: a tipped arrow's head takes its potion's colour (0); a spawn egg's shell its mob's
# first colour (0) and its spots the second (1).
TINTED = {"head": 0, "egg_base": 0, "egg_spot": 1}


def shape_json(item: str, klass: Klass, material: str, sheet_laid) -> dict:
    """effects: returns the item's shape file: a model file's elements, textures and display, uvs on
    its sheet (a face's uv runs 0 to 16 across the sheet each way, so a taller sheet's v is scaled), and
    what Heft reads of it under its "heft" key"""
    tall, laid = sheet_laid
    v = 16.0 / tall
    ns, path = item.split(":")
    sprite = f"heft:solid/{ns}/{path}"
    elements = []
    for box in klass.boxes:
        faces = {}
        for d in DIRECTIONS:
            role = box.roles[d]
            if role is None:
                continue
            w, h = box.face_size(d)
            p = laid[(role, w, h)]
            faces[d] = {"uv": [p.u, p.v * v, p.u + w, (p.v + h) * v], "texture": "#0"}
            if role in TINTED:
                faces[d]["tintindex"] = TINTED[role]
        e = {"from": list(box.frm), "to": list(box.to), "faces": faces}
        if box.rotation:
            r = box.rotation
            e["rotation"] = {"origin": list(r.origin), "axis": r.axis, "angle": r.angle, "rescale": r.rescale}
        elements.append(e)
    display = {k: {"rotation": list(v.rotation), "translation": list(v.translation), "scale": list(v.scale)}
               for k, v in klass.display.items()}
    meta = {"class": klass.name, "material": material, "lies": klass.lies}
    if item in OVER_PACKS:
        meta["over_packs"] = True
    if klass.size:
        meta["size"] = klass.size
    return {"heft": meta, "textures": {"0": sprite, "particle": sprite}, "elements": elements, "display": display}


# How Set It Down shares a spot between several of a class (the handoff's §4.4): flat things climb in a pile,
# lumpy things spread in a cluster; a class not named here is one to a spot unless Set It Down's own tags say otherwise.
STACKING = {
    "ingot": "pile", "nugget": "pile", "bar": "pile", "plate": "pile", "book": "pile", "record": "pile", "rod": "pile",
    "bone": "pile", "steak": "pile", "chop": "pile", "disc": "pile", "loaf": "pile", "pie": "pile", "pie_slice": "pile", "stack": "pile",
    "raw": "cluster", "lump": "cluster", "gem": "cluster", "shard": "cluster", "dust": "cluster", "orb": "cluster",
    "fruit": "cluster", "egg": "cluster", "berries": "cluster", "tuber": "cluster", "root": "cluster",
    "drumstick": "cluster",
}
# Meat by its size (Rusty, 2026-10-26): small cuts share a spot (a chop, a slice, mince, chicken cuts, by their class
# above); a whole joint is one to a spot: a steak, a leg of mutton, a whole chicken or rabbit, a whole fish. Set It
# Down's stackable_single outranks its pile and cluster tags (its D-0009), so these hold against its #c: food tags.
SINGLE = {
    "minecraft:beef", "minecraft:cooked_beef", "minecraft:mutton", "minecraft:cooked_mutton", "minecraft:rotten_flesh",
    "minecraft:chicken", "minecraft:cooked_chicken", "minecraft:rabbit", "minecraft:cooked_rabbit",
    "minecraft:cod", "minecraft:cooked_cod", "minecraft:salmon", "minecraft:cooked_salmon",
    "minecraft:tropical_fish", "minecraft:pufferfish",
}
TAGS = ASSETS.parents[1] / "data" / "setitdown" / "tags" / "item"


def write_tags() -> dict[str, list[str]]:
    """effects: writes Set It Down's size and stacking tags for every item by its class (data/setitdown/tags/item),
    each entry not required, so an item of a mod the pack lacks is skipped; returns the tags' members"""
    members: dict[str, list[str]] = {}
    for item, (class_name, _) in sorted(ITEMS.items()):
        if item.startswith("_kind:"):
            continue
        klass = CLASSES[class_name]()
        if klass.size:
            members.setdefault(f"size/{klass.size}", []).append(item)
        kind = "single" if item in SINGLE else STACKING.get(class_name)
        if kind:
            members.setdefault(f"stackable_{kind}", []).append(item)
    for tag, items in members.items():
        out = TAGS / f"{tag}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps({"replace": False, "values": [{"id": i, "required": False} for i in items]}, indent=1) + "\n")
    return members


def main() -> None:
    sprites = []
    for item, (class_name, material_name) in ITEMS.items():
        ns, path = item.split(":")
        klass = CLASSES[class_name]()
        material = paint.MATERIALS[material_name]
        rgba, n, s, laid, _ = sheet(klass, material)
        tex = ASSETS / "textures" / "solid" / ns
        labpbr.png(rgba, tex / f"{path}.png")
        labpbr.png(n, tex / f"{path}_n.png")
        labpbr.png(s, tex / f"{path}_s.png")
        out = ASSETS / "shapes" / ns / f"{path}.json"
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(shape_json(item, klass, material_name, laid), indent=1) + "\n")
        sprites.append(f"heft:solid/{ns}/{path}")
        print("wrote", item, class_name, material_name)
    atlas = ASSETS / "atlases" / "items.json"
    atlas.parent.mkdir(parents=True, exist_ok=True)
    # The shapes' sheets one by one; the long tail's (Tier B), made on the client, by the folder they are served in.
    sources = [{"type": "single", "resource": r} for r in sorted(sprites)] + [{"type": "directory", "source": "gen", "prefix": "gen/"}]
    atlas.write_text(json.dumps({"sources": sources}, indent=1) + "\n")
    print("wrote", atlas, len(sprites), "sprites")
    tags = write_tags()
    print("wrote Set It Down's tags:", {k: len(v) for k, v in sorted(tags.items())})


if __name__ == "__main__":
    main()
