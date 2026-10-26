"""Every sheet built: 128x density, mipmap-safe sizes, LabPBR maps beside it, within the ranges.

Partitions:
- each sheet: width and height multiples of 16; _n and _s present, the same size
- _n over opaque texels: height FLOOR..CREST, span within the cap, unit normals
- _s: emission only on materials that glow (or have a part that does); F0 the material's or its parts'
- transparent texels' colour spread from the faces (no black pulled in by filtering)
"""
import numpy as np
import pytest
from PIL import Image

import build
import labpbr
import paint

SHEETS = [(item, build.ASSETS / "textures" / "solid" / item.split(":")[0] / item.split(":")[1]) for item in build.ITEMS]


def load(path):
    return np.asarray(Image.open(path).convert("RGBA"))


@pytest.mark.parametrize("item,base", SHEETS)
def test_sizes_and_maps(item, base):
    a = load(f"{base}.png")
    n = load(f"{base}_n.png")
    s = load(f"{base}_s.png")
    h, w = a.shape[:2]
    assert h % 16 == 0 and w % 16 == 0
    assert n.shape == a.shape and s.shape == a.shape


@pytest.mark.parametrize("item,base", SHEETS)
def test_ranges(item, base):
    a, n, s = load(f"{base}.png"), load(f"{base}_n.png"), load(f"{base}_s.png")
    opaque = a[..., 3] > 0
    material = paint.MATERIALS[build.ITEMS[item][1]]
    assert labpbr.breaches_n(n, opaque) == []
    assert labpbr.breaches_s(s, opaque, build.glows(material)) == []
    allowed = {material.f0} | {paint.MATERIALS[m].f0 for _, m in material.parts}
    assert set(np.unique(s[..., 1][opaque]).tolist()) <= allowed


@pytest.mark.parametrize("item,base", SHEETS)
def test_no_black_beside_a_face(item, base):
    a = load(f"{base}.png")
    opaque = a[..., 3] > 0
    beside = np.zeros_like(opaque)
    for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        beside |= np.roll(opaque, (dy, dx), axis=(0, 1))
    beside &= ~opaque
    assert (a[..., :3][beside].max(axis=-1) > 8).all()
