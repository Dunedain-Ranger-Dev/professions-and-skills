package io.github.ranger3462.ddprofessions.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.skill.ActivitySkills.ArmorWeight;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The combat style, Dual Wielding, Blocking and armor trees' perks (their stat-only perks are in {@link PerkStats}).
 * Damage bonuses are worked out as a hit comes in ({@link #onIncoming}); what a hit does after it lands (splashing onto
 * other enemies, bleeding, poison, knockback) once it has ({@link #onDamaged}). Hits from those splashes count for
 * nothing more, so they can't chain.
 */
@EventBusSubscriber(modid = ProfessionsAndSkills.MODID)
public final class CombatPerks {
    public static final float RIPOSTE = 1.3f, DUELIST = 1.25f, EXECUTIONER = 1.5f, KEEP_AWAY = 1.2f, BACKSTAB = 1.3f, ASSASSINATE = 1.5f;
    public static final float BERSERK = 1.5f, SIEGEBREAKER = 1.5f, VANGUARD = 1.1f, MOMENTUM_STEP = 0.03f;
    public static final int MOMENTUM_MAX_STEPS = 5;
    public static final float SPLASH = 0.5f, CLEAVE = 0.3f, IMPALE = 0.6f;
    public static final float BLEED_CHANCE = 0.15f, DISARM_CHANCE = 0.05f, WRESTLE_CHANCE = 0.10f, POISON_CHANCE = 0.15f, IRON_GRIP_CHANCE = 0.15f;
    public static final float DAZE_CHANCE = 0.10f, DEADEYE_CHANCE = 0.20f, EXPLOSIVE_CHANCE = 0.05f, RETRIEVAL_CHANCE = 0.30f;
    public static final float EVASIVE_CHANCE = 0.05f, AGILITY_CHANCE = 0.03f, PARRY_CHANCE = 0.10f, DEFLECT_CHANCE = 0.10f;
    public static final float JUGGERNAUT_STEP = 0.03f, MAIL_BRACE = 0.85f;
    /** How long after a block Riposte lasts, after a hit Vanguard lasts, and between hits Momentum keeps, in ticks. */
    public static final int RIPOSTE_TICKS = 40, VANGUARD_TICKS = 100, MOMENTUM_TICKS = 60;
    /** A mob's chance to keep a weapon knocked out of its hands (vanilla's usual drop chance); otherwise it's lost. */
    public static final float DISARM_DROP_CHANCE = 0.085f;
    public static final int SECOND_WIND_COOLDOWN = 60 * 20;
    private static final String SECOND_WIND_TAG = "ddprofessions.second_wind";

    private static final Map<UUID, Long> LAST_BLOCK = new HashMap<>();
    private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
    private static final Map<UUID, long[]> MOMENTUM = new HashMap<>(); // { last hit time, streak }
    private static final Map<UUID, Integer> HIT_COUNT = new HashMap<>(); // for Whirlwind and Twin Fury
    /** Bleeding entities: ticks left. */
    private static final Map<LivingEntity, Integer> BLEEDING = new IdentityHashMap<>();
    public static final int BLEED_TICKS = 80;
    private static final List<Runnable> NEXT_TICK = new ArrayList<>();
    /** True while splash hits are being dealt, so they don't splash, bleed or count again. */
    private static boolean splashing;

    private CombatPerks() {}

    /** Whether something is an enemy of the player's: a monster, or a mob after them. Never a player or one of their tames. */
    public static boolean isEnemy(Player player, Entity entity) {
        if (!(entity instanceof LivingEntity living) || entity == player || !living.isAlive() || entity instanceof Player) {
            return false;
        }
        if (entity instanceof OwnableEntity owned && player.getUUID().equals(owned.getOwnerUUID())) {
            return false;
        }
        return entity instanceof Enemy || entity instanceof Mob mob && mob.getTarget() == player;
    }

    /** The player's enemies within this many blocks of a spot, besides one. */
    public static List<LivingEntity> enemiesNear(Player player, Vec3 at, double radius, Entity except) {
        return player.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e != except && isEnemy(player, e) && e.position().distanceTo(at) <= radius);
    }

    /** A melee hit by the player themselves, not a splash. */
    private static boolean melee(DamageSource source, Player player) {
        return source.getDirectEntity() == player && !splashing;
    }

    // --- Hits coming in: bonuses and defenses ---

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (splashing) {
            return;
        }
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer player && player != target) {
            event.setAmount(event.getAmount() * attackMultiplier(event, player, target, source));
        }
        if (target instanceof ServerPlayer player) {
            defend(event, player, source);
        }
    }

    private static float attackMultiplier(LivingIncomingDamageEvent event, ServerPlayer player, LivingEntity target, DamageSource source) {
        float multiplier = 1;
        long now = player.level().getGameTime();
        if (melee(source, player)) {
            String style = ActivitySkills.styleFor(player.getMainHandItem()).orElse("");
            boolean dual = ActivitySkills.dualWielding(player);
            if (player.isShiftKeyDown()) {
                startAbility(player, style);
                if (dual) {
                    Abilities.tryStart(player, Perk.TWIN_FURY);
                }
            }
            switch (style) {
                case ActivitySkills.ONE_HANDED -> {
                    if (Perk.RIPOSTE.has(player) && now - LAST_BLOCK.getOrDefault(player.getUUID(), -1000L) <= RIPOSTE_TICKS) {
                        multiplier *= RIPOSTE;
                    }
                    if (Perk.DUELIST.has(player) && enemiesNear(player, player.position(), 8, target).isEmpty()) {
                        multiplier *= DUELIST;
                    }
                }
                case ActivitySkills.TWO_HANDED -> {
                    if (Perk.EXECUTIONER.has(player) && target.getHealth() < target.getMaxHealth() * 0.25f) {
                        multiplier *= EXECUTIONER;
                    }
                    if (Perk.MOMENTUM.has(player)) {
                        long[] streak = MOMENTUM.computeIfAbsent(player.getUUID(), id -> new long[2]);
                        streak[1] = now - streak[0] <= MOMENTUM_TICKS ? Math.min(streak[1] + 1, MOMENTUM_MAX_STEPS) : 0;
                        streak[0] = now;
                        multiplier *= 1 + MOMENTUM_STEP * streak[1];
                    }
                    if (Perk.CRUSHING_BLOW.has(player)) {
                        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> reduction * 0.9f);
                    }
                }
                case ActivitySkills.POLEARMS -> {
                    if (Perk.KEEP_AWAY.has(player) && player.distanceTo(target) > 3.0f) {
                        multiplier *= KEEP_AWAY;
                    }
                }
                case ActivitySkills.DAGGERS -> {
                    if (Perk.BACKSTAB.has(player) && behind(player, target)) {
                        multiplier *= BACKSTAB;
                    }
                    if (Perk.ASSASSINATE.has(player) && target instanceof Mob mob && mob.getTarget() != player && target.getLastHurtByMob() != player) {
                        multiplier *= ASSASSINATE;
                        ((ServerLevel) player.level()).sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                    }
                }
                case ActivitySkills.UNARMED -> {
                    if (Abilities.active(player, Perk.BERSERK)) {
                        multiplier *= BERSERK;
                    }
                    if (Perk.MARTIAL_MASTER.has(player)) {
                        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> 0);
                    }
                }
                default -> {}
            }
            if (dual && Abilities.active(player, Perk.TWIN_FURY) && HIT_COUNT.getOrDefault(player.getUUID(), 0) % 2 == 1) {
                multiplier *= 2; // Every other hit: the off hand's, as Better Combat swings them in turn.
            }
        } else if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getOwner() == player) {
            ItemStack weapon = arrow.getWeaponItem();
            if (weapon != null && weapon.getItem() instanceof CrossbowItem) {
                if (Perk.SIEGEBREAKER.has(player) && (target.isBlocking() || target.getArmorValue() >= 10)) {
                    multiplier *= SIEGEBREAKER;
                }
            } else if (Perk.DEADEYE.has(player) && arrow.isCritArrow() && player.getRandom().nextFloat() < DEADEYE_CHANCE) {
                multiplier *= 2;
                ((ServerLevel) player.level()).sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
            }
        }
        if (Perk.VANGUARD.has(player) && ActivitySkills.wears(player, ArmorWeight.MEDIUM)
                && now - LAST_HURT.getOrDefault(player.getUUID(), -1000L) <= VANGUARD_TICKS) {
            multiplier *= VANGUARD;
        }
        multiplier *= MagicPerks.shatter(player, target);
        return multiplier;
    }

    private static void startAbility(ServerPlayer player, String style) {
        Perk ability = switch (style) {
            case ActivitySkills.ONE_HANDED -> Perk.BLADESTORM;
            case ActivitySkills.TWO_HANDED -> Perk.SKULL_SPLITTER;
            case ActivitySkills.POLEARMS -> Perk.IMPALE;
            case ActivitySkills.DAGGERS -> Perk.SHADOW_FLURRY;
            case ActivitySkills.UNARMED -> Perk.BERSERK;
            default -> null;
        };
        if (ability != null) {
            Abilities.tryStart(player, ability);
        }
    }

    /** Whether the attacker is behind the target: more than a right angle from where it faces. */
    public static boolean behind(Entity attacker, LivingEntity target) {
        Vec3 facing = Vec3.directionFromRotation(0, target.getYHeadRot());
        Vec3 toAttacker = new Vec3(attacker.getX() - target.getX(), 0, attacker.getZ() - target.getZ());
        return toAttacker.lengthSqr() > 1.0E-4 && facing.dot(toAttacker.normalize()) < -0.3;
    }

    private static void defend(LivingIncomingDamageEvent event, ServerPlayer player, DamageSource source) {
        Entity attacker = source.getEntity();
        boolean real = attacker != null && attacker != player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (real && ActivitySkills.wears(player, ArmorWeight.LIGHT)) {
            float dodge = (Perk.EVASIVE.has(player) ? EVASIVE_CHANCE : 0) + AGILITY_CHANCE * Perk.AGILITY.rank(player);
            if (dodge > 0 && player.getRandom().nextFloat() < dodge) {
                event.setCanceled(true);
                player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1.0f, 1.4f);
                if (Perk.PHANTOM.has(player)) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1));
                }
                return;
            }
        }
        if (real && source.getDirectEntity() == attacker && Perk.PARRY.has(player) && ActivitySkills.dualWielding(player)
                && player.getRandom().nextFloat() < PARRY_CHANCE) {
            event.setCanceled(true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.4f, 1.8f);
            return;
        }
        if (source.getDirectEntity() instanceof AbstractArrow && Perk.DEFLECT.has(player) && player.getMainHandItem().isEmpty()
                && player.getRandom().nextFloat() < DEFLECT_CHANCE) {
            event.setCanceled(true); // The arrow bounces off, as from a shield.
            return;
        }
        if (Abilities.active(player, Perk.SHIELD_WALL) && player.isBlocking() && !source.is(DamageTypeTags.BYPASSES_SHIELD)
                && source.getSourcePosition() != null) {
            event.setCanceled(true); // Every side, not just the front.
            return;
        }
        event.setAmount(event.getAmount() * takenMultiplier(player, source));
    }

    /** How much of a hit the player takes after Juggernaut (heavy armor) and Mail Brace (arrows, in medium armor). */
    public static float takenMultiplier(Player player, DamageSource source) {
        float multiplier = 1;
        int juggernaut = Perk.JUGGERNAUT.rank(player);
        if (juggernaut > 0 && ActivitySkills.wears(player, ArmorWeight.HEAVY)) {
            multiplier *= 1 - JUGGERNAUT_STEP * juggernaut;
        }
        if (Perk.MAIL_BRACE.has(player) && source.is(DamageTypeTags.IS_PROJECTILE) && ActivitySkills.wears(player, ArmorWeight.MEDIUM)) {
            multiplier *= MAIL_BRACE;
        }
        return multiplier;
    }

    // --- Hits that landed: what they do next ---

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (splashing || event.getNewDamage() <= 0) {
            return;
        }
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (target instanceof ServerPlayer hurt && source.getEntity() instanceof LivingEntity && source.getEntity() != hurt) {
            LAST_HURT.put(hurt.getUUID(), hurt.level().getGameTime());
            secondWind(hurt);
        }
        if (!(source.getEntity() instanceof ServerPlayer player) || player == target) {
            return;
        }
        float damage = event.getNewDamage();
        var random = player.getRandom();
        if (melee(source, player)) {
            String style = ActivitySkills.styleFor(player.getMainHandItem()).orElse("");
            boolean dual = ActivitySkills.dualWielding(player);
            int hits = HIT_COUNT.merge(player.getUUID(), 1, Integer::sum);
            switch (style) {
                case ActivitySkills.ONE_HANDED -> {
                    if (Abilities.active(player, Perk.BLADESTORM)) {
                        splash(player, enemiesNear(player, player.position(), 3, target), damage * SPLASH);
                    }
                    if (Perk.BLEED.has(player) && random.nextFloat() < BLEED_CHANCE) {
                        bleed(target);
                    }
                    if (Perk.DISARM.has(player) && random.nextFloat() < DISARM_CHANCE) {
                        disarm(target);
                    }
                }
                case ActivitySkills.TWO_HANDED -> {
                    if (Abilities.active(player, Perk.SKULL_SPLITTER)) {
                        splash(player, enemiesNear(player, target.position(), 2.5, target), damage * SPLASH);
                    } else if (Perk.CLEAVE.has(player)) {
                        enemiesNear(player, target.position(), 2, target).stream()
                                .min((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)))
                                .ifPresent(next -> splash(player, List.of(next), damage * CLEAVE));
                    }
                    if (Perk.STAGGER.has(player)) {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1), player);
                    }
                }
                case ActivitySkills.POLEARMS -> {
                    if (Abilities.active(player, Perk.IMPALE)) {
                        Vec3 direction = target.position().subtract(player.position()).multiply(1, 0, 1).normalize();
                        enemiesNear(player, target.position().add(direction.scale(1.5)), 1.5, target).stream()
                                .min((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)))
                                .ifPresent(behind -> splash(player, List.of(behind), damage * IMPALE));
                    }
                    if (Perk.SWEEP.has(player)) {
                        target.knockback(0.6, player.getX() - target.getX(), player.getZ() - target.getZ());
                    }
                }
                case ActivitySkills.DAGGERS -> {
                    if (Perk.POISON_EDGE.has(player) && random.nextFloat() < POISON_CHANCE) {
                        target.addEffect(new MobEffectInstance(MobEffects.POISON, 60), player);
                    }
                }
                case ActivitySkills.UNARMED -> {
                    if (Perk.WRESTLE.has(player) && random.nextFloat() < WRESTLE_CHANCE) {
                        disarm(target);
                    }
                    if (Perk.IRON_GRIP.has(player) && random.nextFloat() < IRON_GRIP_CHANCE) {
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60), player);
                    }
                }
                default -> {}
            }
            if (dual) {
                if (Perk.REND.has(player) && random.nextFloat() < BLEED_CHANCE) {
                    bleed(target);
                }
                if (Perk.WHIRLWIND.has(player) && hits % 5 == 0) {
                    splash(player, enemiesNear(player, player.position(), 3, target), damage * SPLASH);
                }
            }
        } else if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getOwner() == player) {
            ItemStack weapon = arrow.getWeaponItem();
            boolean crossbow = weapon != null && weapon.getItem() instanceof CrossbowItem;
            if (!crossbow && Perk.DAZE.has(player) && random.nextFloat() < DAZE_CHANCE) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), player);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40), player);
            }
            if (crossbow && Perk.PIN.has(player)) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 9), player);
            }
            if (crossbow && Perk.EXPLOSIVE_BOLTS.has(player) && random.nextFloat() < EXPLOSIVE_CHANCE) {
                player.level().explode(arrow, target.getX(), target.getY(0.5), target.getZ(), 1.5f, Level.ExplosionInteraction.NONE);
            }
        }
    }

    /** Deals extra damage from the player to each of these, as melee hits that don't count for any more perks. */
    public static void splash(ServerPlayer player, List<LivingEntity> targets, float damage) {
        if (targets.isEmpty() || damage <= 0) {
            return;
        }
        splashing = true;
        try {
            for (LivingEntity other : targets) {
                other.hurt(player.damageSources().playerAttack(player), damage);
            }
        } finally {
            splashing = false;
        }
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY(0.5), player.getZ(), 1, 0, 0, 0, 0);
    }

    /** Starts the target bleeding: 1 damage a second for {@link #BLEED_TICKS}. */
    public static void bleed(LivingEntity target) {
        BLEEDING.put(target, BLEED_TICKS);
    }

    public static boolean bleeding(LivingEntity target) {
        return BLEEDING.containsKey(target);
    }

    /** Knocks a mob's weapon out of its hands. It keeps it as often as it would have dropped it; otherwise it's lost. */
    public static void disarm(LivingEntity target) {
        if (!(target instanceof Mob mob) || mob.getMainHandItem().isEmpty()) {
            return;
        }
        ItemStack weapon = mob.getMainHandItem();
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (mob.getRandom().nextFloat() < DISARM_DROP_CHANCE) {
            mob.spawnAtLocation(weapon);
        }
        mob.level().playSound(null, mob.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.HOSTILE, 0.6f, 1.2f);
    }

    private static void secondWind(ServerPlayer player) {
        if (!Perk.SECOND_WIND.has(player) || !ActivitySkills.wears(player, ArmorWeight.LIGHT) || player.getHealth() >= player.getMaxHealth() * 0.3f) {
            return;
        }
        long now = player.level().getGameTime();
        if (now - player.getPersistentData().getLong(SECOND_WIND_TAG) < SECOND_WIND_COOLDOWN && player.getPersistentData().contains(SECOND_WIND_TAG)) {
            return;
        }
        player.getPersistentData().putLong(SECOND_WIND_TAG, now);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
    }

    // --- Kills ---

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || player == event.getEntity()) {
            return;
        }
        if (source.getDirectEntity() == player && Perk.VANISH.has(player) && ActivitySkills.wields(player, ActivitySkills.DAGGERS)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, true));
        }
        if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getOwner() == player && Perk.RETRIEVAL.has(player)
                && arrow.getWeaponItem() != null && arrow.getWeaponItem().getItem() instanceof BowItem
                && arrow.pickup == AbstractArrow.Pickup.ALLOWED && player.getRandom().nextFloat() < RETRIEVAL_CHANCE) {
            ItemStack back = arrow.getPickupItemStackOrigin().copyWithCount(1);
            if (!player.getInventory().add(back)) {
                player.drop(back, false);
            }
        }
        if (Perk.BATTLE_RALLY.has(player) && ActivitySkills.wears(player, ArmorWeight.MEDIUM)) {
            player.heal(2);
        }
    }

    // --- Shields ---

    @SubscribeEvent
    public static void onBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getBlocked()) {
            return;
        }
        LAST_BLOCK.put(player.getUUID(), player.level().getGameTime());
        DamageSource source = event.getDamageSource();
        if (Perk.HEAVY_BULWARK.has(player) && ActivitySkills.wears(player, ArmorWeight.HEAVY)) {
            event.setShieldDamage(0);
        } else if (Perk.UNBREAKABLE.has(player)) {
            event.setShieldDamage(event.shieldDamage() * 0.5f);
        }
        if (Perk.SHIELD_BASH.has(player) && source.getDirectEntity() instanceof LivingEntity attacker && source.getEntity() == attacker) {
            attacker.knockback(0.8, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
        }
        if (Perk.REFLECT.has(player) && source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getOwner() instanceof LivingEntity shooter
                && shooter != player) {
            NEXT_TICK.add(() -> {
                if (arrow.isAlive() && shooter.isAlive()) {
                    Vec3 aim = shooter.getEyePosition().subtract(arrow.position());
                    arrow.setOwner(player);
                    arrow.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.1, aim.z, 1.6f, 1.0f);
                }
            });
        }
    }

    // --- Using items: shields and crossbows ---

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isShiftKeyDown() && event.getItem().getItem() instanceof ShieldItem) {
            Abilities.tryStart(player, Perk.SHIELD_WALL);
        }
    }

    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack.getItem() instanceof ShieldItem && Perk.QUICK_GUARD.has(player)) {
            int full = stack.getUseDuration(player);
            if (full - event.getDuration() < 5) {
                event.setDuration(full - 5); // A shield blocks once it's been up 5 ticks.
            }
        } else if (stack.getItem() instanceof CrossbowItem && !CrossbowItem.isCharged(stack)) {
            if (Abilities.active(player, Perk.RAPID_FIRE)) {
                event.setDuration(Math.min(event.getDuration(), 3)); // Charged once 3 ticks are left (CrossbowItem#getUseDuration).
            } else if (Perk.QUICK_RELOAD.has(player) && player.tickCount % 2 == 0) {
                event.setDuration(event.getDuration() - 1);
            }
        }
    }

    // --- Arrows as they're loosed ---

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || splashing || !(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof ServerPlayer player)
                || arrow.tickCount > 0) {
            return;
        }
        ItemStack weapon = arrow.getWeaponItem();
        if (weapon == null) {
            return;
        }
        if (weapon.getItem() instanceof CrossbowItem) {
            if (player.isShiftKeyDown()) {
                Abilities.tryStart(player, Perk.RAPID_FIRE);
            }
            if (Perk.BOLT_PIERCE.has(player)) {
                arrow.setPierceLevel((byte) (arrow.getPierceLevel() + 1));
            }
            return;
        }
        if (!(weapon.getItem() instanceof BowItem)) {
            return;
        }
        if (Perk.STEADY_AIM.has(player)) {
            Vec3 velocity = arrow.getDeltaMovement();
            Vec3 aimed = player.getLookAngle().scale(velocity.length());
            arrow.setDeltaMovement(velocity.add(aimed).scale(0.5).normalize().scale(velocity.length()));
        }
        if (Perk.PIERCING.has(player)) {
            arrow.setPierceLevel((byte) (arrow.getPierceLevel() + 1));
        }
        if (player.isShiftKeyDown()) {
            Abilities.tryStart(player, Perk.VOLLEY);
        }
        if (Abilities.active(player, Perk.VOLLEY)) {
            ItemStack ammo = arrow.getPickupItemStackOrigin().copyWithCount(1);
            Vec3 velocity = arrow.getDeltaMovement();
            NEXT_TICK.add(() -> {
                for (float angle : new float[] { -0.14f, 0.14f }) {
                    Arrow extra = new Arrow(player.level(), player, ammo, weapon);
                    extra.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                    extra.setDeltaMovement(velocity.yRot(angle));
                    extra.setCritArrow(arrow.isCritArrow());
                    extra.setBaseDamage(arrow.getBaseDamage());
                    extra.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                    splashing = true; // Not another volley of its own.
                    try {
                        player.level().addFreshEntity(extra);
                    } finally {
                        splashing = false;
                    }
                }
            });
        }
    }

    // --- Light and heavy armor ---

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && event.getDistance() < 6 && Perk.LIGHT_FOOTED.has(player)
                && ActivitySkills.wears(player, ArmorWeight.LIGHT)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (event.getEntity() instanceof Player player && Perk.SILENT_STEP.has(player) && ActivitySkills.wears(player, ArmorWeight.LIGHT)) {
            event.modifyVisibility(0.7);
        }
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player && event.getEffectInstance().getEffect().is(MobEffects.MOVEMENT_SLOWDOWN)
                && Perk.UNSHAKEN.has(player) && ActivitySkills.wears(player, ArmorWeight.HEAVY)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    // --- Each tick: bleeding, and anything put off a tick ---

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!NEXT_TICK.isEmpty()) {
            List<Runnable> due = List.copyOf(NEXT_TICK);
            NEXT_TICK.clear();
            due.forEach(Runnable::run);
        }
        if (BLEEDING.isEmpty()) {
            return;
        }
        var it = BLEEDING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            LivingEntity entity = entry.getKey();
            int left = entry.getValue() - 1;
            if (!entity.isAlive() || left <= 0) {
                it.remove();
                continue;
            }
            entry.setValue(left);
            if (left % 20 == 0) {
                entity.hurt(entity.damageSources().generic(), 1);
                if (entity.level() instanceof ServerLevel level) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                            entity.getX(), entity.getY(0.6), entity.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }
}
