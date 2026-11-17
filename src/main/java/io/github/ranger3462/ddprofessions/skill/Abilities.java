package io.github.ranger3462.ddprofessions.skill;

import java.util.EnumMap;
import java.util.Map;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The trees' timed abilities: each skill's three-rank signature perk, started by sneaking and doing the skill's thing
 * (striking, digging, loosing an arrow, raising a shield). It lasts {@link #SECONDS} by rank, then cools down for
 * {@link #COOLDOWN_MINUTES}. Times are kept in the player's persistent data, so a relog doesn't reset a cooldown.
 */
@EventBusSubscriber(modid = ProfessionsAndSkills.MODID)
public final class Abilities {
    /** How long an ability lasts, by rank. */
    public static final int[] SECONDS = { 10, 15, 20 };
    /** How long it then cools down, by rank. */
    public static final int[] COOLDOWN_MINUTES = { 4, 3, 2 };
    private static final String DATA = "ddprofessions.abilities";

    /** Each timed ability's tree, for its name (the rank's node title). */
    private static final Map<Perk, String> TREES = new EnumMap<>(Perk.class);

    static {
        TREES.put(Perk.EARTHMOVER, "excavation");
        TREES.put(Perk.HARVEST_MOON, "farming");
        TREES.put(Perk.BLADESTORM, "one_handed");
        TREES.put(Perk.SKULL_SPLITTER, "two_handed");
        TREES.put(Perk.IMPALE, "polearms");
        TREES.put(Perk.SHADOW_FLURRY, "daggers");
        TREES.put(Perk.BERSERK, "unarmed");
        TREES.put(Perk.VOLLEY, "archery");
        TREES.put(Perk.RAPID_FIRE, "crossbows");
        TREES.put(Perk.TWIN_FURY, "dual_wielding");
        TREES.put(Perk.SHIELD_WALL, "blocking");
    }

    private Abilities() {}

    /** The ability's times, to read (empty if it has never run). */
    private static CompoundTag data(Player player, Perk perk) {
        return player.getPersistentData().getCompound(DATA).getCompound(perk.key);
    }

    /** The ability's times, to write: kept in the player's data. */
    private static CompoundTag stored(Player player, Perk perk) {
        CompoundTag all = player.getPersistentData().getCompound(DATA);
        player.getPersistentData().put(DATA, all);
        CompoundTag one = all.getCompound(perk.key);
        all.put(perk.key, one);
        return one;
    }

    /** Whether the player's ability is running now. */
    public static boolean active(Player player, Perk perk) {
        return perk.has(player) && player.level().getGameTime() < data(player, perk).getLong("until");
    }

    /** Whether the player could start the ability now: they have it, and it isn't running or cooling down. */
    public static boolean ready(Player player, Perk perk) {
        return perk.has(player) && player.level().getGameTime() >= data(player, perk).getLong("ready");
    }

    /**
     * Starts the ability if the player has it and it's ready. Returns whether it's running afterwards (already running
     * counts).
     */
    public static boolean tryStart(ServerPlayer player, Perk perk) {
        if (active(player, perk)) {
            return true;
        }
        if (!ready(player, perk)) {
            return false;
        }
        int rank = perk.rank(player);
        long now = player.level().getGameTime();
        CompoundTag one = stored(player, perk);
        long until = now + SECONDS[rank - 1] * 20L;
        one.putLong("until", until);
        one.putLong("ready", until + COOLDOWN_MINUTES[rank - 1] * 60L * 20L);
        one.putBoolean("ended", false);
        one.putBoolean("announced", false);
        player.displayClientMessage(Component.translatable("message.ddprofessions.ability.started", name(perk, rank), SECONDS[rank - 1])
                .withStyle(ChatFormatting.GOLD), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.6f, 1.4f);
        return true;
    }

    /** Ends a running ability and its cooldown at once. For testing. */
    public static void reset(Player player, Perk perk) {
        player.getPersistentData().getCompound(DATA).remove(perk.key);
    }

    private static Component name(Perk perk, int rank) {
        String tree = TREES.get(perk);
        return tree == null ? Component.literal(perk.key) : Component.translatable("skill.ddprofessions." + tree + "." + perk.key + "_" + rank);
    }

    /** Tells the player when an ability wears off, and when it's ready again. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0
                || !player.getPersistentData().contains(DATA)) {
            return;
        }
        long now = player.level().getGameTime();
        for (Perk perk : TREES.keySet()) {
            CompoundTag all = player.getPersistentData().getCompound(DATA);
            if (!all.contains(perk.key)) {
                continue;
            }
            CompoundTag one = all.getCompound(perk.key);
            int rank = Math.max(1, perk.rank(player));
            if (!one.getBoolean("ended") && now >= one.getLong("until")) {
                one.putBoolean("ended", true);
                long seconds = Math.max(0, (one.getLong("ready") - now) / 20);
                player.displayClientMessage(Component.translatable("message.ddprofessions.ability.ended", name(perk, rank), seconds)
                        .withStyle(ChatFormatting.GRAY), true);
            } else if (one.getBoolean("ended") && !one.getBoolean("announced") && now >= one.getLong("ready")) {
                one.putBoolean("announced", true);
                player.displayClientMessage(Component.translatable("message.ddprofessions.ability.ready", name(perk, rank))
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
    }
}
