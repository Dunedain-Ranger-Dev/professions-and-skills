package io.github.ranger3462.ddprofessions.integration.irons;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.beast.Beastmastery;
import io.github.ranger3462.ddprofessions.beast.Companions;
import io.github.ranger3462.ddprofessions.character.SkillLevels;
import io.github.ranger3462.ddprofessions.skill.Perk;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Call Companion with Iron's Spells (PROF-28). Only loaded when Iron's Spells is installed.
 * <ul>
 *   <li>The spell ({@link CallCompanionSpell}), in the Nature school.</li>
 *   <li>Its scroll's recipe (paper, a lead, a bone and an ink sac) only makes a scroll for a player who has Companion
 *   Bond I, so taking the node is what unlocks it.</li>
 *   <li>Taking Companion Bond I gives one scroll free, once per player.</li>
 * </ul>
 */
public final class IronsCompanion {
    public static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, ProfessionsAndSkills.MODID);
    public static final DeferredHolder<AbstractSpell, CallCompanionSpell> CALL_COMPANION = SPELLS.register(CallCompanionSpell.ID.getPath(),
            CallCompanionSpell::new);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, ProfessionsAndSkills.MODID);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<CompanionScrollRecipe>> COMPANION_SCROLL =
            SERIALIZERS.register("companion_scroll", () -> new SimpleCraftingRecipeSerializer<>(CompanionScrollRecipe::new));
    /** How often a player is checked for a newly taken Companion Bond I, in ticks. */
    private static final int CHECK_INTERVAL = 20;

    private IronsCompanion() {}

    public static void register(IEventBus modEventBus) {
        SPELLS.register(modEventBus);
        SERIALIZERS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post event) -> {
            if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_INTERVAL == 0) {
                giveFreeScroll(player);
            }
        });
    }

    /** A Call Companion scroll. */
    public static ItemStack scroll() {
        ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(CALL_COMPANION.get(), 1, scroll);
        return scroll;
    }

    /** Whether a player can craft the scroll: they have Companion Bond I. */
    public static boolean canCraftScroll(Player player) {
        return player instanceof ServerPlayer serverPlayer && SkillLevels.pufferfish() && Perk.COMPANION_BOND.has(serverPlayer);
    }

    /** Gives a player their free scroll, once, after they take Companion Bond I. Returns whether it did. */
    public static boolean giveFreeScroll(ServerPlayer player) {
        if (player.getData(Beastmastery.COMPANION_SCROLL_GIVEN) || !canCraftScroll(player)) {
            return false;
        }
        player.setData(Beastmastery.COMPANION_SCROLL_GIVEN, true);
        ItemStack scroll = scroll();
        if (!player.addItem(scroll)) {
            player.drop(scroll, false);
        }
        player.sendSystemMessage(Component.translatable("message.ddprofessions.companion.scroll_given").withStyle(ChatFormatting.GOLD));
        return true;
    }

    /**
     * The Call Companion scroll's recipe: paper, a lead, a bone and an ink sac, in any order. It makes nothing for a
     * player without Companion Bond I (the result is worked out on the server, which knows their tree).
     */
    public static class CompanionScrollRecipe extends CustomRecipe {
        public CompanionScrollRecipe(CraftingBookCategory category) {
            super(category);
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            if (input.ingredientCount() != 4) {
                return false;
            }
            boolean paper = false, lead = false, bone = false, ink = false;
            for (ItemStack stack : input.items()) {
                if (stack.isEmpty()) {
                    continue;
                }
                if (stack.is(Items.PAPER) && !paper) {
                    paper = true;
                } else if (stack.is(Items.LEAD) && !lead) {
                    lead = true;
                } else if (stack.is(Items.BONE) && !bone) {
                    bone = true;
                } else if (stack.is(Items.INK_SAC) && !ink) {
                    ink = true;
                } else {
                    return false;
                }
            }
            return paper && lead && bone && ink;
        }

        @Override
        public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
            Player crafter = CommonHooks.getCraftingPlayer();
            return crafter != null && canCraftScroll(crafter) ? scroll() : ItemStack.EMPTY;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return width * height >= 4;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return COMPANION_SCROLL.get();
        }
    }
}
