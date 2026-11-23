package io.github.ranger3462.ddprofessions.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.forging.CraftPerks;
import io.github.ranger3462.ddprofessions.forging.Crafts;
import io.github.ranger3462.ddprofessions.forging.CrucibleBlockEntity;
import io.github.ranger3462.ddprofessions.forging.ForgeActions;
import io.github.ranger3462.ddprofessions.forging.ForgedGear;
import io.github.ranger3462.ddprofessions.forging.Forging;
import io.github.ranger3462.ddprofessions.forging.JewelryItem;
import io.github.ranger3462.ddprofessions.forging.Profession;
import io.github.ranger3462.ddprofessions.forging.SoakingVatBlock;
import io.github.ranger3462.ddprofessions.forging.SoakingVatBlockEntity;
import io.github.ranger3462.ddprofessions.forging.TimingBar;
import io.github.ranger3462.ddprofessions.registry.ProfessionsRegistries;
import io.github.ranger3462.ddprofessions.skill.CraftSkill;
import io.github.ranger3462.ddprofessions.skill.CraftingSkills;
import io.github.ranger3462.ddprofessions.skill.Perk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the crafting trees' perks: each does what its node says (the five Studies columns and the 14 others). */
@GameTestHolder(ProfessionsAndSkills.MODID)
@PrefixGameTestTemplate(false)
public class CraftPerkTests {
    private static final String ANY_TEMPLATE = "test/empty";
    /** Enough tries that a 25% bonus can't plausibly miss every time (0.75^40 is about 1 in 100,000). */
    private static final int TRIES = 40;

