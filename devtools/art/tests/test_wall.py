"""The clean-room wall (D-0001): the painter never opens a pack; the numbers file holds no image.

Partitions:
- every Python file under devtools/art outside measure/: names no pack, opens no zip, imports no measure
- heft-style.json: scalars and lists of at most eight; nothing image-sized
- the shipped textures: only Heft's own sheets, under assets/heft/textures/solid (never textures/item, which the block atlas sweeps up)
"""
import json
from pathlib import Path

ART = Path(__file__).resolve().parents[1]


def painter_files():
    return [p for p in ART.rglob("*.py") if "measure" not in p.parts and "tests" not in p.parts]


def test_the_painter_never_opens_a_pack():
    for p in painter_files():
        text = p.read_text()
        assert "zipfile" not in text and "Pixlli" not in text and "resourcepacks" not in text, p
        assert "import measure" not in text and "from measure" not in text, p


def test_the_numbers_file_holds_no_image():
    def walk(v, where):
        if isinstance(v, list):
            assert len(v) <= 8, where
            assert all(not isinstance(x, (list, dict)) for x in v), where
        elif isinstance(v, dict):
            for k, x in v.items():
                walk(x, f"{where}.{k}")
    walk(json.loads((ART / "heft-style.json").read_text()), "style")


def test_only_our_sheets_ship():
    textures = ART.parents[1] / "src" / "main" / "resources" / "assets"
    for png in textures.rglob("*.png"):
        assert png.relative_to(textures).parts[:3] == ("heft", "textures", "solid"), png
