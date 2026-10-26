"""paint: a patch of a solid item's sheet, painted from the measured numbers alone (D-0001).

This file never opens a resource pack and never imports the measuring side; what it knows of the
pack beside it is heft-style.json: value percentiles, a palette of eight, grain, smoothness, the
height and tilt its normals keep. The design -- where a face bevels, what stands proud -- is ours,
and height comes from that design, never from albedo.

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
class Material:
    name: str
    klass: str  # the measured class it takes its numbers from
    f0: int  # LabPBR F0: a dielectric's 0-229, or a metal's code
    glows: bool = False


MATERIALS = {
    "iron": Material("iron", "iron", labpbr.IRON),
    "gold": Material("gold", "gold", labpbr.GOLD),
    "copper": Material("copper", "copper", labpbr.COPPER),
    "netherite": Material("netherite", "netherite", labpbr.ALBEDO_METAL),
}


@dataclass
class Layers:
    """One painted patch, (h, w) each: colour 0..1, height in levels, occlusion 0..1, smoothness 0..1."""
    albedo: np.ndarray
    height: np.ndarray
    ao: np.ndarray
    smooth: np.ndarray
    f0: int
    emission: np.ndarray | None = None


# Bevel width in texels at a face's edge: the rim the geometry's own edge rounds off into.
BEVEL = 3.0
# Levels a bevel falls from the face to the very edge.
BEVEL_DROP = 14.0
# How dark the albedo goes towards an edge, a fraction of the measured rim (the pack outlines its
# faces; ours are small, so less).
RIM_SHARE = 0.6
# Each role's tone and how far its bevel reaches: a top catches the eye, a base is underneath.
ROLES = {
    "ingot_top": (0.60, 1.0),
    "ingot_ledge": (0.58, 1.0),
    "ingot_side": (0.50, 0.7),
    "ingot_base": (0.38, 0.5),
}


def paint(role: str, w: int, h: int, material: Material) -> Layers:
    """requires: role in ROLES; w, h > 0 texels
    effects: returns the patch of that role and size painted in material, the same every run"""
    numbers = STYLE[material.klass]
    tone, bevel_reach = ROLES[role]
    rng = noise.rng_for(material.name, role, w, h)
    broad = noise.value_noise(rng, h, w, 12.0) - 0.5
    mid = noise.value_noise(rng, h, w, 3.0) - 0.5
    fine = rng.random((h, w)) - 0.5
    grain = numbers["grain"]
    # The measured steps between neighbours 1, 4 and 16 texels apart set how much each scale of noise moves the tone.
    spread_of = lambda step: step / max(numbers["value"][4] - numbers["value"][0], 1e-3)  # noqa: E731
    edge = np.clip(1.0 - noise.edge_distance(h, w) / BEVEL, 0.0, 1.0) * bevel_reach
    key = (tone + broad * spread_of(grain[2]) * 4 + mid * spread_of(grain[1]) * 3 + fine * spread_of(grain[0]) * 2
           - edge * max(numbers["rim"], 0.0) * RIM_SHARE * 2)
    albedo = style.ramp(numbers["palette"], key)
    height = labpbr.FACE - edge * BEVEL_DROP + mid * 3.0
    height = np.minimum(height, labpbr.CREST)
    ao = 1.0 - edge * 0.06
    s_med = numbers.get("smoothness", [0.45, 0.5])[0]
    smooth = np.clip(s_med + broad * 0.10 - edge * 0.05, 0.0, 1.0)
    return Layers(albedo, height, ao, smooth, material.f0)
