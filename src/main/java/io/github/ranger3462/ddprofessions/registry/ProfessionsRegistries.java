package io.github.ranger3462.ddprofessions.registry;

import java.util.List;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.character.Characters;
import io.github.ranger3462.ddprofessions.character.ElixirOfRebirthItem;
import io.github.ranger3462.ddprofessions.character.TomeOfCallingItem;
import io.github.ranger3462.ddprofessions.character.TomeOfInsightItem;
import io.github.ranger3462.ddprofessions.forging.Crafts;
import io.github.ranger3462.ddprofessions.forging.Forging;
import io.github.ranger3462.ddprofessions.guild.GuildLedgerBlock;
import io.github.ranger3462.ddprofessions.skill.CraftingSkills;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Everything Professions and Skills adds to the game's registries. */
public final class ProfessionsRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProfessionsAndSkills.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProfessionsAndSkills.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE,
            ProfessionsAndSkills.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProfessionsAndSkills.MODID);

    /** Marks a guild workshop for every crafting profession (DD-88). */
    public static final DeferredBlock<GuildLedgerBlock> GUILD_LEDGER = BLOCKS.registerBlock("guild_ledger", GuildLedgerBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> GUILD_LEDGER_ITEM = ITEMS.registerSimpleBlockItem(GUILD_LEDGER);

    /** A Tome of Calling: read it to choose (or change) your class (DD-83). */
    public static final DeferredItem<TomeOfCallingItem> TOME_OF_CALLING = ITEMS.registerItem("tome_of_calling", TomeOfCallingItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    /** An Elixir of Rebirth: drink it to choose your race again (DD-82). */
    public static final DeferredItem<ElixirOfRebirthItem> ELIXIR_OF_REBIRTH = ITEMS.registerItem("elixir_of_rebirth", ElixirOfRebirthItem::new,
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    /** A Tome of Insight: read it for a bonus ability point (DD-80). */
    public static final DeferredItem<TomeOfInsightItem> TOME_OF_INSIGHT = ITEMS.registerItem("tome_of_insight", TomeOfInsightItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));

    /** The guild workshop's stations, the crafting materials and parts, the finished gear, and the character items. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("professions_and_skills", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ddprofessions"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> GUILD_LEDGER_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                for (var item : List.of(GUILD_LEDGER_ITEM, Forging.BELLOWS_ITEM, Forging.MASTER_ANVIL_ITEM, Forging.GRINDING_WHEEL_ITEM,
                        Forging.QUENCHING_OIL, Forging.COKE_INFUSED_IRON_INGOT, Forging.HEATED_INGOT, Forging.STRUCK_INGOT, Forging.QUENCHED_INGOT,
                        Forging.UNSTABLE_INGOT, Forging.TEMPERED_IRON_INGOT, Forging.SAND_FLUX, Forging.BORAX, Forging.BORAX_DEPOSIT_ITEM,
                        Forging.BORAX_FLUX, Forging.RUNIC_FLUX, Forging.RUNEFORGED_FLUX, Forging.ARMOR_PLATE, Forging.RIVETS, Forging.CHAIN_LINKS,
                        Forging.BLADE, Forging.AXE_HEAD, Forging.TANNING_RACK_ITEM, Forging.LEATHER_SCRAPS, Forging.REFINED_LEATHER,
                        Forging.HARDENED_LEATHER, Forging.LEATHER_BASE, Forging.GAMBESON_BASE)) {
                    output.accept(item.get());
                }
                Forging.gear().forEach(output::accept);
                Crafts.creativeItems().forEach(item -> output.accept(item.get()));
                output.accept(TOME_OF_INSIGHT.get());
                output.accept(ELIXIR_OF_REBIRTH.get());
                output.accept(TOME_OF_CALLING.get());
            })
            .build());

    private ProfessionsRegistries() {}

    public static void register(IEventBus modEventBus) {
        Forging.init(); // Registers the forging blocks and items before the registers are attached.
        Forging.register(modEventBus);
        Crafts.init();
        Crafts.register(modEventBus);
        CraftingSkills.register(modEventBus);
        Characters.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        TABS.register(modEventBus);
    }
}
