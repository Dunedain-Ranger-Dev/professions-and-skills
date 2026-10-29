# Gives a kit for testing forged parts and gear without forging every ingot: tempered ingots at several stars, every
# flux, and the leather and cloth makings. Run with /function ddprofessions:dev/forging_kit.
give @p ddprofessions:tempered_iron_ingot[ddprofessions:stars=1] 32
give @p ddprofessions:tempered_iron_ingot[ddprofessions:stars=3] 32
give @p ddprofessions:tempered_iron_ingot[ddprofessions:stars=5] 32
give @p ddprofessions:tempered_iron_ingot[ddprofessions:stars=7] 32
give @p ddprofessions:sand_flux 16
give @p ddprofessions:borax_flux 16
give @p ddprofessions:runic_flux 8
give @p ddprofessions:runeforged_flux 8
give @p minecraft:iron_nugget 16
give @p minecraft:leather 16
give @p minecraft:honeycomb 16
give @p minecraft:string 32
give @p minecraft:white_wool 8
give @p minecraft:stick 8
give @p ddprofessions:quenching_oil 3
give @p patchouli:guide_book[patchouli:book="ddprofessions:smiths_handbook"]
tellraw @a {"text":"Forging kit given.","color":"green"}
