package io.github.ranger3462.ddprofessions.skill;

import java.util.Arrays;
import java.util.Optional;

import net.minecraft.network.chat.Component;

/** The crafting skills. Each player has XP and a level in each one (see {@link CraftingSkills}). */
public enum CraftSkill {
    // Forging split in two (DD-89): the forge work trains both; armor and weapons each train their own.
    WEAPONSMITHING("weaponsmithing"),
    ARMORSMITHING("armorsmithing"),
    LEATHERWORKING("leatherworking"),
    WEAVING("weaving"),
    WOODWORKING("woodworking"),
    GOLDSMITHING("goldsmithing");

    public final String id;

    CraftSkill(String id) {
        this.id = id;
    }

    public Component displayName() {
        return Component.translatable("skill.ddprofessions." + id);
    }

    public static Optional<CraftSkill> byId(String id) {
        return Arrays.stream(values()).filter(skill -> skill.id.equals(id)).findFirst();
    }
}
