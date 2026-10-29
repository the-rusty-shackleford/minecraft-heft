"""tags: writes Set It Down's size and stacking tags for every item Heft has a class for (D-0006).

    uv run --no-project python devtools/tags.py [--check]

Reads src/main/resources/assets/heft/classes.json (item -> class) and writes
src/main/resources/data/setitdown/tags/item/: size/<size>.json and stackable_<kind>.json, each entry not required, so
an item of a mod the pack lacks is skipped. The jar carries these on the server too. With --check, writes nothing and
exits 1 if any tag file differs from what it would write (the build runs this).

Copyright 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "resources"
CLASSES = ROOT / "assets" / "heft" / "classes.json"
TAGS = ROOT / "data" / "setitdown" / "tags" / "item"

# Set It Down's size for each class not of normal size.
SIZE = {
    "plate": "small", "ingot": "small", "raw": "small", "gem": "small", "stack": "small", "drumstick": "small",
    "steak": "small", "pie_slice": "small", "wedge": "small", "shard": "small", "fruit": "small", "arrow": "small",
    "bucket": "small", "bucket_empty": "small", "tuber": "small", "rod": "small", "bone": "small", "loaf": "small",
    "bar": "small", "root": "small", "lump": "small", "leg": "small", "chop": "small", "rocket": "small",
    "horn": "small", "record": "small", "star": "small", "totem": "small",
    "spawn_egg": "tiny", "nugget": "tiny", "disc": "tiny", "dust": "tiny", "orb": "tiny", "egg": "tiny", "berries": "tiny",
}

# How Set It Down shares a spot between several of a class (the handoff's §4.4): flat things climb in a pile,
# lumpy things spread in a cluster; a class not named here is one to a spot unless Set It Down's own tags say otherwise.
STACKING = {
    "ingot": "pile", "nugget": "pile", "bar": "pile", "plate": "pile", "book": "pile", "record": "pile", "rod": "pile",
    "bone": "pile", "steak": "pile", "chop": "pile", "disc": "pile", "loaf": "pile", "pie": "pile", "pie_slice": "pile", "stack": "pile",
    "raw": "cluster", "lump": "cluster", "gem": "cluster", "shard": "cluster", "dust": "cluster", "orb": "cluster",
    "fruit": "cluster", "egg": "cluster", "berries": "cluster", "tuber": "cluster", "root": "cluster",
    "drumstick": "cluster",
}

# Meat by its size (D-0006): small cuts share a spot (a chop, a slice, mince, chicken cuts, by their class
# above); a whole joint is one to a spot: a steak, a leg of mutton, a whole chicken or rabbit, a whole fish. Set It
# Down's stackable_single outranks its pile and cluster tags (its D-0009), so these hold against its #c: food tags.
SINGLE = {
    "minecraft:beef", "minecraft:cooked_beef", "minecraft:mutton", "minecraft:cooked_mutton", "minecraft:rotten_flesh",
    "minecraft:chicken", "minecraft:cooked_chicken", "minecraft:rabbit", "minecraft:cooked_rabbit",
    "minecraft:cod", "minecraft:cooked_cod", "minecraft:salmon", "minecraft:cooked_salmon",
    "minecraft:tropical_fish", "minecraft:pufferfish",
}


def members(items: dict[str, str]) -> dict[str, list[str]]:
    """effects: returns each tag's members, by the classes of items (item -> class), in item order"""
    out: dict[str, list[str]] = {}
    for item, klass in sorted(items.items()):
        if klass in SIZE:
            out.setdefault(f"size/{SIZE[klass]}", []).append(item)
        kind = "single" if item in SINGLE else STACKING.get(klass)
        if kind:
            out.setdefault(f"stackable_{kind}", []).append(item)
    return out


def main() -> None:
    check = "--check" in sys.argv[1:]
    items = json.loads(CLASSES.read_text())["items"]
    tags = members(items)
    want = {TAGS / f"{tag}.json": json.dumps({"replace": False, "values": [{"id": i, "required": False} for i in values]}, indent=1) + "\n"
            for tag, values in tags.items()}
    stale = sorted(str(p.relative_to(TAGS)) for p in set(want) | set(TAGS.rglob("*.json"))
                   if not p.exists() or p.read_text() != want.get(p))
    if check:
        if stale:
            sys.exit(f"Set It Down's tags differ from the class table: {stale}; run devtools/tags.py")
        print("Set It Down's tags match the class table:", {k: len(v) for k, v in sorted(tags.items())})
        return
    for path in TAGS.rglob("*.json"):
        if path not in want:
            path.unlink()
    for path, text in want.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
    print("wrote Set It Down's tags:", {k: len(v) for k, v in sorted(tags.items())})


if __name__ == "__main__":
    main()
