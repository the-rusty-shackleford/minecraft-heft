"""preview: every built item's shape drawn offline, before the game sees it.

    uv run --no-project --with numpy --with pillow python devtools/art/preview.py [out.png] [item-substring ...]

Each item's shape file and sheet as the game would bake them (one axis of turn per element, about its
origin, stretched back if rescaled), drawn by the shared bbgen renderer from the inventory's three-quarter
view, the front, the side and the top, and once more at the size of an inventory slot (48 pixels, GUI
scale 3), as the hotbar shows it. One row per item.

Copyright 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[2] / "tools" / "bbgen"))
import render  # noqa: E402

ASSETS = HERE.parents[1] / "src" / "main" / "resources" / "assets" / "heft"
CORNERS = {
    "north": lambda x0, y0, z0, x1, y1, z1: [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
    "south": lambda x0, y0, z0, x1, y1, z1: [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
    "east": lambda x0, y0, z0, x1, y1, z1: [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
    "west": lambda x0, y0, z0, x1, y1, z1: [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
    "up": lambda x0, y0, z0, x1, y1, z1: [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
    "down": lambda x0, y0, z0, x1, y1, z1: [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
}
VIEWS = [("gui", 225, 30), ("front", 0, 0), ("side", 90, 0), ("top", 0, 89)]


def turned(p, rotation):
    """The game's element turn: about one axis through the origin, then stretched back if rescaled."""
    if not rotation or rotation["angle"] == 0:
        return p
    axis = "xyz".index(rotation["axis"])
    o = rotation["origin"]
    a = math.radians(rotation["angle"])
    q = [p[i] - o[i] for i in range(3)]
    i, j = [k for k in range(3) if k != axis]
    # Counter-clockwise looking down the axis, as the game turns (x: y to z; y: z to x; z: x to y).
    if axis == 1:
        i, j = j, i
    qi, qj = q[i] * math.cos(a) - q[j] * math.sin(a), q[i] * math.sin(a) + q[j] * math.cos(a)
    q[i], q[j] = qi, qj
    if rotation.get("rescale"):
        s = 1 / math.cos(a)
        q[i] *= s
        q[j] *= s
    return tuple(q[k] + o[k] for k in range(3))


def faces(shape: dict):
    for e in shape["elements"]:
        x0, y0, z0 = e["from"]
        x1, y1, z1 = e["to"]
        for name, face in e["faces"].items():
            u0, v0, u1, v1 = face["uv"]
            yield ([turned(c, e.get("rotation")) for c in CORNERS[name](x0, y0, z0, x1, y1, z1)],
                   [(u0, v0), (u1, v0), (u1, v1), (u0, v1)], name)


def row(item: str) -> Image.Image:
    ns, path = item.split(":")
    shape = json.loads((ASSETS / "shapes" / ns / f"{path}.json").read_text())
    sprite = shape["textures"]["0"].split(":")[1]
    tex = np.asarray(Image.open(ASSETS / "textures" / f"{sprite}.png").convert("RGBA")).astype(np.float32)
    fs = list(faces(shape))
    out = Image.new("RGB", (4 * 260 + 120, 280), (205, 210, 215))
    d = ImageDraw.Draw(out)
    for k, (label, yaw, pitch) in enumerate(VIEWS):
        img, _ = render.render_view(fs, tex, (16, 16), yaw, pitch, (256, 256), [], zoom=0.9)
        out.paste(img, (k * 260, 0))
        d.text((k * 260 + 4, 258), f"{item} {label}", fill=(0, 0, 0))
    icon, _ = render.render_view(fs, tex, (16, 16), 225, 30, (192, 192), [], zoom=0.9)
    small = icon.resize((48, 48), Image.BILINEAR).resize((96, 96), Image.NEAREST)
    out.paste(small, (4 * 260 + 12, 40))
    d.text((4 * 260 + 12, 140), "slot, 2x", fill=(0, 0, 0))
    return out


def main(argv: list[str]) -> None:
    sys.path.insert(0, str(HERE))
    import build
    out = Path(argv[1]) if len(argv) > 1 else HERE / "preview.png"
    wanted = argv[2:]
    items = [i for i in build.ITEMS if not wanted or any(w in i for w in wanted)]
    rows = [row(i) for i in items]
    sheet = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)), (205, 210, 215))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y))
        y += r.height
    sheet.save(out)
    print("wrote", out, len(rows), "items")


if __name__ == "__main__":
    main(sys.argv)
