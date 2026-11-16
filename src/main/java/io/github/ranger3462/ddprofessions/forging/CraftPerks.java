package io.github.ranger3462.ddprofessions.forging;

import java.util.Optional;

import io.github.ranger3462.ddprofessions.skill.CraftSkill;
import io.github.ranger3462.ddprofessions.skill.CraftingSkills;
import io.github.ranger3462.ddprofessions.skill.Perk;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the crafting perks do, beyond the forge work's own ({@link ForgingPerks}). Each is a node in that skill's tree
 * (PROF-1): a level gives a point to spend, rather than a bonus by itself. The Studies columns are in
 * {@link CraftingSkills#addXp}.
 */
public final class CraftPerks {
    private CraftPerks() {}

    /** Extra room for error, as a share of the bar: +2% a rank of Steady Scraper, Steady Needle or Steady Knife. */
    public static float sweetSpotBonus(Player player, CraftSkill skill) {
        Perk perk = switch (skill) {
            case WEAVING -> Perk.STEADY_NEEDLE;
            case WOODWORKING -> Perk.STEADY_KNIFE;
            case GOLDSMITHING -> Perk.STEADY_CHASE;
            default -> Perk.STEADY_SCRAPER;
        };
        return 0.02f * perk.rank(player);
    }

    /** Scales the chance a miss ruins the piece: 25% less a rank of Careful Tanner, Careful Weaver or Careful Carver. */
    public static float failMultiplier(Player player, CraftSkill skill) {
        Perk perk = switch (skill) {
            case WEAVING -> Perk.CAREFUL_WEAVER;
            case WOODWORKING -> Perk.CAREFUL_CARVER;
            case GOLDSMITHING -> Perk.CAREFUL_GOLDSMITH;
            default -> Perk.CAREFUL_TANNER;
        };
        return 1.0f - 0.25f * perk.rank(player);
    }

    /** The chance boiling leather doesn't use up a level of water: 25% a rank of Water Saver. */
    public static float boilSavesWater(Player player) {
        return 0.25f * Perk.WATER_SAVER.rank(player);
    }

    /** The chance of a bonus piece: a third stave (Fine Split), a third cloth panel (Fine Weave), a second refined leather (Supple Hides). */
    public static final float BONUS_PIECE = 0.25f;
    /** How much faster a perk makes a timed step: billets curing (Seasoner), gold refining (Assayer). */
    public static final float FASTER = 0.75f;
    /** Keen Edge: blades and axe heads give this much more Weaponsmithing XP. */
    public static final float KEEN_EDGE_XP = 1.5f;
    /** The chance a Masterwork perk lifts a piece one ★. */
    public static final float MASTERWORK_CHANCE = 0.10f;

    /** Whether a bonus piece comes out, for a player with this perk. */
    public static boolean bonusPiece(Player player, Perk perk, RandomSource random) {
        return perk.has(player) && random.nextFloat() < BONUS_PIECE;
    }

    /**
     * The Masterwork perk that can lift this crafted piece, if any: forged weapons (Weaponsmithing's Masterwork), plate
     * armor (Armorsmithing's), jewelry and gilded gear (Goldsmithing's), and bows and crossbows (Master Bowyer).
     */
    public static Optional<Perk> masterworkPerk(ItemStack result) {
        if (result.getItem() instanceof JewelryItem) {
            return Optional.of(Perk.MASTERWORK_JEWELRY);
        }
        if (result.is(Items.BOW) || result.is(Items.CROSSBOW)) {
            return Optional.of(Perk.MASTER_BOWYER);
        }
        if (!ForgedGear.isGear(result)) {
            return Optional.empty();
        }
        return switch (CraftingXp.skillFor(result)) {
            case WEAPONSMITHING -> Optional.of(Perk.MASTERWORK_WEAPON);
            case ARMORSMITHING -> Optional.of(Perk.MASTERWORK_ARMOR);
            case GOLDSMITHING -> Optional.of(Perk.MASTERWORK_JEWELRY);
            default -> Optional.empty();
        };
    }

    /** The skill whose level caps a Masterwork piece's extra ★. */
    public static CraftSkill masterworkSkill(Perk perk) {
        return switch (perk) {
            case MASTERWORK_WEAPON -> CraftSkill.WEAPONSMITHING;
            case MASTERWORK_ARMOR -> CraftSkill.ARMORSMITHING;
            case MASTER_BOWYER -> CraftSkill.WOODWORKING;
            default -> CraftSkill.GOLDSMITHING;
        };
    }

    /**
     * Whether the player's next Masterwork-eligible craft comes out a ★ higher. It's fixed until they craft one
     * ({@link #usedMasterworkRoll}), so the crafting grid shows the result honestly and refilling it can't reroll.
     */
    public static boolean masterworkRoll(Player player) {
        int rolls = player.getData(CraftingSkills.MASTERWORK_ROLLS);
        long seed = player.getUUID().getLeastSignificantBits() ^ player.getUUID().getMostSignificantBits() * 31 ^ rolls * 0x9E3779B97F4A7C15L;
        return RandomSource.create(seed).nextFloat() < MASTERWORK_CHANCE;
    }

    /** The player crafted a piece their Masterwork perk applies to: move on to the next roll. */
    public static void usedMasterworkRoll(Player player) {
        player.setData(CraftingSkills.MASTERWORK_ROLLS, player.getData(CraftingSkills.MASTERWORK_ROLLS) + 1);
    }

    /**
     * The crafter's perks on a starred result about to come out at {@code stars}: Masterwork (one ★ more, up to what
     * their skill allows) and Balanced Blade. Returns the ★ it comes out at.
     */
    public static int onStarredResult(Player crafter, ItemStack result, int stars) {
        if (Perk.BALANCED_BLADE.has(crafter) && ForgedGear.takesBalance(result)) {
            result.set(Forging.BALANCED.get(), true);
        }
        Optional<Perk> masterwork = masterworkPerk(result);
        if (masterwork.isPresent() && masterwork.get().has(crafter) && stars < Forging.MAX_STARS
                && stars + 1 <= ForgeActions.skillStars(crafter, masterworkSkill(masterwork.get())) && masterworkRoll(crafter)) {
            result.set(Forging.MASTERWORK.get(), true);
            return stars + 1;
        }
        return stars;
    }
}
