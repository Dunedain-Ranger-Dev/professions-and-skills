package io.github.ranger3462.ddprofessions.beast;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.character.SkillLevels;
import io.github.ranger3462.ddprofessions.forging.Forging;
import io.github.ranger3462.ddprofessions.integration.skills.PufferfishSkills;
import io.github.ranger3462.ddprofessions.skill.ActivitySkills;
import io.github.ranger3462.ddprofessions.skill.Perk;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Beastmastery (PROF-6, docs/design/beastmastery.md): the skill of fighting alongside tames.
 * <ul>
 *   <li>A tame is a mob in {@link #PETS} with an owner. Iron's and Ars summons aren't in it.</li>
 *   <li>Experience comes from taming (by the mob, {@link #tamingXp}), breeding, and your tames' hits, which train it like
 *   a combat style does, while you're within {@link #TRAINING_RANGE}.</li>
 *   <li>The tree's nodes ({@link Perk}) raise your tames' stats. The bonuses are kept on the tame itself, so they stay
 *   while you're offline, and are brought up to date whenever you're near it.</li>
 * </ul>
 * The companion, the barding and the collars are in {@link Companions}, {@link BardingItem} and {@link Collars}.
 */
@EventBusSubscriber(modid = ProfessionsAndSkills.MODID)
public final class Beastmastery {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
            ProfessionsAndSkills.MODID);
    /** Set on a tame once its owner has had the taming experience for it, so each tame pays once. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> TAMING_CREDITED = ATTACHMENTS.register("taming_credited",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());

    public static final TagKey<EntityType<?>> PETS = tag("beastmastery_pets");
    /** Taming experience by tier, from entity tags so the pack can move a mob between them; the first tag that has it wins. */
    public static final List<TamingTier> TAMING_TIERS = List.of(new TamingTier(tag("taming_xp/dragon"), 400), new TamingTier(tag("taming_xp/great"), 150),
            new TamingTier(tag("taming_xp/mount"), 60), new TamingTier(tag("taming_xp/small"), 30));
    /** Taming experience for a tame in no tier (a wolf). */
    public static final int DEFAULT_TAMING_XP = 40;
    public static final int BREED_XP = 10;
    /** How near its owner a tame has to be to train them, or to have its bonuses brought up to date. */
    public static final double TRAINING_RANGE = 32;
    /** How near its owner a tame has to be to count as following them (into a dungeon, or for Rally). */
    public static final double FOLLOW_RANGE = 16;
    /** How often each player's tames are checked, in ticks. */
    private static final int SCAN_INTERVAL = 20;
    /** Mender: how long a tame has to go unhurt before it heals, in ticks, and how much it heals each scan. */
    static final int MENDER_DELAY = 100;
    static final float MENDER_HEAL = 1.0f;
    /** Rally's Speed and Strength, in ticks. */
    static final int RALLY_DURATION = 100;

    /** The tree's bonuses to a tame. */
    public static final double ANIMAL_FRIEND_HEALTH = 0.10;
    public static final double PACK_LEADER_DAMAGE = 0.10;
    public static final double THICK_HIDE_ARMOR = 2;
    public static final double VITALITY_HEALTH = 0.03;
    public static final double FEROCITY_DAMAGE = 0.03;
    /** Where the bonuses are kept on a tame's attributes. */
    private static final ResourceLocation BONUS = ProfessionsAndSkills.id("beastmastery");

    /** The best barding or collar ★ each Beastmastery level allows: ★2 to start, ★3 at 10, up to ★7 at 50. */
    public static final int[] STAR_LEVELS = { 0, 0, 10, 20, 30, 40, 50 };

    public record TamingTier(TagKey<EntityType<?>> tag, int xp) {}

    private Beastmastery() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, ProfessionsAndSkills.id(path));
    }

    /** Whether this is a tame: a mob in {@link #PETS} with an owner. */
    public static boolean isTame(Entity entity) {
        return entity.getType().is(PETS) && entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
    }

    /** Whether this is one of this player's tames. */
    public static boolean isTameOf(Entity entity, Player player) {
        return isTame(entity) && player.getUUID().equals(((OwnableEntity) entity).getOwnerUUID());
    }

    /** A tame's owner, if they're online. */
    public static Optional<ServerPlayer> owner(Entity entity) {
        if (isTame(entity) && entity.level().getServer() != null) {
            return Optional.ofNullable(entity.level().getServer().getPlayerList().getPlayer(((OwnableEntity) entity).getOwnerUUID()));
        }
        return Optional.empty();
    }

    /** Experience for taming a mob of this kind. */
    public static int tamingXp(EntityType<?> type) {
        for (TamingTier tier : TAMING_TIERS) {
            if (type.is(tier.tag())) {
                return tier.xp();
            }
        }
        return DEFAULT_TAMING_XP;
    }

    /** Gives the owner their taming experience for this tame, once. */
    public static void creditTaming(Entity tame, ServerPlayer owner) {
        if (!tame.getData(TAMING_CREDITED)) {
            tame.setData(TAMING_CREDITED, true);
            ActivitySkills.award(owner, ActivitySkills.BEASTMASTERY, tamingXp(tame.getType()));
        }
    }

    /** A player's Beastmastery level, from 0 (0 without Pufferfish's Skills). */
    public static int level(ServerPlayer player) {
        return SkillLevels.pufferfish() ? PufferfishSkills.level(player, ProfessionsAndSkills.id(ActivitySkills.BEASTMASTERY)).orElse(0) : 0;
    }

    /**
     * The best barding or collar ★ a player can craft, by their Beastmastery level ({@link #STAR_LEVELS}). Without
     * Pufferfish's Skills there's no Beastmastery level, so no cap.
     */
    public static int starCap(Player player) {
        if (!SkillLevels.pufferfish() || !(player instanceof ServerPlayer serverPlayer)) {
            return Forging.MAX_STARS;
        }
        int level = level(serverPlayer);
        int stars = 1;
        while (stars < Forging.MAX_STARS && STAR_LEVELS[stars] <= level) {
            stars++;
        }
        return stars;
    }

    /** The Beastmastery level needed to craft this ★ of barding or collar. */
    public static int levelFor(int stars) {
        return STAR_LEVELS[Math.clamp(stars, 1, Forging.MAX_STARS) - 1];
    }

    /** Whether Beastmastery caps this crafted item's ★: barding and collars. */
    public static boolean capsStars(ItemStack crafted) {
        return crafted.getItem() instanceof BardingItem || crafted.is(BeastItems.COLLAR.get());
    }

    /**
     * The player's tames that are following them, within {@link #FOLLOW_RANGE}: tames that aren't sitting, and any tame
     * on the player's lead or carrying them. For Dungeon Delving to take into its dungeons (DD-118).
     */
    public static List<Mob> followingTames(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FOLLOW_RANGE),
                mob -> isTameOf(mob, player) && mob.isAlive() && isFollowing(mob, player));
    }

    private static boolean isFollowing(Mob mob, ServerPlayer player) {
        if (mob.getLeashHolder() == player || player.getVehicle() == mob) {
            return true;
        }
        return mob instanceof TamableAnimal tamable && !tamable.isOrderedToSit();
    }

    /** Brings a tame's bonuses up to date with its owner's tree (twice over for an Alpha's companion). */
    public static void refreshBonuses(LivingEntity tame, ServerPlayer owner) {
        double times = Perk.ALPHA.has(owner) && Companions.isCompanion(owner, tame) ? 2 : 1;
        double health = (Perk.ANIMAL_FRIEND.has(owner) ? ANIMAL_FRIEND_HEALTH : 0) + Perk.PET_VITALITY.rank(owner) * VITALITY_HEALTH;
        double damage = (Perk.PACK_LEADER.has(owner) ? PACK_LEADER_DAMAGE : 0) + Perk.PET_FEROCITY.rank(owner) * FEROCITY_DAMAGE;
        double armor = Perk.THICK_HIDE.has(owner) ? THICK_HIDE_ARMOR : 0;
        setModifier(tame, Attributes.MAX_HEALTH, BONUS, health * times, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(tame, Attributes.ATTACK_DAMAGE, BONUS, damage * times, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(tame, Attributes.ARMOR, BONUS, armor * times, AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * Sets a lasting modifier on a mob's attribute (0 removes it), only touching it if it changed. Lasting, so it's saved
     * with the mob. Health is kept within a lowered maximum.
     */
    static void setModifier(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0) {
            if (current != null) {
                instance.removeModifier(id);
            }
        } else if (current == null || current.amount() != amount || current.operation() != operation) {
            instance.removeModifier(id);
            instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
        }
        if (entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    /** Each second: the player's tames nearby get their bonuses brought up to date, Mender's healing, and taming credit. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % SCAN_INTERVAL != 0) {
            return;
        }
        for (Mob tame : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(TRAINING_RANGE),
                mob -> isTameOf(mob, player) && mob.isAlive())) {
            // Tames that didn't come through the vanilla taming event (Ice and Fire's hatched dragons) pay out here.
            creditTaming(tame, player);
            refreshBonuses(tame, player);
            Companions.seen(player, tame);
            if (Perk.MENDER.has(player) && tame.getHealth() < tame.getMaxHealth()
                    && tame.tickCount - tame.getLastHurtByMobTimestamp() > MENDER_DELAY) {
                tame.heal(MENDER_HEAL);
            }
        }
    }

    @SubscribeEvent
    public static void onTame(AnimalTameEvent event) {
        if (event.getTamer() instanceof ServerPlayer player && event.getAnimal().getType().is(PETS)) {
            creditTaming(event.getAnimal(), player);
        }
    }

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent event) {
        if (event.getCausedByPlayer() instanceof ServerPlayer player) {
            ActivitySkills.award(player, ActivitySkills.BEASTMASTERY, BREED_XP);
        }
        if (event.getChild() != null) {
            event.getChild().setData(TAMING_CREDITED, true); // Born tame, not tamed: breeding paid for it.
        }
    }

    /** A tame's hits train its owner, like a combat style (scaled against weak mobs); and Rally, when its owner is hit. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        float amount = event.getNewDamage();
        LivingEntity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (amount <= 0 || attacker == null) {
            return;
        }
        owner(attacker).ifPresent(owner -> trainFromHit(owner, attacker, target, amount));
        if (target instanceof ServerPlayer player && attacker instanceof LivingEntity && attacker != player && Perk.RALLY.has(player)) {
            for (Mob tame : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FOLLOW_RANGE),
                    mob -> isTameOf(mob, player) && mob.isAlive())) {
                tame.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, RALLY_DURATION));
                tame.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, RALLY_DURATION));
            }
        }
    }

    /**
     * A tame's hit trains its owner's Beastmastery, if the owner is near it: not hits on the owner or their other tames.
     * Returns whether it did.
     */
    public static boolean trainFromHit(ServerPlayer owner, Entity tame, LivingEntity target, float amount) {
        if (owner == target || isTameOf(target, owner) || owner.level() != tame.level() || owner.distanceTo(tame) > TRAINING_RANGE) {
            return false;
        }
        ActivitySkills.awardAgainst(owner, ActivitySkills.BEASTMASTERY, ActivitySkills.perHit(amount), target);
        return true;
    }

    /** Right-clicking your own grown wolf with wolf barding puts it on (a wolf's own click only takes vanilla wolf armor). */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getTarget() instanceof Wolf wolf) || !(held.getItem() instanceof BardingItem barding)
                || barding.getBodyType() != AnimalArmorItem.BodyType.CANINE || !wolf.isOwnedBy(player) || !wolf.getBodyArmorItem().isEmpty()
                || wolf.isBaby()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!player.level().isClientSide) {
            wolf.setBodyArmorItem(held.copyWithCount(1));
            held.consume(1, player);
        }
    }

    /** The entity with this UUID, wherever it's loaded. */
    static Optional<Entity> find(net.minecraft.server.MinecraftServer server, UUID id) {
        for (var level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }
}
