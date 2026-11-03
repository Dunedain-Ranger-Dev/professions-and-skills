package io.github.ranger3462.ddprofessions.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.ranger3462.ddprofessions.beast.BardingItem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.AnimalArmorItem;

/**
 * Lets our wolf barding (PROF-6) count as wolf armor: vanilla only counts {@code minecraft:wolf_armor}, and that one check
 * decides whether the armor takes the wolf's damage, shows on the wolf, and can be sheared off or repaired.
 */
@Mixin(Wolf.class)
public abstract class WolfMixin {
    @Inject(method = "hasArmor", at = @At("HEAD"), cancellable = true)
    private void ddprofessions$barding(CallbackInfoReturnable<Boolean> cir) {
        if (((Wolf) (Object) this).getBodyArmorItem().getItem() instanceof BardingItem barding
                && barding.getBodyType() == AnimalArmorItem.BodyType.CANINE) {
            cir.setReturnValue(true);
        }
    }
}
