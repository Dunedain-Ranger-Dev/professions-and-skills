package io.github.ranger3462.ddprofessions.character;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Mob levels: how strong a mob is, shown on its nameplate and used to scale skill experience, so a fire master
 * learns nothing from burning a level 1 zombie. A mob's level comes from its own stats ({@link #fromStats}), so modded
 * mobs get one with no setup, unless another mod knows better ({@link #addSource}): Dungeon Delving puts dungeon mobs
 * in their tier's band. Worked out once and kept on the mob.
 */
@EventBusSubscriber(modid = ProfessionsAndSkills.MODID)
public final class MobLevels {
    private static final String LEVEL_TAG = ProfessionsAndSkills.MODID + ":level";
    /** Skill levels a mob can be below yours before experience starts dropping off. */
    public static final int FULL_XP_GAP = 5;
    /** Each level below that takes off this much of the experience... */
    public static final double FALLOFF_PER_LEVEL = 0.1;
    /** ...down to this trickle. */
    public static final double MIN_MULTIPLIER = 0.05;

    /** Other mods' say in a mob's level, asked in turn; the first with an answer wins. */
    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    /** Another mod's rule for some mobs' levels (e.g. by where they are), given the level their stats alone would give. */
    @FunctionalInterface
    public interface Source {
        OptionalInt level(Mob mob, int fromStats);
    }

    private MobLevels() {}

    /** Lets another mod set some mobs' levels. Asked on the server only, the first time a mob's level is needed. */
    public static void addSource(Source source) {
        SOURCES.add(source);
    }

    /** A mob's level (players and non-mobs are 0, as they have none). */
    public static int level(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return 0;
        }
        var data = mob.getPersistentData();
        if (data.contains(LEVEL_TAG)) {
            return data.getInt(LEVEL_TAG);
        }
        int level = compute(mob);
        if (!mob.level().isClientSide()) {
            data.putInt(LEVEL_TAG, level);
        }
        return level;
    }

    private static int compute(Mob mob) {
        int fromStats = fromStats(mob);
        if (mob.level().isClientSide()) {
            return fromStats;
        }
        for (Source source : SOURCES) {
            OptionalInt level = source.level(mob, fromStats);
            if (level.isPresent()) {
                return level.getAsInt();
            }
        }
        return fromStats;
    }

    /**
     * A level from how tough a mob is: health, plus four per point of attack damage and two per point of armor, a
     * level per 12 (a zombie is about level 3, an iron golem 13, the Wither around 30).
     */
    public static int fromStats(LivingEntity entity) {
        double attack = entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? entity.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        double power = entity.getMaxHealth() + 4 * attack + 2 * entity.getArmorValue();
        return Math.max(1, (int) Math.round(power / 12.0));
    }

    /**
     * How much experience a skill gets from a mob: all of it if the mob is no more than {@link #FULL_XP_GAP} levels
     * below the skill, then 10% less per level past that, down to {@link #MIN_MULTIPLIER}. Skills count from 0, so
     * a new skill always gets full experience.
     */
    public static double xpMultiplier(int skillLevel, int mobLevel) {
        int gap = skillLevel - mobLevel - FULL_XP_GAP;
        return gap <= 0 ? 1.0 : Math.max(MIN_MULTIPLIER, 1.0 - gap * FALLOFF_PER_LEVEL);
    }

    /**
     * Works a mob's level out again from its stats now (after they've been changed: a realm guard kitted out, a monster
     * levelled by its area), and tells the players who can see it.
     */
    public static int refresh(Mob mob) {
        mob.getPersistentData().remove(LEVEL_TAG);
        int level = level(mob);
        if (!mob.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntity(mob, new MobLevelPayload(mob.getId(), level));
        }
        return level;
    }

    /** Tells a player's client a mob's level when it comes into view, for its nameplate. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof Mob mob) {
            PacketDistributor.sendToPlayer(player, new MobLevelPayload(mob.getId(), level(mob)));
        }
    }
}
