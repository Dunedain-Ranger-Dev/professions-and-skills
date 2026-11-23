package io.github.ranger3462.ddprofessions.skill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The magic school trees' perks: Quick Casting, each school's effect and its capstone. Spell Engine and Iron's Spells
 * feed them through their integrations: which school a hit's damage belongs to ({@link #addSchoolOf}), casts
 * ({@link #onCast}), heals ({@link #onHeal}), cooldowns ({@link #cooldown}) and mana costs ({@link #manaCost}).
 */
@EventBusSubscriber(modid = ProfessionsAndSkills.MODID)
public final class MagicPerks {
    public static final float QUICK_CASTING = 0.9f;
    public static final float FLUX = 0.9f;
    public static final int KINDLING_SECONDS = 3, OVERLOAD_EVERY = 10;
    public static final float ARC = 0.5f, STORM_CALLER_CHANCE = 0.10f, SIPHON = 0.10f, LICH_HEALTH = 0.2f;
    public static final int SHATTER_TICKS = 100;
    public static final int AEGIS_TICKS = 30 * 20;

    /** Each school's three perks: Quick Casting, its effect and its capstone. */
    private static final Map<MagicSchool, Perk[]> PERKS = new EnumMap<>(MagicSchool.class);

    static {
        PERKS.put(MagicSchool.FIRE, new Perk[] { Perk.FIRE_QUICK_CASTING, Perk.FIRE_EFFECT, Perk.FIRE_CAPSTONE });
        PERKS.put(MagicSchool.FROST, new Perk[] { Perk.FROST_QUICK_CASTING, Perk.FROST_EFFECT, Perk.FROST_CAPSTONE });
        PERKS.put(MagicSchool.LIGHTNING, new Perk[] { Perk.LIGHTNING_QUICK_CASTING, Perk.LIGHTNING_EFFECT, Perk.LIGHTNING_CAPSTONE });
        PERKS.put(MagicSchool.ARCANE, new Perk[] { Perk.ARCANE_QUICK_CASTING, Perk.ARCANE_EFFECT, Perk.ARCANE_CAPSTONE });
        PERKS.put(MagicSchool.HOLY, new Perk[] { Perk.HOLY_QUICK_CASTING, Perk.HOLY_EFFECT, Perk.HOLY_CAPSTONE });
        PERKS.put(MagicSchool.EARTH, new Perk[] { Perk.EARTH_QUICK_CASTING, Perk.EARTH_EFFECT, Perk.EARTH_CAPSTONE });
        PERKS.put(MagicSchool.WATER, new Perk[] { Perk.WATER_QUICK_CASTING, Perk.WATER_EFFECT, Perk.WATER_CAPSTONE });
        PERKS.put(MagicSchool.AIR, new Perk[] { Perk.AIR_QUICK_CASTING, Perk.AIR_EFFECT, Perk.AIR_CAPSTONE });
        PERKS.put(MagicSchool.NECROMANCY, new Perk[] { Perk.NECROMANCY_QUICK_CASTING, Perk.NECROMANCY_EFFECT, Perk.NECROMANCY_CAPSTONE });
    }

    /** Ways to tell a hit's school, one per spell mod. */
    private static final List<Function<DamageSource, Optional<MagicSchool>>> SCHOOL_OF = new ArrayList<>();
    /** Enemies chilled by a player's frost spell (Shatter): who chilled them, and until when. */
    private record Chill(UUID by, long until) {}
    private static final Map<LivingEntity, Chill> CHILLED = new WeakHashMap<>();
    private static final Map<UUID, Integer> ARCANE_HITS = new HashMap<>();
    /** True while an arc of lightning is being dealt, so it doesn't arc again. */
    private static boolean arcing;

    private MagicPerks() {}

    public static Perk quickCasting(MagicSchool school) {
        return PERKS.get(school)[0];
    }

    public static Perk effect(MagicSchool school) {
        return PERKS.get(school)[1];
    }

    public static Perk capstone(MagicSchool school) {
        return PERKS.get(school)[2];
    }

    /** Lets a spell mod say which school a hit's damage belongs to. */
    public static void addSchoolOf(Function<DamageSource, Optional<MagicSchool>> schoolOf) {
        SCHOOL_OF.add(schoolOf);
    }

    public static Optional<MagicSchool> schoolOf(DamageSource source) {
        for (var schoolOf : SCHOOL_OF) {
            Optional<MagicSchool> school = schoolOf.apply(source);
            if (school.isPresent()) {
                return school;
            }
        }
        return Optional.empty();
    }

    /** A spell's cooldown in ticks, after the caster's Quick Casting for its school. */
    public static int cooldown(Player player, MagicSchool school, int ticks) {
        return quickCasting(school).has(player) ? Math.round(ticks * QUICK_CASTING) : ticks;
    }

    /** A spell's mana cost, after Flux (arcane spells cost less). Only Iron's Spells has mana. */
    public static int manaCost(Player player, MagicSchool school, int cost) {
        return school == MagicSchool.ARCANE && effect(school).has(player) ? Math.round(cost * FLUX) : cost;
    }

    /** A player cast a spell of this school. */
    public static void onCast(ServerPlayer player, MagicSchool school) {
        if (school == MagicSchool.EARTH && capstone(school).has(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100)); // Stoneskin
        }
        if (school == MagicSchool.AIR && effect(school).has(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1)); // Tailwind
        }
    }

    /** A player's spell of this school healed something, by this much (before the heal is applied). */
    public static void onHeal(Player caster, LivingEntity target, float amount, MagicSchool school) {
        if (school != MagicSchool.HOLY || target.level().isClientSide()) {
            return;
        }
        if (effect(school).has(caster)) { // Blessing: cures one harmful effect.
            target.getActiveEffects().stream().filter(effect -> !effect.getEffect().value().isBeneficial()).findFirst()
                    .ifPresent(effect -> target.removeEffect(effect.getEffect()));
        }
        float over = amount - (target.getMaxHealth() - target.getHealth());
        if (capstone(school).has(caster) && over > 0) { // Aegis: overhealing gives Absorption.
            target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, AEGIS_TICKS), caster);
        }
    }

    /** Shatter: double damage on the next hit against an enemy this player chilled, which uses the chill up. */
    public static float shatter(Player player, LivingEntity target) {
        Chill chill = CHILLED.get(target);
        if (chill == null || !chill.by().equals(player.getUUID())) {
            return 1;
        }
        CHILLED.remove(target);
        return player.level().getGameTime() <= chill.until() ? 2 : 1;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player == event.getEntity()) {
            return;
        }
        Optional<MagicSchool> school = schoolOf(event.getSource());
        if (school.isEmpty()) {
            return;
        }
        if (school.get() == MagicSchool.NECROMANCY && capstone(school.get()).has(player) && player.getHealth() < player.getMaxHealth() * LICH_HEALTH) {
            event.setAmount(event.getAmount() * 2); // Lich Form
        }
        if (school.get() == MagicSchool.ARCANE && capstone(school.get()).has(player) && !arcing) {
            int hits = ARCANE_HITS.merge(player.getUUID(), 1, Integer::sum);
            if (hits % OVERLOAD_EVERY == 0) {
                event.setAmount(event.getAmount() * 2); // Overload
            }
        }
    }

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        LivingEntity target = event.getEntity();
        if (!(source.getEntity() instanceof ServerPlayer player) || player == target || event.getNewDamage() <= 0 || arcing) {
            return;
        }
        Optional<MagicSchool> found = schoolOf(source);
        if (found.isEmpty()) {
            return;
        }
        MagicSchool school = found.get();
        float damage = event.getNewDamage();
        boolean effect = effect(school).has(player), capstone = capstone(school).has(player);
        switch (school) {
            case FIRE -> {
                boolean wasBurning = target.isOnFire();
                if (effect) { // Kindling
                    target.setRemainingFireTicks(target.getRemainingFireTicks() + KINDLING_SECONDS * 20);
                }
                if (capstone && wasBurning) { // Wildfire
                    CombatPerks.enemiesNear(player, target.position(), 3, target).forEach(other -> other.igniteForSeconds(KINDLING_SECONDS));
                }
            }
            case FROST -> {
                if (effect) { // Deep Chill
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), player);
                }
                if (capstone) { // Shatter: the next hit doubles
                    CHILLED.put(target, new Chill(player.getUUID(), player.level().getGameTime() + SHATTER_TICKS));
                }
            }
            case LIGHTNING -> {
                if (effect) { // Arc
                    CombatPerks.enemiesNear(player, target.position(), 4, target).stream()
                            .min((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)))
                            .ifPresent(next -> {
                                arcing = true;
                                try {
                                    next.hurt(source, damage * ARC);
                                } finally {
                                    arcing = false;
                                }
                            });
                }
                if (capstone && player.getRandom().nextFloat() < STORM_CALLER_CHANCE && player.level() instanceof ServerLevel level) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(target.getX(), target.getY(), target.getZ());
                        bolt.setCause(player);
                        level.addFreshEntity(bolt);
                    }
                }
            }
            case EARTH -> {
                if (effect) { // Rooted
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 9), player);
                }
            }
            case WATER -> {
                if (effect) { // Tidal
                    Vec3 away = target.position().subtract(player.position());
                    target.knockback(0.8, -away.x, -away.z);
                }
                if (capstone) { // Riptide
                    player.heal(1);
                }
            }
            case AIR -> {
                if (capstone) { // Cyclone
                    target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 15, 2), player);
                }
            }
            case NECROMANCY -> {
                if (effect) { // Siphon
                    player.heal(damage * SIPHON);
                }
            }
            default -> {}
        }
    }
}