    @GameTest(template = ANY_TEMPLATE)
    public static void studiesGiveMoreXp(GameTestHelper helper) {
        ServerPlayer player = player(helper, "scholar");
        for (CraftSkill skill : CraftSkill.values()) {
            CraftingSkills.setXp(player, skill, 0);
            CraftingSkills.addXp(player, skill, 100);
            helper.assertTrue(CraftingSkills.xp(player, skill) == 100, skill + " XP is plain without Studies");
            Perk studies = CraftingSkills.studiesFor(skill);
            player.addTag(studies.tag(5));
            CraftingSkills.setXp(player, skill, 0);
            CraftingSkills.addXp(player, skill, 100);
            helper.assertTrue(CraftingSkills.xp(player, skill) == 125, skill + " XP is 25% more with Studies V, got " + CraftingSkills.xp(player, skill));
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void keenEdgeAndPaddingActWhenCrafted(GameTestHelper helper) {
        ServerPlayer player = player(helper, "smith");
        ItemStack blade = starred(Forging.BLADE.get(), 3);
        crafted(player, blade);
        helper.assertTrue(CraftingSkills.xp(player, CraftSkill.WEAPONSMITHING) == 6, "a ★3 blade gives 6 XP");
        player.addTag(Perk.KEEN_EDGE.tag(1));
        CraftingSkills.setXp(player, CraftSkill.WEAPONSMITHING, 0);
        crafted(player, blade);
        helper.assertTrue(CraftingSkills.xp(player, CraftSkill.WEAPONSMITHING) == 9, "Keen Edge makes it 9, got " + CraftingSkills.xp(player, CraftSkill.WEAPONSMITHING));

        crafted(player, starred(Forging.GAMBESON_BASE.get(), 4));
        helper.assertTrue(find(player, Crafts.CLOTH_PANEL.get()).isEmpty(), "a gambeson base hands nothing back without Padding");
        player.addTag(Perk.PADDING.tag(1));
        crafted(player, starred(Forging.GAMBESON_BASE.get(), 4));
        ItemStack panel = find(player, Crafts.CLOTH_PANEL.get());
        helper.assertTrue(panel.getCount() == 1 && Forging.stars(panel) == 4, "with Padding it hands back a ★4 cloth panel, got " + panel);
        helper.succeed();
    }

    /** Masterwork: a fixed roll per player, one ★ more up to the skill's cap, used up only by a craft. */
    @GameTest(template = ANY_TEMPLATE)
    public static void masterworkLiftsAPieceOneStar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "master");
        CraftingSkills.setLevel(player, CraftSkill.GOLDSMITHING, 30);
        int lucky = 0;
        int hits = 0;
        for (int i = 0; i < 1000; i++) {
            player.setData(CraftingSkills.MASTERWORK_ROLLS, i);
            if (CraftPerks.masterworkRoll(player)) {
                hits++;
                lucky = i;
            }
        }
        helper.assertTrue(hits > 50 && hits < 150, "about one roll in ten is a Masterwork, got " + hits + " in 1000");
        player.setData(CraftingSkills.MASTERWORK_ROLLS, lucky);

        ItemStack f = starred(Crafts.GOLD_FITTINGS.get(), 3);
        ItemStack air = ItemStack.EMPTY;
        CraftingInput ring = CraftingInput.of(3, 3, List.of(air, f, air, f.copy(), air, f.copy(), air, f.copy(), air));
        ItemStack plain = craft(level, player, ring);
        helper.assertTrue(Forging.stars(plain) == 3 && !plain.has(Forging.MASTERWORK.get()), "no Masterwork without the perk, got " + plain);

        player.addTag(Perk.MASTERWORK_JEWELRY.tag(1));
        ItemStack lifted = craft(level, player, ring);
        helper.assertTrue(Forging.stars(lifted) == 4 && Boolean.TRUE.equals(lifted.get(Forging.MASTERWORK.get())), "a lucky roll makes it ★4, got " + lifted);
        helper.assertTrue(Forging.stars(craft(level, player, ring)) == 4, "refilling the grid doesn't reroll");
        crafted(player, lifted);
        helper.assertTrue(player.getData(CraftingSkills.MASTERWORK_ROLLS) == lucky + 1, "crafting it uses the roll up");

        player.setData(CraftingSkills.MASTERWORK_ROLLS, lucky);
        CraftingSkills.setLevel(player, CraftSkill.GOLDSMITHING, 6); // ★3 at most
        helper.assertTrue(Forging.stars(craft(level, player, ring)) == 3, "but never past what the goldsmith's skill allows");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void balancedBladesSwingFaster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "balancer");
        CraftingSkills.setLevel(player, CraftSkill.WEAPONSMITHING, 30);
        ItemStack blade = starred(Forging.BLADE.get(), 3);
        CraftingInput sword = CraftingInput.of(1, 3, List.of(blade, blade.copy(), new ItemStack(Items.STICK)));
        double plain = attackSpeed(craft(level, player, sword));
        player.addTag(Perk.BALANCED_BLADE.tag(1));
        ItemStack balanced = craft(level, player, sword);
        helper.assertTrue(Boolean.TRUE.equals(balanced.get(Forging.BALANCED.get())), "Balanced Blade marks the sword");
        helper.assertTrue(Math.abs(attackSpeed(balanced) - plain - ForgedGear.BALANCED_SPEED) < 1e-6,
                "and it swings 0.1 faster: " + plain + " then " + attackSpeed(balanced));
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void gemSetterSetsAGem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "setter");
        ItemStack ring = starred(Crafts.GOLD_RING.get(), 3);
        CraftingInput input = CraftingInput.of(2, 1, List.of(ring, new ItemStack(Items.DIAMOND)));
        helper.assertTrue(assemble(level, player, input).isEmpty(), "no gem setting without the perk");
        player.addTag(Perk.GEM_SETTER.tag(1));
        ItemStack set = assemble(level, player, input);
        helper.assertTrue(set.is(Crafts.GOLD_RING.get()) && set.get(Forging.GEM.get()) == JewelryItem.Gem.DIAMOND && Forging.stars(set) == 3,
                "Gem Setter sets the diamond, keeping the ★, got " + set);
        helper.assertTrue(assemble(level, player, CraftingInput.of(2, 1, List.of(set, new ItemStack(Items.EMERALD)))).isEmpty(), "one gem a piece");
        helper.succeed();
    }

    /** Quick Fingers finishes the cloth on a perfect first stitch; Fine Weave sometimes gives a third panel. */
    @GameTest(template = ANY_TEMPLATE)
    public static void quickFingersAndFineWeave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = spot(helper);
        ServerPlayer player = player(helper, "nimble");
        player.getInventory().selected = 8; // Work from the last hotbar slot, so what's made lands elsewhere.
        CraftingSkills.setLevel(player, CraftSkill.WEAVING, 30);
        player.addTag(Perk.QUICK_FINGERS.tag(1));
        player.addTag(Perk.FINE_WEAVE.tag(1));
        try {
            set(level, ledger, ProfessionsRegistries.GUILD_LEDGER.get());
            set(level, ledger.offset(2, 0, 0), Crafts.SPINNING_WHEEL.get());
            BlockPos loom = ledger.offset(-2, 0, 0);
            set(level, loom, Blocks.LOOM);
            set(level, ledger.offset(0, 0, 2), Blocks.CAULDRON);
            int stitches = 0;
            for (int i = 0; i < TRIES; i++) {
                player.setItemInHand(InteractionHand.MAIN_HAND, starred(Crafts.SIZED_YARN.get(), 3).copyWithCount(3));
                stitches += landAll(player, level, loom, TimingBar.Kind.WEAVE, TimingBar.Hit.GOLD);
            }
            helper.assertTrue(stitches == TRIES, "each cloth takes one perfect stitch, got " + stitches + " stitches for " + TRIES);
            int panels = count(player, Crafts.CLOTH_PANEL.get());
            helper.assertTrue(panels > 2 * TRIES && panels <= 3 * TRIES, "Fine Weave adds some third panels, got " + panels);
        } finally {
            clear(level, ledger);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void fineSplitAndSeasoner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = spot(helper);
        ServerPlayer player = player(helper, "turner");
        player.getInventory().selected = 8; // Work from the last hotbar slot, so what's made lands elsewhere.
        CraftingSkills.setLevel(player, CraftSkill.WOODWORKING, 30);
        try {
            set(level, ledger, ProfessionsRegistries.GUILD_LEDGER.get());
            set(level, ledger.offset(2, 0, 0), Blocks.SMOKER);
            BlockPos vatPos = ledger.offset(-2, 0, 0);
            level.setBlock(vatPos, Crafts.FINISHING_VAT.get().defaultBlockState().setValue(SoakingVatBlock.LEVEL, 3), Block.UPDATE_CLIENTS);
            BlockPos lathe = ledger.offset(0, 0, -2);
            set(level, lathe, Crafts.WOOD_LATHE.get());
            SoakingVatBlockEntity vat = (SoakingVatBlockEntity) level.getBlockEntity(vatPos);
            vat.setLiquorStars(3);

            long plain = cure(level, vatPos, vat, player);
            player.addTag(Perk.SEASONER.tag(1));
            long seasoned = cure(level, vatPos, vat, player);
            helper.assertTrue(seasoned == Math.round(plain * CraftPerks.FASTER), "Seasoner cures billets 25% faster: " + plain + " then " + seasoned);

            player.addTag(Perk.FINE_SPLIT.tag(1));
            for (int i = 0; i < TRIES; i++) {
                player.setItemInHand(InteractionHand.MAIN_HAND, starred(Crafts.CURED_BILLET.get(), 3));
                landAll(player, level, lathe, TimingBar.Kind.TURN, TimingBar.Hit.GOLD);
            }
            int staves = count(player, Crafts.STAVE.get());
            helper.assertTrue(staves > 2 * TRIES && staves <= 3 * TRIES, "Fine Split adds some third staves, got " + staves);
        } finally {
            clear(level, ledger);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void suppleHidesGiveMoreLeather(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = spot(helper);
        ServerPlayer player = player(helper, "currier");
        try {
            set(level, pos, Crafts.TANNING_BARREL.get());
            SoakingVatBlockEntity vat = (SoakingVatBlockEntity) level.getBlockEntity(pos);
            vat.setLiquorStars(3);
            vat.start(List.of(new ItemStack(Crafts.SCRAPED_HIDE.get(), TRIES)), 0);
            SoakingVatBlock.collect(level, pos, level.getBlockState(pos), vat, player);
            helper.assertTrue(count(player, Forging.REFINED_LEATHER.get()) == TRIES, "one leather a hide without Supple Hides");
            player.getInventory().clearContent();
            player.addTag(Perk.SUPPLE_HIDES.tag(1));
            vat.start(List.of(new ItemStack(Crafts.SCRAPED_HIDE.get(), TRIES)), 0);
            SoakingVatBlock.collect(level, pos, level.getBlockState(pos), vat, player);
            int leather = count(player, Forging.REFINED_LEATHER.get());
            helper.assertTrue(leather > TRIES && leather <= 2 * TRIES, "Supple Hides gives some second leathers, got " + leather);
        } finally {
            clear(level, pos);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void assayerRefinesFaster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos ledger = spot(helper);
        ServerPlayer player = player(helper, "assayer");
        try {
            set(level, ledger, ProfessionsRegistries.GUILD_LEDGER.get());
            set(level, ledger.offset(2, 0, 0), Blocks.FURNACE);
            BlockPos crucible = ledger.offset(-2, 0, 0);
            set(level, crucible, Crafts.CRUCIBLE.get());
            set(level, ledger.offset(0, 0, 2), Crafts.JEWELERS_BENCH.get());
            CrucibleBlockEntity refiner = (CrucibleBlockEntity) level.getBlockEntity(crucible);
            int plain = refineOne(level, crucible, refiner, player);
            player.addTag(Perk.ASSAYER.tag(1));
            int assayed = refineOne(level, crucible, refiner, player);
            helper.assertTrue(plain == CrucibleBlockEntity.ticksFor(3, false) && assayed == Math.round(plain * CraftPerks.FASTER),
                    "a crucible tended by an Assayer refines 25% faster: " + plain + " then " + assayed + " ticks");
        } finally {
            clear(level, ledger);
        }
        helper.succeed();
    }

    // --- Helpers ---

    /** Opens the crucible as the player (as tending it does), then counts the ticks one ingot takes. */
    private static int refineOne(ServerLevel level, BlockPos pos, CrucibleBlockEntity crucible, ServerPlayer player) {
        crucible.clearContent();
        crucible.createMenu(0, player.getInventory(), player);
        crucible.setItem(CrucibleBlockEntity.INPUT, new ItemStack(Items.GOLD_INGOT));
        crucible.setItem(CrucibleBlockEntity.SALT, new ItemStack(Crafts.BONE_ASH.get()));
        int ticks = 0;
        while (crucible.getItem(CrucibleBlockEntity.OUTPUT).isEmpty() && ticks < 10_000) {
            CrucibleBlockEntity.serverTick(level, pos, level.getBlockState(pos), crucible);
            ticks++;
        }
        return ticks;
    }

    /** Puts a billet in the finishing vat and says how long it will take, then empties the vat again. */
    private static long cure(ServerLevel level, BlockPos pos, SoakingVatBlockEntity vat, ServerPlayer player) {
        ItemStack billet = new ItemStack(Crafts.SEASONED_BILLET.get());
        SoakingVatBlock.insert(level, pos, level.getBlockState(pos), vat, player, billet, SoakingVatBlock.soakFor(billet, Profession.WOODWORKING).orElseThrow());
        long time = vat.readyAt() - level.getGameTime();
        vat.takeAll();
        return time;
    }

    private static double attackSpeed(ItemStack weapon) {
        return weapon.getItem().getDefaultAttributeModifiers(weapon).modifiers().stream().filter(entry -> entry.attribute().is(Attributes.ATTACK_SPEED))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    /** What the player would get crafting this: the matching recipe's result, assembled as them. */
    private static ItemStack craft(ServerLevel level, ServerPlayer player, CraftingInput input) {
        CommonHooks.setCraftingPlayer(player);
        try {
            return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow().value().assemble(input, level.registryAccess());
        } finally {
            CommonHooks.setCraftingPlayer(null);
        }
    }

    /** Like {@link #craft}, but empty when no recipe matches or it gives nothing. */
    private static ItemStack assemble(ServerLevel level, ServerPlayer player, CraftingInput input) {
        CommonHooks.setCraftingPlayer(player);
        try {
            return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).map(recipe -> recipe.value().assemble(input, level.registryAccess()))
                    .orElse(ItemStack.EMPTY);
        } finally {
            CommonHooks.setCraftingPlayer(null);
        }
    }

    /** As if the player took this out of a crafting grid. */
    private static void crafted(ServerPlayer player, ItemStack stack) {
        EventHooks.firePlayerCraftingEvent(player, stack, new SimpleContainer(9));
    }

    /** Plays a minigame through with every strike landing as {@code hit}; returns how many strikes it took. */
    private static int landAll(ServerPlayer player, ServerLevel level, BlockPos pos, TimingBar.Kind kind, TimingBar.Hit hit) {
        ForgeActions.end(player);
        ForgeActions.Session session = ForgeActions.begin(player, level, pos, level.getBlockState(pos), player.getMainHandItem(), kind, level.getGameTime())
                .orElseThrow();
        long now = level.getGameTime();
        int strikes = 0;
        while (ForgeActions.session(player).isPresent()) {
            now += 3;
            strikes++;
            ForgeActions.land(player, level, session, player.getMainHandItem(), now, hit);
        }
        return strikes;
    }

    private static ItemStack starred(Item item, int stars) {
        ItemStack stack = new ItemStack(item);
        stack.set(Forging.STARS.get(), stars);
        return stack;
    }

    private static ItemStack find(ServerPlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).findFirst().orElse(ItemStack.EMPTY);
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    private static BlockPos spot(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(1, 2, 1)).above(110);
    }

    private static void set(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void clear(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 1, 4))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(6)).forEach(ItemEntity::discard);
    }
}
