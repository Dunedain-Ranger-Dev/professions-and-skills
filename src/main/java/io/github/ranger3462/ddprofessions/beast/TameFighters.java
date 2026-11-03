package io.github.ranger3462.ddprofessions.beast;

import java.util.Map;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.mixin.MobAccessor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Tames that fight for their owner (PROF-6), so it's not only wolves that pull their weight: any tame in
 * {@link #TAME_FIGHTERS} (cats, parrots and Critters and Companions' small pets) attacks what hurts its owner and what its
 * owner attacks, like a wolf. Goals a tame already has are left alone, so the stag beetle and the like keep their own.
 * The ones with no attack of their own get a small one ({@link #ATTACK_DAMAGE}), which Beastmastery's damage bonuses
 * then raise like a wolf's; a tame with no attack at all (the weevil, which spits) isn't given melee.
 */
@EventBusSubscriber(modid = ProfessionsAndSkills.MODID)
public final class TameFighters {
    public static final TagKey<EntityType<?>> TAME_FIGHTERS = TagKey.create(Registries.ENTITY_TYPE, ProfessionsAndSkills.id("tame_fighters"));
    /**
     * Attack damage for the fighters that have none of their own (a wolf's is 4; cats' and parrots' 3, from vanilla), only
     * added where it's missing.
     */
    static final Map<ResourceLocation, Double> ATTACK_DAMAGE = Map.of(
            critter("red_panda"), 3.0,
            critter("roly_poly"), 1.5,
            critter("stick_bug"), 1.5,
            critter("ladybug"), 1.0,
            critter("shima_enaga"), 1.0,
            critter("snail"), 1.0);
    /** How fast a tame chases what it's attacking, against its walking speed. */
    static final double CHASE_SPEED = 1.25;

    private TameFighters() {}

    private static ResourceLocation critter(String path) {
        return ResourceLocation.fromNamespaceAndPath("crittersandcompanions", path);
    }

    /** Gives the fighters with no attack one (on the mod bus; see {@link BeastItems#register}). */
    @SuppressWarnings("unchecked")
    static void addAttackDamage(EntityAttributeModificationEvent event) {
        ATTACK_DAMAGE.forEach((id, damage) -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(type -> {
            EntityType<? extends LivingEntity> living = (EntityType<? extends LivingEntity>) type;
            if (event.getTypes().contains(living) && !event.has(living, Attributes.ATTACK_DAMAGE)) {
                event.add(living, Attributes.ATTACK_DAMAGE, damage);
            }
        }));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof TamableAnimal tame && tame.getType().is(TAME_FIGHTERS)) {
            arm(tame);
        }
    }

    /** Gives a tame the wolf's fighting goals it hasn't got. The owner goals only act once it's tamed. */
    public static void arm(TamableAnimal tame) {
        if (tame.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
            return; // Nothing to hit with.
        }
        GoalSelector goals = ((MobAccessor) tame).ddprofessions$goalSelector();
        GoalSelector targets = ((MobAccessor) tame).ddprofessions$targetSelector();
        if (!has(targets, OwnerHurtByTargetGoal.class)) {
            targets.addGoal(1, new OwnerHurtByTargetGoal(tame));
        }
        if (!has(targets, OwnerHurtTargetGoal.class)) {
            targets.addGoal(2, new OwnerHurtTargetGoal(tame));
        }
        if (!has(goals, MeleeAttackGoal.class)) {
            goals.addGoal(3, new MeleeAttackGoal(tame, CHASE_SPEED, true));
        }
    }

    /** Whether a mob has a goal of this kind. */
    public static boolean has(GoalSelector selector, Class<? extends Goal> kind) {
        return selector.getAvailableGoals().stream().anyMatch(wrapped -> kind.isInstance(wrapped.getGoal()));
    }

    /** Whether a tame has a goal of this kind, among its goals or its target goals. */
    public static boolean hasGoal(TamableAnimal tame, Class<? extends Goal> kind) {
        return has(((MobAccessor) tame).ddprofessions$goalSelector(), kind) || has(((MobAccessor) tame).ddprofessions$targetSelector(), kind);
    }
}
