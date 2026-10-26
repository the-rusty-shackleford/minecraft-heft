"""shapes: one function per class of solid item, the same elements for every item of the class (D-0003).

A class is its boxes and its display transforms; an item of it differs only in its material. Every
model is centred on the block's centre (8, 8, 8), as the game's own flat items are, so display
transforms read as the game's do. Its `fixed` turn is how an item frame shows it and how Set It Down
lays it down (a model the hand draws as the item's own is posed by it), so it lays the thinnest
extent out of the surface, the model's top towards the viewer.

Each face names the patch it is painted with: a role (what the painter makes of it) and its size in
sixteenths. Faces with the same role and size share one patch on the sheet.

Copyright 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later.
"""
from __future__ import annotations

from dataclasses import dataclass, field

DIRECTIONS = ("down", "up", "north", "south", "west", "east")


@dataclass(frozen=True)
class Rotation:
    origin: tuple[float, float, float]
    axis: str  # "x", "y" or "z"
    angle: float  # -45, -22.5, 0, 22.5 or 45, as the game allows
    rescale: bool = False


@dataclass(frozen=True)
class Box:
    """One element: from one corner to the other, each face painted as its role says (None: not drawn)."""
    frm: tuple[float, float, float]
    to: tuple[float, float, float]
    roles: dict[str, str | None]
    rotation: Rotation | None = None

    def face_size(self, direction: str) -> tuple[float, float]:
        """effects: returns the face's (width, height) in sixteenths as its patch is laid: up/down x by z,
        north/south x by y, west/east z by y"""
        dx, dy, dz = (self.to[i] - self.frm[i] for i in range(3))
        return {"down": (dx, dz), "up": (dx, dz), "north": (dx, dy), "south": (dx, dy), "west": (dz, dy), "east": (dz, dy)}[direction]


@dataclass(frozen=True)
class Display:
    rotation: tuple[float, float, float] = (0.0, 0.0, 0.0)
    translation: tuple[float, float, float] = (0.0, 0.0, 0.0)
    scale: tuple[float, float, float] = (1.0, 1.0, 1.0)


@dataclass(frozen=True)
class Klass:
    name: str
    boxes: tuple[Box, ...]
    display: dict[str, Display]
    size: str | None  # Set It Down's size tag, or None for NORMAL
    lies: str = "on_base"  # how its fixed turn lays it: "on_base" (top out) or "face_out" (front out)
    extra: dict = field(default_factory=dict)


# A chunky thing's display: shown turned three-quarters in the inventory, so its depth reads in the
# hotbar (the handoff's §8.1, adopted); held as a block is held, smaller; lying on its base in a frame.
def _material_display(gui_scale: float, hand_scale: float, ground_scale: float, frame_scale: float) -> dict[str, Display]:
    g, h = gui_scale, hand_scale
    return {
        "gui": Display((30, 225, 0), (0, 0, 0), (g, g, g)),
        "ground": Display((0, 0, 0), (0, 2, 0), (ground_scale,) * 3),
        "fixed": Display((-90, 0, 0), (0, 0, 0), (frame_scale,) * 3),
        "head": Display((0, 180, 0), (0, 13, 7), (1, 1, 1)),
        "thirdperson_righthand": Display((75, 45, 0), (0, 2.5, 0), (h, h, h)),
        "thirdperson_lefthand": Display((75, -45, 0), (0, 2.5, 0), (h, h, h)),
        "firstperson_righthand": Display((0, 45, 0), (0, 2, 0), (h * 1.2,) * 3),
        "firstperson_lefthand": Display((0, -45, 0), (0, 2, 0), (h * 1.2,) * 3),
    }


def _all(role: str | None, **overrides: str | None) -> dict[str, str | None]:
    roles = {d: role for d in DIRECTIONS}
    roles.update(overrides)
    return roles


def ingot() -> Klass:
    """The ingot: 10 x 3 x 5, a trapezoid in three courses (10 x 5, 9 x 4, the 8 x 3 top), each course's
    rim a step of half a sixteenth -- the cast bar's taper, its edges catching light. Every ingot of
    every mod is these three boxes."""
    y0 = 8 - 1.5
    courses = (
        Box((3.0, y0, 5.5), (13.0, y0 + 1, 10.5), _all("ingot_side", up="ingot_ledge", down="ingot_base")),
        Box((3.5, y0 + 1, 6.0), (12.5, y0 + 2, 10.0), _all("ingot_side", up="ingot_ledge", down=None)),
        Box((4.0, y0 + 2, 6.5), (12.0, y0 + 3, 9.5), _all("ingot_side", up="ingot_top", down=None)),
    )
    return Klass("ingot", courses, _material_display(1.15, 0.5, 0.6, 1.3), "small")


CLASSES = {"ingot": ingot}
