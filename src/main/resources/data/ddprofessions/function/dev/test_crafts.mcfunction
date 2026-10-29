# Builds a test crafts hall south of the test forge: a level 3 tannery, weaving room, woodshop and goldsmithy around one Guild
# Ledger (DD-88), and gives a kit for every step. Run with /function ddprofessions:dev/test_crafts.
function ddprofessions:dev/clear_test_crafts

# Plank floor
fill -22 -61 16 -8 -61 30 minecraft:spruce_planks

# The Guild Ledger in the middle
setblock -15 -60 23 ddprofessions:guild_ledger[facing=south]

# Tannery (west): Tanning Rack, a cauldron for the vat, Shaping Rack, Tanning Barrel, Fleshing Beam
setblock -19 -60 20 ddprofessions:tanning_rack[facing=east]
setblock -19 -60 22 minecraft:cauldron
setblock -19 -60 24 ddprofessions:tanning_barrel
setblock -19 -60 26 ddprofessions:fleshing_beam[facing=east]
setblock -15 -60 26 ddprofessions:shaping_rack[facing=north]

# Weaving room (north): Spinning Wheel, a cauldron for the vat, loom, Great Loom, Dye Works
setblock -17 -60 19 ddprofessions:spinning_wheel[facing=south]
setblock -15 -60 21 minecraft:cauldron
setblock -15 -60 19 minecraft:loom[facing=south]
setblock -13 -60 19 ddprofessions:great_loom[facing=south]
setblock -11 -60 19 ddprofessions:dye_works

# Woodshop (east): smoker, a cauldron for the vat, Wood Lathe, Treadle Lathe, Bowyer's Bench
setblock -11 -60 22 minecraft:smoker[facing=west]
setblock -11 -60 24 minecraft:cauldron
setblock -13 -60 27 ddprofessions:wood_lathe[facing=north]
setblock -11 -60 28 ddprofessions:treadle_lathe[facing=west]
setblock -9 -60 26 ddprofessions:bowyers_bench[facing=west]

# Goldsmithy (south): furnace, Crucible, Jeweler's Bench, Great Crucible, Jeweler's Loupe
setblock -17 -60 29 minecraft:furnace[facing=north]
setblock -15 -60 29 ddprofessions:crucible
setblock -13 -60 29 ddprofessions:jewelers_bench[facing=north]
setblock -11 -60 29 ddprofessions:great_crucible
setblock -19 -60 29 ddprofessions:jewelers_loupe[facing=north]

# Skill levels high enough for ★7, and a kit for each craft
skills set leatherworking 30
skills set weaving 30
skills set woodworking 30
skills set goldsmithing 30
skills set weaponsmithing 30
skills set armorsmithing 30
give @p minecraft:leather 8
give @p ddprofessions:bark_liquor 3
give @p ddprofessions:elder_liquor 3
give @p minecraft:shears
give @p minecraft:white_wool 8
give @p ddprofessions:wheat_sizing 2
give @p ddprofessions:arcane_sizing 2
give @p minecraft:oak_log 8
give @p ddprofessions:linseed_oil 2
give @p ddprofessions:sylvan_sap 2
give @p minecraft:coal 8
give @p minecraft:gold_ingot 12
give @p ddprofessions:bone_ash 2
give @p ddprofessions:sunsalt 2
tellraw @a {"text":"Test crafts hall built. Right-click the Guild Ledger to check it.","color":"green"}
