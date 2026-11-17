package io.github.ranger3462.ddprofessions.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.forging.ForgedGear;
import io.github.ranger3462.ddprofessions.skill.ActivitySkills.ArmorWeight;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Perks that are a stat bonus only while something holds: a polearm in hand, two weapons, a shield, enough heavy
 * armor. Twice a second each player's bonuses are worked out again, and the transient attribute modifiers set to match
 * ({@link #update}).
 */
@EventBusSubscriber(modid = ProfessionsAndSkills.MODID)
public final class PerkStats {
    /** One bonus: a modifier on an attribute. */
    public record Stat(String name, Holder<Attribute> attribute, double amount, Operation operation) {
        ResourceLocation id() {
            return ProfessionsAndSkills.id("perk/" + name);
        }
    }

    /** The modifiers we last gave each player, by id, so ones that no longer apply come off. */
    private static final Map<UUID, Map<ResourceLocation, Holder<Attribute>>> APPLIED = new HashMap<>();

    private PerkStats() {}

    /** The bonuses that apply to the player right now. */
    public static List<Stat> bonuses(Player player) {
        List<Stat> stats = new ArrayList<>();
        boolean oneHanded = ActivitySkills.wields(player, ActivitySkills.ONE_HANDED);
        boolean polearm = ActivitySkills.wields(player, ActivitySkills.POLEARMS);
        boolean dagger = ActivitySkills.wields(player, ActivitySkills.DAGGERS);
        boolean unarmed = player.getMainHandItem().isEmpty();
        boolean dual = ActivitySkills.dualWielding(player);
        if (oneHanded && Perk.FOOTWORK.has(player)) {
            stats.add(new Stat("footwork", Attributes.MOVEMENT_SPEED, 0.03, Operation.ADD_MULTIPLIED_BASE));
        }
        if (polearm && Perk.REACH.has(player)) {
            stats.add(new Stat("reach", Attributes.ENTITY_INTERACTION_RANGE, 0.5, Operation.ADD_VALUE));
        }
        if (polearm && Perk.BRACE.has(player)) {
            stats.add(new Stat("brace", Attributes.KNOCKBACK_RESISTANCE, 0.1, Operation.ADD_VALUE));
        }
        if (polearm && Perk.PHALANX.has(player)) {
            stats.add(new Stat("phalanx", Attributes.ARMOR, 2, Operation.ADD_VALUE));
        }
        if (dagger && Perk.QUICK_HANDS.has(player)) {
            stats.add(new Stat("quick_hands", Attributes.ATTACK_SPEED, 0.05, Operation.ADD_MULTIPLIED_BASE));
        }
        if (dagger && Abilities.active(player, Perk.SHADOW_FLURRY)) {
            stats.add(new Stat("shadow_flurry", Attributes.ATTACK_SPEED, 0.5, Operation.ADD_MULTIPLIED_BASE));
        }
        if (unarmed && Perk.IRON_FIST.has(player)) {
            stats.add(new Stat("iron_fist", Attributes.ATTACK_DAMAGE, 1, Operation.ADD_VALUE));
        }
        if (dual && Perk.FLURRY.has(player)) {
            stats.add(new Stat("flurry", Attributes.ATTACK_SPEED, 0.03, Operation.ADD_MULTIPLIED_BASE));
        }
        if (dual && Perk.OFF_HAND_GUARD.has(player)) {
            stats.add(new Stat("off_hand_guard", Attributes.ARMOR, 1, Operation.ADD_VALUE));
        }
        if (Perk.BULWARK.has(player) && (player.getMainHandItem().getItem() instanceof ShieldItem || player.getOffhandItem().getItem() instanceof ShieldItem)) {
            stats.add(new Stat("bulwark", Attributes.ARMOR, 1, Operation.ADD_VALUE));
        }
        if (Perk.STEADY_FOOTING.has(player) && ActivitySkills.wears(player, ArmorWeight.MEDIUM)) {
            stats.add(new Stat("steady_footing", Attributes.KNOCKBACK_RESISTANCE, 0.1, Operation.ADD_VALUE));
        }
        int heavy = ActivitySkills.wornCount(player, ArmorWeight.HEAVY);
        if (Perk.PLATED.has(player) && heavy > 0) {
            stats.add(new Stat("plated", Attributes.ARMOR, heavy, Operation.ADD_VALUE));
        }
        if (Perk.FORTRESS.has(player)) {
            // Forged heavy armor slows by HEAVY_SLOW a piece (multiplied in); each of these undoes one piece's.
            double undo = -ForgedGear.HEAVY_SLOW / (1 + ForgedGear.HEAVY_SLOW);
            int i = 0;
            for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
                if (player.getItemBySlot(slot).getItem() instanceof ForgedGear.Armor armor && armor.weight == ForgedGear.Weight.HEAVY) {
                    stats.add(new Stat("fortress_" + i++, Attributes.MOVEMENT_SPEED, undo, Operation.ADD_MULTIPLIED_TOTAL));
                }
            }
        }
        if (Perk.ARCHMAGES_ROBE.has(player) && ActivitySkills.wornCount(player, ArmorWeight.MAGE) == 4) {
            attribute("spell_power", "generic").ifPresent(a -> stats.add(new Stat("archmages_robe", a, 1, Operation.ADD_VALUE)));
            attribute("irons_spellbooks", "spell_power").ifPresent(a -> stats.add(new Stat("archmages_robe_irons", a, 0.1, Operation.ADD_MULTIPLIED_BASE)));
        }
        return stats;
    }

    /** Another mod's attribute, if it's installed. */
    static Optional<Holder<Attribute>> attribute(String namespace, String path) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath(namespace, path)).map(holder -> (Holder<Attribute>) holder);
    }

    /** Sets the player's perk modifiers to match {@link #bonuses}. */
    public static void update(Player player) {
        Map<ResourceLocation, Holder<Attribute>> before = APPLIED.getOrDefault(player.getUUID(), Map.of());
        Map<ResourceLocation, Holder<Attribute>> now = new HashMap<>();
        for (Stat stat : bonuses(player)) {
            AttributeInstance instance = player.getAttribute(stat.attribute());
            if (instance == null) {
                continue;
            }
            AttributeModifier modifier = new AttributeModifier(stat.id(), stat.amount(), stat.operation());
            AttributeModifier current = instance.getModifier(stat.id());
            if (current == null || current.amount() != modifier.amount() || current.operation() != modifier.operation()) {
                instance.addOrUpdateTransientModifier(modifier);
            }
            now.put(stat.id(), stat.attribute());
        }
        for (Map.Entry<ResourceLocation, Holder<Attribute>> old : before.entrySet()) {
            if (!now.containsKey(old.getKey())) {
                AttributeInstance instance = player.getAttribute(old.getValue());
                if (instance != null) {
                    instance.removeModifier(old.getKey());
                }
            }
        }
        if (now.isEmpty()) {
            APPLIED.remove(player.getUUID());
        } else {
            APPLIED.put(player.getUUID(), now);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0) {
            update(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        APPLIED.remove(event.getEntity().getUUID());
    }
}
