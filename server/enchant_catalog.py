#!/usr/bin/env python3
"""Prices and descriptions for every enchantment the ClassicGUIShop book shop can sell.

Usage:
  enchant_catalog.py apply <guishop enchantments.json>   rewrite prices/enabled flags in place
  enchant_catalog.py txt <output.txt>                    write the player guide (CUSTOM_ENCHANTS.txt)

A price is per level (level III costs 3x). None means the enchant is not sold, with the reason given.
"""

import json
import os
import sys
import tempfile
from pathlib import Path

SWORD, BOOTS, LEGS, CHEST, HELMET, ARMOR = "Swords", "Boots", "Leggings", "Chestplates", "Helmets", "Any armor piece"
TOOLS = "Pickaxes, axes, shovels and hoes"
PET_ARMOR = "Horse armor and wolf armor"
NETHER_STAR = "Nether Star (hold it in your offhand)"

# id path -> (name, goes on, max level, price per level or None, what it does, note or removal reason)
CUSTOM = {
    # ---- Common / rare / particle
    "0_common_enchants_item/auto_jump": ("Auto Jump", BOOTS, 2, 150, "Walk straight up blocks: step height +2 (I) or +3 (II), no jumping needed.", ""),
    "2_rare_enchants_item/ally_item_display": ("Allay Item Display", "Item Frame", 1, 150, "Hit a block with the enchanted item frame to place a decorative allay that holds an item.", ""),
    "2_rare_enchants_item/loyalty_speed": ("Loyalty Speed", "Trident", 3, 200, "A Loyalty trident flies back to you faster.", "Not with Riptide."),
    "2_rare_enchants_item/water_breathing": ("Water Breathing", HELMET, 1, 600, "Gives Water Breathing for 30 seconds every 4 minutes.", ""),
    "3_particle_enchants_item/aura_blue": ("Blue Aura", BOOTS, 1, 250, "Blue particles swirl around you (cosmetic).", "Only one aura per pair of boots."),
    "3_particle_enchants_item/aura_green": ("Green Aura", BOOTS, 1, 250, "Green particles swirl around you (cosmetic).", "Only one aura per pair of boots."),
    "3_particle_enchants_item/aura_orange": ("Orange Aura", BOOTS, 1, 250, "Orange particles swirl around you (cosmetic).", "Only one aura per pair of boots."),
    "3_particle_enchants_item/aura_purple": ("Purple Aura", BOOTS, 1, 250, "Purple particles swirl around you (cosmetic).", "Only one aura per pair of boots."),
    "3_particle_enchants_item/aura_red": ("Red Aura", BOOTS, 1, 250, "Red particles swirl around you (cosmetic).", "Only one aura per pair of boots."),
    "3_particle_enchants_item/aura_rgb": ("RGB Aura", BOOTS, 1, 400, "Rainbow particles swirl around you (cosmetic).", "Only one aura per pair of boots."),
    "3_particle_enchants_item/aura_yellow": ("Yellow Aura", BOOTS, 1, 250, "Yellow particles swirl around you (cosmetic).", "Only one aura per pair of boots."),
    "3_particle_enchants_item/flame_sword": ("Flame Sword", SWORD, 1, 300, "A ring of flame particles appears around whatever you hit (cosmetic, no extra damage).", ""),
    # ---- Epic / ultimate
    "4_epic_enchants_item/fire_resistance": ("Fire Resistance", LEGS, 1, 5000, "Permanent Fire Resistance while worn.", ""),
    "4_epic_enchants_item/head_cosmetic": ("Head Cosmetic", "Decorative blocks (glass, flowers, lanterns and similar)", 1, 300, "Hit a block while holding the enchanted block to wear it as a hat. Your helmet slot must be empty.", ""),
    "4_epic_enchants_item/invisible_item_frame": ("Invisible Item Frame", "Item Frame / Glow Item Frame", 1, 200, "Item frames you place are invisible, so only the item shows.", ""),
    "4_epic_enchants_item/night_vision": ("Night Vision", HELMET, 1, 800, "Permanent Night Vision while worn.", ""),
    "5_ultimate_enchants_item/baby_mode": ("Baby Mode", "Turtle Shell helmet", 1, 500, "Shrinks you to half size while worn.", ""),
    "5_ultimate_enchants_item/copper_generator": ("Copper Generator", "Block of Copper", 1, None, "Turns the block into a mob that drops copper every 5 minutes forever.", "REMOVED: infinite free resources."),
    "5_ultimate_enchants_item/jump_boost": ("Jump Boost", BOOTS, 3, 800, "Permanent Jump Boost I-III while worn.", ""),
    "5_ultimate_enchants_item/livestock_slayer": ("Livestock Slayer", SWORD, 5, 80, "+1 damage per level against cows, sheep, pigs and chickens.", "Only one Slayer enchant per sword."),
    "5_ultimate_enchants_item/pet_glow": ("Pet Glow", PET_ARMOR, 1, 150, "Your horse or wolf glows so you can find it.", ""),
    "5_ultimate_enchants_item/pet_jump": ("Pet Jump", PET_ARMOR, 1, 300, "Gives your horse or wolf Jump Boost III.", ""),
    "5_ultimate_enchants_item/poison_aspect": ("Poison Aspect", SWORD, 1, 2500, "10% chance to poison the target for 3-6 seconds.", "Not with Fire Aspect."),
    "5_ultimate_enchants_item/speed_boost": ("Speed Boost", BOOTS, 3, 2000, "Permanent Speed I-III while worn.", ""),
    # ---- Legendary / mythical
    "6_legendary_enchants_item/auto_beetroot_planter": ("Auto Beetroot Planter", "Hoes", 1, 300, "Plants beetroot seeds from your inventory on empty farmland you walk over.", "Only one Auto Planter per hoe."),
    "6_legendary_enchants_item/auto_wheat_planter": ("Auto Wheat Planter", "Hoes", 1, 300, "Plants wheat seeds from your inventory on empty farmland you walk over.", "Only one Auto Planter per hoe."),
    "6_legendary_enchants_item/coal_generator": ("Coal Generator", "Block of Coal", 1, None, "Turns the block into a mob that drops coal every 5 minutes forever.", "REMOVED: infinite free resources."),
    "6_legendary_enchants_item/guardian_slayer": ("Guardian Slayer", SWORD, 5, 150, "+1 damage per level against guardians and elder guardians.", "Only one Slayer enchant per sword."),
    "6_legendary_enchants_item/haste_boost": ("Haste", TOOLS, 3, 1000, "Haste I-III while you hold the tool.", ""),
    "6_legendary_enchants_item/pet_speed": ("Pet Speed", PET_ARMOR, 1, 400, "Gives your horse or wolf Speed III.", ""),
    "6_legendary_enchants_item/redstone_generator": ("Redstone Generator", "Block of Redstone", 1, None, "Turns the block into a mob that drops redstone every 5 minutes forever.", "REMOVED: infinite free resources."),
    "6_legendary_enchants_item/slime_slayer": ("Slime Slayer", SWORD, 5, 100, "+1 damage per level against slimes and magma cubes.", "Only one Slayer enchant per sword."),
    "7_mythical_enchants_item/auto_carrot_planter": ("Auto Carrot Planter", "Hoes", 1, 300, "Plants carrots from your inventory on empty farmland you walk over.", "Only one Auto Planter per hoe."),
    "7_mythical_enchants_item/auto_potato_planter": ("Auto Potato Planter", "Hoes", 1, 300, "Plants potatoes from your inventory on empty farmland you walk over.", "Only one Auto Planter per hoe."),
    "7_mythical_enchants_item/cresselia": ("Cresselia", SWORD, 3, 800, "+0.5 damage per level during the day.", "Only one of Cresselia, Darkrai or Torrent."),
    "7_mythical_enchants_item/darkrai": ("Darkrai", SWORD, 3, 800, "+0.5 damage per level at night.", "Only one of Cresselia, Darkrai or Torrent."),
    "7_mythical_enchants_item/dolphins_grace": ("Dolphin's Grace", BOOTS, 1, 1500, "Permanent Dolphin's Grace (fast swimming) while worn.", ""),
    "7_mythical_enchants_item/fangs_sword": ("Fangs Sword", SWORD, 1, None, "30% chance to summon evoker fangs under the target on hit.", "REMOVED: too strong in PvP."),
    "7_mythical_enchants_item/infinite_milk_bucket": ("Infinite Milk Bucket", NETHER_STAR, 1, 2000, "Turns an empty bucket in your inventory into a milk bucket.", "Careful: it removes ALL empty buckets you carry and gives back only one milk bucket."),
    "7_mythical_enchants_item/lapis_generator": ("Lapis Generator", "Block of Lapis", 1, None, "Turns the block into a mob that drops lapis every 5 minutes forever.", "REMOVED: infinite free resources."),
    "7_mythical_enchants_item/pet_resistance": ("Pet Resistance", PET_ARMOR, 1, 600, "Gives your horse or wolf Resistance II.", ""),
    "7_mythical_enchants_item/skeleton_slayer": ("Skeleton Slayer", SWORD, 5, 150, "+1 damage per level against skeletons, strays and wither skeletons.", "Only one Slayer enchant per sword."),
    "7_mythical_enchants_item/spider_slayer": ("Spider Slayer", SWORD, 5, 120, "+1 damage per level against spiders and cave spiders.", "Only one Slayer enchant per sword."),
    "7_mythical_enchants_item/super_firework": ("Super Firework", "Firework Rocket", 2, 1000, "Rockets boost your elytra for longer (flight duration 4 or 5).", ""),
    "7_mythical_enchants_item/torrent": ("Torrent", SWORD, 3, 800, "+0.5 damage per level while it is raining.", "Only one of Cresselia, Darkrai or Torrent."),
    "7_mythical_enchants_item/upgrade_frost_walker": ("Upgrade Frost Walker", BOOTS, 1, 600, "Hold the boots in your MAIN hand and they become Frost Walker V.", ""),
    "7_mythical_enchants_item/water_mining": ("Water Mining", "Pickaxes, axes and shovels", 3, 300, "Mine faster while underwater.", ""),
    "7_mythical_enchants_item/zombie_slayer": ("Zombie Slayer", SWORD, 5, 150, "+1 damage per level against zombies, husks, drowned and zombie villagers.", "Only one Slayer enchant per sword."),
    # ---- Special
    "8_special_enchants_item/advance_efficiency": ("Advance Efficiency", TOOLS, 3, 3000, "Huge mining speed: +50 / +85 / +120 efficiency (near-instant mining).", "Not with Efficiency."),
    "8_special_enchants_item/advance_riptide": ("Advance Riptide", "Trident", 5, 500, "A stronger Riptide that launches you further.", "Not with Riptide, Loyalty or Loyalty Speed."),
    "8_special_enchants_item/anti_gravity": ("Anti Gravity", BOOTS, 1, 6000, "No fall damage while worn.", ""),
    "8_special_enchants_item/armor_plus": ("Armor Plus", CHEST, 2, 2500, "+1 (I) or +2 (II) armor points.", ""),
    "8_special_enchants_item/auto_farmland": ("Auto Farmland", "Hoes", 1, 400, "Grass and dirt you walk over turn into farmland.", ""),
    "8_special_enchants_item/burning_time": ("Burning Time", BOOTS, 5, None, "Makes you stay on fire LONGER.", "REMOVED: it only hurts the wearer."),
    "8_special_enchants_item/compact_totem": ("Compact Totem", "Totem of Undying", 1, None, "Totems stack up to 3.", "REMOVED: totems are disabled on this server."),
    "8_special_enchants_item/ender_slayer": ("Ender Slayer", SWORD, 5, 200, "+1 damage per level against endermen and endermites.", "Only one Slayer enchant per sword."),
    "8_special_enchants_item/equipment_drops_bonus": ("Equipment Drops Bonus", SWORD, 2, 1500, "+10% (I) or +15% (II) chance that mobs drop the gear they wear.", ""),
    "8_special_enchants_item/fast_sweeping": ("Fast Sweeping", SWORD, 2, 1200, "Faster attack speed.", "Not with Advance Sharpness."),
    "8_special_enchants_item/gold_generator": ("Gold Generator", "Block of Gold", 1, None, "Turns the block into a mob that drops gold every 5 minutes forever.", "REMOVED: infinite free resources."),
    "8_special_enchants_item/illager_slayer": ("Illager Slayer", SWORD, 5, 250, "+1 damage per level against pillagers, vindicators, evokers, witches and ravagers.", "Only one Slayer enchant per sword."),
    "8_special_enchants_item/infinite_lava_bucket": ("Infinite Lava Bucket", NETHER_STAR, 1, 5000, "Turns an empty bucket in your inventory into a lava bucket.", "Careful: it removes ALL empty buckets you carry and gives back only one lava bucket."),
    "8_special_enchants_item/infinite_water_bucket": ("Infinite Water Bucket", NETHER_STAR, 1, 3000, "Turns an empty bucket in your inventory into a water bucket.", "Careful: it removes ALL empty buckets you carry and gives back only one water bucket."),
    "8_special_enchants_item/iron_generator": ("Iron Generator", "Block of Iron", 1, None, "Turns the block into a mob that drops iron every 5 minutes forever.", "REMOVED: infinite free resources."),
    "8_special_enchants_item/lighting_sword": ("Lightning Sword", SWORD, 1, None, "30% chance to strike the target with lightning.", "REMOVED: too strong in PvP."),
    "8_special_enchants_item/lucky": ("Lucky", "Fishing Rod", 3, 500, "Luck I-III while you hold the rod (better fishing loot).", ""),
    "8_special_enchants_item/mob_egg_grinder": ("Mob Egg Grinder", SWORD, 1, 4000, "0.1% chance that a mob you kill drops its spawn egg.", ""),
    "8_special_enchants_item/pet_max_health": ("Pet Max Health", PET_ARMOR, 1, 800, "Gives your horse or wolf more health.", ""),
    "8_special_enchants_item/saturation": ("Saturation", HELMET, 1, 6000, "Permanent Saturation (you never get hungry) while worn.", ""),
    "8_special_enchants_item/self_repair": ("Self Repair", TOOLS, 4, 1500, "30% chance that using the tool costs no durability.", "Not with Mending or Unbreakable."),
    "8_special_enchants_item/upgrade_lure": ("Upgrade Lure", "Fishing Rod", 1, 800, "The rod becomes Lure V (fish bite very fast).", ""),
    "8_special_enchants_item/upgrade_multishot": ("Upgrade MultiShot", "Bow", 2, 1500, "The bow fires extra arrows like Multishot.", ""),
    "8_special_enchants_item/upgrade_power": ("Upgrade Power", "Bow", 1, None, "The bow becomes Power X while held.", "REMOVED: Power X one-shots players."),
    # ---- Exclusive
    "9_exclusive_enchants_item/advance_sharpness": ("Advance Sharpness", SWORD, 3, None, "+15 / +20 / +25 attack damage.", "REMOVED: one-shots players."),
    "9_exclusive_enchants_item/apple_of_midas": ("Apple of Midas", "Shears", 1, 1500, "1% chance for a golden apple when you break oak leaves.", ""),
    "9_exclusive_enchants_item/auto_craft": ("Auto Craft", NETHER_STAR, 1, 2000, "Automatically compacts items in your inventory into blocks (9 ingots -> block, 4 -> block).", "Not with the Infinite Bucket enchants."),
    "9_exclusive_enchants_item/auto_smelt": ("Auto Smelt", "Pickaxes and shovels", 1, 4000, "Mined stone, ores, sand, clay and ancient debris drop already smelted. Fortune still works.", "Not with Silk Touch."),
    "9_exclusive_enchants_item/break_reach": ("Break Reach", TOOLS, 2, 2500, "Break blocks from further away.", ""),
    "9_exclusive_enchants_item/chaos_attacker": ("Chaos Attacker", SWORD, 1, None, "Gives YOU Darkness on every hit, with tiny random effects on you or the target.", "REMOVED: mostly hurts the user."),
    "9_exclusive_enchants_item/chicken_generator": ("Food Generator", "Raw Chicken", 1, None, "Turns the item into a mob that drops raw chicken every 5 minutes forever.", "REMOVED: infinite free resources."),
    "9_exclusive_enchants_item/diamond_generator": ("Diamond Generator", "Block of Diamond", 1, None, "Turns the block into a mob that drops a diamond every 5 minutes forever.", "REMOVED: infinite free diamonds."),
    "9_exclusive_enchants_item/emerald_generator": ("Emerald Generator", "Block of Emerald", 1, None, "Turns the block into a mob that drops an emerald every 5 minutes forever.", "REMOVED: infinite free resources."),
    "9_exclusive_enchants_item/ender_dragon_slayer": ("Ender Dragon Slayer", SWORD, 5, 400, "+1 damage per level against the Ender Dragon.", "Only one Slayer enchant per sword."),
    "9_exclusive_enchants_item/exp_mastery": ("Exp Mastery", BOOTS, 1, None, "Meant to boost XP gain.", "REMOVED: broken in this version (does nothing)."),
    "9_exclusive_enchants_item/glass_breaker": ("Glass Breaker", "Shears", 1, 300, "Break glass instantly.", ""),
    "9_exclusive_enchants_item/inquisitive": ("Inquisitive", SWORD, 5, 400, "Undead mobs drop 1.5x to 3.5x more XP.", ""),
    "9_exclusive_enchants_item/item_magnet": ("Item Magnet", BOOTS, 1, 3000, "Pulls dropped items within 5 blocks to you.", ""),
    "9_exclusive_enchants_item/max_health": ("Max Health", LEGS, 1, None, "Health Boost III (+6 hearts).", "REMOVED: bypasses the lifesteal heart system."),
    "9_exclusive_enchants_item/mining_experience_bonus": ("Mining Experience Bonus", "Pickaxes", 5, 150, "A little extra XP from mining.", ""),
    "9_exclusive_enchants_item/netherite_generator": ("Netherite Generator", "Block of Netherite", 1, None, "Turns the block into a mob that drops netherite scrap every 5 minutes forever.", "REMOVED: infinite free netherite."),
    "9_exclusive_enchants_item/pet_regeneration": ("Pet Regeneration", PET_ARMOR, 1, 1000, "Gives your horse or wolf Regeneration IV.", ""),
    "9_exclusive_enchants_item/regeneration_boots": ("Regeneration", LEGS, 2, None, "Permanent Regeneration I-II (despite the name it goes on leggings).", "REMOVED: too strong in PvP."),
    "9_exclusive_enchants_item/spawner_breaker": ("Spawner Breaker", "Pickaxes", 1, None, "Spawners drop themselves when mined.", "REMOVED: lets players stack mob farms of any type."),
    "9_exclusive_enchants_item/strength_gift": ("Strength Gift", SWORD, 1, None, "10% chance on hit to give you Strength.", "REMOVED: too strong in PvP."),
    "9_exclusive_enchants_item/super_knockback": ("Super Knockback", SWORD, 3, None, "+5 / +6 / +7 knockback.", "REMOVED: launches players off the map."),
    "9_exclusive_enchants_item/unbreakable": ("Unbreakable", "Tools, weapons, shears, fishing rods, flint and steel", 1, 12000, "The item never loses durability.", "Not with Unbreaking, Mending or Self Repair."),
    "9_exclusive_enchants_item/upgrade_fortune": ("Upgrade Fortune", "Pickaxes", 1, None, "The pickaxe becomes Fortune V while held.", "REMOVED: breaks the economy."),
    "9_exclusive_enchants_item/upgrade_looting": ("Upgrade Looting", SWORD, 1, None, "The sword becomes Looting V while held.", "REMOVED: breaks the economy."),
    "9_exclusive_enchants_item/warden_slayer": ("Warden Slayer", SWORD, 5, 500, "+1 damage per level against the Warden.", "Only one Slayer enchant per sword."),
    "9_exclusive_enchants_item/wither_slayer": ("Wither Slayer", SWORD, 5, 400, "+1 damage per level against the Wither.", "Only one Slayer enchant per sword."),
    # ---- Curses and internals: never sold
    "1_curse_enchants_item/curse_of_blindness": ("Curse of Blindness", ARMOR, 1, None, "Blindness for 10 seconds every minute.", "REMOVED: curse."),
    "1_curse_enchants_item/curse_of_glowing": ("Curse of Glowing", ARMOR, 1, None, "You glow through walls.", "REMOVED: curse."),
    "1_curse_enchants_item/curse_of_mining_fatigue": ("Curse of Mining Fatigue", "Tools", 1, None, "Mining Fatigue III while held.", "REMOVED: curse."),
    "1_curse_enchants_item/curse_of_poison": ("Curse of Poison", ARMOR, 1, None, "Poison for 10 seconds every minute.", "REMOVED: curse."),
    "1_curse_enchants_item/curse_of_slowness": ("Curse of Slowness", ARMOR, 1, None, "Slowness for 10 seconds every minute.", "REMOVED: curse."),
    "1_curse_enchants_item/curse_of_sound": ("Curse of Sound", SWORD, 1, None, "Plays anvil and donkey sounds when you hit.", "REMOVED: curse."),
    "1_curse_enchants_item/curse_of_unluck": ("Curse of Unluck", ARMOR, 1, None, "Bad Luck for 20 seconds every minute.", "REMOVED: curse."),
    "random_enchant_item/random_enchant": ("Random Enchant", "(internal)", 1, None, "Internal helper used by the mod.", "REMOVED: not a real enchant."),
}

