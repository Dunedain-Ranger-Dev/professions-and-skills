package io.github.ranger3462.ddprofessions.character;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.ranger3462.ddprofessions.ProfessionsConfig;
import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.integration.skills.PufferfishSkills;
import io.github.ranger3462.ddprofessions.skill.CraftSkill;
import io.github.ranger3462.ddprofessions.skill.CraftingSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * A player's skill levels, for the character level (PROF-14). With Pufferfish's Skills these are its categories (our
 * professions, combat styles, armor, magic schools and Beastmastery, and the RPG Series' class tree), counting from
 * 0; the crafting skills are mirrored into their categories. Without it, only the crafting skills count. The character level averages the best few skills,
 * RuneScape-style, so a crafter levels as well as a fighter.
 */
public final class SkillLevels {
    /** Skills that count toward the character level by default: all of ours, and the RPG Series' class tree for now. */
    public static final List<String> DEFAULT_SKILLS = List.of(
            // Professions (PROF-14)
            "ddprofessions:mining", "ddprofessions:woodcutting", "ddprofessions:excavation", "ddprofessions:farming", "ddprofessions:fishing",
            "ddprofessions:weaponsmithing", "ddprofessions:armorsmithing", "ddprofessions:leatherworking", "ddprofessions:weaving",
            "ddprofessions:woodworking", "ddprofessions:goldsmithing",
            // Combat styles, armor, magic schools and Beastmastery (PROF-11)
            "ddprofessions:one_handed", "ddprofessions:two_handed", "ddprofessions:polearms", "ddprofessions:daggers", "ddprofessions:unarmed",
            "ddprofessions:archery", "ddprofessions:crossbows", "ddprofessions:dual_wielding", "ddprofessions:blocking",
            "ddprofessions:light_armor", "ddprofessions:medium_armor", "ddprofessions:heavy_armor", "ddprofessions:mage_armor",
            "ddprofessions:fire_magic", "ddprofessions:frost_magic", "ddprofessions:lightning_magic", "ddprofessions:arcane_magic",
            "ddprofessions:holy_magic", "ddprofessions:earth_magic", "ddprofessions:water_magic", "ddprofessions:air_magic",
            "ddprofessions:necromancy", "ddprofessions:beastmastery",
            // Class trees (PROF-5): only the player's own class is unlocked, so only it can level.
            "ddprofessions:fighter", "ddprofessions:archer", "ddprofessions:rogue", "ddprofessions:mage");

    private SkillLevels() {}

    public static boolean pufferfish() {
        return ModList.get().isLoaded("puffish_skills");
    }

    /** The player's level in each counting skill (from 0), by skill id. Pufferfish's Skills only; empty without it. */
    public static java.util.Map<ResourceLocation, Integer> levelsById(ServerPlayer player) {
        java.util.Map<ResourceLocation, Integer> levels = new java.util.LinkedHashMap<>();
        if (pufferfish()) {
            for (String id : ProfessionsConfig.CHARACTER_LEVEL_SKILLS.get()) {
                ResourceLocation category = ResourceLocation.tryParse(id);
                if (category != null) {
                    PufferfishSkills.level(player, category).ifPresent(level -> levels.put(category, level));
                }
            }
        }
        return levels;
    }

    /** The player's level in each skill that counts, from 0. */
    public static List<Integer> levels(ServerPlayer player) {
        List<Integer> levels = new ArrayList<>();
        if (pufferfish()) {
            for (String id : ProfessionsConfig.CHARACTER_LEVEL_SKILLS.get()) {
                ResourceLocation category = ResourceLocation.tryParse(id);
                if (category != null) {
                    PufferfishSkills.level(player, category).ifPresent(levels::add);
                }
            }
        } else {
            for (CraftSkill skill : CraftSkill.values()) {
                levels.add(CraftingSkills.level(player, skill) - 1);
            }
        }
        return levels;
    }

    /**
     * The character level for these skill levels: 1 plus the average of the best {@code characterLevelTopSkills}
     * of them (missing ones count as 0), rounded down.
     */
    public static int characterLevel(List<Integer> levels, int top) {
        int count = Math.max(1, top);
        int sum = levels.stream().sorted(Comparator.reverseOrder()).limit(count).mapToInt(Integer::intValue).sum();
        return 1 + sum / count;
    }

    /** Unlocks a skill tree for a player (a class's, when they pick it). */
    public static void unlockTree(ServerPlayer player, ResourceLocation tree) {
        if (pufferfish()) {
            PufferfishSkills.unlock(player, tree);
        }
    }

    /** Locks a skill tree again, taking back the points spent in it (a class's, when they change class). */
    public static void lockTree(ServerPlayer player, ResourceLocation tree) {
        if (pufferfish()) {
            PufferfishSkills.lock(player, tree);
        }
    }

    /** Copies a crafting skill's XP into its Pufferfish category, so the tree's levels and points follow it. */
    public static void mirror(ServerPlayer player, CraftSkill skill, int xp) {
        if (pufferfish()) {
            PufferfishSkills.setTotal(player, ProfessionsAndSkills.id(skill.id), xp);
        }
    }
}
