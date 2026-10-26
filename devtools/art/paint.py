"""paint: a patch of a solid item's sheet, painted from the measured numbers alone (D-0001).

This file never opens a resource pack and never imports the measuring side; what it knows of the
packs and mods beside it is heft-style.json: value percentiles, a palette of eight, grain, smoothness.
The design -- where a face rolls off at its edge, how a cast top ripples, where an ore's rock pits,
which grains of a powder glow -- is ours, and height comes from that design, never from albedo.

A material is a colour (a measured class) and a finish (ours): cast metal, ore rock, gem, crystal,
powder, mineral, fired clay. A role is what a face is to its class (an ingot's top, a gem's table).

Copyright 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later.
"""
from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / "tools" / "pbr"))
import labpbr  # noqa: E402
import noise  # noqa: E402
import style  # noqa: E402

STYLE = style.load(Path(__file__).resolve().parent / "heft-style.json")


@dataclass(frozen=True)
class Finish:
    """How a surface is made, ours: the tone each role sits at, how much its broad and fine variation move
    it, how a face rolls off at its edges, the relief its height carries, and its smoothness."""
    broad: float  # tone moved by broad variation (12 texels apart), in palette steps
    fine: float  # tone moved by grain (2 to 3 texels apart)
    speck: float  # tone moved texel by texel
    bevel: float  # texels a face rolls off over at its edges
    bevel_drop: float  # levels the roll-off falls
    relief: float  # levels of the finish's own relief (pits, ripples, grains), hung below the face
    relief_scale: float  # texels between its features
    smooth: float  # perceptual smoothness
    smooth_spread: float
    porosity: int = 0
    grain: tuple[float, float] | None = None  # (across, down) texels between grain lines, for wood or a stack of pages


