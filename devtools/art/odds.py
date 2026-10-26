"""odds: phase 5's items -- dyes, music discs, buckets, sherds and patterns, orbs, and the odds and ends that
deserve a shape of their own -- each a class and a finish, its colour measured from its own sprite.

Copyright 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later.
"""
from __future__ import annotations

COLOURS = ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple",
           "blue", "brown", "green", "red", "black")
DISCS = ("11", "13", "5", "blocks", "bounce", "cat", "chirp", "creator", "creator_music_box", "far", "lava_chicken", "mall",
         "mellohi", "otherside", "pigstep", "precipice", "relic", "stal", "strad", "tears", "wait", "ward")
SHERDS = ("angler", "archer", "arms_up", "blade", "brewer", "burn", "danger", "explorer", "flow", "friend", "guster", "heart",
          "heartbreak", "howl", "miner", "mourner", "plenty", "prize", "scrape", "sheaf", "shelter", "skull", "snort")
PATTERNS = ("creeper", "flow", "flower", "globe", "guster", "mojang", "piglin", "skull")
BUCKETS = ("water", "lava", "milk", "powder_snow", "cod", "salmon", "pufferfish", "tropical_fish", "axolotl", "tadpole")

# item -> (class, finish)
ODDS: dict[str, tuple[str, str]] = {
    **{f"minecraft:{c}_dye": ("dust", "powder") for c in COLOURS},
    **{f"minecraft:music_disc_{d}": ("record", "gilt") for d in DISCS},
    **{f"minecraft:{s}_pottery_sherd": ("plate", "clay") for s in SHERDS},
    **{f"minecraft:{p}_banner_pattern": ("plate", "paper") for p in PATTERNS},
    "minecraft:bucket": ("bucket_empty", "metal"),
    **{f"minecraft:{b}_bucket": ("bucket", "liquid") for b in BUCKETS},
    **{f"minecraft:{o}": ("orb", f) for o, f in (
        ("ender_pearl", "gem"), ("ender_eye", "gem"), ("slime_ball", "skin"), ("magma_cream", "skin"), ("snowball", "powder"),
        ("fire_charge", "rock"), ("wind_charge", "crystal"), ("heart_of_the_sea", "gem"), ("clay_ball", "clay"), ("firework_star", "rock"))},
    "minecraft:nether_star": ("star", "gem"),
    "minecraft:shulker_shell": ("shell", "skin"),
    "minecraft:firework_rocket": ("rocket", "paper"),
    "minecraft:goat_horn": ("horn", "mineral"),
    "minecraft:totem_of_undying": ("totem", "gilt"),
    "minecraft:saddle": ("saddle", "hide"),
}

WHITE = {"minecraft:milk_bucket", "minecraft:powder_snow_bucket"}  # buckets of something white: their lightest colours
GLOWS = {"minecraft:magma_cream", "minecraft:fire_charge", "minecraft:nether_star", "minecraft:ender_eye"}
F0 = {"metal": 230}


def colour(item: str) -> str:
    ns, path = item.split(":")
    return f"odd_{ns}_{path}"
