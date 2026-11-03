package io.github.ranger3462.ddprofessions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/** A mob's goals, which vanilla keeps to itself: for arming tames that don't fight on their own (PROF-6). */
@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("goalSelector")
    GoalSelector ddprofessions$goalSelector();

    @Accessor("targetSelector")
    GoalSelector ddprofessions$targetSelector();
}
