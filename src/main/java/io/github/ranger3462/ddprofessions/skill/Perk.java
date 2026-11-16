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
    // Woodcutting
    TREE_FELLER("tree_feller", 3),
    FORESTER("forester", 1),
    // Farming
    BOUNTIFUL("bountiful", 1),
    REPLANTER("replanter", 1),
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
