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

import math
from dataclasses import dataclass, field, replace

DIRECTIONS = ("down", "up", "north", "south", "west", "east")
SQRT2 = math.sqrt(2.0)


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
        dx, dy, dz = (round(self.to[i] - self.frm[i], 4) for i in range(3))
        return {"down": (dx, dz), "up": (dx, dz), "north": (dx, dy), "south": (dx, dy), "west": (dz, dy), "east": (dz, dy)}[direction]

    def corners(self) -> list[tuple[float, float, float]]:
        """effects: returns its eight corners where the game draws them: turned about its origin, stretched back if rescaled"""
        pts = [tuple(self.to[k] if (i >> k) & 1 else self.frm[k] for k in range(3)) for i in range(8)]
        r = self.rotation
        if r is None or r.angle == 0:
            return pts
        axis = "xyz".index(r.axis)
        i, j = [(1, 2), (2, 0), (0, 1)][axis]  # counter-clockwise looking down the axis: y to z, z to x, x to y
        a = math.radians(r.angle)
        s = 1 / math.cos(a) if r.rescale else 1.0
        out = []
        for p in pts:
            q = [p[k] - r.origin[k] for k in range(3)]
            qi, qj = q[i] * math.cos(a) - q[j] * math.sin(a), q[i] * math.sin(a) + q[j] * math.cos(a)
            q[i], q[j] = qi * s, qj * s
            out.append(tuple(q[k] + r.origin[k] for k in range(3)))
        return out

    def moved(self, d: tuple[float, float, float]) -> Box:
        add = lambda p: tuple(p[k] + d[k] for k in range(3))  # noqa: E731
        return replace(self, frm=add(self.frm), to=add(self.to),
                       rotation=None if self.rotation is None else replace(self.rotation, origin=add(self.rotation.origin)))


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


# --- building blocks ---------------------------------------------------------------------------

def _all(role: str | None, **overrides: str | None) -> dict[str, str | None]:
    roles = {d: role for d in DIRECTIONS}
    roles.update(overrides)
    return roles


def box(lo, hi, role: str | None, rotation: Rotation | None = None, **overrides: str | None) -> Box:
    return Box(tuple(map(float, lo)), tuple(map(float, hi)), _all(role, **overrides), rotation)


