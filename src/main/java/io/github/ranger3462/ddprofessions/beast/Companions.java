package io.github.ranger3462.ddprofessions.beast;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.character.SkillLevels;
import io.github.ranger3462.ddprofessions.skill.Perk;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Each player's companion (PROF-6): one tame, bonded with a Bonding Charm once they have Companion Bond I. When it dies
 * it's saved whole (its name, armor, saddle, collar, bonuses and looks), and its armor and saddle don't drop; a pack
 * animal's chest drops as usual and isn't saved, so nothing doubles. Calling it back (the charm, held to channel) brings
 * a dead companion back at your side, or fetches a living one from wherever it is, even an unloaded chunk or another
 * dimension. Companion Bond's ranks shorten the cooldown and raise the health it comes back with.
 * <p>
 * Kept in the world's saved data by owner, so a companion that dies while its owner is offline is saved too.
 */
@EventBusSubscriber(modid = ProfessionsAndSkills.MODID)
public final class Companions {
    /** Cooldown after a call, in ticks, by Companion Bond rank: 10, 5 and 2 minutes. */
    public static final int[] COOLDOWN = { 12000, 6000, 2400 };
    /** The share of its max health a dead companion comes back with, by rank. */
    public static final float[] RETURN_HEALTH = { 0.5f, 0.75f, 1.0f };
    /** How long to wait for an unloaded companion's chunk to load, in ticks. */
    static final int FETCH_TIMEOUT = 100;
    private static final TicketType<ChunkPos> FETCH_TICKET = TicketType.create(ProfessionsAndSkills.MODID + ":companion",
            Comparator.comparingLong(ChunkPos::toLong), FETCH_TIMEOUT);
    /** What isn't saved with a dead companion: its death, its fire, what it was doing, and a pack animal's chest. */
    private static final String[] NOT_SAVED = { "DeathTime", "HurtTime", "HurtByTimestamp", "Fire", "Motion", "FallDistance", "Passengers",
            "leash", "active_effects", "Items", "ChestedHorse" };

    /** Players waiting for a companion in an unloaded chunk: their UUID, and when to give up. */
    private static final Map<UUID, Long> FETCHING = new HashMap<>();

    private Companions() {}

    /** A player's companion: its UUID, its saved self while it's dead, where it was last seen, and when it can next be called. */
    static final class Entry {
        UUID companion;
        CompoundTag body;
        ResourceKey<Level> dimension;
        BlockPos pos;
        long readyAt;
    }

    /** Every player's companion, in the overworld's saved data. */
    static final class Records extends SavedData {
        final Map<UUID, Entry> entries = new HashMap<>();

