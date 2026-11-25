package io.github.ranger3462.ddprofessions.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.forging.ForgedGear;
import io.github.ranger3462.ddprofessions.forging.Forging;
import io.github.ranger3462.ddprofessions.skill.Abilities;
import io.github.ranger3462.ddprofessions.skill.CombatPerks;
import io.github.ranger3462.ddprofessions.skill.FieldPerks;
import io.github.ranger3462.ddprofessions.skill.MagicPerks;
import io.github.ranger3462.ddprofessions.skill.MagicSchool;
import io.github.ranger3462.ddprofessions.skill.Perk;
import io.github.ranger3462.ddprofessions.skill.PerkStats;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the gathering, combat, armor and magic tree perks (skill/Abilities, PerkStats, CombatPerks, FieldPerks, MagicPerks). */
@GameTestHolder(ProfessionsAndSkills.MODID)
@PrefixGameTestTemplate(false)
public class SkillPerkTests {
    private static final String ANY_TEMPLATE = "test/empty";

    @GameTest(template = ANY_TEMPLATE)
    public static void abilitiesRunThenCoolDown(GameTestHelper helper) {
        ServerPlayer player = player(helper, "stormy", BlockPos.ZERO);
        helper.assertFalse(Abilities.tryStart(player, Perk.BLADESTORM), "no ability without its perk");
        player.addTag(Perk.BLADESTORM.tag(2));
        helper.assertTrue(Abilities.tryStart(player, Perk.BLADESTORM) && Abilities.active(player, Perk.BLADESTORM), "the ability starts");
        helper.assertTrue(Abilities.tryStart(player, Perk.BLADESTORM), "starting it again while it runs is fine");
        helper.assertFalse(Abilities.ready(player, Perk.BLADESTORM), "it isn't ready again while running or cooling down");
        Abilities.reset(player, Perk.BLADESTORM);
        helper.assertTrue(Abilities.ready(player, Perk.BLADESTORM) && !Abilities.active(player, Perk.BLADESTORM), "a reset ends it");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void statPerksFollowWhatYouHold(GameTestHelper helper) {
        ServerPlayer player = player(helper, "pikeman", BlockPos.ZERO);
        player.addTag(Perk.REACH.tag(1));
        player.addTag(Perk.PHALANX.tag(1));
        player.addTag(Perk.IRON_FIST.tag(1));
        double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), armor = player.getAttributeValue(Attributes.ARMOR);
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TRIDENT));
        PerkStats.update(player);
        helper.assertTrue(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) == reach + 0.5
                && player.getAttributeValue(Attributes.ARMOR) == armor + 2, "a polearm gives Reach and Phalanx");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        PerkStats.update(player);
        helper.assertTrue(player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) == reach && player.getAttributeValue(Attributes.ARMOR) == armor
                && player.getAttributeValue(Attributes.ATTACK_DAMAGE) == damage + 1, "empty hands drop them, and Iron Fist applies");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void fortressUndoesHeavySlow(GameTestHelper helper) {
        ServerPlayer player = player(helper, "tank", BlockPos.ZERO);
        double base = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            if (type != ArmorItem.Type.BODY) {
                ItemStack piece = new ItemStack(Forging.ARMOR.get(ForgedGear.Weight.HEAVY).get(type).get());
                player.setItemSlot(type.getSlot(), piece);
                // A fake player doesn't tick, so its armor's modifiers go on by hand.
                piece.forEachModifier(type.getSlot(), (attribute, modifier) -> player.getAttribute(attribute).addOrUpdateTransientModifier(modifier));
            }
        }
        double slowed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        player.addTag(Perk.FORTRESS.tag(1));
        PerkStats.update(player);
        double fortress = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(slowed < base && Math.abs(fortress - base) < 1.0E-6, "heavy armor slows " + base + " to " + slowed + ", and Fortress undoes it: " + fortress);
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void duelistHitsHarderAlone(GameTestHelper helper) {
        BlockPos at = TestArena.box(helper, 0, 4);
        ServerPlayer player = player(helper, "duelist", at);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        float plain = hitPig(helper, player, at.east(2));
        player.addTag(Perk.DUELIST.tag(1));
        float duelist = hitPig(helper, player, at.west(2));
        TestArena.clearBox(helper, at, 4);
        helper.assertTrue(Math.abs(duelist - plain * CombatPerks.DUELIST) < 0.01f, "Duelist: " + plain + " becomes " + duelist);
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void armorPerksSoftenHits(GameTestHelper helper) {
        ServerPlayer player = player(helper, "juggernaut", BlockPos.ZERO);
        var arrow = player.damageSources().arrow(null, null);
        player.addTag(Perk.JUGGERNAUT.tag(3));
        player.addTag(Perk.MAIL_BRACE.tag(1));
        helper.assertTrue(CombatPerks.takenMultiplier(player, arrow) == 1, "no armor, no perks");
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        helper.assertTrue(Math.abs(CombatPerks.takenMultiplier(player, arrow) - 0.91f) < 1.0E-5, "Juggernaut III in heavy armor: 9% less");
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
        helper.assertTrue(Math.abs(CombatPerks.takenMultiplier(player, arrow) - CombatPerks.MAIL_BRACE) < 1.0E-5, "Mail Brace in medium armor, against arrows");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 100)
    public static void bleedingHurtsOverTime(GameTestHelper helper) {
        BlockPos at = TestArena.box(helper, 2, 4);
        Pig pig = pig(helper, at);
        float health = pig.getHealth();
        CombatPerks.bleed(pig);
        helper.runAfterDelay(45, () -> {
            float lost = health - pig.getHealth();
            pig.discard();
            TestArena.clearBox(helper, at, 4);
            helper.assertTrue(lost >= 2, "a bleeding pig loses about 1 a second; lost " + lost);
            helper.succeed();
        });
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void disarmTakesTheWeapon(GameTestHelper helper) {
        BlockPos at = TestArena.box(helper, 3, 4);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        helper.getLevel().addFreshEntity(zombie);
        CombatPerks.disarm(zombie);
        boolean disarmed = zombie.getMainHandItem().isEmpty();
        zombie.discard();
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(4)).forEach(ItemEntity::discard);
        TestArena.clearBox(helper, at, 4);
        helper.assertTrue(disarmed, "the zombie loses its sword");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void sifterTurnsGravelToFlint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "sifter", BlockPos.ZERO);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        BlockPos pos = player.blockPosition();
        java.util.List<ItemEntity> drops = new java.util.ArrayList<>(java.util.List.of(new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.GRAVEL))));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockDropsEvent(level, pos, Blocks.GRAVEL.defaultBlockState(),
                null, drops, player, player.getMainHandItem()));
        helper.assertTrue(drops.getFirst().getItem().is(Items.GRAVEL), "without Sifter, gravel stays gravel");
        player.addTag(Perk.SIFTER.tag(1));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockDropsEvent(level, pos, Blocks.GRAVEL.defaultBlockState(),
                null, drops, player, player.getMainHandItem()));
        helper.assertTrue(drops.getFirst().getItem().is(Items.FLINT), "with Sifter, gravel always gives flint");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void fertileGroundGrowsCrops(GameTestHelper helper) {
        BlockPos at = TestArena.box(helper, 5, 4);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "farmer", at);
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-4, -1, -4), at.offset(4, -1, 4))) {
            level.setBlock(pos, Blocks.FARMLAND.defaultBlockState(), 2);
            level.setBlock(pos.above(), Blocks.WHEAT.defaultBlockState(), 2);
        }
        int grown = FieldPerks.growNear(player, 200);
        int aged = 0;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-4, 0, -4), at.offset(4, 0, 4))) {
            if (level.getBlockState(pos).getBlock() instanceof CropBlock crop) {
                aged += crop.getAge(level.getBlockState(pos));
            }
        }
        TestArena.clearBox(helper, at, 4);
        helper.assertTrue(grown > 0 && aged == grown, "Fertile Ground ages crops: " + grown + " grown, " + aged + " ages");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void earthmoverDigsAFace(GameTestHelper helper) {
        BlockPos at = TestArena.box(helper, 6, 5);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, "digger", at);
        player.setYRot(0); // Facing south (+z): the face is across x and y.
        player.setXRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        player.addTag(Perk.EARTHMOVER.tag(1));
        BlockPos center = at.offset(0, 2, 3);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, 0), center.offset(1, 1, 0))) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
        }
        Abilities.tryStart(player, Perk.EARTHMOVER);
        player.gameMode.destroyBlock(center);
        int left = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, 0), center.offset(1, 1, 0))) {
            left += level.getBlockState(pos).isAir() ? 0 : 1;
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(3)).forEach(ItemEntity::discard);
        TestArena.clearBox(helper, at, 5);
        helper.assertTrue(left == 0, "Earthmover digs the whole 3x3 face; " + left + " left");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void magicPerksAdjustCastsAndHeals(GameTestHelper helper) {
        ServerPlayer player = player(helper, "mage", BlockPos.ZERO);
        helper.assertTrue(MagicPerks.cooldown(player, MagicSchool.FIRE, 100) == 100, "no Quick Casting, no change");
        player.addTag(Perk.FIRE_QUICK_CASTING.tag(1));
        player.addTag(Perk.ARCANE_EFFECT.tag(1));
        helper.assertTrue(MagicPerks.cooldown(player, MagicSchool.FIRE, 100) == 90 && MagicPerks.cooldown(player, MagicSchool.FROST, 100) == 100,
                "Quick Casting shortens its own school's cooldowns");
        helper.assertTrue(MagicPerks.manaCost(player, MagicSchool.ARCANE, 50) == 45 && MagicPerks.manaCost(player, MagicSchool.FIRE, 50) == 50,
                "Flux cuts arcane mana costs");
        ServerPlayer friend = player(helper, "friend", BlockPos.ZERO);
        friend.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        friend.setHealth(friend.getMaxHealth() - 2);
        player.addTag(Perk.HOLY_EFFECT.tag(1));
        player.addTag(Perk.HOLY_CAPSTONE.tag(1));
        MagicPerks.onHeal(player, friend, 6, MagicSchool.HOLY);
        helper.assertFalse(friend.hasEffect(MobEffects.POISON), "Blessing cures the poison");
        helper.assertTrue(friend.hasEffect(MobEffects.ABSORPTION), "Aegis turns overhealing into Absorption");
        helper.succeed();
    }

    private static float hitPig(GameTestHelper helper, ServerPlayer player, BlockPos at) {
        Pig pig = pig(helper, at);
        float before = pig.getHealth();
        pig.hurt(player.damageSources().playerAttack(player), 4);
        float lost = before - pig.getHealth();
        pig.discard();
        return lost;
    }

    private static Pig pig(GameTestHelper helper, BlockPos at) {
        Pig pig = EntityType.PIG.create(helper.getLevel());
        pig.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        pig.setNoAi(true);
        helper.getLevel().addFreshEntity(pig);
        return pig;
    }

    /** A fake player at a spot: an arena's, or (for BlockPos.ZERO) 100 blocks above the test. */
    private static ServerPlayer player(GameTestHelper helper, String name, BlockPos at) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos pos = at.equals(BlockPos.ZERO) ? helper.absolutePos(BlockPos.ZERO).above(100) : at;
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        TestArena.noCrits(player);
        return player;
    }
}
