package io.github.ranger3462.ddprofessions.beast;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.forging.StarredItem;
import io.github.ranger3462.ddprofessions.registry.ProfessionsRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Beastmastery's items (PROF-6): barding for wolves and horses, collars for cats and parrots, and the Bonding Charm. */
public final class BeastItems {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, ProfessionsAndSkills.MODID);

    /**
     * One material a kind of barding, for its look: a horse's is textures/entity/horse/armor/horse_armor_&lt;kind&gt;_barding,
     * a wolf's textures/entity/wolf/wolf_armor (one for all three). The stats all come from the ★, so the materials give none.
     */
    public static final Map<BardingItem.Kind, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIAL = new EnumMap<>(BardingItem.Kind.class);
    /** Barding by kind, for wolves and for the horse family. */
    public static final Map<BardingItem.Kind, DeferredItem<BardingItem>> WOLF_BARDING = new EnumMap<>(BardingItem.Kind.class);
    public static final Map<BardingItem.Kind, DeferredItem<BardingItem>> HORSE_BARDING = new EnumMap<>(BardingItem.Kind.class);

    static {
        for (BardingItem.Kind kind : BardingItem.Kind.values()) {
            DeferredHolder<ArmorMaterial, ArmorMaterial> material = MATERIALS.register(kind.key() + "_barding", () -> new ArmorMaterial(
                    new EnumMap<>(Map.of(ArmorItem.Type.BODY, 0)), 10,
                    kind == BardingItem.Kind.LEATHER ? SoundEvents.ARMOR_EQUIP_LEATHER : SoundEvents.ARMOR_EQUIP_CHAIN,
                    () -> Ingredient.EMPTY, List.of(), 0.0f, 0.0f));
            MATERIAL.put(kind, material);
            WOLF_BARDING.put(kind, ProfessionsRegistries.ITEMS.registerItem("wolf_" + kind.key() + "_barding",
                    properties -> new BardingItem(material, kind, AnimalArmorItem.BodyType.CANINE, properties), new Item.Properties().stacksTo(1)));
            HORSE_BARDING.put(kind, ProfessionsRegistries.ITEMS.registerItem("horse_" + kind.key() + "_barding",
                    properties -> new BardingItem(material, kind, AnimalArmorItem.BodyType.EQUESTRIAN, properties), new Item.Properties().stacksTo(1)));
        }
    }

    /** A starred collar for a cat or parrot ({@link Collars}). */
    public static final DeferredItem<StarredItem> COLLAR = ProfessionsRegistries.ITEMS.registerItem("collar",
            StarredItem.WithTooltip.of("tooltip.ddprofessions.collar"), new Item.Properties().stacksTo(1));
    /** Bonds a companion, and calls it back ({@link Companions}). */
    public static final DeferredItem<BondingCharmItem> BONDING_CHARM = ProfessionsRegistries.ITEMS.registerItem("bonding_charm", BondingCharmItem::new,
            new Item.Properties().stacksTo(1));

    private BeastItems() {}

    /** Loads the class, registering the items, before the registers are attached. */
    public static void init() {}

    public static void register(IEventBus modEventBus) {
        MATERIALS.register(modEventBus);
        Beastmastery.register(modEventBus);
        Collars.register(modEventBus);
        modEventBus.addListener(TameFighters::addAttackDamage);
    }

    /** For the creative tab. */
    public static List<Item> creativeItems() {
        List<Item> items = new ArrayList<>();
        WOLF_BARDING.values().forEach(item -> items.add(item.get()));
        HORSE_BARDING.values().forEach(item -> items.add(item.get()));
        items.add(COLLAR.get());
        items.add(BONDING_CHARM.get());
        return items;
    }
}
