package io.github.ranger3462.ddprofessions.skill;

import java.util.ArrayList;
import java.util.List;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The gathering trees' perks beyond {@link GatheringPerks}' chains: faster breaking, tools that last, Excavation's finds
 * and Earthmover, Farming's Harvest Moon and growth, and Fishing's catches.
 */
@EventBusSubscriber(modid = ProfessionsAndSkills.MODID)
public final class FieldPerks {
    public static final float FASTER = 1.15f, STURDY_CHANCE = 0.15f, BARK_CHANCE = 0.10f;
    public static final float TREASURE_CHANCE = 0.01f, BURIED_RICHES = 3;
    public static final float GOLDEN_CARROT_CHANCE = 0.02f, GOLDEN_APPLE_CHANCE = 0.005f;
    public static final float DOUBLE_CATCH_CHANCE = 0.10f, TREASURE_HUNTER_CHANCE = 0.10f, MASTER_ANGLER_CHANCE = 0.02f;
    /** Quick Bite: ticks off the wait for a bite, as one level of Lure. */
    public static final int QUICK_BITE = 100;
    public static final int FERTILE_RADIUS = 4, FERTILE_TRIES = 6, DEEP_SIGHT_RADIUS = 6;
    /** Rare finds for Master Angler; the pack can add dungeon keys and the like. */
    public static final TagKey<Item> MASTER_ANGLER_FINDS = TagKey.create(Registries.ITEM, ProfessionsAndSkills.id("master_angler_finds"));
    private static final String SHAKEN_TAG = "ddprofessions.shaken";

    /** Tools that get their durability back once this tick's break is done (the Sturdy perks). */
    private record Mend(ItemStack tool, int damage) {}
    private static final List<Mend> MENDS = new ArrayList<>();
    /** Ores Deep Sight is showing: where, and until when. */
    private record Sparkle(ServerPlayer player, List<BlockPos> ores, long until) {}
    private static final List<Sparkle> SPARKLES = new ArrayList<>();
    /** True while Earthmover or Harvest Moon breaks the extra blocks, so they don't start more. */
    private static boolean sweeping;

    private FieldPerks() {}

