package io.github.ranger3462.ddprofessions.integration.irons;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.beast.BondingCharmItem;
import io.github.ranger3462.ddprofessions.beast.Companions;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Call Companion (PROF-28): the Bonding Charm's call as an Iron's Spells spell in the Nature school. It channels as long
 * as the charm does, and does what the charm does ({@link Companions#call}): a fallen companion comes back, a living one
 * comes to you. Companion Bond's cooldown is the one that counts, so the spell's own is short; the cast is refused (with
 * the reason) while there's no companion or the bond is cooling down, before any mana is spent.
 */
public class CallCompanionSpell extends AbstractSpell {
    public static final ResourceLocation ID = ProfessionsAndSkills.id("call_companion");
    public static final int MANA_COST = 40;
    /** The spell's own cooldown, in ticks: short, since Companion Bond's is the one that counts. */
    public static final int SPELL_COOLDOWN = 100;

    private final DefaultConfig config = new DefaultConfig()
            .setMinRarity(SpellRarity.COMMON)
            .setSchoolResource(SchoolRegistry.NATURE_RESOURCE)
            .setMaxLevel(1)
            .setCooldownSeconds(5)
            .setAllowCrafting(false) // Only from Companion Bond's own recipe (CompanionScrollRecipe), not the Scroll Forge.
            .build();

    public CallCompanionSpell() {
        this.baseManaCost = MANA_COST;
        this.manaCostPerLevel = 0;
        this.baseSpellPower = 0;
        this.spellPowerPerLevel = 0;
        this.castTime = BondingCharmItem.CHANNEL;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return ID;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return config;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    // Iron's reads a spell's settings from its config; an add-on spell's defaults don't always reach it (it fell back to
    // Evocation), so the settings that make Call Companion what it is are fixed here. The same values are in
    // data/ddprofessions/irons_spellbooks_spell_config/call_companion.json for Iron's newer config.

    @Override
    public SchoolType getSchoolType() {
        return SchoolRegistry.NATURE.get();
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinRarity() {
        return SpellRarity.COMMON.getValue();
    }

    @Override
    public int getSpellCooldown() {
        return SPELL_COOLDOWN;
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return !(entity instanceof ServerPlayer player) || Companions.canCall(player);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (entity instanceof ServerPlayer player) {
            Companions.call(player);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
