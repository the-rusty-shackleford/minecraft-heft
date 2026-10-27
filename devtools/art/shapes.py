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


def material_display(boxes, fill: float = 1.0, gui_turn=GUI_TURN) -> dict[str, Display]:
    """A chunky thing's display: turned three-quarters in the inventory so its depth reads in the hotbar;
    held small, as one holds an ingot; lying on its base in a frame, its top towards you. Every scale is
    worked from the shape's own extent times the class's `fill` (its size among the others: a nugget is a
    small thing in its slot, an ingot fills it)."""
    gx, gy, _ = extent(boxes, gui_turn)
    g = round(GUI_FILL * fill / max(gx, gy), 3)
    w, h, d = extent(boxes)
    across = max(w, h, d)
    ground = round(GROUND_FILL * fill / across, 3)
    frame = round(FRAME_FILL * fill / max(w, d), 3)
    hand = round(HAND_FILL * fill / across, 3)
    first = round(FIRST_FILL * fill / across, 3)
    return {
        "gui": Display(gui_turn, (0, 0, 0), (g, g, g)),
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


def material(name: str, boxes: list[Box], size: str | None, fill: float = 1.0, gui_turn=GUI_TURN) -> Klass:
    b = centred(boxes)
    return Klass(name, b, material_display(b, fill, gui_turn), size)


GUI_TURN_FLAT = (50.0, 225.0, 0.0)  # a sheet or a book seen more from above, so its face reads in the slot


def upright_display(boxes) -> dict[str, Display]:
    """An upright, face-on thing's display (a sign): turned a little in the inventory so its thickness shows; on
    the ground and in the hand as the game holds a flat item (its own item/generated transforms, scaled to the
    thing's width); face out in a frame."""
    w, h, _ = extent(boxes)
    k = 16.0 / max(w, h)
    gx, gy, _ = extent(boxes, SIGN_GUI_TURN)
    g = round(15.0 / max(gx, gy), 3)
    return {
        "gui": Display(SIGN_GUI_TURN, (0, 0, 0), (g, g, g)),
        "ground": Display((0, 0, 0), (0, 2, 0), (round(0.5 * k, 3),) * 3),
        "fixed": Display((0, 180, 0), (0, 0, 0), (round(1.0 * k, 3),) * 3),
        "head": Display((0, 180, 0), (0, 13, 7), (1, 1, 1)),
        "thirdperson_righthand": Display((0, 0, 0), (0, 3, 1), (round(0.55 * k, 3),) * 3),
        "thirdperson_lefthand": Display((0, 0, 0), (0, 3, 1), (round(0.55 * k, 3),) * 3),
        "firstperson_righthand": Display((0, -90, 25), (1.13, 3.2, 1.13), (round(0.68 * k, 3),) * 3),
        "firstperson_lefthand": Display((0, -90, 25), (1.13, 3.2, 1.13), (round(0.68 * k, 3),) * 3),
    }


SIGN_GUI_TURN = (10.0, -25.0, 0.0)


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


def rod() -> Klass:
    """The rod (stick, blaze rod, breeze rod): 11 x 1.5 x 1.5 along x, each end stepped in (the handoff's
    §4.3)."""
    return material("rod", [
        box((3.0, 7.25, 7.25), (13.0, 8.75, 8.75), "rod", west=None, east=None),
        box((2.5, 7.4, 7.4), (3.0, 8.6, 8.6), "rod", west="rod_end", east=None),
        box((13.0, 7.4, 7.4), (13.5, 8.6, 8.6), "rod", east="rod_end", west=None),
    ], "small", 1.0)


def bone() -> Klass:
    """The bone: a shaft 1.5 thick, flaring at each end into two knobs side by side, the ends turned an eighth so they
    read round; 12 x 2 x 5, lying flat."""
    out = [box((4.4, 7.25, 7.25), (11.6, 8.75, 8.75), "bone", west=None, east=None)]
    for x0, x1 in ((3.4, 4.4), (11.6, 12.6)):
        out.append(box((x0, 7.1, 6.6), (x1, 8.9, 9.4), "bone"))
    for (x0, x1), sx in (((2.0, 4.0), -1), ((12.0, 14.0), 1)):
        for (z0, z1), sz in (((5.5, 7.7), -1), ((8.3, 10.5), 1)):
            c = ((x0 + x1) / 2, 8.0, (z0 + z1) / 2)
            # Mirrored across both axes, so each end's two knobs splay apart alike.
            out.append(box((x0, 7.0, z0), (x1, 9.0, z1), "knob", Rotation(c, "y", 22.5 * sx * sz)))
    return material("bone", out, "small", 1.0)


def plate() -> Klass:
    """The plate (paper, a map, leather, hide, membrane): 11 x 0.5 x 9, its corners cut a sixteenth (two
    sheets crossed, a hair apart), one corner lifted in a curl."""
    return material("plate", [
        box((2.5, 7.75, 4.5), (13.5, 8.25, 11.5), "sheet_edge", up="sheet", down="sheet"),
        box((3.5, 7.76, 3.5), (12.5, 8.24, 12.5), "sheet_edge", up="sheet", down="sheet"),
        box((10.0, 7.8, 9.5), (13.0, 8.2, 12.0), "sheet_edge", Rotation((10.0, 8.0, 10.75), "z", 22.5), up="sheet", down="sheet"),
    ], "small", 1.0, GUI_TURN_FLAT)


def book() -> Klass:
    """The book: 10 x 2.5 x 7 lying shut, its spine to the west; two covers half a sixteenth thick overhanging
    the page block half a sixteenth on the three open sides."""
    return material("book", [
        box((3.0, 6.75, 4.5), (13.0, 7.25, 11.5), "cover_edge", up=None, down="cover"),
        box((3.0, 8.75, 4.5), (13.0, 9.25, 11.5), "cover_edge", up="cover", down=None),
        box((3.0, 7.25, 4.5), (3.6, 8.75, 11.5), "cover", up=None, down=None),
        box((3.6, 7.25, 5.0), (12.5, 8.75, 11.0), "pages", up=None, down=None, west=None),
    ], None, 1.0, GUI_TURN_FLAT)


def sign() -> Klass:
    """The sign: a board 14 x 8 x 1.5 on a post, standing face on, as the sign block is."""
    b = centred([
        box((1.0, 5.0, 7.25), (15.0, 13.0, 8.75), "board_edge", north="board_face", south="board_face"),
        box((7.25, 0.5, 7.4), (8.75, 5.0, 8.6), "post", up=None),
    ])
    return Klass("sign", b, upright_display(b), None, lies="face_out")


def hanging_sign() -> Klass:
    """The hanging sign: a board 14 x 8 x 1.5 under a bar, on two chains."""
    b = centred([
        box((1.0, 3.0, 7.25), (15.0, 11.0, 8.75), "board_edge", north="board_face", south="board_face"),
        box((0.5, 13.5, 7.0), (15.5, 15.0, 9.0), "bar"),
        box((3.5, 11.0, 7.75), (4.0, 13.5, 8.25), "chain", up=None, down=None),
        box((12.0, 11.0, 7.75), (12.5, 13.5, 8.25), "chain", up=None, down=None),
    ])
    return Klass("hanging_sign", b, upright_display(b), None, lies="face_out")


def arrow() -> Klass:
    """The arrow: a shaft along x, a two-bladed head and a point at the east end, two crossed vanes of
    fletching at the west; 13 x 2.4 x 2.4. A tipped arrow's head takes its potion's colour (tint 0)."""
    return material("arrow", [
        box((2.0, 7.6, 7.6), (12.0, 8.4, 8.4), "shaft", west="shaft", east=None),
        box((12.0, 6.8, 7.85), (14.0, 9.2, 8.15), "head"),
        box((12.0, 7.85, 6.8), (14.0, 8.15, 9.2), "head"),
        box((14.0, 7.7, 7.7), (15.0, 8.3, 8.3), "head"),
        box((2.0, 6.8, 7.9), (5.5, 9.2, 8.1), "fletch"),
        box((2.0, 7.9, 6.8), (5.5, 8.1, 9.2), "fletch"),
    ], "small", 1.0)


# --- phase 4: food -------------------------------------------------------------------------------

def ring(cx: float, cz: float, apothem: float, thick: float, y0: float, y1: float, role: str) -> list[Box]:
    """A hollow octagonal wall: eight tangential segments, each a side of the octagon whose apothem is `apothem`
    (centre to the middle of a side, at the wall's middle), `thick` through; four square to the axes, four turned
    45 degrees about the centre."""
    side = 2 * apothem * math.tan(math.radians(22.5)) + thick * 0.42  # long enough to close each corner
    out = []
    for angle in (0.0, 45.0):
        for dx, dz, along_x in ((apothem, 0.0, False), (-apothem, 0.0, False), (0.0, apothem, True), (0.0, -apothem, True)):
            hx, hz = (side / 2, thick / 2) if along_x else (thick / 2, side / 2)
            out.append(box((cx + dx - hx, y0, cz + dz - hz), (cx + dx + hx, y1, cz + dz + hz), role,
                           None if angle == 0 else Rotation((cx, (y0 + y1) / 2, cz), "y", angle)))
    return out


def fruit() -> Klass:
    """A round fruit (an apple, a beetroot, an eye): three octagonal tiers rounding a body 6.6 across, a stem and
    a leaf turned off it."""
    return material("fruit", [
        *octagon(8, 8, 2.4, 5.6, 6.4, "skin", "skin", "skin"),
        *octagon(8, 8, 3.3, 6.4, 8.8, "skin", "skin", None),
        *octagon(8, 8, 2.5, 8.8, 9.7, "skin_top", "skin", None),
        box((7.75, 9.7, 7.75), (8.25, 11.2, 8.25), "stem", down=None),
        box((8.25, 10.5, 7.6), (10.6, 10.75, 8.6), "leaf", Rotation((8.25, 10.6, 8.1), "z", -22.5)),
    ], "small", 0.8)


def egg() -> Klass:
    """The egg: four octagonal tiers, standing, its narrow end up."""
    return material("egg", [
        *octagon(8, 8, 2.0, 5.8, 6.6, "skin", "skin", "skin"),
        *octagon(8, 8, 2.8, 6.6, 8.4, "skin", "skin", None),
        *octagon(8, 8, 2.4, 8.4, 9.6, "skin", "skin", None),
        *octagon(8, 8, 1.4, 9.6, 10.3, "skin_top", "skin", None),
    ], "tiny", 0.7)


def root() -> Klass:
    """The root (a carrot): diamond-section segments tapering along x to a point, a tuft of three leaves at the
    crown."""
    out = []
    x = 4.0
    for side, length in ((2.6, 2.6), (2.2, 2.4), (1.7, 2.2), (1.1, 1.8), (0.55, 1.2)):
        h = side / 2
        out.append(box((x, 8 - h, 8 - h), (x + length, 8 + h, 8 + h), "skin", Rotation((x + length / 2, 8.0, 8.0), "x", 45.0),
                       west="skin_top", east="skin_top"))
        x += length
    for angle in (-22.5, 0.0, 22.5):
        out.append(box((1.6, 7.85, 7.4), (4.0, 8.15, 8.6), "leaf", Rotation((4.0, 8.0, 8.0), "y", angle) if angle else None))
    return material("root", out, "small", 1.0)


def tuber() -> Klass:
    """The tuber (a potato): two rounded lumps run together, a long one and a smaller one set a little off it, so it
    reads as an oblong with a dip; each lump three octagonal tiers, swelling in the middle. About 9.5 x 3.3 x 6."""
    out = []
    for cx, cz, tiers in ((6.6, 8.0, ((2.4, 6.0, 6.6), (3.0, 6.6, 8.6), (2.2, 8.6, 9.3))),
                          (9.6, 8.3, ((2.0, 6.1, 6.7), (2.6, 6.7, 8.4), (1.8, 8.4, 9.0)))):
        for k, (r, y0, y1) in enumerate(tiers):
            out += octagon(cx, cz, r, y0, y1, "skin_top", "skin", "skin" if k == 0 else None)
    return material("tuber", out, "small", 0.85)


def loaf() -> Klass:
    """The loaf: 10 x 4 x 5, its crown rounded by a deep cut along its top edges, its foot hardly at all."""
    return material("loaf", chamfered((3, 6.5, 5.5), (13, 10.5, 10.5), 1.6, "crust_top", "crust_top", "crust", "crust", cb=0.4, end="crust"),
                    "small", 1.0)


def disc() -> Klass:
    """The cookie: an octagon 7 across and a sixteenth thick."""
    return material("disc", octagon(8, 8, 3.6, 7.5, 8.5, "crust_top", "crust", "crust"), "tiny", 0.75, GUI_TURN_FLAT)


def pie() -> Klass:
    """The whole pie: an octagonal crust 9 across, its filling set a hair high inside the rim."""
    return material("pie", [
        *octagon(8, 8, 4.6, 7.0, 8.4, "crust_top", "crust", "crust"),
        *octagon(8, 8, 3.8, 8.4, 8.7, "filling", "filling", None),
    ], None, 1.0, GUI_TURN_FLAT)


def pie_slice() -> Klass:
    """A slice of pie: a wedge stepped to its point, its crust at the back."""
    out = []
    for i, (x0, x1) in enumerate(((4.0, 12.0), (5.0, 11.0), (6.0, 10.0), (7.0, 9.0))):
        out.append(box((x0, 7.0, 6.0 + i * 1.5), (x1, 8.5, 7.5 + i * 1.5), "crust", up="filling", north="crust_top" if i == 0 else None))
    return material("pie_slice", out, "small", 0.9, GUI_TURN_FLAT)


def steak() -> Klass:
    """A cut of meat or a fillet: a slab 9 x 1.6 x 8, its corners cut by two crossed slabs a hair apart."""
    return material("steak", [
        box((3.5, 7.2, 5.0), (12.5, 8.8, 11.0), "meat_edge", up="meat", down="meat"),
        box((4.5, 7.21, 4.0), (11.5, 8.79, 12.0), "meat_edge", up="meat", down="meat"),
    ], "small", 0.95, GUI_TURN_FLAT)


def drumstick() -> Klass:
    """A drumstick: a rounded joint of meat, the bone running out of it to a knuckle."""
    return material("drumstick", [
        *chamfered((4.0, 6.3, 5.5), (10.5, 9.7, 10.5), 1.2, "meat", "meat", "meat_edge", "meat_edge", cb=1.2, end="meat"),
        box((10.5, 7.6, 7.6), (13.4, 8.4, 8.4), "bone", west=None),
        box((13.4, 7.3, 7.2), (14.4, 8.7, 8.8), "bone"),
    ], "small", 0.95)


def chop() -> Klass:
    """A pork chop: a round of meat in a rim of fat, a slab 8.4 across and 1.6 thick, the bone running out of its side;
    the meat shows on both faces, set a hair proud inside the fat."""
    return material("chop", [
        *octagon(7.4, 8.0, 4.2, 7.2, 8.8, "fat", "fat", "fat"),
        *octagon(7.0, 8.0, 3.2, 8.8, 8.9, "chop_meat", None, None),
        *octagon(7.0, 8.0, 3.2, 7.1, 7.2, None, None, "chop_meat"),
        box((11.4, 7.6, 7.4), (13.8, 8.4, 8.6), "bone", west=None),
    ], "small", 0.95, GUI_TURN_FLAT)


def leg() -> Klass:
    """A leg of mutton: a whole joint, full at the rump and narrowing to the shank, its bone running out to a knuckle;
    about 12.5 x 4.4 x 6.4, lying on its side, one to a spot."""
    return material("leg", [
        *chamfered((2.6, 5.8, 4.8), (8.0, 10.2, 11.2), 1.4, "meat", "meat", "meat_edge", "meat_edge", cb=1.4, end="meat"),
        *chamfered((8.0, 6.4, 5.6), (11.0, 9.6, 10.4), 1.0, "meat", "meat", "meat_edge", "meat_edge", cb=1.0, end="meat"),
        box((11.0, 7.4, 7.4), (13.4, 8.6, 8.6), "bone", west=None),
        box((13.4, 7.0, 7.0), (14.6, 9.0, 9.0), "bone"),
    ], "small", 1.0)


def fish() -> Klass:
    """A fish, upright on its belly as it swims, thin side to side: a body rounded along its back and belly, a
    tapering head, a tail fin and a dorsal fin; it lies on its side set down or framed."""
    b = centred([
        *chamfered((3.5, 6.0, 7.0), (11.5, 10.0, 9.0), 0.9, "body", "body", "body", "body", end="body"),
        box((11.5, 6.6, 7.2), (13.2, 9.4, 8.8), "body"),
        box((1.4, 5.4, 7.85), (3.5, 10.6, 8.15), "fin"),
        box((6.0, 10.0, 7.9), (9.0, 11.0, 8.1), "fin"),
    ])
    return Klass("fish", b, upright_display(b), None, lies="face_out")


def berries() -> Klass:
    """A bunch of berries: five round-cornered berries on a stalk."""
    pts = ((6.2, 6.6, 7.0), (8.4, 6.6, 6.4), (7.4, 6.6, 8.8), (9.4, 6.6, 8.6), (7.8, 8.2, 7.6))
    out = []
    for i, (x, y, z) in enumerate(pts):
        out.append(box((x - 0.9, y - 0.9, z - 0.9), (x + 0.9, y + 0.9, z + 0.9), "skin",
                       Rotation((x, y, z), "y", 22.5) if i % 2 else None, up="skin_top"))
    out.append(box((7.65, 9.0, 7.45), (7.95, 10.2, 7.75), "stem", down=None))
    return material("berries", out, "tiny", 0.75)


def wedge() -> Klass:
    """A slice of melon: its rind along the bottom, its flesh stepped to a point, thin front to back, standing face
    on as a sign does."""
    b = centred([
        box((2.5, 5.0, 7.25), (13.5, 6.0, 8.75), "rind"),
        box((3.5, 6.0, 7.25), (12.5, 7.5, 8.75), "flesh", down=None),
        box((4.8, 7.5, 7.25), (11.2, 9.0, 8.75), "flesh", down=None),
        box((6.2, 9.0, 7.25), (9.8, 10.5, 8.75), "flesh", down=None),
        box((7.4, 10.5, 7.25), (8.6, 11.4, 8.75), "flesh", down=None),
    ])
    return Klass("wedge", b, upright_display(b), "small", lies="face_out")


def bowl() -> Klass:
    """A bowl of something: an octagonal foot, a hollow octagonal wall of eight segments, and what is in it set
    half a sixteenth below the rim."""
    return material("bowl", [
        *octagon(8, 8, 2.8, 5.8, 6.3, "bowl", "bowl", "bowl"),
        *ring(8, 8, 3.85, 0.7, 6.3, 8.8, "bowl"),
        *octagon(8, 8, 3.85, 6.3, 8.3, "fill", "bowl", None),  # its apothem (3.56) just past the wall's inner face (3.5)
    ], None, 1.0, GUI_TURN_FLAT)


def plated() -> Klass:
    """A plated meal: a flat octagonal dish and the food heaped on it in two tiers."""
    return material("plated", [
        *octagon(8, 8, 4.7, 6.2, 6.8, "dish", "dish", "dish"),
        *octagon(8, 8, 2.9, 6.8, 8.5, "food", "food", None),
        *octagon(8.3, 7.8, 1.8, 8.5, 9.5, "food_top", "food", None),
    ], None, 1.0, GUI_TURN_FLAT)


def stack() -> Klass:
    """A sandwich: two slices of bread, their edges eased, and a filling showing between them."""
    return material("stack", [
        *chamfered((3.5, 5.8, 4.5), (12.5, 7.2, 11.5), 0.5, "bread", "bread", "bread", "bread", cb=0.5, end="bread"),
        box((3.0, 7.2, 4.0), (13.0, 8.2, 12.0), "filling"),
        *chamfered((3.5, 8.2, 4.5), (12.5, 9.8, 11.5), 0.8, "bread_top", "bread_top", "bread", "bread", cb=0.2, end="bread"),
    ], "small", 1.0)


# --- phase 5: odds and ends -------------------------------------------------------------------------

def spawn_egg() -> Klass:
    """Every spawn egg, any mod's (a kind, shapes/_kind/spawn_egg.json): the egg in a pale shell tinted with the
    mob's first colour, five spots standing proud of it tinted with its second, as the game tints its sprite."""
    out = [
        *octagon(8, 8, 2.0, 5.8, 6.6, "egg_base", "egg_base", "egg_base"),
        *octagon(8, 8, 2.8, 6.6, 8.4, "egg_base", "egg_base", None),
        *octagon(8, 8, 2.4, 8.4, 9.6, "egg_base", "egg_base", None),
        *octagon(8, 8, 1.4, 9.6, 10.3, "egg_base", "egg_base", None),
    ]
    a = 2.8 * math.cos(math.radians(22.5))
    for x0, x1, z0, z1 in ((7.3, 8.5, 8 + a - 0.05, 8 + a + 0.15), (7.6, 8.6, 8 - a - 0.15, 8 - a + 0.05),
                           (8 + a - 0.05, 8 + a + 0.15, 7.2, 8.3), (8 - a - 0.15, 8 - a + 0.05, 7.8, 8.8)):
        out.append(box((x0, 7.0, z0), (x1, 8.1, z1), "egg_spot"))
    out.append(box((7.4, 10.3, 7.6), (8.4, 10.45, 8.5), "egg_spot"))
    return material("spawn_egg", out, "tiny", 0.7)


def record() -> Klass:
    """A music disc: an octagonal record 9 across, its label raised a quarter sixteenth at the middle."""
    return material("record", [
        *octagon(8, 8, 4.6, 7.7, 8.3, "record", "record", "record"),
        *octagon(8, 8, 1.6, 8.3, 8.55, "label", "label", None),
    ], "small", 1.0, GUI_TURN_FLAT)


def _pail(fill_y: float, name: str) -> Klass:
    return material(name, [
        *octagon(8, 8, 3.2, 5.6, 6.1, "pail", "pail", "pail"),
        *ring(8, 8, 3.25, 0.5, 6.1, 10.4, "pail"),
        *octagon(8, 8, 3.45, 6.1, fill_y, "contents", "pail", None),
        box((4.2, 10.4, 7.8), (4.6, 11.4, 8.2), "handle"),
        box((11.4, 10.4, 7.8), (11.8, 11.4, 8.2), "handle"),
        box((4.2, 11.4, 7.8), (11.8, 11.8, 8.2), "handle"),
    ], "small", 0.9)


def bucket() -> Klass:
    """A bucket of something: a hollow iron pail on a foot, its handle up, what it holds just below the rim."""
    return _pail(9.8, "bucket")


def bucket_empty() -> Klass:
    """The empty bucket: the same pail, its floor showing."""
    return _pail(6.3, "bucket_empty")


def orb() -> Klass:
    """An orb (a pearl, a slime ball, a snowball, a charge): three octagonal tiers rounding a ball 6 across."""
    return material("orb", [
        *octagon(8, 8, 1.9, 5.6, 6.4, "orb", "orb", "orb"),
        *octagon(8, 8, 3.0, 6.4, 9.2, "orb", "orb", None),
        *octagon(8, 8, 1.9, 9.2, 10.0, "orb_top", "orb", None),
    ], "tiny", 0.7)


def star() -> Klass:
    """The nether star: four points, two bars crossed and turned 45 degrees, about a heart, standing face on."""
    b = centred([
        box((3.0, 7.0, 7.4), (13.0, 9.0, 8.6), "star", Rotation((8.0, 8.0, 8.0), "z", 45.0)),
        box((3.0, 7.0, 7.4), (13.0, 9.0, 8.6), "star", Rotation((8.0, 8.0, 8.0), "z", -45.0)),
        box((6.6, 6.6, 7.0), (9.4, 9.4, 9.0), "star"),
    ])
    return Klass("star", b, upright_display(b), "small", lies="face_out")


def shell() -> Klass:
    """The shulker's shell: a low dome of three octagonal tiers, open beneath."""
    return material("shell", [
        *octagon(8, 8, 4.4, 5.6, 7.0, "shell", "shell", "shell_inner"),
        *octagon(8, 8, 3.6, 7.0, 8.6, "shell", "shell", None),
        *octagon(8, 8, 2.4, 8.6, 9.6, "shell", "shell", None),
    ], None, 1.0)


def rocket() -> Klass:
    """The firework rocket: a paper tube of diamond section along x, its nose stepped to a point, its stick behind."""
    out = []
    x = 4.0
    for side, length, role in ((2.6, 6.0, "tube"), (1.8, 1.2, "cone"), (1.0, 0.9, "cone")):
        h = side / 2
        out.append(box((x, 8 - h, 8 - h), (x + length, 8 + h, 8 + h), role, Rotation((x + length / 2, 8.0, 8.0), "x", 45.0)))
        x += length
    out.append(box((1.0, 7.8, 7.8), (4.0, 8.2, 8.2), "handle"))
    return material("rocket", out, "small", 1.0)


def horn() -> Klass:
    """The goat horn: three segments sweeping upward along x, thin side to side, lying on its side set down."""
    b = centred([
        box((2.5, 6.6, 6.9), (6.6, 9.0, 9.1), "horn"),
        box((6.3, 7.0, 7.1), (9.6, 9.0, 8.9), "horn", Rotation((6.5, 8.0, 8.0), "z", 22.5)),
        box((8.8, 8.4, 7.3), (11.4, 10.0, 8.7), "horn", Rotation((9.0, 9.2, 8.0), "z", 45.0)),
        box((10.4, 10.3, 7.6), (12.0, 11.3, 8.4), "horn_tip", Rotation((10.6, 10.8, 8.0), "z", 45.0)),
    ])
    return Klass("horn", b, upright_display(b), "small", lies="face_out")


def totem() -> Klass:
    """The totem of undying: a little figure, its head, its body, its wings spread, its feet, standing face on."""
    b = centred([
        box((6.4, 10.4, 7.0), (9.6, 13.6, 9.0), "figure"),
        box((6.6, 6.0, 7.2), (9.4, 10.4, 8.8), "figure"),
        box((3.4, 7.2, 7.6), (6.6, 10.2, 8.4), "wing"),
        box((9.4, 7.2, 7.6), (12.6, 10.2, 8.4), "wing"),
        box((6.8, 3.6, 7.4), (7.8, 6.0, 8.6), "figure"),
        box((8.2, 3.6, 7.4), (9.2, 6.0, 8.6), "figure"),
    ])
    return Klass("totem", b, upright_display(b), "small", lies="face_out")


def saddle() -> Klass:
    """The saddle: a seat eased along its sides, a pommel at the front, a stirrup strap down each side."""
    return material("saddle", [
        *chamfered((3.5, 7.0, 5.0), (12.5, 9.0, 11.0), 0.8, "seat", "seat", "seat", "seat", cb=0.2, end="seat"),
        box((10.8, 9.0, 7.0), (12.2, 10.2, 9.0), "seat"),
        box((7.4, 5.0, 4.6), (8.6, 7.6, 5.0), "strap"),
        box((7.4, 5.0, 11.0), (8.6, 7.6, 11.4), "strap"),
    ], None, 1.0)


CLASSES = {"ingot": ingot, "nugget": nugget, "bar": bar, "raw": raw, "lump": lump, "gem": gem, "shard": shard, "dust": dust,
           "rod": rod, "bone": bone, "plate": plate, "book": book, "sign": sign, "hanging_sign": hanging_sign, "arrow": arrow,
           "fruit": fruit, "egg": egg, "root": root, "tuber": tuber, "loaf": loaf, "disc": disc, "pie": pie, "pie_slice": pie_slice,
           "steak": steak, "chop": chop, "leg": leg, "drumstick": drumstick, "fish": fish, "berries": berries, "wedge": wedge, "bowl": bowl, "plated": plated,
           "stack": stack, "spawn_egg": spawn_egg, "record": record, "bucket": bucket, "bucket_empty": bucket_empty, "orb": orb,
           "star": star, "shell": shell, "rocket": rocket, "horn": horn, "totem": totem, "saddle": saddle}
