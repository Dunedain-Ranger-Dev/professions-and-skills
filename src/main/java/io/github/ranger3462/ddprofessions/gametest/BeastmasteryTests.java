package io.github.ranger3462.ddprofessions.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.beast.BardingItem;
import io.github.ranger3462.ddprofessions.beast.BeastItems;
import io.github.ranger3462.ddprofessions.beast.Beastmastery;
import io.github.ranger3462.ddprofessions.beast.Collars;
import io.github.ranger3462.ddprofessions.beast.Companions;
import io.github.ranger3462.ddprofessions.beast.TameFighters;
import io.github.ranger3462.ddprofessions.character.SkillLevels;
import io.github.ranger3462.ddprofessions.forging.Forging;
import io.github.ranger3462.ddprofessions.integration.skills.PufferfishSkills;
import io.github.ranger3462.ddprofessions.skill.ActivitySkills;
import io.github.ranger3462.ddprofessions.skill.Perk;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks Beastmastery (PROF-6): tames' XP and bonuses, barding and collars, and the companion. */
@GameTestHolder(ProfessionsAndSkills.MODID)
@PrefixGameTestTemplate(false)
public class BeastmasteryTests {
    private static final String ANY_TEMPLATE = "test/empty";

    @GameTest(template = ANY_TEMPLATE)
    public static void tamingPaysByTheMob(GameTestHelper helper) {
        helper.assertTrue(Beastmastery.tamingXp(EntityType.CAT) == 30, "a cat pays 30");
        helper.assertTrue(Beastmastery.tamingXp(EntityType.WOLF) == 40, "a wolf pays 40");
        helper.assertTrue(Beastmastery.tamingXp(EntityType.HORSE) == 60, "a horse pays 60");
        ServerPlayer player = player(helper, "tamer");
        Wolf wild = EntityType.WOLF.create(helper.getLevel());
        helper.assertTrue(!Beastmastery.isTame(wild), "a wild wolf isn't a tame");
        Wolf tame = tame(helper, EntityType.WOLF, player, 0);
        try {
            helper.assertTrue(Beastmastery.isTameOf(tame, player), "a wolf with an owner is their tame");
            if (SkillLevels.pufferfish()) {
                int before = total(player);
                Beastmastery.creditTaming(tame, player);
                Beastmastery.creditTaming(tame, player);
                helper.assertTrue(total(player) == before + 40, "taming pays once per tame");
            }
        } finally {
            tame.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void tamesHitsTrainTheirOwner(GameTestHelper helper) {
        if (!SkillLevels.pufferfish()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper, "packleader");
        Wolf wolf = tame(helper, EntityType.WOLF, player, 0);
        Pig pig = EntityType.PIG.create(helper.getLevel());
        pig.moveTo(wolf.getX() + 1, wolf.getY(), wolf.getZ());
        helper.getLevel().addFreshEntity(pig);
        try {
            int before = total(player);
            helper.assertTrue(Beastmastery.trainFromHit(player, wolf, pig, 4.0f) && total(player) == before + 4, "a 4-damage bite gives 4 XP");
            helper.assertTrue(!Beastmastery.trainFromHit(player, wolf, player, 4.0f), "biting its owner trains nothing");
            player.moveTo(wolf.getX() + 100, wolf.getY(), wolf.getZ());
            helper.assertTrue(!Beastmastery.trainFromHit(player, wolf, pig, 4.0f), "nothing while the owner is far away");
        } finally {
            wolf.discard();
            pig.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void tamesGetTheTreesBonuses(GameTestHelper helper) {
        ServerPlayer player = player(helper, "beastmaster");
        Wolf wolf = tame(helper, EntityType.WOLF, player, 0);
        try {
            double baseHealth = wolf.getAttributeBaseValue(Attributes.MAX_HEALTH);
            double baseArmor = wolf.getAttributeValue(Attributes.ARMOR);
            for (String tag : new String[] { Perk.ANIMAL_FRIEND.tag(1), Perk.THICK_HIDE.tag(1), Perk.PET_VITALITY.tag(1), Perk.PET_VITALITY.tag(2) }) {
                player.addTag(tag);
            }
            Beastmastery.refreshBonuses(wolf, player);
            double expected = baseHealth * (1 + Beastmastery.ANIMAL_FRIEND_HEALTH + 2 * Beastmastery.VITALITY_HEALTH);
            helper.assertTrue(Math.abs(wolf.getMaxHealth() - expected) < 0.01, "Animal Friend and Vitality II raise max health, got " + wolf.getMaxHealth());
            helper.assertTrue(wolf.getAttributeValue(Attributes.ARMOR) == baseArmor + Beastmastery.THICK_HIDE_ARMOR, "Thick Hide adds armor");
            player.getTags().clear();
            Beastmastery.refreshBonuses(wolf, player);
            helper.assertTrue(wolf.getMaxHealth() == baseHealth && wolf.getAttributeValue(Attributes.ARMOR) == baseArmor, "a respec takes them away");
        } finally {
            wolf.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void followingTamesLeaveSittersBehind(GameTestHelper helper) {
        ServerPlayer player = player(helper, "walker");
        Wolf follower = tame(helper, EntityType.WOLF, player, 0);
        Wolf sitter = tame(helper, EntityType.WOLF, player, 2);
        Wolf faraway = tame(helper, EntityType.WOLF, player, 20);
        sitter.setOrderedToSit(true);
        try {
            var following = Beastmastery.followingTames(player);
            helper.assertTrue(following.contains(follower) && !following.contains(sitter) && !following.contains(faraway),
                    "only the standing wolf nearby is following, got " + following);
        } finally {
            follower.discard();
            sitter.discard();
            faraway.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void bardingScalesWithStars(GameTestHelper helper) {
        BardingItem wolfPlate = BeastItems.WOLF_BARDING.get(BardingItem.Kind.PLATE).get();
        BardingItem horseLeather = BeastItems.HORSE_BARDING.get(BardingItem.Kind.LEATHER).get();
        helper.assertTrue(horseLeather.armor(1) == 3 && wolfPlate.armor(7) == 15, "leather ★1 is 3 armor, plate ★7 is 15");
        helper.assertTrue(wolfPlate.wolfDurability(1) == 128 && wolfPlate.wolfDurability(5) == 256, "plate wolf barding doubles its durability by ★5");
        ItemStack barding = new ItemStack(wolfPlate);
        barding.set(Forging.STARS.get(), 3);
        wolfPlate.applyStats(barding);
        helper.assertTrue(barding.getMaxDamage() == wolfPlate.wolfDurability(3), "crafting sets a wolf's barding's durability by ★");
        helper.assertTrue(Beastmastery.levelFor(2) == 0 && Beastmastery.levelFor(3) == 10 && Beastmastery.levelFor(7) == 50, "★3 needs 10, ★7 needs 50");
        if (SkillLevels.pufferfish()) {
            ServerPlayer player = player(helper, "barder");
            helper.assertTrue(Beastmastery.starCap(player) == 2, "a new beastmaster can make ★2");
            PufferfishSkills.setTotal(player, ProfessionsAndSkills.id(ActivitySkills.BEASTMASTERY), 5350); // Level 20.
            helper.assertTrue(Beastmastery.level(player) == 20 && Beastmastery.starCap(player) == 4, "level 20 can make ★4, got level "
                    + Beastmastery.level(player));
        }
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        wolf.setBodyArmorItem(barding);
        helper.assertTrue(wolf.hasArmor(), "a wolf wearing our barding counts as armored");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void collarsGiveHealthAndArmor(GameTestHelper helper) {
        ServerPlayer player = player(helper, "catlady");
        Cat cat = tame(helper, EntityType.CAT, player, 0);
        try {
            double health = cat.getMaxHealth();
            double armor = cat.getAttributeValue(Attributes.ARMOR);
            ItemStack collar = new ItemStack(BeastItems.COLLAR.get());
            collar.set(Forging.STARS.get(), 4);
            Collars.putOn(cat, collar);
            helper.assertTrue(cat.getMaxHealth() == health + 4 && cat.getAttributeValue(Attributes.ARMOR) == armor + 1, "a ★4 collar: +4 health, +1 armor");
            ItemStack off = Collars.takeOff(cat);
            helper.assertTrue(Forging.stars(off) == 4 && cat.getMaxHealth() == health && cat.getAttributeValue(Attributes.ARMOR) == armor,
                    "taking it off gives it back and takes the bonuses away");
        } finally {
            cat.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void companionComesBackWhole(GameTestHelper helper) {
        ServerPlayer player = player(helper, "ranger");
        player.addTag(Perk.COMPANION_BOND.tag(3));
        Wolf wolf = tame(helper, EntityType.WOLF, player, 0);
        UUID id = wolf.getUUID();
        ItemStack barding = new ItemStack(BeastItems.WOLF_BARDING.get(BardingItem.Kind.MAIL).get());
        barding.set(Forging.STARS.get(), 3);
        wolf.setBodyArmorItem(barding);
        wolf.setCustomName(Component.literal("Fang"));
        Entity back = null;
        try {
            helper.assertTrue(Companions.bond(player, wolf), "bonding needs Companion Bond and a tame of your own");
            wolf.die(helper.getLevel().damageSources().generic());
            helper.assertTrue(Companions.isDead(player), "a fallen companion is saved");
            AABB around = wolf.getBoundingBox().inflate(4);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, around, item -> item.getItem().getItem() instanceof BardingItem).isEmpty(),
                    "its barding doesn't drop");
            wolf.discard(); // A dead mob lingers for a second; calling it back comes after that.
            helper.assertTrue(Companions.call(player), "calling brings it back");
            back = helper.getLevel().getEntity(id);
            helper.assertTrue(back instanceof Wolf, "it comes back as the same wolf");
            Wolf returned = (Wolf) back;
            helper.assertTrue(returned.hasCustomName() && "Fang".equals(returned.getCustomName().getString()), "with its name");
            helper.assertTrue(Forging.stars(returned.getBodyArmorItem()) == 3, "with its ★3 barding, got " + returned.getBodyArmorItem());
            // Fake players aren't in the world's player list, so compare the owner's UUID rather than isOwnedBy.
            helper.assertTrue(player.getUUID().equals(returned.getOwnerUUID()), "and its owner");
            helper.assertTrue(returned.getHealth() == returned.getMaxHealth(), "at full health, with Companion Bond III");
            helper.assertTrue(Companions.cooldown(player) == Companions.COOLDOWN[2] && !Companions.canCall(player), "then a 2 minute cooldown");
        } finally {
            wolf.discard();
            if (back != null) {
                back.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void callingFetchesALivingCompanion(GameTestHelper helper) {
        ServerPlayer player = player(helper, "shepherd");
        player.addTag(Perk.COMPANION_BOND.tag(1));
        Wolf wolf = tame(helper, EntityType.WOLF, player, 0);
        try {
            Companions.bond(player, wolf);
            player.moveTo(wolf.getX() + 30, wolf.getY(), wolf.getZ());
            helper.assertTrue(Companions.call(player) && wolf.distanceTo(player) < 2, "it comes to you, got " + wolf.distanceTo(player));
            helper.assertTrue(Companions.cooldown(player) == Companions.COOLDOWN[0], "a 10 minute cooldown at Companion Bond I");
        } finally {
            wolf.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    @SuppressWarnings("unchecked")
    public static void passiveTamesFightForTheirOwner(GameTestHelper helper) {
        ServerPlayer player = player(helper, "menagerie");
        Cat cat = tame(helper, EntityType.CAT, player, 0);
        Parrot parrot = tame(helper, EntityType.PARROT, player, 1);
        java.util.List<TamableAnimal> made = new java.util.ArrayList<>(java.util.List.of(cat, parrot));
        try {
            for (TamableAnimal tame : java.util.List.of(cat, parrot)) {
                helper.assertTrue(TameFighters.hasGoal(tame, OwnerHurtByTargetGoal.class) && TameFighters.hasGoal(tame, OwnerHurtTargetGoal.class)
                        && TameFighters.hasGoal(tame, MeleeAttackGoal.class), "a tame " + tame.getType().getDescriptionId() + " defends its owner");
            }
            helper.assertTrue(parrot.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0, "a parrot can peck");
            // Critters and Companions, when it's installed: a passive red panda gets an attack, and a stag beetle keeps its own goals.
            var panda = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("crittersandcompanions", "red_panda"));
            var beetle = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("crittersandcompanions", "stag_beetle"));
            if (panda.isPresent() && beetle.isPresent()) {
                TamableAnimal redPanda = tame(helper, (EntityType<TamableAnimal>) panda.get(), player, 2);
                TamableAnimal stagBeetle = tame(helper, (EntityType<TamableAnimal>) beetle.get(), player, 3);
                made.add(redPanda);
                made.add(stagBeetle);
                helper.assertTrue(Beastmastery.isTameOf(redPanda, player) && redPanda.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) == 3.0
                        && TameFighters.hasGoal(redPanda, MeleeAttackGoal.class), "a tame red panda counts and fights");
                long ownerGoals = ((io.github.ranger3462.ddprofessions.mixin.MobAccessor) stagBeetle).ddprofessions$targetSelector().getAvailableGoals()
                        .stream().filter(goal -> goal.getGoal() instanceof OwnerHurtByTargetGoal).count();
                helper.assertTrue(ownerGoals == 1, "a stag beetle's own owner goal isn't doubled, got " + ownerGoals);
            }
        } finally {
            made.forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** Silk Sizing takes Critters and Companions' Silk (it's only there with that mod and Farmer's Delight). */
    @GameTest(template = ANY_TEMPLATE)
    public static void silkSizingTakesCrittersSilk(GameTestHelper helper) {
        var silk = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("crittersandcompanions", "silk"));
        if (silk.isEmpty() || !net.neoforged.fml.ModList.get().isLoaded("farmersdelight")) {
            helper.succeed();
            return;
        }
        helper.assertTrue(new ItemStack(silk.get()).is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                ProfessionsAndSkills.id("silk"))), "Critters and Companions' Silk is in the silk tag");
        var recipe = helper.getLevel().getRecipeManager().byKey(ProfessionsAndSkills.id("silk_sizing"));
        helper.assertTrue(recipe.isPresent() && recipe.get().value().getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(silk.get()))),
                "Silk Sizing loads and takes the silk");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos pos = helper.absolutePos(BlockPos.ZERO).above(100);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return player;
    }

    /** A tame of this player's, this many blocks east of them. */
    private static <T extends TamableAnimal> T tame(GameTestHelper helper, EntityType<T> type, ServerPlayer owner, int east) {
        ServerLevel level = helper.getLevel();
        T tame = type.create(level);
        tame.setTame(true, true);
        tame.setOwnerUUID(owner.getUUID());
        tame.moveTo(owner.getX() + east, owner.getY(), owner.getZ());
        tame.setNoAi(true); // Stays where it's put.
        level.addFreshEntity(tame);
        return tame;
    }

    private static int total(ServerPlayer player) {
        return PufferfishSkills.total(player, ProfessionsAndSkills.id(ActivitySkills.BEASTMASTERY)).orElseThrow();
    }
}
