package io.github.ranger3462.ddprofessions.forging;

import java.util.List;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * Goldsmithing's jewelry (PROF-8), worn in a Curios slot: a ring (more health) or an amulet (more armor), better by its
 * ★. Without Curios it's just an ornament. The bonus is worked out here; {@link GoldCurios} hands it to Curios. A goldsmith
 * with Gem Setter can set one {@link Gem} in a piece, for a second bonus ({@link GemSetting}).
 */
public class JewelryItem extends StarredItem {
    public enum Kind {
        /** +1 max health a ★. */
        RING("ring", Attributes.MAX_HEALTH, 1.0),
        /** +0.5 armor a ★. */
        AMULET("necklace", Attributes.ARMOR, 0.5);

        /** The Curios slot it's worn in. */
        public final String slot;
        public final Holder<Attribute> attribute;
        public final double perStar;

        Kind(String slot, Holder<Attribute> attribute, double perStar) {
            this.slot = slot;
            this.attribute = attribute;
            this.perStar = perStar;
        }
    }

    /** A gem set in jewelry (Gem Setter), and what it adds on top of the piece's own bonus. */
    public enum Gem implements StringRepresentable {
        /** +1 armor toughness. */
        DIAMOND("diamond", Items.DIAMOND, Attributes.ARMOR_TOUGHNESS, 1.0, AttributeModifier.Operation.ADD_VALUE),
        /** +1 luck. */
        EMERALD("emerald", Items.EMERALD, Attributes.LUCK, 1.0, AttributeModifier.Operation.ADD_VALUE),
        /** +5% movement speed. */
        AMETHYST("amethyst", Items.AMETHYST_SHARD, Attributes.MOVEMENT_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        public static final Codec<Gem> CODEC = StringRepresentable.fromEnum(Gem::values);
        public static final StreamCodec<ByteBuf, Gem> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Gem::ordinal);

        public final String id;
        public final Item item;
        public final Holder<Attribute> attribute;
        public final double amount;
        public final AttributeModifier.Operation operation;

        Gem(String id, Item item, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
            this.id = id;
            this.item = item;
            this.attribute = attribute;
            this.amount = amount;
            this.operation = operation;
        }

        /** The gem this item is, if it's one that can be set. */
        public static java.util.Optional<Gem> of(ItemStack stack) {
            return java.util.Arrays.stream(values()).filter(gem -> stack.is(gem.item)).findFirst();
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public final Kind kind;

    public JewelryItem(Properties properties, Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    /** What this piece adds, for its ★. */
    public double bonus(ItemStack stack) {
        return kind.perStar * Forging.stars(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.ddprofessions.jewelry." + kind.name().toLowerCase(), String.format("%.1f", bonus(stack)))
                .withStyle(ChatFormatting.BLUE));
        Gem gem = stack.get(Forging.GEM.get());
        if (gem != null) {
            tooltip.add(Component.translatable("tooltip.ddprofessions.gem." + gem.id).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