VANILLA = {
    "protection": 300, "fire_protection": 120, "blast_protection": 150, "projectile_protection": 150,
    "feather_falling": 150, "respiration": 100, "aqua_affinity": 150, "thorns": 250, "depth_strider": 120,
    "frost_walker": 150, "soul_speed": 150, "swift_sneak": 400,
    "sharpness": 300, "smite": 120, "bane_of_arthropods": 80, "knockback": 150, "fire_aspect": 400,
    "looting": 700, "sweeping_edge": 150,
    "efficiency": 200, "silk_touch": 1500, "unbreaking": 300, "fortune": 900, "mending": 3500,
    "power": 250, "punch": 200, "flame": 600, "infinity": 2000,
    "luck_of_the_sea": 200, "lure": 150,
    "loyalty": 200, "impaling": 150, "riptide": 250, "channeling": 800,
    "multishot": 800, "quick_charge": 250, "piercing": 150,
    "density": 250, "breach": 400, "wind_burst": 600, "lunge": 250,
    "binding_curse": None, "vanishing_curse": None,
}


def price_table():
    table = {f"minecraft:{key}": price for key, price in VANILLA.items()}
    table.update({f"enchantplus:{key}": entry[3] for key, entry in CUSTOM.items()})
    return table


def apply(path):
    path = Path(path)
    data = json.loads(path.read_text())
    table = price_table()
    changed = unknown = 0
    for key, offer in data.get("offers", {}).items():
        if key not in table:
            unknown += 1
            offer["enabled"] = False
            continue
        price = table[key]
        offer["enabled"] = price is not None
        if price is not None:
            offer["pricePerLevel"] = float(price)
        if key.startswith("enchantplus:"):
            offer["name"] = CUSTOM[key.split(":", 1)[1]][0]
        changed += 1
    data["defaultPricePerLevel"] = 2000.0
    fd, tmp = tempfile.mkstemp(dir=path.parent, prefix=path.name, suffix=".tmp")
    with os.fdopen(fd, "w") as out:
        json.dump(data, out, indent=2)
    os.replace(tmp, path)
    sold = sum(1 for offer in data["offers"].values() if offer.get("enabled"))
    print(f"updated {changed} offers ({sold} for sale, {unknown} unknown ones hidden)")


