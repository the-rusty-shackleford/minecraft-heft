#!/bin/sh
# The measuring side of the wall (D-0001): reads Rusty's own copy of Pixlli, writes numbers only to
# devtools/art/heft-style.json. Nothing under devtools/art/ but this folder names the pack.
here=$(dirname "$0")
pack="${1:-$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/Minecraft/minecraft/resourcepacks/Pixlli V57 26.3-1.13 128x.zip}"
exec ~/.local/bin/uv run --no-project --with numpy --with pillow python -I "$here/../../../../tools/pbr/measure.py" \
    "$pack" "$here/classes.json" "$here/../heft-style.json"
