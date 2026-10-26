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
    "minecraft:iron_ingot": ("ingot", "iron"),
}

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
    filled = np.zeros(shape, dtype=bool)
    f0 = material.f0
    for (role, w, h), p in laid.items():
        x0, y0 = round(p.u * PPU), round(p.v * PPU)
        pw, ph = round(w * PPU), round(h * PPU)
        layers = paint.paint(role, pw, ph, material)
        sl = (slice(y0, y0 + ph), slice(x0, x0 + pw))
        albedo[sl] = layers.albedo
        height[sl] = layers.height
        ao[sl] = layers.ao
        smooth[sl] = layers.smooth
        pnx, pny = labpbr.normals(layers.height, NORMAL_STRENGTH)
        nx[sl], ny[sl] = pnx, pny
        filled[sl] = True
    steps = int(GAP * PPU)
    for a in (height, ao, nx, ny, smooth):
        a[...] = labpbr.spread(a, filled, steps)
    for c in range(3):
        albedo[..., c] = labpbr.spread(albedo[..., c], filled, steps)
    rgba = np.zeros(shape + (4,), dtype=np.uint8)
    rgba[..., :3] = np.clip(np.round(albedo * 255), 0, 255)
    rgba[..., 3] = np.where(filled, 255, 0)
    return rgba, labpbr.encode_n(nx, ny, ao, height), labpbr.encode_s(smooth, f0), (tall, laid), filled


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
        e = {"from": list(box.frm), "to": list(box.to), "faces": faces}
        if box.rotation:
            r = box.rotation
            e["rotation"] = {"origin": list(r.origin), "axis": r.axis, "angle": r.angle, "rescale": r.rescale}
        elements.append(e)
    display = {k: {"rotation": list(v.rotation), "translation": list(v.translation), "scale": list(v.scale)}
               for k, v in klass.display.items()}
    meta = {"class": klass.name, "material": material, "lies": klass.lies}
    if klass.size:
        meta["size"] = klass.size
    return {"heft": meta, "textures": {"0": sprite, "particle": sprite}, "elements": elements, "display": display}


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
    atlas.write_text(json.dumps({"sources": [{"type": "single", "resource": r} for r in sorted(sprites)]}, indent=1) + "\n")
    print("wrote", atlas, len(sprites), "sprites")


if __name__ == "__main__":
    main()