def roman(n):
    return ["", "I", "II", "III", "IV", "V"][n] if n <= 5 else str(n)


def money(n):
    return f"${n:,.0f}"


def txt(out):
    lines = [
        "CUSTOM ENCHANTS ON PLAY.COSMICPIXEL.ORG",
        "=" * 39,
        "",
        "Where to get them:",
        "  /shop -> Enchanted Books   buy a specific enchant book for money (price per level below)",
        "  /enchants (or /ce)         random book for XP levels: Common 5, Epic 15, Legendary 25, Exclusive 40",
        "  Put the book and your item in an anvil to apply it.",
        "",
        "Rules that apply to every custom enchant:",
        "  - Only ONE Slayer enchant per sword (Livestock, Zombie, Skeleton, Spider, Ender, ... Slayer).",
        "  - Effects like Speed, Night Vision or Fire Resistance only work while the item is worn or held.",
        "  - Enchants in the REMOVED list at the end cannot be bought or rolled. The reason is listed next to them.",
        "",
    ]
    groups = [
        ("COMBAT (swords)", lambda e: e[1] == SWORD),
        ("ARMOR", lambda e: e[1] in (BOOTS, LEGS, CHEST, HELMET, ARMOR) or "Turtle" in e[1]),
        ("TOOLS", lambda e: any(word in e[1] for word in ("Pickaxe", "pickaxe", "Hoes", "Shears", "Tools", "Fishing", "Bow", "Trident"))),
        ("PETS", lambda e: e[1] == PET_ARMOR),
        ("EVERYTHING ELSE", lambda e: True),
    ]
    groups = [(title, lambda e, test=test: e[3] is not None and test(e)) for title, test in groups]
    groups.append(("REMOVED (cannot be bought or rolled)", lambda e: True))
    placed = set()
    for title, test in groups:
        entries = [(key, e) for key, e in sorted(CUSTOM.items(), key=lambda kv: kv[1][0]) if key not in placed and test(e)]
        if not entries:
            continue
        lines += [title, "-" * len(title)]
        for key, (name, goes_on, max_level, price, does, note) in entries:
            placed.add(key)
            levels = f" I-{roman(max_level)}" if max_level > 1 else ""
            if price is None:
                cost = "not sold"
            elif max_level > 1:
                cost = f"{money(price)} per level ({money(price * max_level)} for {roman(max_level)})"
            else:
                cost = money(price)
            lines.append(f"{name}{levels}")
            lines.append(f"    Goes on: {goes_on}")
            lines.append(f"    Does:    {does}")
            lines.append(f"    Price:   {cost}")
            if note:
                lines.append(f"    Note:    {note}")
            lines.append("")
    Path(out).write_text("\n".join(lines))
    print(f"wrote {out}: {len(CUSTOM)} enchants, {sum(1 for e in CUSTOM.values() if e[3] is not None)} for sale")


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in ("apply", "txt"):
        sys.exit(__doc__)
    {"apply": apply, "txt": txt}[sys.argv[1]](sys.argv[2])
