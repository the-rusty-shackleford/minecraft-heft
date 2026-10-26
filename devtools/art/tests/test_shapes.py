"""The class canon (D-0003), held for every class and every item built.

Partitions:
- each class: within the element budget; its fixed turn lays its thinnest extent out of the surface and
  its top towards the viewer; its patches fit a sheet; centred on the block's centre
- the built shape files: one per item in ITEMS; same elements for every item of a class (consistency
  enforced structurally, the handoff's §4.3); uvs within 0..16
"""
import json
import math

import numpy as np
import pytest

import build
from layout import patches
from shapes import CLASSES

BUDGET = 40  # Canon.BUDGET in the domain layer


def turn(rx, ry, rz):
    """The game's rotationXYZ: Rx(Ry(Rz p))."""
    def m(axis, deg):
        r = math.radians(deg)
        c, s = math.cos(r), math.sin(r)
        return {0: np.array([[1, 0, 0], [0, c, -s], [0, s, c]]), 1: np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
                2: np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[axis]
    return m(0, rx) @ m(1, ry) @ m(2, rz)


def corners(klass):
    """Every element's corners where the game draws them: turned about its origin, stretched back if rescaled."""
    pts = []
    for b in klass.boxes:
        box = np.array([[b.to[k] if (i >> k) & 1 else b.frm[k] for k in range(3)] for i in range(8)], dtype=float)
        if b.rotation is not None and b.rotation.angle != 0:
            axis = "xyz".index(b.rotation.axis)
            angles = [0.0, 0.0, 0.0]
            angles[axis] = b.rotation.angle
            o = np.array(b.rotation.origin, dtype=float)
            box = (box - o) @ turn(*angles).T
            if b.rotation.rescale:
                stretch = np.full(3, 1 / math.cos(math.radians(b.rotation.angle)))
                stretch[axis] = 1.0
                box = box * stretch
            box = box + o
        pts.extend(box)
    return np.array(pts)


@pytest.mark.parametrize("name", sorted(CLASSES))
def test_within_budget(name):
    assert len(CLASSES[name]().boxes) <= BUDGET


@pytest.mark.parametrize("name", sorted(CLASSES))
def test_fixed_lays_it_thin_out_and_top_out(name):
    k = CLASSES[name]()
    r = turn(*k.display["fixed"].rotation)
    rested = (corners(k) - 8) @ r.T
    size = rested.max(0) - rested.min(0)
    assert size[2] <= min(size[0], size[1]) + 1e-4
    if k.lies == "on_base":
        assert np.allclose(r @ [0, 1, 0], [0, 0, -1])


@pytest.mark.parametrize("name", sorted(CLASSES))
def test_patches_fit_a_sheet(name):
    patches(CLASSES[name]())


@pytest.mark.parametrize("name", sorted(CLASSES))
def test_centred_on_the_block(name):
    c = corners(CLASSES[name]())
    assert np.allclose((c.min(0) + c.max(0)) / 2, [8, 8, 8])


def test_one_shape_file_per_item_same_elements_within_a_class():
    by_class = {}
    for item, (klass, _) in build.ITEMS.items():
        ns, path = item.split(":")
        data = json.loads((build.ASSETS / "shapes" / ns / f"{path}.json").read_text())
        assert data["heft"]["class"] == klass
        geometry = [(e["from"], e["to"], e.get("rotation"), sorted(e["faces"])) for e in data["elements"]]
        by_class.setdefault(klass, set()).add(json.dumps(geometry))
        for e in data["elements"]:
            for f in e["faces"].values():
                assert all(0 <= x <= 16 for x in f["uv"])
    assert all(len(g) == 1 for g in by_class.values())
