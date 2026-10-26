# Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later
"""The loud twin: does the shader read Heft's _n beside a sprite on Heft's own atlas? (phase 0, spike b)

    uv run --no-project --with numpy --with pillow python devtools/booth/loud.py

Writes, into the test mod's resources only (never shipped), a copy of the iron ingot's sheet with the
same colour and _s but an _n of strong diagonal ridges and grooves in height, put on the gold ingot. Under
Iris with a LabPBR shader the twin shows ridges where the iron ingot is smooth if, and only if, the maps
are read; with shaders off the two look the same.
"""
import json
import shutil
from pathlib import Path

import numpy as np
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
MAIN = REPO / "src/main/resources/assets/heft"
TEST = REPO / "src/gametest/resources/assets/heft"

src = MAIN / "textures/solid/minecraft/iron_ingot"
out = TEST / "textures/solid/booth/loud_ingot"
out.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(f"{src}.png", f"{out}.png")
shutil.copyfile(f"{src}_s.png", f"{out}_s.png")
n = np.asarray(Image.open(f"{src}_n.png").convert("RGBA")).copy()
h, w = n.shape[:2]
y, x = np.mgrid[0:h, 0:w]
phase = (x + y) % 12 / 12.0 * 2 * np.pi
slope = np.cos(phase)  # the ridges' slope across them
tilt = 0.75 * slope / np.sqrt(2)
n[..., 0] = np.clip(np.round((tilt * 0.5 + 0.5) * 255), 0, 255)
n[..., 1] = np.clip(np.round((-tilt * 0.5 + 0.5) * 255), 0, 255)
n[..., 3] = np.clip(np.round(220 + 30 * np.sin(phase)), 175, 253)
Image.fromarray(n, "RGBA").save(f"{out}_n.png")

shape = json.loads((MAIN / "shapes/minecraft/iron_ingot.json").read_text())
shape["textures"] = {"0": "heft:solid/booth/loud_ingot", "particle": "heft:solid/booth/loud_ingot"}
shape["heft"]["material"] = "loud"
(TEST / "shapes/minecraft").mkdir(parents=True, exist_ok=True)
(TEST / "shapes/minecraft/gold_ingot.json").write_text(json.dumps(shape, indent=1) + "\n")
(TEST / "atlases").mkdir(parents=True, exist_ok=True)
(TEST / "atlases/items.json").write_text(json.dumps({"sources": [{"type": "single", "resource": "heft:solid/booth/loud_ingot"}]}, indent=1) + "\n")
print("wrote the loud twin on minecraft:gold_ingot")