def chamfered(lo, hi, c: float, top: str, chamfer: str, side: str, base: str, cb: float | None = None, end: str = "end") -> list[Box]:
    """A bar from lo to hi whose four long edges (along x) are cut back by c at 45 degrees, full length, so
    its ends show a cut-cornered section and no corner is left to notch (the handoff's §4.3: "a chamfer on
    the four long edges"). A core, a top and a bottom course each inset c, and over each long edge a slab
    turned 45 degrees whose outer face lies on the cut; the slabs stop a hair short of the ends, so the
    part of their end buried in the core never shares a plane with the core's end. The bottom edges are
    cut by `cb` (c if None): less than the top makes the section a trapezoid, wider at the foot, as a cast
    ingot is. Every face at the two ends is the `end` role, so the end's pieces read as one face."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    cb = c if cb is None else cb
    hair = 0.005
    out = [
        box((x0, y0 + cb, z0), (x1, y1 - c, z1), side, up=None, down=None, west=end, east=end),
        box((x0, y1 - c, z0 + c), (x1, y1, z1 - c), side, up=top, down=None, north=None, south=None, west=end, east=end),
        box((x0, y0, z0 + cb), (x1, y0 + cb, z1 - cb), side, down=base, up=None, north=None, south=None, west=end, east=end),
    ]
    # Each edge: where its cut's middle is, the turn that sets the slab's top on the cut, its role and its cut.
    for ym, zm, angle, role, k in ((y1 - c / 2, z0 + c / 2, -45.0, chamfer, c), (y1 - c / 2, z1 - c / 2, 45.0, chamfer, c),
                                   (y0 + cb / 2, z0 + cb / 2, -135.0, base, cb), (y0 + cb / 2, z1 - cb / 2, 135.0, base, cb)):
        t = k * 0.75  # how deep the slab reaches under its face: past the cut's inner corner (k / sqrt 2), buried in core and course
        span = k * SQRT2
        flip = abs(angle) > 90  # a bottom edge: the slab hangs from the cut, its down face on it, turned the other way
        a = angle + (180.0 if angle < 0 else -180.0) if flip else angle
        if flip:
            b = box((x0 + hair, ym, zm - span / 2), (x1 - hair, ym + t, zm + span / 2), None,
                    Rotation((8.0, ym, zm), "x", a), down=role, west=end, east=end)
        else:
            b = box((x0 + hair, ym - t, zm - span / 2), (x1 - hair, ym, zm + span / 2), None,
                    Rotation((8.0, ym, zm), "x", a), up=role, west=end, east=end)
        out.append(b)
    return out


def octagon(centre_x: float, centre_z: float, radius: float, y0: float, y1: float, role_top: str, role_side: str,
            role_bottom: str | None) -> list[Box]:
    """A regular octagonal tier of circumradius `radius`: four slabs a quarter turn apart in two pairs, each
    R cos 22.5 by R sin 22.5 in half-size, so every corner lies on the octagon (the handoff's §6.3). Each
    slab's top and bottom are set a hair apart so no two faces lie in one plane."""
    a, b = radius * math.cos(math.radians(22.5)), radius * math.sin(math.radians(22.5))
    out = []
    for k, (hx, hz, angle) in enumerate(((a, b, 0.0), (b, a, 0.0), (a, b, 45.0), (b, a, 45.0))):
        e = 0.012 * k
        out.append(box((centre_x - hx, y0 + e, centre_z - hz), (centre_x + hx, y1 - e, centre_z + hz), role_side,
                       None if angle == 0 else Rotation((centre_x, (y0 + y1) / 2, centre_z), "y", angle),
                       up=role_top, down=role_bottom))
    return out


def centred(boxes: list[Box]) -> tuple[Box, ...]:
    """effects: returns the boxes moved so the box around every corner is centred on (8, 8, 8)"""
    pts = [p for b in boxes for p in b.corners()]
    lo = [min(p[k] for p in pts) for k in range(3)]
    hi = [max(p[k] for p in pts) for k in range(3)]
    d = tuple(round(8.0 - (lo[k] + hi[k]) / 2, 4) for k in range(3))
    return tuple(b.moved(d) for b in boxes)


def extent(boxes, turn=(0.0, 0.0, 0.0)) -> tuple[float, float, float]:
    """effects: returns the size of the box around every corner, turned by a display rotation (degrees XYZ)"""
    rx, ry, rz = (math.radians(a) for a in turn)

    def apply(p):
        x, y, z = (p[k] - 8.0 for k in range(3))
        x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
        return x, y, z
    pts = [apply(p) for b in boxes for p in b.corners()]
    return tuple(max(p[k] for p in pts) - min(p[k] for p in pts) for k in range(3))


# --- displays ----------------------------------------------------------------------------------

GUI_TURN = (30.0, 225.0, 0.0)  # the inventory's three-quarter view (the handoff's §8.1, adopted)
GUI_FILL = 14.0  # sixteenths of the slot the icon spans, its wider way
GROUND_FILL = 7.0  # sixteenths across a dropped one: a flat item's 16 at the game's 0.5 is 8, a solid one reads bigger
FRAME_FILL = 12.0  # sixteenths across, in an item frame
HAND_FILL = 5.5  # sixteenths across, held, seen by others
FIRST_FILL = 4.5  # sixteenths across, held, in your own first-person view


def material_display(boxes, fill: float = 1.0) -> dict[str, Display]:
    """A chunky thing's display: turned three-quarters in the inventory so its depth reads in the hotbar;
    held small, as one holds an ingot; lying on its base in a frame, its top towards you. Every scale is
    worked from the shape's own extent times the class's `fill` (its size among the others: a nugget is a
    small thing in its slot, an ingot fills it)."""
    gx, gy, _ = extent(boxes, GUI_TURN)
    g = round(GUI_FILL * fill / max(gx, gy), 3)
    w, h, d = extent(boxes)
    across = max(w, h, d)
    ground = round(GROUND_FILL * fill / across, 3)
    frame = round(FRAME_FILL * fill / max(w, d), 3)
    hand = round(HAND_FILL * fill / across, 3)
    first = round(FIRST_FILL * fill / across, 3)
    return {
        "gui": Display(GUI_TURN, (0, 0, 0), (g, g, g)),
        "ground": Display((0, 0, 0), (0, 2, 0), (ground,) * 3),
        "fixed": Display((-90, 0, 0), (0, 0, 0), (frame,) * 3),
        "head": Display((0, 180, 0), (0, 13, 7), (1, 1, 1)),
        # The left hand's entries equal the right's: the game mirrors a left-hand transform itself
        # (ItemTransform.apply negates its y and z turn and its x offset).
        "thirdperson_righthand": Display((75, 45, 0), (0, 2.5, 0), (hand,) * 3),
        "thirdperson_lefthand": Display((75, 45, 0), (0, 2.5, 0), (hand,) * 3),
        # Held across the palm, its top a little towards you, not pointed away like a baton.
        "firstperson_righthand": Display((10, -20, 5), (1.0, 3.0, 0), (first,) * 3),
        "firstperson_lefthand": Display((10, -20, 5), (1.0, 3.0, 0), (first,) * 3),
    }


def material(name: str, boxes: list[Box], size: str | None, fill: float = 1.0) -> Klass:
    b = centred(boxes)
    return Klass(name, b, material_display(b, fill), size)


# --- the classes -------------------------------------------------------------------------------

def ingot() -> Klass:
    """The ingot: 10 x 3 x 5, its top long edges cut back a sixteenth at 45 degrees and its bottom ones a
    quarter, so it stands wider at the foot, its top 10 x 3 (the handoff's §4.3). Every ingot of every mod
    is these seven boxes."""
    return material("ingot", chamfered((3, 6.5, 5.5), (13, 9.5, 10.5), 1.0, "top", "chamfer", "side", "base", cb=0.25), "small")


def nugget() -> Klass:
    """The nugget: the ingot's form at 4 x 2 x 3, cut back half a sixteenth."""
    return material("nugget", chamfered((6, 7, 6.5), (10, 9, 9.5), 0.6, "top", "chamfer", "side", "base", cb=0.2), "tiny", 0.6)


def bar() -> Klass:
    """The brick: 8 x 3 x 4, its top edges eased half a sixteenth."""
    return material("bar", chamfered((4, 6.5, 6), (12, 9.5, 10), 0.5, "top", "chamfer", "side", "base"), "small", 0.9)


def raw() -> Klass:
    """The raw chunk: three lumps grown together, two of them turned a sixteenth of a turn, about 7 x 4.5 x 6.
    The same three for every raw metal."""
    return material("raw", [
        box((4.5, 5.5, 5.2), (10.0, 8.6, 10.6), "rock", Rotation((7.25, 7.0, 7.9), "y", 22.5), up="rock_top"),
        box((7.0, 6.6, 5.8), (11.4, 9.4, 9.8), "rock", up="rock_top"),
        box((5.2, 7.6, 6.6), (8.6, 10.0, 9.6), "rock", Rotation((6.9, 8.8, 8.1), "y", -22.5), up="rock_top"),
    ], "small", 0.85)


def lump() -> Klass:
    """The lump (coal, lapis, scrap): four broken pieces fused, flatter than it is wide, about 6.5 x 4.5 x 6."""
    return material("lump", [
        box((5.0, 5.5, 5.2), (10.4, 8.4, 10.2), "rock", Rotation((7.7, 7.0, 7.7), "y", 22.5), up="rock_top"),
        box((6.6, 6.8, 6.0), (11.0, 9.4, 9.4), "rock", Rotation((8.8, 8.1, 7.7), "x", 22.5), up="rock_top"),
        box((4.6, 6.2, 7.2), (7.8, 8.8, 10.8), "rock", Rotation((6.2, 7.5, 9.0), "z", -22.5), up="rock_top"),
        box((7.0, 8.6, 6.8), (9.4, 10.0, 9.2), "rock", up="rock_top"),
    ], "small", 0.8)


def gem() -> Klass:
    """The gem: a round brilliant lying on its pavilion, its table up: four octagonal tiers (pavilion,
    girdle, crown, table), each four slabs, 8 x 4 across."""
    tiers = (
        octagon(8, 8, 2.2, 6.0, 7.1, "facet", "facet_low", "facet_low"),
        octagon(8, 8, 4.0, 7.1, 8.3, "facet", "girdle", None),
        octagon(8, 8, 3.1, 8.3, 9.3, "facet", "facet", None),
        octagon(8, 8, 2.0, 9.3, 9.9, "table", "facet", None),
    )
    return material("gem", [b for t in tiers for b in t], "small", 0.8)


def shard() -> Klass:
    """The shard: a crystal of diamond section, turned an eighth about its length, tapering in three steps to
    a point; about 3.4 x 3.4 x 9."""
    out = []
    z = 3.5
    for side, length in ((2.4, 3.6), (1.9, 2.6), (1.3, 2.0), (0.7, 0.9)):
        h = side / 2
        out.append(box((8 - h, 8 - h, z), (8 + h, 8 + h, z + length), "crystal",
                       Rotation((8.0, 8.0, z + length / 2), "z", 45.0), north="crystal_end", south="crystal_end"))
        z += length
    return material("shard", out, "small")


def dust() -> Klass:
    """The dust pile: a low round mound in three octagonal tiers, each set a little off the one beneath, about
    9 x 2 x 9. The same mound for every dust."""
    tiers = (
        octagon(8.0, 8.0, 4.7, 6.8, 7.55, "dust_top", "dust_side", "dust_side"),
        octagon(8.4, 7.8, 3.3, 7.55, 8.3, "dust_top", "dust_side", None),
        octagon(8.7, 7.6, 1.8, 8.3, 8.9, "dust_top", "dust_side", None),
    )
    return material("dust", [b for t in tiers for b in t], "tiny", 0.85)


CLASSES = {"ingot": ingot, "nugget": nugget, "bar": bar, "raw": raw, "lump": lump, "gem": gem, "shard": shard, "dust": dust}
