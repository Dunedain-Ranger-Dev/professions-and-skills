package io.github.ranger3462.ddprofessions.forging;

import java.util.Optional;

import io.github.ranger3462.ddprofessions.skill.Perk;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * Gem Setter (Goldsmithing): a ring or amulet and one gem ({@link JewelryItem.Gem}) at a crafting table set the gem in
 * the piece. Only for a goldsmith with the perk, and one gem a piece. The piece keeps its ★ and everything else.
 */
public class GemSetting extends CustomRecipe {
    public GemSetting(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return set(input).isPresent();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Player crafter = CommonHooks.getCraftingPlayer();
        if (crafter == null || !Perk.GEM_SETTER.has(crafter)) {
            return ItemStack.EMPTY;
        }
        return set(input).orElse(ItemStack.EMPTY);
    }

    /** The jewelry with the gem set, if the grid holds exactly one unset piece and one gem. */
    static Optional<ItemStack> set(CraftingInput input) {
        ItemStack jewelry = ItemStack.EMPTY;
        JewelryItem.Gem gem = null;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            Optional<JewelryItem.Gem> asGem = JewelryItem.Gem.of(stack);
            if (stack.getItem() instanceof JewelryItem && !stack.has(Forging.GEM.get()) && jewelry.isEmpty()) {
                jewelry = stack;
            } else if (asGem.isPresent() && gem == null) {
                gem = asGem.get();
            } else {
                return Optional.empty();
            }
        }
        if (jewelry.isEmpty() || gem == null) {
            return Optional.empty();
        }
        ItemStack set = jewelry.copyWithCount(1);
        set.set(Forging.GEM.get(), gem);
        return Optional.of(set);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Forging.GEM_SETTING.get();
    }
}
