package io.github.ranger3462.ddprofessions.beast;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.forging.Forging;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Collars for the tames that can't wear barding (PROF-6): cats and parrots ({@link #WEARS_COLLARS}). Right-click your own
 * with a collar to put it on; sneak and right-click it with an empty hand to take it off. A collar gives +1 max health
 * per ★, and +1 armor from ★4. It's kept on the tame (so a companion comes back with it), and drops when the tame dies.
 */
@EventBusSubscriber(modid = ProfessionsAndSkills.MODID)
public final class Collars {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            ProfessionsAndSkills.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ItemStack>> COLLAR = ATTACHMENTS.register("collar",
            () -> AttachmentType.builder(() -> ItemStack.EMPTY).serialize(ItemStack.OPTIONAL_CODEC).build());
    public static final TagKey<EntityType<?>> WEARS_COLLARS = TagKey.create(Registries.ENTITY_TYPE, ProfessionsAndSkills.id("wears_collars"));
    public static final double HEALTH_PER_STAR = 1;
    public static final int ARMOR_FROM_STARS = 4;
    private static final ResourceLocation BONUS = ProfessionsAndSkills.id("collar");

    private Collars() {}

    static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    public static ItemStack collar(LivingEntity tame) {
        return tame.getData(COLLAR);
    }

    /** Puts a collar on a tame (one of it), replacing none. */
    public static void putOn(LivingEntity tame, ItemStack collar) {
        tame.setData(COLLAR, collar.copyWithCount(1));
        applyStats(tame);
    }

    /** Takes a tame's collar off and returns it. */
    public static ItemStack takeOff(LivingEntity tame) {
        ItemStack collar = collar(tame);
        tame.setData(COLLAR, ItemStack.EMPTY);
        applyStats(tame);
        return collar;
    }

    /** Sets the tame's collar bonuses for the collar it's wearing (none without one). */
    public static void applyStats(LivingEntity tame) {
        ItemStack collar = collar(tame);
        int stars = collar.isEmpty() ? 0 : Math.max(1, Forging.stars(collar));
        Beastmastery.setModifier(tame, Attributes.MAX_HEALTH, BONUS, stars * HEALTH_PER_STAR, AttributeModifier.Operation.ADD_VALUE);
        Beastmastery.setModifier(tame, Attributes.ARMOR, BONUS, stars >= ARMOR_FROM_STARS ? 1 : 0, AttributeModifier.Operation.ADD_VALUE);
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof LivingEntity tame)
                || !tame.getType().is(WEARS_COLLARS) || !Beastmastery.isTameOf(tame, player)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.is(BeastItems.COLLAR.get())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (!player.level().isClientSide && collar(tame).isEmpty()) {
                putOn(tame, held);
                held.consume(1, player);
                tame.playSound(SoundEvents.ARMOR_EQUIP_LEATHER.value());
            }
        } else if (held.isEmpty() && player.isShiftKeyDown() && !player.level().isClientSide && !collar(tame).isEmpty()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            ItemStack collar = takeOff(tame);
            if (!player.addItem(collar)) {
                player.drop(collar, false);
            }
            player.displayClientMessage(Component.translatable("message.ddprofessions.collar.removed", tame.getDisplayName()), true);
        }
    }

    /** A collar drops with its tame, unless the tame is a companion, which keeps it to come back with. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity tame = event.getEntity();
        if (!collar(tame).isEmpty() && !Companions.isCompanion(tame)) {
            event.getDrops().add(new ItemEntity(tame.level(), tame.getX(), tame.getY(), tame.getZ(), takeOff(tame)));
        }
    }
}
