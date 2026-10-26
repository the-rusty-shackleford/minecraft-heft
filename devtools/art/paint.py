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


FINISHES = {
    "metal": Finish(broad=0.35, fine=0.18, speck=0.05, bevel=3.0, bevel_drop=12, relief=5, relief_scale=14, smooth=0.62, smooth_spread=0.10),
    "rock": Finish(broad=0.55, fine=0.45, speck=0.18, bevel=4.0, bevel_drop=16, relief=24, relief_scale=5, smooth=0.22, smooth_spread=0.08, porosity=24),
    "gem": Finish(broad=0.25, fine=0.10, speck=0.03, bevel=2.0, bevel_drop=8, relief=0, relief_scale=8, smooth=0.92, smooth_spread=0.03),
    "crystal": Finish(broad=0.30, fine=0.15, speck=0.05, bevel=2.0, bevel_drop=8, relief=4, relief_scale=10, smooth=0.80, smooth_spread=0.06),
    "powder": Finish(broad=0.30, fine=0.40, speck=0.45, bevel=3.0, bevel_drop=10, relief=14, relief_scale=1.5, smooth=0.12, smooth_spread=0.06, porosity=40),
    "mineral": Finish(broad=0.45, fine=0.30, speck=0.12, bevel=3.0, bevel_drop=14, relief=18, relief_scale=7, smooth=0.38, smooth_spread=0.12, porosity=10),
    "clay": Finish(broad=0.35, fine=0.30, speck=0.15, bevel=3.0, bevel_drop=12, relief=12, relief_scale=3, smooth=0.18, smooth_spread=0.05, porosity=45),
}

# The tone each role sits at along its palette (0 its darkest kept colour, 1 its lightest), and how much of
# its finish's bevel it carries: a top catches the eye, a chamfer sits between, a side and a base fall away.
ROLES = {
    "top": (0.62, 1.0), "chamfer": (0.70, 0.6), "side": (0.50, 0.8), "base": (0.38, 0.6), "end": (0.50, 0.15),
    "rock_top": (0.60, 1.0), "rock": (0.48, 1.0),
    "table": (0.70, 1.0), "facet": (0.58, 1.0), "girdle": (0.48, 0.8), "facet_low": (0.40, 1.0),
    "crystal": (0.58, 1.0), "crystal_end": (0.66, 0.8),
    "dust_top": (0.60, 1.0), "dust_side": (0.48, 1.0),
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


SPRITE = (0.25, 1.0)  # a palette measured off a mod's 16x sprite: its outline left out
M = labpbr
MATERIALS = {m.name: m for m in (
    Material("iron", "iron", "metal", M.IRON), Material("gold", "gold", "metal", M.GOLD),
    Material("copper", "copper", "metal", M.COPPER), Material("netherite", "netherite", "metal", M.ALBEDO_METAL),
    Material("brass", "brass", "metal", M.ALBEDO_METAL, keep=SPRITE), Material("zinc", "zinc", "metal", M.ALBEDO_METAL, keep=SPRITE),
    Material("steel", "steel", "metal", M.IRON, keep=SPRITE), Material("voidsteel", "voidsteel", "metal", M.ALBEDO_METAL, keep=SPRITE),
    Material("raw_iron", "raw_iron", "rock", 18), Material("raw_gold", "raw_gold", "rock", 18),
    Material("raw_copper", "raw_copper", "rock", 18), Material("raw_zinc", "raw_zinc", "rock", 18, keep=SPRITE),
    Material("raw_voidsteel", "raw_voidsteel", "rock", 18, keep=SPRITE), Material("raw_galestone", "raw_galestone", "rock", 18, keep=SPRITE),
    Material("diamond", "diamond", "gem", 44), Material("emerald", "emerald", "gem", 14),
    Material("rose_quartz", "rose_quartz", "gem", 12, keep=SPRITE), Material("galestone", "galestone", "gem", 12, keep=SPRITE),
    Material("amethyst", "amethyst", "crystal", 12), Material("prismarine", "prismarine", "crystal", 10),
    Material("echo", "echo", "crystal", 12, glows=True, keep=SPRITE), Material("quartz", "quartz", "crystal", 12),
    Material("coal_shard", "coal_shard", "crystal", 10, keep=SPRITE),
    Material("redstone", "redstone_dust_item", "powder", 6, keep=SPRITE), Material("glowstone", "glowstone", "powder", 6, glows=True),
    Material("gunpowder", "gunpowder", "powder", 5, keep=SPRITE), Material("sugar", "sugar", "powder", 8, keep=SPRITE),
    Material("bone_meal", "bone_meal", "powder", 6, keep=SPRITE), Material("blaze_powder", "blaze_powder", "powder", 6, glows=True, keep=SPRITE),
    Material("coal", "coal_item", "mineral", 10, keep=SPRITE), Material("charcoal", "charcoal", "mineral", 8, keep=SPRITE),
    Material("lapis", "lapis", "mineral", 10), Material("netherite_scrap", "netherite_scrap", "mineral", 20, keep=SPRITE),
    Material("brick", "brick", "clay", 8), Material("nether_brick", "nether_brick", "clay", 8),
)}


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
    numbers = STYLE[material.colour]
    finish = FINISHES[material.finish]
    tone, bevel_reach = ROLES[role]
    rng = noise.rng_for(material.name, role, w, h)
    broad = noise.value_noise(rng, h, w, 12.0) - 0.5
    fine = noise.value_noise(rng, h, w, 2.5) - 0.5
    speck = rng.random((h, w)) - 0.5
    relief_field = noise.value_noise(rng, h, w, finish.relief_scale)
    edge = np.clip(1.0 - noise.edge_distance(h, w) / finish.bevel, 0.0, 1.0) * bevel_reach
    key = tone + broad * finish.broad + fine * finish.fine + speck * finish.speck - edge * 0.12
    lo, hi = material.keep
    palette = numbers["palette"][int(round(lo * 7)):int(round(hi * 7)) + 1]
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
