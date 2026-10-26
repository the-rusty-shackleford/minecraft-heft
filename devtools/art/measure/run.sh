#!/bin/sh
# The measuring side of the wall (D-0001): reads Rusty's own copy of Pixlli, and, for a material it
# draws nothing for, the owning mod's own sprite (the client jar, the instance's mods); writes numbers
# only to devtools/art/heft-style.json. Nothing under devtools/art/ but this folder names a pack.
here=$(dirname "$0")
inst="$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher"
pack="${1:-$inst/instances/Minecraft/minecraft/resourcepacks/Pixlli V57 26.3-1.13 128x.zip}"
exec ~/.local/bin/uv run --no-project --with numpy --with pillow python -I "$here/../../../../tools/pbr/measure.py" \
    "$pack" "$here/classes.json" "$here/../heft-style.json" \
    "$inst/libraries/com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar" "$inst/instances/Minecraft/minecraft/mods"
