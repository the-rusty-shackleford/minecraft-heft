"""layout: the patches of a class laid out on one square sheet, eight texels to the sixteenth (128 to the block).

Copyright 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later.
"""
from __future__ import annotations

from dataclasses import dataclass

from shapes import DIRECTIONS, Klass

PPU = 8  # texels per sixteenth: 128 to the block, the density of a 128x pack
SHEET = 16  # sixteenths across the sheet: 128 texels, a multiple of 16 so mipmaps keep every level
TALLER = (16, 24, 32)  # the sheet's heights tried in turn, sixteenths (128, 192, 256 texels: each a multiple of 16)
GAP = 0.25  # sixteenths between patches, filled from each patch's own edge (same material either side)
STEP = 0.25  # the skyline's column width, sixteenths


@dataclass(frozen=True)
class Patch:
    role: str
    w: float
    h: float
    u: float  # where it lies on the sheet, sixteenths from the left
    v: float  # and from the top


def patches(klass: Klass) -> tuple[float, dict[tuple[str, float, float], Patch]]:
    """effects: returns the least height in TALLER whose sheet holds the class's patches, and the patches
    laid on it; raises if none does"""
    for height in TALLER:
        try:
            return height, _laid(klass, height)
        except ValueError:
            continue
    raise ValueError(f"{klass.name}: its patches do not fit {SHEET} x {TALLER[-1]}")


def _laid(klass: Klass, height: float) -> dict[tuple[str, float, float], Patch]:
    """effects: returns the class's patches laid on a SHEET x height sheet, each GAP from its neighbours,
    keyed by (role, w, h): a skyline, each patch (tallest, then widest first) set as high as it will go,
    then as far left; raises if they do not fit"""
    wanted = sorted({(box.roles[d], *box.face_size(d)) for box in klass.boxes for d in DIRECTIONS if box.roles[d]},
                    key=lambda k: (-k[2], -k[1], k[0]))
    cols = int(SHEET / STEP)
    sky = [0.0] * cols  # how far down each column of the sheet is taken, sixteenths
    out: dict[tuple[str, float, float], Patch] = {}
    for role, w, h in wanted:
        span = int(-(-(w + GAP) // STEP))
        best = None
        for c in range(cols - span + 1):
            top = max(sky[c:c + span])
            if top + h + GAP <= height and (best is None or top < best[1]):
                best = (c, top)
        if best is None:
            raise ValueError(f"{klass.name}: its patches do not fit one sheet")
        c, top = best
        out[(role, w, h)] = Patch(role, w, h, c * STEP + GAP / 2, top + GAP / 2)
        for k in range(c, c + span):
            sky[k] = top + h + GAP
    return out
