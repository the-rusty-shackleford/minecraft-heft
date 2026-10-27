"""food: phase 4's items -- vanilla's and Farmer's Delight's food -- each a class and a finish, its colour
measured from its own sprite (numbers only), its parts made of the materials its class names.

Copyright 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later.
"""
from __future__ import annotations

# item -> (class, finish). Raw and cooked pairs share a class and differ in colour and finish.
FOOD: dict[str, tuple[str, str]] = {
    "minecraft:apple": ("fruit", "skin"), "minecraft:golden_apple": ("fruit", "gilt"), "minecraft:enchanted_golden_apple": ("fruit", "gilt"),
    "minecraft:beetroot": ("fruit", "skin"), "minecraft:chorus_fruit": ("fruit", "skin"), "minecraft:popped_chorus_fruit": ("fruit", "crust"),
    "minecraft:spider_eye": ("fruit", "meat"), "minecraft:fermented_spider_eye": ("fruit", "meat"),
    "minecraft:egg": ("egg", "shell"),
    "minecraft:carrot": ("root", "skin"), "minecraft:golden_carrot": ("root", "gilt"),
    "minecraft:potato": ("tuber", "skin"), "minecraft:baked_potato": ("tuber", "crust"), "minecraft:poisonous_potato": ("tuber", "skin"),
    "minecraft:bread": ("loaf", "crust"),
    "minecraft:cookie": ("disc", "crust"), "farmersdelight:honey_cookie": ("disc", "crust"), "farmersdelight:sweet_berry_cookie": ("disc", "crust"),
    "minecraft:pumpkin_pie": ("pie", "crust"), "farmersdelight:pumpkin_pie_slice": ("pie_slice", "crust"),
    "minecraft:beef": ("steak", "meat"), "minecraft:cooked_beef": ("steak", "cooked"), "minecraft:porkchop": ("chop", "meat"),
    "minecraft:cooked_porkchop": ("chop", "cooked"), "minecraft:mutton": ("leg", "meat"), "minecraft:cooked_mutton": ("leg", "cooked"),
    "minecraft:rotten_flesh": ("steak", "meat"), "farmersdelight:minced_beef": ("steak", "meat"),
    "farmersdelight:cod_slice": ("steak", "meat"), "farmersdelight:cooked_cod_slice": ("steak", "cooked"),
    "farmersdelight:salmon_slice": ("steak", "meat"), "farmersdelight:cooked_salmon_slice": ("steak", "cooked"),
    "minecraft:chicken": ("drumstick", "meat"), "minecraft:cooked_chicken": ("drumstick", "cooked"),
    "minecraft:rabbit": ("drumstick", "meat"), "minecraft:cooked_rabbit": ("drumstick", "cooked"),
    "farmersdelight:chicken_cuts": ("drumstick", "meat"), "farmersdelight:cooked_chicken_cuts": ("drumstick", "cooked"),
    "minecraft:cod": ("fish", "scales"), "minecraft:cooked_cod": ("fish", "cooked"), "minecraft:salmon": ("fish", "scales"),
    "minecraft:cooked_salmon": ("fish", "cooked"), "minecraft:tropical_fish": ("fish", "scales"), "minecraft:pufferfish": ("fish", "scales"),
    "minecraft:sweet_berries": ("berries", "skin"), "minecraft:glow_berries": ("berries", "skin"),
    "minecraft:melon_slice": ("wedge", "flesh"), "minecraft:glistering_melon_slice": ("wedge", "gilt"), "farmersdelight:pumpkin_slice": ("wedge", "flesh"),
    "minecraft:dried_kelp": ("plate", "hide"),
    **{f"minecraft:{s}": ("bowl", "liquid") for s in ("mushroom_stew", "rabbit_stew", "beetroot_soup", "suspicious_stew")},
    **{f"farmersdelight:{s}": ("bowl", "liquid") for s in (
        "baked_cod_stew", "bone_broth", "chicken_soup", "fish_stew", "onion_soup", "pumpkin_soup", "vegetable_soup", "tomato_sauce")},
    **{f"farmersdelight:{s}": ("bowl", "cooked") for s in (
        "fried_rice", "mushroom_rice", "vegetable_noodles", "pasta_with_meatballs", "pasta_with_mutton_chop", "squid_ink_pasta",
        "ratatouille", "mixed_salad", "nether_salad", "gleaming_salad", "cabbage_rolls")},
    **{f"farmersdelight:{s}": ("plated", "cooked") for s in (
        "grilled_salmon", "roasted_mutton_chops", "honey_glazed_ham", "roast_chicken", "shepherds_pie", "stuffed_potato")},
    **{f"farmersdelight:{s}": ("stack", "crust") for s in ("bacon_sandwich", "chicken_sandwich", "egg_sandwich")},
}

# Hue bands for single items whose sprite draws leaves beside the fruit.
BAND = {"minecraft:sweet_berries": (330, 25), "minecraft:glow_berries": (20, 70)}

GLOWS = {"minecraft:glow_berries", "minecraft:enchanted_golden_apple"}
F0 = {"gilt": 231}  # gold leaf on a golden apple, carrot or melon: LabPBR's gold


# Items the game draws with another item's sprite.
SAME_SPRITE = {"minecraft:enchanted_golden_apple": "minecraft:golden_apple"}


def colour(item: str) -> str:
    """effects: returns the measured class the item's own sprite is measured into"""
    ns, path = SAME_SPRITE.get(item, item).split(":")
    return f"food_{ns}_{path}"
