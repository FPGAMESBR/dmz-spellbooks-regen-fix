package com.dmzspellbooksfix;

import net.minecraftforge.common.ForgeConfigSpec;

public final class DmzSpellbooksFixConfig {
    public static final ForgeConfigSpec SPEC;

    // [KiRegenCompensation]
    public static final ForgeConfigSpec.BooleanValue COMPENSATE_MAX_MANA_WHILE_CHARGING;
    public static final ForgeConfigSpec.BooleanValue COMPENSATE_MAX_MANA_PASSIVELY;
    public static final ForgeConfigSpec.DoubleValue MAX_INFLATION_RATIO_CAP;

    // [ManaRegenAttribute]
    public static final ForgeConfigSpec.BooleanValue APPLY_MANA_REGEN_WHILE_CHARGING;
    public static final ForgeConfigSpec.BooleanValue APPLY_MANA_REGEN_PASSIVELY;
    public static final ForgeConfigSpec.DoubleValue MANA_REGEN_ATTRIBUTE_WEIGHT;
    public static final ForgeConfigSpec.DoubleValue MIN_MANA_REGEN_FACTOR;
    public static final ForgeConfigSpec.DoubleValue EXTRA_PERCENT_MAX_ENERGY_PER_SEC_WHEN_CHARGING;

    // [IronsManaSyncFixes]
    public static final ForgeConfigSpec.BooleanValue FIX_MAGIC_MANAGER_INT_TRUNCATION_CLAMP;
    public static final ForgeConfigSpec.BooleanValue ALLOW_POSITIVE_MANA_GRANTS_TO_KI;
    public static final ForgeConfigSpec.BooleanValue FORWARD_MAGIC_MANAGER_REGEN_TO_KI;
    public static final ForgeConfigSpec.BooleanValue PREVENT_RESPAWN_KI_WIPE;

    // [DmzSpellbooksBugFixes]
    public static final ForgeConfigSpec.BooleanValue FIX_COUNTERSPELL_VS_KI_PROJECTILES;
    public static final ForgeConfigSpec.BooleanValue COUNTERSPELL_INTERRUPTS_CHARGING_KI_PLAYER;
    public static final ForgeConfigSpec.BooleanValue FIX_FORTIFY_PERMANENT_ABSORPTION;
    public static final ForgeConfigSpec.BooleanValue FIX_CONTINUOUS_SPELL_MANA_CHECK;
    public static final ForgeConfigSpec.BooleanValue FIX_SPELL_CAPABILITY_DETECTION;
    public static final ForgeConfigSpec.BooleanValue ENFORCE_EXCLUDED_SPELL_IDS;
    public static final ForgeConfigSpec.BooleanValue FIX_KI_DAMAGE_DEFAULT_ATTRIBUTE_PATCH;
    public static final ForgeConfigSpec.BooleanValue APPLY_SOFT_CAP_TO_KI_SPELL_RESIST;
    public static final ForgeConfigSpec.BooleanValue APPLY_SOFT_CAP_TO_TECHNIQUE_TIMING;
    public static final ForgeConfigSpec.BooleanValue PRESERVE_PRIOR_SPELL_DAMAGE_MODIFIERS;
    public static final ForgeConfigSpec.IntValue ACTIVE_CAST_WINDOW_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("KiRegenCompensation");
        COMPENSATE_MAX_MANA_WHILE_CHARGING = builder
                .comment("Scale DMZ Ki charging speed by (currentMaxEnergy / nativeMaxKi) so Iron's Spells Max Mana bonuses never make Ki charging slower.")
                .define("compensateMaxManaWhileCharging", true);
        COMPENSATE_MAX_MANA_PASSIVELY = builder
                .comment("Scale DMZ passive Ki regeneration by (currentMaxEnergy / nativeMaxKi) as well. Disabled by default to preserve DMZ passive regen balance.")
                .define("compensateMaxManaPassively", false);
        MAX_INFLATION_RATIO_CAP = builder
                .comment("Maximum multiplier allowed from (currentMaxEnergy / nativeMaxKi) compensation.")
                .defineInRange("maxInflationRatioCap", 20.0D, 1.0D, 1000.0D);
        builder.pop();

        builder.push("ManaRegenAttribute");
        APPLY_MANA_REGEN_WHILE_CHARGING = builder
                .comment("Apply Iron's Spells Mana Regeneration attribute (from gear, curios, enchantments, effects) to boost DMZ active Ki charging.")
                .define("applyManaRegenWhileCharging", true);
        APPLY_MANA_REGEN_PASSIVELY = builder
                .comment("Apply Iron's Spells Mana Regeneration attribute to DMZ passive Ki regeneration as well. Disabled by default to prevent excessive passive regen.")
                .define("applyManaRegenPassively", false);
        MANA_REGEN_ATTRIBUTE_WEIGHT = builder
                .comment("How strongly Iron's Spells Mana Regeneration attribute scales DMZ Ki regen: factor = max(minManaRegenFactor, 1.0 + (manaRegen - 1.0) * weight).")
                .defineInRange("manaRegenAttributeWeight", 1.0D, 0.0D, 20.0D);
        MIN_MANA_REGEN_FACTOR = builder
                .comment("Lower bound multiplier when Mana Regeneration attribute is reduced below 1.0 (100%).")
                .defineInRange("minManaRegenFactor", 0.25D, 0.01D, 1.0D);
        EXTRA_PERCENT_MAX_ENERGY_PER_SEC_WHEN_CHARGING = builder
                .comment("Optional extra % of current Max Energy restored per second while actively charging Ki, scaled by Mana Regeneration (0.0 disables).")
                .defineInRange("extraPercentMaxEnergyPerSecondWhenCharging", 0.0D, 0.0D, 100.0D);
        builder.pop();