        static Records load(CompoundTag tag, HolderLookup.Provider registries) {
            Records records = new Records();
            for (Tag element : tag.getList("companions", Tag.TAG_COMPOUND)) {
                CompoundTag entryTag = (CompoundTag) element;
                Entry entry = new Entry();
                entry.companion = entryTag.getUUID("companion");
                entry.body = entryTag.contains("body") ? entryTag.getCompound("body") : null;
                if (entryTag.contains("dimension")) {
                    entry.dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(entryTag.getString("dimension")));
                    entry.pos = BlockPos.of(entryTag.getLong("pos"));
                }
                entry.readyAt = entryTag.getLong("ready_at");
                records.entries.put(entryTag.getUUID("owner"), entry);
            }
            return records;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            entries.forEach((owner, entry) -> {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putUUID("owner", owner);
                entryTag.putUUID("companion", entry.companion);
                if (entry.body != null) {
                    entryTag.put("body", entry.body);
                }
                if (entry.dimension != null) {
                    entryTag.putString("dimension", entry.dimension.location().toString());
                    entryTag.putLong("pos", entry.pos.asLong());
                }
                entryTag.putLong("ready_at", entry.readyAt);
                list.add(entryTag);
            });
            tag.put("companions", list);
            return tag;
        }
    }

    static Records records(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Records::new, Records::load, null),
                ProfessionsAndSkills.MODID + "_companions");
    }

    private static Optional<Entry> entry(ServerPlayer player) {
        return Optional.ofNullable(records(player.server).entries.get(player.getUUID()));
    }

    /** A player's Companion Bond rank: 0 to 3. Without Pufferfish's Skills there's no tree, so everyone has rank I. */
    public static int rank(ServerPlayer player) {
        return SkillLevels.pufferfish() ? Perk.COMPANION_BOND.rank(player) : 1;
    }

    /** The UUID of a player's companion, alive or dead. */
    public static Optional<UUID> companion(ServerPlayer player) {
        return entry(player).map(entry -> entry.companion);
    }

    /** Whether a player's companion is dead, waiting to be called back. */
    public static boolean isDead(ServerPlayer player) {
        return entry(player).map(entry -> entry.body != null).orElse(false);
    }

    public static boolean isCompanion(ServerPlayer owner, Entity tame) {
        return entry(owner).map(entry -> entry.companion.equals(tame.getUUID())).orElse(false);
    }

    /** Whether a tame is its owner's companion, online or not. */
    public static boolean isCompanion(Entity tame) {
        if (!(tame instanceof OwnableEntity ownable) || ownable.getOwnerUUID() == null || tame.level().getServer() == null) {
            return false;
        }
        Entry entry = records(tame.level().getServer()).entries.get(ownable.getOwnerUUID());
        return entry != null && entry.companion.equals(tame.getUUID());
    }

    /** Bonds one of a player's tames as their companion, releasing the one before. */
    public static boolean bond(ServerPlayer player, Entity tame) {
        if (!Beastmastery.isTameOf(tame, player)) {
            return false;
        }
        if (rank(player) == 0) {
            player.displayClientMessage(Component.translatable("message.ddprofessions.companion.need_bond").withStyle(ChatFormatting.RED), true);
            return false;
        }
        Records records = records(player.server);
        Entry entry = new Entry();
        entry.companion = tame.getUUID();
        entry.dimension = tame.level().dimension();
        entry.pos = tame.blockPosition();
        Entry before = records.entries.put(player.getUUID(), entry);
        if (before != null) {
            entry.readyAt = before.readyAt;
        }
        records.setDirty();
        player.displayClientMessage(Component.translatable("message.ddprofessions.companion.bonded", tame.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    /** Notes where a player's companion is, when they're near it. */
    static void seen(ServerPlayer player, Entity tame) {
        entry(player).filter(entry -> entry.companion.equals(tame.getUUID())).ifPresent(entry -> {
            if (entry.dimension != tame.level().dimension() || entry.pos == null || !entry.pos.closerThan(tame.blockPosition(), 8)) {
                entry.dimension = tame.level().dimension();
                entry.pos = tame.blockPosition();
                records(player.server).setDirty();
            }
        });
    }

    /** Ticks left before a player can call their companion again (0 if they can now). */
    public static long cooldown(ServerPlayer player) {
        return entry(player).map(entry -> Math.max(0, entry.readyAt - player.server.overworld().getGameTime())).orElse(0L);
    }

    /** Whether a player can call their companion now, telling them why not if they can't. */
    public static boolean canCall(ServerPlayer player) {
        if (entry(player).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ddprofessions.companion.none").withStyle(ChatFormatting.RED), true);
            return false;
        }
        long cooldown = cooldown(player);
        if (cooldown > 0) {
            player.displayClientMessage(Component.translatable("message.ddprofessions.companion.cooldown", (cooldown + 19) / 20)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
    }

    /**
     * Calls a player's companion: a dead one comes back at their side, and a living one is brought to them. Starts the
     * cooldown. Returns whether it worked (or, for one in an unloaded chunk, whether the fetch has started).
     */
    public static boolean call(ServerPlayer player) {
        if (!canCall(player)) {
            return false;
        }
        Records records = records(player.server);
        Entry entry = records.entries.get(player.getUUID());
        int rank = Math.max(1, rank(player));
        if (entry.body != null) {
            Optional<Entity> back = revive(player, entry, RETURN_HEALTH[rank - 1]);
            if (back.isEmpty()) {
                return false;
            }
            entry.body = null;
            startCooldown(player, entry, rank);
            player.displayClientMessage(Component.translatable("message.ddprofessions.companion.returned", back.get().getDisplayName())
                    .withStyle(ChatFormatting.GOLD), true);
            return true;
        }
        Optional<Entity> living = Beastmastery.find(player.server, entry.companion);
        if (living.isPresent()) {
            bringTo(living.get(), player);
            startCooldown(player, entry, rank);
            return true;
        }
        // Not loaded: load where it was last seen and look again each tick until it turns up.
        ServerLevel level = entry.dimension == null ? null : player.server.getLevel(entry.dimension);
        if (level == null) {
            player.displayClientMessage(Component.translatable("message.ddprofessions.companion.lost").withStyle(ChatFormatting.RED), true);
            return false;
        }
        ChunkPos chunk = new ChunkPos(entry.pos);
        level.getChunkSource().addRegionTicket(FETCH_TICKET, chunk, 2, chunk);
        FETCHING.put(player.getUUID(), player.server.overworld().getGameTime() + FETCH_TIMEOUT);
        startCooldown(player, entry, rank);
        return true;
    }

    private static void startCooldown(ServerPlayer player, Entry entry, int rank) {
        entry.readyAt = player.server.overworld().getGameTime() + COOLDOWN[rank - 1];
        records(player.server).setDirty();
    }

    /** Brings a dead companion back from its saved self, at the player's side, with this share of its health. */
    private static Optional<Entity> revive(ServerPlayer player, Entry entry, float health) {
        ServerLevel level = player.serverLevel();
        Entity entity = EntityType.loadEntityRecursive(entry.body.copy(), level, loaded -> {
            loaded.moveTo(player.getX(), player.getY(), player.getZ(), loaded.getYRot(), loaded.getXRot());
            return loaded;
        });
        if (entity instanceof LivingEntity living) {
            living.setHealth(living.getMaxHealth() * health);
        }
        if (entity == null || !level.addFreshEntity(entity)) {
            player.displayClientMessage(Component.translatable("message.ddprofessions.companion.lost").withStyle(ChatFormatting.RED), true);
            return Optional.empty();
        }
        level.playSound(null, entity.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, entity.getSoundSource(), 1.0f, 1.0f);
        entry.dimension = level.dimension();
        entry.pos = entity.blockPosition();
        return Optional.of(entity);
    }

    /** Brings a living companion to the player, across dimensions if need be. */
    static void bringTo(Entity tame, ServerPlayer player) {
        Entity moved = tame;
        if (tame.level() == player.level()) {
            tame.teleportTo(player.getX(), player.getY(), player.getZ());
        } else {
            moved = tame.changeDimension(new DimensionTransition(player.serverLevel(), player.position(), Vec3.ZERO, tame.getYRot(), tame.getXRot(),
                    DimensionTransition.DO_NOTHING));
        }
        if (moved != null) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, moved.getSoundSource(), 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable("message.ddprofessions.companion.fetched", moved.getDisplayName())
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Fetches waiting on a chunk to load: done when the companion turns up, given up after {@link #FETCH_TIMEOUT}. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (FETCHING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        for (Iterator<Map.Entry<UUID, Long>> it = FETCHING.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Long> fetch = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(fetch.getKey());
            Entry entry = records(server).entries.get(fetch.getKey());
            if (player == null || entry == null) {
                it.remove();
                continue;
            }
            Optional<Entity> found = Beastmastery.find(server, entry.companion);
            if (found.isPresent()) {
                bringTo(found.get(), player);
                it.remove();
            } else if (now > fetch.getValue()) {
                entry.readyAt = 0; // It never turned up: the call doesn't count.
                records(server).setDirty();
                player.displayClientMessage(Component.translatable("message.ddprofessions.companion.lost").withStyle(ChatFormatting.RED), true);
                it.remove();
            }
        }
    }

    /** Saves a companion whole as it dies, and keeps its armor and saddle from dropping, since they come back with it. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity tame = event.getEntity();
        if (tame.level().isClientSide || !isCompanion(tame)) {
            return;
        }
        MinecraftServer server = tame.level().getServer();
        UUID owner = ((OwnableEntity) tame).getOwnerUUID();
        Records records = records(server);
        Entry entry = records.entries.get(owner);
        CompoundTag body = new CompoundTag();
        if (!tame.saveAsPassenger(body)) {
            return;
        }
        for (String key : NOT_SAVED) {
            body.remove(key);
        }
        entry.body = body;
        entry.dimension = tame.level().dimension();
        entry.pos = tame.blockPosition();
        records.setDirty();
        tame.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
        if (tame instanceof AbstractHorse) {
            tame.getSlot(AbstractHorse.EQUIPMENT_SLOT_OFFSET).set(ItemStack.EMPTY); // The saddle.
        }
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            player.sendSystemMessage(Component.translatable("message.ddprofessions.companion.fell", tame.getDisplayName()).withStyle(ChatFormatting.GOLD));
        }
    }

    /** Right-clicking one of your tames with a Bonding Charm makes it your companion (before the tame's own click, which would sit it). */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().getMainHandItem().is(BeastItems.BONDING_CHARM.get())
                || !Beastmastery.isTameOf(event.getTarget(), event.getEntity())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            bond(player, event.getTarget());
        }
    }
}