FINISHES = {
    "metal": Finish(broad=0.35, fine=0.18, speck=0.05, bevel=3.0, bevel_drop=12, relief=5, relief_scale=14, smooth=0.62, smooth_spread=0.10),
    "rock": Finish(broad=0.55, fine=0.45, speck=0.18, bevel=4.0, bevel_drop=16, relief=24, relief_scale=5, smooth=0.22, smooth_spread=0.08, porosity=24),
    "gem": Finish(broad=0.25, fine=0.10, speck=0.03, bevel=2.0, bevel_drop=8, relief=0, relief_scale=8, smooth=0.92, smooth_spread=0.03),
    "crystal": Finish(broad=0.30, fine=0.15, speck=0.05, bevel=2.0, bevel_drop=8, relief=4, relief_scale=10, smooth=0.80, smooth_spread=0.06),
    "powder": Finish(broad=0.30, fine=0.40, speck=0.45, bevel=3.0, bevel_drop=10, relief=14, relief_scale=1.5, smooth=0.12, smooth_spread=0.06, porosity=40),
    "mineral": Finish(broad=0.45, fine=0.30, speck=0.12, bevel=3.0, bevel_drop=14, relief=18, relief_scale=7, smooth=0.38, smooth_spread=0.12, porosity=10),
    "clay": Finish(broad=0.35, fine=0.30, speck=0.15, bevel=3.0, bevel_drop=12, relief=12, relief_scale=3, smooth=0.18, smooth_spread=0.05, porosity=45),
    "wood": Finish(broad=0.45, fine=0.12, speck=0.06, bevel=3.0, bevel_drop=10, relief=8, relief_scale=4, smooth=0.30, smooth_spread=0.06, porosity=20, grain=(40.0, 1.6)),
    "stack": Finish(broad=0.25, fine=0.10, speck=0.04, bevel=1.5, bevel_drop=4, relief=10, relief_scale=1, smooth=0.20, smooth_spread=0.04, porosity=30, grain=(60.0, 1.1)),
    "paper": Finish(broad=0.20, fine=0.10, speck=0.05, bevel=2.0, bevel_drop=4, relief=3, relief_scale=6, smooth=0.22, smooth_spread=0.04, porosity=35),
    "skin": Finish(broad=0.35, fine=0.12, speck=0.05, bevel=2.5, bevel_drop=6, relief=3, relief_scale=6, smooth=0.55, smooth_spread=0.08, porosity=5),
    "gilt": Finish(broad=0.30, fine=0.10, speck=0.04, bevel=2.5, bevel_drop=6, relief=2, relief_scale=8, smooth=0.75, smooth_spread=0.06),
    "shell": Finish(broad=0.25, fine=0.15, speck=0.10, bevel=2.5, bevel_drop=6, relief=2, relief_scale=3, smooth=0.40, smooth_spread=0.04, porosity=20),
    "crust": Finish(broad=0.45, fine=0.30, speck=0.12, bevel=3.0, bevel_drop=10, relief=12, relief_scale=4, smooth=0.22, smooth_spread=0.06, porosity=40),
    "meat": Finish(broad=0.50, fine=0.25, speck=0.08, bevel=3.0, bevel_drop=8, relief=8, relief_scale=5, smooth=0.50, smooth_spread=0.10, porosity=10),
    "cooked": Finish(broad=0.50, fine=0.35, speck=0.12, bevel=3.0, bevel_drop=10, relief=12, relief_scale=4, smooth=0.32, smooth_spread=0.10, porosity=20),
    "scales": Finish(broad=0.35, fine=0.25, speck=0.10, bevel=2.5, bevel_drop=6, relief=5, relief_scale=2, smooth=0.60, smooth_spread=0.08),
    "flesh": Finish(broad=0.35, fine=0.25, speck=0.15, bevel=2.0, bevel_drop=6, relief=4, relief_scale=3, smooth=0.50, smooth_spread=0.06, porosity=10),
    "liquid": Finish(broad=0.40, fine=0.15, speck=0.08, bevel=1.0, bevel_drop=2, relief=4, relief_scale=4, smooth=0.80, smooth_spread=0.06),
    "ceramic": Finish(broad=0.15, fine=0.08, speck=0.03, bevel=2.0, bevel_drop=6, relief=0, relief_scale=8, smooth=0.70, smooth_spread=0.04),
    "hide": Finish(broad=0.45, fine=0.25, speck=0.08, bevel=3.0, bevel_drop=8, relief=8, relief_scale=5, smooth=0.32, smooth_spread=0.08, porosity=15),
}

# The tone each role sits at along its palette (0 its darkest kept colour, 1 its lightest), and how much of
# its finish's bevel it carries: a top catches the eye, a chamfer sits between, a side and a base fall away.
ROLES = {
    "top": (0.62, 1.0), "chamfer": (0.70, 0.6), "side": (0.50, 0.8), "base": (0.38, 0.6), "end": (0.50, 0.15),
    "rock_top": (0.60, 1.0), "rock": (0.48, 1.0),
    "table": (0.70, 1.0), "facet": (0.58, 1.0), "girdle": (0.48, 0.8), "facet_low": (0.40, 1.0),
    "crystal": (0.58, 1.0), "crystal_end": (0.66, 0.8),
    "dust_top": (0.60, 1.0), "dust_side": (0.48, 1.0),
    "rod": (0.55, 0.8), "rod_end": (0.62, 0.4),
    "sheet": (0.60, 0.6), "sheet_edge": (0.48, 0.3),
    "cover": (0.55, 1.0), "cover_edge": (0.45, 0.6), "pages": (0.70, 0.3),
    "board_face": (0.58, 1.0), "board_edge": (0.48, 0.6), "post": (0.50, 0.8), "bar": (0.45, 0.8), "chain": (0.55, 0.5),
    "shaft": (0.55, 0.5), "head": (0.58, 0.6), "fletch": (0.70, 0.3),
    "skin": (0.55, 0.6), "skin_top": (0.62, 0.6), "stem": (0.45, 0.4), "leaf": (0.55, 0.4),
    "crust": (0.50, 0.8), "crust_top": (0.62, 0.8), "filling": (0.58, 0.4),
    "meat": (0.58, 0.6), "meat_edge": (0.45, 0.5), "bone": (0.70, 0.5), "knob": (0.76, 0.8),
    "body": (0.55, 0.6), "fin": (0.48, 0.4), "rind": (0.50, 0.6), "flesh": (0.62, 0.4),
    "bowl": (0.50, 0.6), "fill": (0.60, 0.2), "dish": (0.70, 0.4), "food": (0.55, 0.5), "food_top": (0.62, 0.5),
    "bread": (0.55, 0.8), "bread_top": (0.62, 0.8),
    "egg_base": (0.70, 0.5), "egg_spot": (0.70, 0.3), "record": (0.40, 0.6), "label": (0.60, 0.3),
    "pail": (0.55, 0.6), "handle": (0.45, 0.4), "contents": (0.60, 0.2),
    "orb": (0.58, 0.6), "orb_top": (0.66, 0.6), "star": (0.65, 0.5), "shell": (0.55, 0.6), "shell_inner": (0.35, 0.3),
    "tube": (0.55, 0.6), "cone": (0.62, 0.5), "horn": (0.58, 0.5), "horn_tip": (0.48, 0.5),
    "figure": (0.58, 0.6), "wing": (0.52, 0.5), "seat": (0.55, 0.8), "strap": (0.45, 0.4),
}


