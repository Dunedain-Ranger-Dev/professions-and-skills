package io.github.ranger3462.ddprofessions.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ProfessionsAndSkills.MODID)
@PrefixGameTestTemplate(false)
public class HeatingTests {
    private static final String ANY_TEMPLATE = "test/empty";

    /** A blast furnace heats plain iron for forging; steel (the Witcher's) takes coke-infused iron. */
    @GameTest(template = ANY_TEMPLATE)
    public static void blastFurnacesHeatIronAndMakeSteelFromCokeIron(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var iron = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.BLASTING,
                new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(Items.IRON_INGOT)), level).orElseThrow();
        helper.assertTrue(iron.value().getResultItem(level.registryAccess()).is(io.github.ranger3462.ddprofessions.forging.Forging.HEATED_INGOT.get()),
                "a blast furnace should heat an iron ingot for forging, not make " + iron.value().getResultItem(level.registryAccess()));
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("witcher_rpg", "steel_ingot")).ifPresent(steel -> {
            var coke = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.BLASTING,
                    new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(io.github.ranger3462.ddprofessions.forging.Forging.COKE_INFUSED_IRON_INGOT.get())),
                    level).orElseThrow();
            helper.assertTrue(coke.value().getResultItem(level.registryAccess()).is(steel), "coke-infused iron should blast into steel");
        });
        helper.succeed();
    }
}