    // --- Breaking speed ---

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        BlockState state = event.getState();
        float speed = event.getNewSpeed();
        if (Perk.STONEMASON.has(player) && (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Tags.Blocks.STONES))) {
            speed *= FASTER;
        }
        if (Perk.TIMBER.has(player) && state.is(BlockTags.LOGS)) {
            speed *= FASTER;
        }
        if (Perk.SWIFT_SHOVEL.has(player) && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            speed *= FASTER;
        }
        if (player.getMainHandItem().isEmpty() && (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)) && Abilities.active(player, Perk.BERSERK)) {
            speed *= 50; // Berserk digs dirt and sand by hand at once.
        }
        event.setNewSpeed(speed);
    }

    // --- Breaking ---

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        ItemStack tool = player.getMainHandItem();
        Perk sturdy = tool.canPerformAction(ItemAbilities.PICKAXE_DIG) ? Perk.STURDY_PICK : tool.canPerformAction(ItemAbilities.AXE_DIG) ? Perk.STURDY_AXE
                : tool.canPerformAction(ItemAbilities.SHOVEL_DIG) ? Perk.STURDY_SHOVEL : null;
        if (sturdy != null && sturdy.has(player) && tool.isDamageableItem() && player.getRandom().nextFloat() < STURDY_CHANCE) {
            MENDS.add(new Mend(tool, tool.getDamageValue()));
        }
        if (sweeping) {
            return;
        }
        if (tool.canPerformAction(ItemAbilities.SHOVEL_DIG) && state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            if (player.isShiftKeyDown()) {
                Abilities.tryStart(player, Perk.EARTHMOVER);
            }
            if (Abilities.active(player, Perk.EARTHMOVER)) {
                sweep(player, event.getPos(), face(player, event.getPos()), pos -> level.getBlockState(pos).is(BlockTags.MINEABLE_WITH_SHOVEL));
            }
        }
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            if (player.isShiftKeyDown()) {
                Abilities.tryStart(player, Perk.HARVEST_MOON);
            }
            if (Abilities.active(player, Perk.HARVEST_MOON)) {
                int radius = Perk.HARVEST_MOON.rank(player);
                List<BlockPos> field = new ArrayList<>();
                BlockPos.betweenClosed(event.getPos().offset(-radius, 0, -radius), event.getPos().offset(radius, 0, radius)).forEach(pos -> field.add(pos.immutable()));
                sweep(player, event.getPos(), field, pos -> level.getBlockState(pos).getBlock() instanceof CropBlock other && other.isMaxAge(level.getBlockState(pos)));
            }
        }
    }

    /** The 3x3 face around a dug block, across the way the player is looking. */
    static List<BlockPos> face(Player player, BlockPos center) {
        Direction.Axis facing = Direction.orderedByNearest(player)[0].getAxis();
        List<BlockPos> face = new ArrayList<>();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                face.add(switch (facing) {
                    case X -> center.offset(0, a, b);
                    case Y -> center.offset(a, 0, b);
                    case Z -> center.offset(a, b, 0);
                });
            }
        }
        return face;
    }

    /**
     * Breaks these blocks as the player, where they pass the check, stopping before the tool would break. Not the
     * origin: that's the block already being broken, and breaking it again here would drop it twice.
     */
    private static void sweep(ServerPlayer player, BlockPos origin, List<BlockPos> blocks, java.util.function.Predicate<BlockPos> breaks) {
        sweeping = true;
        try {
            for (BlockPos pos : blocks) {
                ItemStack tool = player.getMainHandItem();
                if (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1) {
                    break;
                }
                if (!pos.equals(origin) && breaks.test(pos) && player.level().getBlockState(pos).getDestroySpeed(player.level(), pos) >= 0) {
                    player.gameMode.destroyBlock(pos);
                }
            }
        } finally {
            sweeping = false;
        }
    }

    /** Deep Sight (Mining): when Vein Miner takes a vein, ores nearby sparkle for a few seconds. */
    static void deepSight(ServerPlayer player, ServerLevel level, BlockPos at) {
        if (!Perk.DEEP_SIGHT.has(player)) {
            return;
        }
        List<BlockPos> ores = new ArrayList<>();
        BlockPos.betweenClosed(at.offset(-DEEP_SIGHT_RADIUS, -DEEP_SIGHT_RADIUS, -DEEP_SIGHT_RADIUS), at.offset(DEEP_SIGHT_RADIUS, DEEP_SIGHT_RADIUS, DEEP_SIGHT_RADIUS))
                .forEach(pos -> {
                    if (level.getBlockState(pos).is(Tags.Blocks.ORES)) {
                        ores.add(pos.immutable());
                    }
                });
        if (!ores.isEmpty()) {
            SPARKLES.add(new Sparkle(player, ores, level.getGameTime() + 100));
        }
    }

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || event.getDrops().isEmpty()) {
            return;
        }
        ServerLevel level = event.getLevel();
        BlockState state = event.getState();
        List<ItemEntity> drops = event.getDrops();
        RandomSource random = level.getRandom();
        if (state.is(BlockTags.LOGS) && Perk.BARK_COLLECTOR.has(player) && random.nextFloat() < BARK_CHANCE) {
            extra(level, drops, drops.getFirst().getItem().copyWithCount(1));
        }
        if (Perk.SIFTER.has(player) && (state.is(Tags.Blocks.GRAVELS) || state.is(Blocks.GRAVEL)) && !silkTouched(player)) {
            drops.forEach(drop -> {
                if (drop.getItem().is(state.getBlock().asItem())) {
                    drop.setItem(new ItemStack(Items.FLINT, drop.getItem().getCount()));
                }
            });
        }
        if (Perk.TREASURE_SENSE.has(player) && (state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Tags.Blocks.GRAVELS))) {
            float chance = TREASURE_CHANCE * (Perk.BURIED_RICHES.has(player) && Abilities.active(player, Perk.EARTHMOVER) ? BURIED_RICHES : 1);
            if (random.nextFloat() < chance) {
                extra(level, drops, find(random));
            }
        }
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && Perk.GOLDEN_HARVEST.has(player)) {
            float roll = random.nextFloat();
            if (roll < GOLDEN_APPLE_CHANCE) {
                extra(level, drops, new ItemStack(Items.GOLDEN_APPLE));
            } else if (roll < GOLDEN_APPLE_CHANCE + GOLDEN_CARROT_CHANCE) {
                extra(level, drops, new ItemStack(Items.GOLDEN_CARROT));
            }
        }
    }

    private static boolean silkTouched(Player player) {
        return player.getMainHandItem().getEnchantments().keySet().stream().anyMatch(holder -> holder.is(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH));
    }

    /** Treasure Sense's find. */
    public static ItemStack find(RandomSource random) {
        int roll = random.nextInt(100);
        Item item = roll < 30 ? Items.FLINT : roll < 55 ? Items.CLAY_BALL : roll < 75 ? Items.BONE : roll < 87 ? Items.GOLD_NUGGET
                : roll < 97 ? Items.IRON_NUGGET : roll < 99 ? Items.EMERALD : Items.DIAMOND;
        return new ItemStack(item);
    }

    private static void extra(ServerLevel level, List<ItemEntity> drops, ItemStack stack) {
        ItemEntity first = drops.getFirst();
        drops.add(new ItemEntity(level, first.getX(), first.getY(), first.getZ(), stack));
    }

    // --- Each tick: mended tools, Deep Sight's sparkle, Fertile Ground ---

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (Mend mend : MENDS) {
            if (mend.tool().getDamageValue() > mend.damage()) {
                mend.tool().setDamageValue(mend.damage());
            }
        }
        MENDS.clear();
        SPARKLES.removeIf(sparkle -> {
            ServerLevel level = sparkle.player().serverLevel();
            if (level.getGameTime() > sparkle.until() || sparkle.player().isRemoved()) {
                return true;
            }
            if (level.getGameTime() % 10 == 0) {
                for (BlockPos pos : sparkle.ores()) {
                    if (level.getBlockState(pos).is(Tags.Blocks.ORES)) {
                        level.sendParticles(sparkle.player(), ParticleTypes.WAX_ON, true, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0);
                    }
                }
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0 || !Perk.FERTILE_GROUND.has(player)) {
            return;
        }
        growNear(player, FERTILE_TRIES);
    }

    /** Fertile Ground: gives a few random crops near the player a growth tick. Returns how many it found. */
    public static int growNear(ServerPlayer player, int tries) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        int grown = 0;
        for (int i = 0; i < tries; i++) {
            BlockPos pos = player.blockPosition().offset(random.nextInt(FERTILE_RADIUS * 2 + 1) - FERTILE_RADIUS, random.nextInt(5) - 2,
                    random.nextInt(FERTILE_RADIUS * 2 + 1) - FERTILE_RADIUS);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
                level.setBlock(pos, crop.getStateForAge(crop.getAge(state) + 1), 2);
                grown++;
            }
        }
        return grown;
    }

    // --- Fishing ---

    @SubscribeEvent
    public static void onHookJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() && event.getEntity() instanceof FishingHook hook && hook.getPlayerOwner() instanceof ServerPlayer player
                && Perk.QUICK_BITE.has(player)) {
            hook.lureSpeed += QUICK_BITE;
        }
    }

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getDrops().isEmpty()) {
            return;
        }
        FishingHook hook = event.getHookEntity();
        RandomSource random = player.getRandom();
        ItemStack first = event.getDrops().getFirst();
        if (Perk.DOUBLE_CATCH.has(player) && first.is(ItemTags.FISHES) && random.nextFloat() < DOUBLE_CATCH_CHANCE) {
            reel(hook, player, first.copy());
        }
        int hunter = Perk.TREASURE_HUNTER.rank(player);
        if (hunter > 0 && random.nextFloat() < TREASURE_HUNTER_CHANCE * hunter) {
            LootTable treasure = player.server.reloadableRegistries().getLootTable(BuiltInLootTables.FISHING_TREASURE);
            LootParams params = new LootParams.Builder(player.serverLevel()).withParameter(LootContextParams.ORIGIN, hook.position())
                    .withParameter(LootContextParams.TOOL, player.getMainHandItem()).withParameter(LootContextParams.THIS_ENTITY, hook)
                    .withParameter(LootContextParams.ATTACKING_ENTITY, player).withLuck(player.getLuck()).create(LootContextParamSets.FISHING);
            treasure.getRandomItems(params).forEach(stack -> reel(hook, player, stack));
        }
        if (Perk.MASTER_ANGLER.has(player) && random.nextFloat() < MASTER_ANGLER_CHANCE) {
            BuiltInRegistries.ITEM.getTag(MASTER_ANGLER_FINDS).flatMap(tag -> tag.getRandomElement(random))
                    .ifPresent(item -> reel(hook, player, new ItemStack(item)));
        }
    }

    /** Throws a catch from the hook to the player, as vanilla does with what's fished up. */
    static void reel(FishingHook hook, Player player, ItemStack stack) {
        ItemEntity item = new ItemEntity(hook.level(), hook.getX(), hook.getY(), hook.getZ(), stack);
        double dx = player.getX() - hook.getX(), dy = player.getY() - hook.getY(), dz = player.getZ() - hook.getZ();
        item.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.08, dz * 0.1);
        hook.level().addFreshEntity(item);
    }

    /** Shake: hooking a mob pulls one of its drops off it, once per mob. */
    @SubscribeEvent
    public static void onHookHit(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof FishingHook hook) || !(hook.getPlayerOwner() instanceof ServerPlayer player)
                || !(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Mob mob) || !Perk.SHAKE.has(player)
                || mob.getTags().contains(SHAKEN_TAG)) {
            return;
        }
        mob.addTag(SHAKEN_TAG);
        shake(player, mob).ifPresent(stack -> reel(hook, player, stack));
    }

    /** One random item from a mob's drops. */
    public static java.util.Optional<ItemStack> shake(ServerPlayer player, Mob mob) {
        LootTable table = player.server.reloadableRegistries().getLootTable(mob.getLootTable());
        LootParams params = new LootParams.Builder(player.serverLevel()).withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position()).withParameter(LootContextParams.DAMAGE_SOURCE, player.damageSources().playerAttack(player))
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, player).withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, player)
                .withLuck(player.getLuck()).create(LootContextParamSets.ENTITY);
        List<ItemStack> drops = table.getRandomItems(params);
        if (drops.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(drops.get(player.getRandom().nextInt(drops.size())).copyWithCount(1));
    }
}