        builder.push("IronsManaSyncFixes");
        FIX_MAGIC_MANAGER_INT_TRUNCATION_CLAMP = builder
                .comment("Prevent Iron's Spells MagicManager.regenPlayerMana from truncating Max Mana to int and pulling DMZ CurrentEnergy downward every 10 ticks.")
                .define("fixMagicManagerIntTruncationClamp", true);
        ALLOW_POSITIVE_MANA_GRANTS_TO_KI = builder
                .comment("Allow positive external Iron's Spells mana gains (e.g. Mana Potions / Instant Mana / Gluttony / Soul Siphon) to add into DMZ CurrentEnergy instead of being discarded.")
                .define("allowPositiveManaGrantsToKi", true);
        FORWARD_MAGIC_MANAGER_REGEN_TO_KI = builder
                .comment("Allow MagicManager's passive Mana regen (when irons_spellbooks-server.toml manaRegenMultiplier > 0) to add into DMZ CurrentEnergy. Disabled by default.")
                .define("forwardMagicManagerRegenToKi", false);
        PREVENT_RESPAWN_KI_WIPE = builder
                .comment("Prevent Iron's Spells manaSpawnPercent=0 on respawn from wiping DMZ's refilled Ki back to 0.")
                .define("preventRespawnKiWipe", true);
        builder.pop();

        builder.push("DmzSpellbooksBugFixes");
        FIX_COUNTERSPELL_VS_KI_PROJECTILES = builder
                .comment("Allow Iron's Spells Counterspell raycast (Utils.validAntiMagicTarget) to hit DMZ AbstractKiProjectile entities so DMZ Spellbooks' Counterspell cancel actually fires.")
                .define("fixCounterspellVsKiProjectiles", true);
        COUNTERSPELL_INTERRUPTS_CHARGING_KI_PLAYER = builder
                .comment("When Counterspell hits a ServerPlayer who is actively charging a DMZ Ki Attack, discard their charging Ki projectile to interrupt it.")
                .define("counterspellInterruptsChargingKiPlayer", true);
        FIX_FORTIFY_PERMANENT_ABSORPTION = builder
                .comment("Track and cleanly remove the Ki-scaled extra absorption added by DMZ Spellbooks on Fortify when Fortify expires, is removed, or is recast (fixes permanent/infinite stacking golden hearts).")
                .define("fixFortifyPermanentAbsorption", true);
        FIX_CONTINUOUS_SPELL_MANA_CHECK = builder
                .comment("Use Ki-scaled mana cost inside Iron's Spells MagicManager continuous-cast tick checks (fixes Flamethrower, Electrocute, Ray of Frost, etc. stopping early or draining inconsistently).")
                .define("fixContinuousSpellManaCheck", true);
        FIX_SPELL_CAPABILITY_DETECTION = builder
                .comment("Enhance DMZ Spellbooks spell capability detection with 1-hop payload entity/effect inspection and fix false-positive exclusions like Soulfire Ray (also improves Iron's Spells addons like T.O Magic 'n Extras).")
                .define("fixSpellCapabilityDetection", true);
        ENFORCE_EXCLUDED_SPELL_IDS = builder
                .comment("Actually enforce integration.excludedSpellIds from dmz_spellbooks-common.toml across damage, healing, summons, tooltips, and mana cost scaling.")
                .define("enforceExcludedSpellIds", true);
        FIX_KI_DAMAGE_DEFAULT_ATTRIBUTE_PATCH = builder
                .comment("Fix DMZ Spellbooks' Mojmap-only reflection ('defaultValue') on Attribute.class by trying SRG 'f_22076_' so dragonminez:ki_damage default value is actually patched in production Forge.")
                .define("fixKiDamageDefaultAttributePatch", true);
        APPLY_SOFT_CAP_TO_KI_SPELL_RESIST = builder
                .comment("Apply Iron's Spells official Utils.softCapFormula to Spell Resist against DMZ Ki blasts instead of linear 100% immunity at +100% Spell Resist.")
                .define("applySoftCapToKiSpellResist", true);
        APPLY_SOFT_CAP_TO_TECHNIQUE_TIMING = builder
                .comment("Apply Iron's Spells official Utils.softCapFormula to Cast Time Reduction and Cooldown Reduction on DMZ techniques so +100% gear does not reduce techniques to 1 tick.")
                .define("applySoftCapToTechniqueTiming", true);
        PRESERVE_PRIOR_SPELL_DAMAGE_MODIFIERS = builder
                .comment("Use SpellDamageEvent.getAmount() instead of getOriginalAmount() in DMZ Spellbooks so earlier buffs, crits, and modifiers from other mods are preserved.")
                .define("preservePriorSpellDamageModifiers", true);
        ACTIVE_CAST_WINDOW_TICKS = builder
                .comment("Window in ticks during which DMZ Spellbooks matches delayed heals, barriers, Fortify, and summons to a player's active cast (DMZ Spellbooks default is 5 ticks = 0.25s).")
                .defineInRange("activeCastWindowTicks", 40, 5, 600);
        builder.pop();

        SPEC = builder.build();
    }

    private DmzSpellbooksFixConfig() {
    }

    public static boolean isReady() {
        return SPEC.isLoaded();
    }
}
