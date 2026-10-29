package io.github.ranger3462.ddprofessions;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.ranger3462.ddprofessions.forging.GoldCurios;
import io.github.ranger3462.ddprofessions.integration.apotheosis.ApotheosisForging;
import io.github.ranger3462.ddprofessions.integration.skills.PufferfishSkills;
import io.github.ranger3462.ddprofessions.integration.spellengine.ForgedCasting;
import io.github.ranger3462.ddprofessions.integration.spellengine.SpellEngineMagic;
import io.github.ranger3462.ddprofessions.registry.ProfessionsRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Professions and Skills: skill trees that level from what players do, forging and the other crafting professions,
 * and D&D-style races, classes and ability scores. Each other mod it works with is optional.
 */
@Mod(ProfessionsAndSkills.MODID)
public class ProfessionsAndSkills {
    public static final String MODID = "ddprofessions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ProfessionsAndSkills(IEventBus modEventBus, ModContainer modContainer) {
        ProfessionsRegistries.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, ProfessionsConfig.SPEC);
        if (ModList.get().isLoaded("puffish_skills")) {
            PufferfishSkills.register();
        }
        if (ModList.get().isLoaded("spell_engine")) {
            SpellEngineMagic.register();
            ForgedCasting.register(modEventBus);
        }
        if (ModList.get().isLoaded("curios")) {
            // Items exist only after registration, so the jewelry is handed to Curios in common setup.
            modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(GoldCurios::register));
        }
        if (ModList.get().isLoaded("apotheosis")) {
            ApotheosisForging.register();
        }
    }

    /** Builds a resource location in this mod's namespace, e.g. {@code ddprofessions:forged_sword}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
