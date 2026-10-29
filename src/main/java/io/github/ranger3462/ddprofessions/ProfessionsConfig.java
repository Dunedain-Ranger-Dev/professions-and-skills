package io.github.ranger3462.ddprofessions;

import java.util.List;

import io.github.ranger3462.ddprofessions.character.SkillLevels;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Settings in {@code config/ddprofessions-common.toml}. */
public final class ProfessionsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CHARACTER_LEVEL_SKILLS = BUILDER
            .comment("Which skills (Pufferfish's Skills categories) count toward the character level. Without Pufferfish's",
                    "Skills, only the crafting skills count.")
            .defineList("characterLevelSkills", SkillLevels.DEFAULT_SKILLS, () -> "ddprofessions:mining",
                    value -> value instanceof String text && ResourceLocation.tryParse(text) != null);

    public static final ModConfigSpec.IntValue CHARACTER_LEVEL_TOP_SKILLS = BUILDER
            .comment("The character level is 1 plus the average of a player's best this-many skill levels (RuneScape-style), so",
                    "crafters and fighters level alike. Each character level past 1 earns an ability point.")
            .defineInRange("characterLevelTopSkills", 6, 1, 20);

    static final ModConfigSpec SPEC = BUILDER.build();

    private ProfessionsConfig() {}
}