@dataclass(frozen=True)
class Material:
    name: str
    colour: str  # the measured class its palette comes from
    finish: str
    f0: int  # LabPBR F0: a dielectric's 0-229, or a metal's code
    glows: bool = False
    keep: tuple[float, float] = (0.0, 1.0)  # the share of the palette kept, darkest to lightest: a 16x sprite's
    # darkest colours are its outline, never the material
    parts: tuple[tuple[str, str], ...] = ()  # role -> another material that role is made of (a book's pages, a sign's chains)
    band: tuple[float, float] | None = None  # only the palette's colours whose hue lies in this band (degrees, wrapping)
    minus: str | None = None  # without the palette's colours close to another class's (a stew's sprite less its bowl)
    gain: float = 1.0  # each kept colour's value multiplied by it: a 16x sprite drawn near black (coal) lifted so its faces part
    chroma: float = 1.0  # and its saturation, so a lift does not bring up a cast the sprite's near-blacks hid


SPRITE = (0.25, 1.0)  # a palette measured off a mod's 16x sprite: its outline left out
M = labpbr
MATERIALS = {m.name: m for m in (
    Material("iron", "iron", "metal", M.IRON), Material("gold", "gold", "metal", M.GOLD),
    Material("copper", "copper", "metal", M.COPPER), Material("netherite", "netherite", "metal", M.ALBEDO_METAL),
    # Create's brass sprite keeps half its palette at a near-white yellow; its middle is the metal (Rusty, 2026-10-26: too pale).
    Material("brass", "brass", "metal", M.ALBEDO_METAL, keep=(0.25, 0.5)), Material("zinc", "zinc", "metal", M.ALBEDO_METAL, keep=SPRITE),
    Material("steel", "steel", "metal", M.IRON, keep=SPRITE), Material("voidsteel", "voidsteel", "metal", M.ALBEDO_METAL, keep=SPRITE),
    Material("raw_iron", "raw_iron", "rock", 18), Material("raw_gold", "raw_gold", "rock", 18),
    Material("raw_copper", "raw_copper", "rock", 18), Material("raw_zinc", "raw_zinc", "rock", 18, keep=SPRITE),
    Material("raw_voidsteel", "raw_voidsteel", "rock", 18, keep=SPRITE), Material("raw_galestone", "raw_galestone", "rock", 18, keep=SPRITE),
    Material("diamond", "diamond", "gem", 44), Material("emerald", "emerald", "gem", 14),
    Material("rose_quartz", "rose_quartz", "gem", 12, keep=SPRITE), Material("galestone", "galestone", "gem", 12, keep=SPRITE),
    Material("amethyst", "amethyst", "crystal", 12), Material("prismarine", "prismarine", "crystal", 10),
    Material("echo", "echo", "crystal", 12, glows=True, keep=SPRITE), Material("quartz", "quartz", "crystal", 12),
    Material("coal_shard", "coal_shard", "crystal", 10, keep=SPRITE, gain=1.9, chroma=0.35),
    Material("redstone", "redstone_dust_item", "powder", 6, keep=SPRITE), Material("glowstone", "glowstone", "powder", 6, glows=True),
    Material("gunpowder", "gunpowder", "powder", 5, keep=SPRITE), Material("sugar", "sugar", "powder", 8, keep=SPRITE),
    Material("bone_meal", "bone_meal", "powder", 6, keep=SPRITE), Material("blaze_powder", "blaze_powder", "powder", 6, glows=True, keep=SPRITE),
    # Coal's sprite tops out at #353637: its faces drew as one black (Rusty, 2026-10-26: lift it).
    Material("coal", "coal_item", "mineral", 10, keep=SPRITE, gain=1.9, chroma=0.35), Material("charcoal", "charcoal", "mineral", 8, keep=SPRITE),
    Material("lapis", "lapis", "mineral", 10), Material("netherite_scrap", "netherite_scrap", "mineral", 20, keep=SPRITE),
    Material("brick", "brick", "clay", 8), Material("nether_brick", "nether_brick", "clay", 8),
    # Phase 3: rods, plates, books, signs, arrows.
    Material("stick", "stick", "wood", 6, keep=SPRITE), Material("blaze_rod", "blaze_rod", "crystal", 10, glows=True, keep=SPRITE),
    Material("breeze_rod", "breeze_rod", "crystal", 10, keep=SPRITE), Material("bone", "bone", "mineral", 8, keep=SPRITE),
    Material("paper", "paper", "paper", 4, keep=SPRITE), Material("map_paper", "map_paper", "paper", 4, keep=SPRITE),
    Material("leather", "leather", "hide", 6, keep=SPRITE), Material("rabbit_hide", "rabbit_hide", "hide", 6, keep=SPRITE),
    Material("phantom_membrane", "phantom_membrane", "hide", 8, keep=SPRITE), Material("kangaroo_hide", "kangaroo_hide", "hide", 6, keep=SPRITE),
    Material("pages", "paper", "stack", 4, keep=(0.4, 1.0)),
    Material("book", "book", "hide", 6, keep=(0.15, 0.6), parts=(("pages", "pages"),)),
    Material("writable_book", "writable_book", "hide", 6, keep=(0.15, 0.6), parts=(("pages", "pages"),)),
    Material("written_book", "written_book", "hide", 6, keep=(0.15, 0.6), parts=(("pages", "pages"),)),
    Material("enchanted_book", "enchanted_book", "hide", 6, keep=(0.15, 0.6), parts=(("pages", "pages"),)),
    Material("knowledge_book", "knowledge_book", "hide", 6, keep=(0.25, 0.7), parts=(("pages", "pages"),)),
    Material("chain", "chain", "metal", M.IRON),
    *[Material(f"{w}_wood", f"{w}_planks", "wood", 6, parts=(("chain", "chain"),))
      for w in ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped", "pale_oak", "poplar")],
    Material("flint_head", "flint", "crystal", 10, keep=SPRITE), Material("feather_fletch", "feather", "paper", 4, keep=SPRITE),
    Material("spectral_head", "spectral_arrow", "metal", M.GOLD, keep=(0.55, 1.0)), Material("tipped_head", "iron", "crystal", 10),
    Material("arrow", "stick", "wood", 6, keep=SPRITE, parts=(("head", "flint_head"), ("fletch", "feather_fletch"))),
    # Phase 4's parts: what a food's class makes of other things.
    Material("food_stem", "stick", "wood", 6, keep=SPRITE), Material("foliage", "food_minecraft_carrot", "skin", 6, keep=(0.0, 1.0), band=(60, 170)),
    Material("bowl_wood", "bowl", "wood", 6, keep=(0.45, 1.0)), Material("dish", "quartz", "ceramic", 12),
    Material("food_bone", "bone", "mineral", 8, keep=SPRITE), Material("bread_food", "food_minecraft_bread", "crust", 6, keep=SPRITE),
    Material("spectral_arrow", "stick", "wood", 6, keep=SPRITE, parts=(("head", "spectral_head"), ("fletch", "feather_fletch"))),
    Material("tipped_arrow", "stick", "wood", 6, keep=SPRITE, parts=(("head", "tipped_head"), ("fletch", "feather_fletch"))),
)}


# The hue band a class's own colour is taken from, where its sprite also draws something else (a carrot's leaves).
BAND = {"wedge": (330, 50), "root": (0, 65)}


def _odd_materials() -> list[Material]:
    """Each of phase 5's items' material: its own sprite's palette, its finish, its class's parts."""
    import odds
    out = [Material("egg_white", "quartz", "shell", 12), Material("record_body", "record_body", "metal", 30, keep=(0.0, 0.35)),
           Material("bucket_iron", "iron", "metal", M.IRON)]
    for item, (klass, finish) in odds.ODDS.items():
        name = odds.colour(item)
        parts, band, minus = (), None, None
        if klass == "record":
            parts, band = (("record", "record_body"),), (0, 359.9)
        keep = SPRITE
        if klass in ("bucket", "bucket_empty"):
            # What the bucket holds: the sprite's saturated colours (water's blue is among its darker ones), or for a
            # white (milk, powder snow) its lightest; the pail and handle are iron.
            parts = (("pail", "bucket_iron"), ("handle", "bucket_iron"))
            keep, band = ((0.8, 1.0), None) if item in odds.WHITE else ((0.0, 1.0), (0, 359.9))
        out.append(Material(item, name, finish, odds.F0.get(finish, 10), glows=item in odds.GLOWS, keep=keep, parts=parts,
                            band=band, minus=minus))
    return out


def _food_materials() -> list[Material]:
    """Each food item's material: its own sprite's palette (its outline left out), its finish, its class's parts."""
    import food
    parts = {
        "fruit": (("stem", "food_stem"), ("leaf", "foliage")), "root": (("leaf", "foliage"),), "berries": (("stem", "food_stem"),),
        "drumstick": (("bone", "food_bone"),), "bowl": (("bowl", "bowl_wood"),), "plated": (("dish", "dish"),),
        "stack": (("bread", "bread_food"), ("bread_top", "bread_food")),
    }
    minus = {"bowl": "bowl", "plated": "quartz", "stack": "food_minecraft_bread"}
    out = []
    for item, (klass, finish) in food.FOOD.items():
        name = food.colour(item)
        p = parts.get(klass, ())
        if klass == "wedge":
            out.append(Material(name + "_rind", name, finish, 8, keep=SPRITE, band=(60, 170)))
            p = (("rind", name + "_rind"),)
        out.append(Material(item, name, finish, food.F0.get(finish, 8), glows=item in food.GLOWS, keep=SPRITE, parts=p,
                            band=food.BAND.get(item, BAND.get(klass)), minus=minus.get(klass)))
    return out


def select(palette: list[str], material: Material) -> list[str]:
    """effects: returns the colours of palette (darkest first) the material keeps: its share by value, then those in its
    hue band and not close to its `minus` class's colours; the share alone if a filter would leave none"""
    import colorsys
    lo, hi = material.keep
    kept = palette[int(round(lo * 7)):int(round(hi * 7)) + 1]
    out = kept
    if material.band is not None:
        a, b = material.band

        def inside(c):
            r, g, bl = style.rgb(c)
            h, sat, _ = colorsys.rgb_to_hsv(r, g, bl)
            d = h * 360
            return sat > 0.12 and ((a <= d <= b) if a <= b else (d >= a or d <= b))
        out = [c for c in out if inside(c)]
    if material.minus is not None:
        other = [style.rgb(c) for c in STYLE[material.minus]["palette"]]
        out = [c for c in out if min(float(np.abs(style.rgb(c) - o).sum()) for o in other) > 0.18]
    out = out if out else kept
    if (material.gain, material.chroma) != (1.0, 1.0):
        out = [_gained(c, material.gain, material.chroma) for c in out]
    return out


def _gained(c: str, gain: float, chroma: float) -> str:
    """effects: returns colour c with its value multiplied by gain and its saturation by chroma (each at most 1), its hue kept"""
    import colorsys
    h, s, v = colorsys.rgb_to_hsv(*style.rgb(c))
    r, g, b = colorsys.hsv_to_rgb(h, min(1.0, s * chroma), min(1.0, v * gain))
    return "#%02x%02x%02x" % tuple(round(x * 255) for x in (r, g, b))


MATERIALS.update({m.name: m for m in _food_materials()})
MATERIALS.update({m.name: m for m in _odd_materials()})


def part(role: str, material: Material) -> Material:
    """effects: returns the material {@code role} is made of in material: a part's own, or material itself"""
    name = dict(material.parts).get(role)
    return material if name is None else MATERIALS[name]


@dataclass
class Layers:
    """One painted patch, (h, w) each: colour 0..1, height in levels, occlusion 0..1, smoothness 0..1, emission 0..1."""
    albedo: np.ndarray
    height: np.ndarray
    ao: np.ndarray
    smooth: np.ndarray
    f0: int
    porosity: int
    emission: np.ndarray | None = None


def paint(role: str, w: int, h: int, material: Material) -> Layers:
    """requires: role in ROLES; w, h > 0 texels
    effects: returns the patch of that role and size painted in material, the same every run"""
    material = part(role, material)
    numbers = STYLE[material.colour]
    finish = FINISHES[material.finish]
    tone, bevel_reach = ROLES[role]
    rng = noise.rng_for(material.name, role, w, h)
    broad = (noise.value_noise(rng, h, w, 12.0) if finish.grain is None
             else noise.value_noise_stretched(rng, h, w, *finish.grain)) - 0.5
    fine = noise.value_noise(rng, h, w, 2.5) - 0.5
    speck = rng.random((h, w)) - 0.5
    relief_field = noise.value_noise(rng, h, w, finish.relief_scale)
    edge = np.clip(1.0 - noise.edge_distance(h, w) / finish.bevel, 0.0, 1.0) * bevel_reach
    key = tone + broad * finish.broad + fine * finish.fine + speck * finish.speck - edge * 0.12
    palette = select(numbers["palette"], material)
    albedo = style.ramp(palette, np.clip(key, 0.0, 1.0))
    # Height: the face at its level; its edges rolled off; the finish's relief hung below the face (pits,
    # grains, a cast top's ripples), never above it.
    height = labpbr.FACE - edge * finish.bevel_drop - (1.0 - relief_field) * finish.relief
    height = np.minimum(height, labpbr.CREST)
    # Pits and grain hold a little dark: the occlusion of the finish's own relief.
    ao = 1.0 - edge * 0.05 - (1.0 - relief_field) * (finish.relief / 255.0) * 0.8
    smooth = np.clip(finish.smooth + broad * finish.smooth_spread - edge * 0.04 - (1.0 - relief_field) * 0.05, 0.0, 1.0)
    emission = None
    if material.glows:
        # The brightest grains glow: the lighter half of the tone, strongest at its top.
        emission = np.clip((key - (tone - 0.05)) * 3.0, 0.0, 1.0) * 0.85
    return Layers(albedo, height, ao, smooth, material.f0, finish.porosity, emission)
