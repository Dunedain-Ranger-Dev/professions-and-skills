package io.github.ranger3462.ddprofessions.beast;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.forging.Forging;
import io.github.ranger3462.ddprofessions.forging.StarredItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Barding (PROF-6): starred armor for wolves and the horse family, made at the guild workshops from the same parts as
 * player armor. Its ★ comes from its parts and sets its armor; a wolf's barding takes the wolf's damage into its
 * durability (like vanilla wolf armor, which a mixin lets it stand in for), so its ★ sets that too. Horse barding never
 * wears out, like vanilla horse armor.
 */
public class BardingItem extends AnimalArmorItem {
    /** What it's made of: leather (Leatherworking), mail and plate (Forging). */
    public enum Kind {
        LEATHER(new int[] { 3, 3, 4, 4, 5, 5, 6 }, 64),
        MAIL(new int[] { 5, 6, 7, 8, 9, 10, 11 }, 96),
        PLATE(new int[] { 8, 9, 10, 12, 13, 14, 15 }, 128);

        /** Armor by ★ (vanilla's leather horse armor is 3, diamond 11). */
        final int[] armor;
        /** A wolf's barding's durability at ★1 (vanilla wolf armor is 64); each ★ adds a quarter. */
        final int wolfDurability;

        Kind(int[] armor, int wolfDurability) {
            this.armor = armor;
            this.wolfDurability = wolfDurability;
        }

        public String key() {
            return name().toLowerCase();
        }
    }

    public final Kind kind;

    public BardingItem(Holder<ArmorMaterial> material, Kind kind, BodyType bodyType, Properties properties) {
        super(material, bodyType, false, bodyType == BodyType.CANINE ? properties.durability(kind.wolfDurability) : properties);
        this.kind = kind;
    }

    private static int index(int stars) {
        return Math.clamp(stars, 1, Forging.MAX_STARS) - 1;
    }

    /** Armor for this kind of barding at this ★. */
    public int armor(int stars) {
        return kind.armor[index(stars)];
    }

    /** A wolf's barding's durability at this ★. */
    public int wolfDurability(int stars) {
        return kind.wolfDurability + kind.wolfDurability * index(stars) / 4;
    }

    /** Sets a freshly crafted piece's durability for its ★ (a wolf's; a horse's never wears). */
    public void applyStats(ItemStack stack) {
        if (getBodyType() == BodyType.CANINE) {
            stack.set(DataComponents.MAX_DAMAGE, wolfDurability(Forging.stars(stack)));
        }
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        return ItemAttributeModifiers.builder().add(Attributes.ARMOR, new AttributeModifier(ProfessionsAndSkills.id("barding"),
                armor(Forging.stars(stack)), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.BODY).build();
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return ItemAttributeModifiers.EMPTY; // Always read per stack, above.
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        return stack.has(Forging.STARS.get()) ? name.copy().append(" ").append(StarredItem.starsText(Forging.stars(stack))) : name;
    }
}
