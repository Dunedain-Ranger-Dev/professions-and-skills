package io.github.ranger3462.ddprofessions.skill;

import net.minecraft.world.entity.player.Player;

/**
 * Skill tree perks our own code acts on (PROF-1, PROF-15). Each rank is a node in a Pufferfish's Skills tree whose
 * reward is a player tag ({@link #tag}), so unlocking the node turns the perk on and a respec turns it off. The trees
 * are written by tools/skill_trees.py, which uses the same tag names.
 */
public enum Perk {
    // Weaponsmithing (the anvil)
    STEADY_HAND("steady_hand", 3),
    SURE_STRIKES("sure_strikes", 2),
    KEEN_EDGE("keen_edge", 1),
    BALANCED_BLADE("balanced_blade", 1),
    MASTERWORK_WEAPON("masterwork_weapon", 1),
    WEAPON_STUDIES("weapon_studies", 5),
    // Armorsmithing (the grindstone, and the metal)
    STEADY_GRIND("steady_grind", 3),
    SLOW_COOLING("slow_cooling", 2),
    THRIFTY("thrifty", 2),
    MASTERWORK_ARMOR("masterwork_armor", 1),
    ARMOR_STUDIES("armor_studies", 5),
    // Leatherworking
    STEADY_SCRAPER("steady_scraper", 3),
    CAREFUL_TANNER("careful_tanner", 2),
    WATER_SAVER("water_saver", 2),
    SUPPLE_HIDES("supple_hides", 1),
    TANNING_STUDIES("tanning_studies", 5),
    // Weaving
    STEADY_NEEDLE("steady_needle", 3),
    CAREFUL_WEAVER("careful_weaver", 2),
    QUICK_FINGERS("quick_fingers", 1),
    PADDING("padding", 1),
    FINE_WEAVE("fine_weave", 1),
    WEAVING_STUDIES("weaving_studies", 5),
    // Woodworking
    STEADY_KNIFE("steady_knife", 3),
    CAREFUL_CARVER("careful_carver", 2),
    SEASONER("seasoner", 1),
    FINE_SPLIT("fine_split", 1),
    MASTER_BOWYER("master_bowyer", 1),
    WOODWORKING_STUDIES("woodworking_studies", 5),
    // Goldsmithing
    STEADY_CHASE("steady_chase", 3),
    CAREFUL_GOLDSMITH("careful_goldsmith", 2),
    ASSAYER("assayer", 1),
    GEM_SETTER("gem_setter", 1),
    MASTERWORK_JEWELRY("masterwork_jewelry", 1),
    GOLDSMITH_STUDIES("goldsmith_studies", 5),
    // Mining
    VEIN_MINER("vein_miner", 3),
    PROSPECTOR("prospector", 1),
    STONEMASON("stonemason", 1),
    STURDY_PICK("sturdy_pick", 1),
    DEEP_SIGHT("deep_sight", 1),
    MOTHERLODE("motherlode", 1),
    // Woodcutting
    TREE_FELLER("tree_feller", 3),
    FORESTER("forester", 1),
    TIMBER("timber", 1),
    STURDY_AXE("sturdy_axe", 1),
    BARK_COLLECTOR("bark_collector", 1),
    HEARTWOOD("heartwood", 1),
    // Excavation
    TREASURE_SENSE("treasure_sense", 1),
    SWIFT_SHOVEL("swift_shovel", 1),
    EARTHMOVER("earthmover", 3),
    STURDY_SHOVEL("sturdy_shovel", 1),
    SIFTER("sifter", 1),
    BURIED_RICHES("buried_riches", 1),
    // Farming
    BOUNTIFUL("bountiful", 1),
    REPLANTER("replanter", 1),
    HARVEST_MOON("harvest_moon", 3),
    FERTILE_GROUND("fertile_ground", 1),
    GOLDEN_HARVEST("golden_harvest", 1),
    // Fishing
    QUICK_BITE("quick_bite", 1),
    TREASURE_HUNTER("treasure_hunter", 3),
    DOUBLE_CATCH("double_catch", 1),
    SHAKE("shake", 1),
    MASTER_ANGLER("master_angler", 1),
    // One-Handed
    RIPOSTE("riposte", 1),
    FOOTWORK("footwork", 1),
    BLADESTORM("bladestorm", 3),
    BLEED("bleed", 1),
    DISARM("disarm", 1),
    DUELIST("duelist", 1),
    // Two-Handed
    CLEAVE("cleave", 1),
    CRUSHING_BLOW("crushing_blow", 1),
    SKULL_SPLITTER("skull_splitter", 3),
    STAGGER("stagger", 1),
    MOMENTUM("momentum", 1),
    EXECUTIONER("executioner", 1),
    // Polearms
    REACH("reach", 1),
    BRACE("brace", 1),
    IMPALE("impale", 3),
    SWEEP("sweep", 1),
    KEEP_AWAY("keep_away", 1),
    PHALANX("phalanx", 1),
    // Daggers
    BACKSTAB("backstab", 1),
    QUICK_HANDS("quick_hands", 1),
    SHADOW_FLURRY("shadow_flurry", 3),
    POISON_EDGE("poison_edge", 1),
    VANISH("vanish", 1),
    ASSASSINATE("assassinate", 1),
    // Unarmed
    IRON_FIST("iron_fist", 1),
    DEFLECT("deflect", 1),
    BERSERK("berserk", 3),
    WRESTLE("disarm_unarmed", 1),
    IRON_GRIP("iron_grip", 1),
    MARTIAL_MASTER("martial_master", 1),
    // Archery
    STEADY_AIM("steady_aim", 1),
    RETRIEVAL("retrieval", 1),
    VOLLEY("volley", 3),
    DAZE("daze", 1),
    PIERCING("piercing", 1),
    DEADEYE("deadeye", 1),
    // Crossbows
    QUICK_RELOAD("quick_reload", 1),
    BOLT_PIERCE("bolt_pierce", 1),
    RAPID_FIRE("rapid_fire", 3),
    EXPLOSIVE_BOLTS("explosive_bolts", 1),
    PIN("pin", 1),
    SIEGEBREAKER("siegebreaker", 1),
    // Dual Wielding
    FLURRY("flurry", 1),
    OFF_HAND_GUARD("off_hand_guard", 1),
    TWIN_FURY("twin_fury", 3),
    REND("dual_bleed", 1),
    PARRY("parry", 1),
    WHIRLWIND("whirlwind", 1),
    // Blocking
    BULWARK("bulwark", 1),
    QUICK_GUARD("quick_guard", 1),
    SHIELD_WALL("shield_wall", 3),
    SHIELD_BASH("shield_bash", 1),
    REFLECT("reflect", 1),
    UNBREAKABLE("unbreakable", 1),
    // Light Armor
    EVASIVE("evasive", 1),
    SILENT_STEP("silent_step", 1),
    AGILITY("agility", 3),
    LIGHT_FOOTED("light_footed", 1),
    SECOND_WIND("second_wind", 1),
    PHANTOM("phantom", 1),
    // Medium Armor
    STEADY_FOOTING("steady_footing", 1),
    BATTLE_RALLY("battle_rally", 1),
    MAIL_BRACE("brace_mail", 1),
    VANGUARD("vanguard", 1),
    // Heavy Armor
    PLATED("plated", 1),
    JUGGERNAUT("juggernaut", 3),
    UNSHAKEN("unshaken", 1),
    HEAVY_BULWARK("heavy_bulwark", 1),
    FORTRESS("fortress", 1),
    // Mage Armor
    MANA_SHIELD("mana_shield", 1),
    ARCHMAGES_ROBE("archmages_robe", 1),
    // Magic schools: each has Quick Casting, a school effect and a capstone (MagicPerks).
    FIRE_QUICK_CASTING("fire_quick_casting", 1),
    FIRE_EFFECT("fire_effect", 1),
    FIRE_CAPSTONE("fire_capstone", 1),
    FROST_QUICK_CASTING("frost_quick_casting", 1),
    FROST_EFFECT("frost_effect", 1),
    FROST_CAPSTONE("frost_capstone", 1),
    LIGHTNING_QUICK_CASTING("lightning_quick_casting", 1),
    LIGHTNING_EFFECT("lightning_effect", 1),
    LIGHTNING_CAPSTONE("lightning_capstone", 1),
    ARCANE_QUICK_CASTING("arcane_quick_casting", 1),
    ARCANE_EFFECT("arcane_effect", 1),
    ARCANE_CAPSTONE("arcane_capstone", 1),
    HOLY_QUICK_CASTING("holy_quick_casting", 1),
    HOLY_EFFECT("holy_effect", 1),
    HOLY_CAPSTONE("holy_capstone", 1),
    EARTH_QUICK_CASTING("earth_quick_casting", 1),
    EARTH_EFFECT("earth_effect", 1),
    EARTH_CAPSTONE("earth_capstone", 1),
    WATER_QUICK_CASTING("water_quick_casting", 1),
    WATER_EFFECT("water_effect", 1),
    WATER_CAPSTONE("water_capstone", 1),
    AIR_QUICK_CASTING("air_quick_casting", 1),
    AIR_EFFECT("air_effect", 1),
    AIR_CAPSTONE("air_capstone", 1),
    NECROMANCY_QUICK_CASTING("necromancy_quick_casting", 1),
    NECROMANCY_EFFECT("necromancy_effect", 1),
    NECROMANCY_CAPSTONE("necromancy_capstone", 1),
    // Beastmastery (PROF-6): bonuses for the player's tames, and the companion
    ANIMAL_FRIEND("animal_friend", 1),
    PACK_LEADER("pack_leader", 1),
    THICK_HIDE("thick_hide", 1),
    COMPANION_BOND("companion_bond", 3),
    MENDER("mender", 1),
    RALLY("rally", 1),
    ALPHA("alpha", 1),
    PET_VITALITY("pet_vitality", 5),
    PET_FEROCITY("pet_ferocity", 5);

    public final String key;
    public final int ranks;

    Perk(String key, int ranks) {
        this.key = key;
        this.ranks = ranks;
    }

    /** The player tag a rank's node gives (ranks count from 1). */
    public String tag(int rank) {
        return "ddprofessions.perk." + key + (ranks > 1 ? "_" + rank : "");
    }

    /** The highest rank of this perk the player has, or 0. */
    public int rank(Player player) {
        for (int rank = ranks; rank >= 1; rank--) {
            if (player.getTags().contains(tag(rank))) {
                return rank;
            }
        }
        return 0;
    }

    public boolean has(Player player) {
        return rank(player) > 0;
    }
}
