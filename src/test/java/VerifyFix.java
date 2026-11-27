import com.dmzspellbooks.DmzIronsIntegrationEvents;
import com.dmzspellbooks.DmzSpellbooksConfig;
import com.dmzspellbooks.spellki.TechniqueTimingBridge;
import com.dmzspellbooksfix.DmzSpellbooksFixConfig;
import com.dmzspellbooksfix.DmzSpellbooksFixEvents;
import com.dmzspellbooksfix.DmzSpellbooksFixHelpers;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsData;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import io.redspace.ironsspellbooks.api.events.ChangeManaEvent;
import io.redspace.ironsspellbooks.api.events.CounterSpellEvent;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellSummonEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.player.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class VerifyFix {
    @SuppressWarnings("rawtypes")
    public static void main(String[] args) throws Exception {
        Path configOut = Path.of(args[0]);
        Files.createDirectories(configOut.getParent());

        CommentedFileConfig cfg = CommentedFileConfig.builder(configOut)
                .sync()
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .build();
        cfg.load();
        DmzSpellbooksFixConfig.SPEC.setConfig(cfg);
        DmzSpellbooksFixConfig.SPEC.save();
        cfg.save();
        cfg.close();

        // Also load real dmz_spellbooks-common.toml so excludedSpellIds tests hit real config!
        if (args.length > 1) {
            Path dmzCfgPath = Path.of(args[1]);
            CommentedFileConfig dmzCfg = CommentedFileConfig.builder(dmzCfgPath).sync().build();
            dmzCfg.load();
            DmzSpellbooksConfig.SPEC.setConfig(dmzCfg);
            dmzCfg.close();
        }

        // 1. Verify config defaults
        assertTrue(DmzSpellbooksFixConfig.isReady(), "Config SPEC must report isLoaded()");
        assertTrue(DmzSpellbooksFixConfig.COMPENSATE_MAX_MANA_WHILE_CHARGING.get(), "Default compensateMaxManaWhileCharging=true");
        assertTrue(!DmzSpellbooksFixConfig.COMPENSATE_MAX_MANA_PASSIVELY.get(), "Default compensateMaxManaPassively=false");
        assertTrue(DmzSpellbooksFixConfig.APPLY_MANA_REGEN_WHILE_CHARGING.get(), "Default applyManaRegenWhileCharging=true");
        assertTrue(!DmzSpellbooksFixConfig.APPLY_MANA_REGEN_PASSIVELY.get(), "Default applyManaRegenPassively=false");
        assertTrue(DmzSpellbooksFixConfig.FIX_MAGIC_MANAGER_INT_TRUNCATION_CLAMP.get(), "Default fixMagicManagerIntTruncationClamp=true");
        assertTrue(DmzSpellbooksFixConfig.ALLOW_POSITIVE_MANA_GRANTS_TO_KI.get(), "Default allowPositiveManaGrantsToKi=true");
        assertTrue(!DmzSpellbooksFixConfig.FORWARD_MAGIC_MANAGER_REGEN_TO_KI.get(), "Default forwardMagicManagerRegenToKi=false");
        assertTrue(DmzSpellbooksFixConfig.PREVENT_RESPAWN_KI_WIPE.get(), "Default preventRespawnKiWipe=true");

        assertTrue(DmzSpellbooksFixConfig.FIX_COUNTERSPELL_VS_KI_PROJECTILES.get(), "Default fixCounterspellVsKiProjectiles=true");
        assertTrue(DmzSpellbooksFixConfig.COUNTERSPELL_INTERRUPTS_CHARGING_KI_PLAYER.get(), "Default counterspellInterruptsChargingKiPlayer=true");
        assertTrue(DmzSpellbooksFixConfig.FIX_FORTIFY_PERMANENT_ABSORPTION.get(), "Default fixFortifyPermanentAbsorption=true");
        assertTrue(DmzSpellbooksFixConfig.FIX_CONTINUOUS_SPELL_MANA_CHECK.get(), "Default fixContinuousSpellManaCheck=true");
        assertTrue(DmzSpellbooksFixConfig.FIX_SPELL_CAPABILITY_DETECTION.get(), "Default fixSpellCapabilityDetection=true");
        assertTrue(DmzSpellbooksFixConfig.ENFORCE_EXCLUDED_SPELL_IDS.get(), "Default enforceExcludedSpellIds=true");
        assertTrue(DmzSpellbooksFixConfig.FIX_KI_DAMAGE_DEFAULT_ATTRIBUTE_PATCH.get(), "Default fixKiDamageDefaultAttributePatch=true");
        assertTrue(DmzSpellbooksFixConfig.APPLY_SOFT_CAP_TO_KI_SPELL_RESIST.get(), "Default applySoftCapToKiSpellResist=true");
        assertTrue(DmzSpellbooksFixConfig.APPLY_SOFT_CAP_TO_TECHNIQUE_TIMING.get(), "Default applySoftCapToTechniqueTiming=true");
        assertTrue(DmzSpellbooksFixConfig.PRESERVE_PRIOR_SPELL_DAMAGE_MODIFIERS.get(), "Default preservePriorSpellDamageModifiers=true");
        assertTrue(DmzSpellbooksFixConfig.ACTIVE_CAST_WINDOW_TICKS.get() == 40, "Default activeCastWindowTicks=40");

        // 2. Verify EVERY Mixin target class, method, and field matches runtime signatures in mods/
        Method mRegen = MagicManager.class.getDeclaredMethod("regenPlayerMana", ServerPlayer.class, MagicData.class);
        assertTrue(mRegen.getReturnType() == boolean.class, "MagicManager.regenPlayerMana returns boolean");

        Method mTickLambda = MagicManager.class.getDeclaredMethod("lambda$tick$0", boolean.class, Player.class);
        assertTrue(mTickLambda.getReturnType() == void.class, "MagicManager.lambda$tick$0 present");

        Method mRespawn = ServerPlayerEvents.class.getDeclaredMethod("onPlayerRespawn", PlayerEvent.PlayerRespawnEvent.class);
        assertTrue(mRespawn != null, "ServerPlayerEvents.onPlayerRespawn present");

        Method mSetMana = MagicData.class.getDeclaredMethod("setMana", float.class);
        assertTrue(mSetMana != null, "MagicData.setMana(float) present");

        Method mValidAntiMagic = Utils.class.getDeclaredMethod("validAntiMagicTarget", Entity.class);
        assertTrue(mValidAntiMagic.getReturnType() == boolean.class, "Utils.validAntiMagicTarget(Entity) present");

        Method mTechGetReduction = TechniqueTimingBridge.class.getDeclaredMethod("getReduction", Attribute.class);
        assertTrue(mTechGetReduction.getReturnType() == double.class, "TechniqueTimingBridge.getReduction(Attribute) present");

        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("onManaChanged", ChangeManaEvent.class) != null, "onManaChanged present");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("patchAttributeDefaultValue", Attribute.class, double.class) != null, "patchAttributeDefaultValue present");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("isPercentOrAttackDamageBasedSpell", AbstractSpell.class) != null, "isPercentOrAttackDamageBasedSpell present");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("detectCapabilities", AbstractSpell.class) != null, "detectCapabilities present");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("getCapabilities", AbstractSpell.class) != null, "getCapabilities present");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("hasScalableAspect", AbstractSpell.class) != null, "hasScalableAspect present");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("getScaledUniqueInfo", AbstractSpell.class, int.class, LivingEntity.class, List.class) != null, "getScaledUniqueInfo present");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("onSpellDamage", SpellDamageEvent.class) != null, "onSpellDamage present");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("onSpellSummon", SpellSummonEvent.class) != null, "onSpellSummon present");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("onMobEffectAdded", MobEffectEvent.Added.class) != null, "onMobEffectAdded present");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("onKiAttackHurt", LivingHurtEvent.class) != null, "onKiAttackHurt present");

        // Verify findActiveSpell & lambda$findNearbyActiveCast$14 exist
        Class<?> aspectClass = Class.forName("com.dmzspellbooks.DmzIronsIntegrationEvents$SpellScalingAspect");
        Class<?> activeCastClass = Class.forName("com.dmzspellbooks.DmzIronsIntegrationEvents$ActiveCast");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("findActiveSpell", Player.class, aspectClass) != null, "findActiveSpell present");
        assertTrue(DmzIronsIntegrationEvents.class.getDeclaredMethod("lambda$findNearbyActiveCast$14", long.class, activeCastClass) != null, "lambda$findNearbyActiveCast$14 present");

        Field fSync = DmzIronsIntegrationEvents.class.getDeclaredField("INTERNAL_MANA_SYNC");
        assertTrue(Set.class.isAssignableFrom(fSync.getType()), "INTERNAL_MANA_SYNC is Set");

        Method mNativeMaxKi = DmzIronsIntegrationEvents.class.getDeclaredMethod("getNativeMaxKi", ServerPlayer.class, StatsData.class);
        assertTrue(mNativeMaxKi.getReturnType() == float.class, "getNativeMaxKi returns float");

        Method mMarkDirty = StatsDeltaSync.class.getDeclaredMethod("markDirty", ServerPlayer.class, String[].class);
        assertTrue(mMarkDirty != null, "StatsDeltaSync.markDirty present");

        assertTrue(AbstractKiProjectile.class.getMethod("isFiring") != null, "AbstractKiProjectile.isFiring present");
        assertTrue(AbstractKiProjectile.class.getMethod("isOwner", Entity.class) != null, "AbstractKiProjectile.isOwner present");
        assertTrue(CounterSpellEvent.class.getField("target") != null, "CounterSpellEvent.target present");
        assertTrue(DMZEvent.KiChargeEvent.class != null && DMZEvent.EnergyRegenEvent.class != null, "DMZ events present");

        // 3. Unit tests: SRG Attribute.defaultValue patch (f_22076_)
        RangedAttribute testAttr = new RangedAttribute("attribute.test.ki_damage", 0.0D, 0.0D, 1000.0D);
        assertClose(testAttr.m_22082_(), 0.0D, 1e-9, "Initial defaultValue is 0.0");
        boolean patched = DmzSpellbooksFixHelpers.patchAttributeDefaultValueSrgSafe(testAttr, 1.0D);
        assertTrue(patched, "patchAttributeDefaultValueSrgSafe must succeed on SRG Attribute");
        assertClose(testAttr.m_22082_(), 1.0D, 1e-9, "Patched defaultValue must equal 1.0");

        // 4. Unit tests: Soft Cap formula (matches Iron's Spells Utils.softCapFormula)
        assertClose(DmzSpellbooksFixHelpers.applySoftCapReduction(0.0), 0.0, 1e-9, "SoftCap 0% -> 0%");
        assertClose(DmzSpellbooksFixHelpers.applySoftCapReduction(-0.5), 0.0, 1e-9, "SoftCap negative -> 0%");
        assertClose(DmzSpellbooksFixHelpers.applySoftCapReduction(0.25), 0.25, 1e-9, "SoftCap +25% -> 25%");
        assertClose(DmzSpellbooksFixHelpers.applySoftCapReduction(0.5), 0.5, 1e-9, "SoftCap +50% -> 50%");
        assertClose(DmzSpellbooksFixHelpers.applySoftCapReduction(1.0), 0.75, 1e-9, "SoftCap +100% -> 75% (not 100%!)");
        assertClose(DmzSpellbooksFixHelpers.applySoftCapReduction(2.0), 0.875, 1e-9, "SoftCap +200% -> 87.5%");
        assertClose(DmzSpellbooksFixHelpers.computeSoftCappedKiDamage(100.0F, 1.0), 25.0, 1e-4, "100 Ki dmg with +100% Spell Resist -> 25 dmg (no godmode)");
        assertClose(DmzSpellbooksFixHelpers.computeSoftCappedKiDamage(100.0F, 0.4), 60.0, 1e-4, "100 Ki dmg with +40% Spell Resist -> 60 dmg");

        // 5. Unit tests: excludedSpellIds enforcement
        assertTrue(DmzSpellbooksFixHelpers.isSpellIdExcluded("irons_spellbooks:counterspell"), "counterspell must be excluded");
        assertTrue(DmzSpellbooksFixHelpers.isSpellIdExcluded("irons_spellbooks:teleport"), "teleport must be excluded");
        assertTrue(DmzSpellbooksFixHelpers.isSpellIdExcluded("irons_spellbooks:summon_ender_chest"), "summon_ender_chest must be excluded");
        assertTrue(!DmzSpellbooksFixHelpers.isSpellIdExcluded("irons_spellbooks:fireball"), "fireball must NOT be excluded");

        // 6. Unit tests: Percent exclusion false-positive fix & capability detection on real spells
        assertTrue(DmzSpellbooksFixHelpers.shouldOverridePercentExclusionFromText(
                "ui.irons_spellbooks.damage reduced_healing DamageSources"),
                "SoulfireRay-like debuff + flat damage must override false exclusion");
        assertTrue(!DmzSpellbooksFixHelpers.shouldOverridePercentExclusionFromText(
                "ui.irons_spellbooks.percent_damage echoing_strikes"),
                "EchoingStrikes must stay excluded");
        assertTrue(!DmzSpellbooksFixHelpers.shouldOverridePercentExclusionFromText(
                "ui.irons_spellbooks.damage_reduction oakskin"),
                "Oakskin (no flat damage key) must stay excluded");

        // Load real spell classes (without running MC <clinit>) to test enhanceCapabilitiesForClass end-to-end!
        ClassLoader cl = VerifyFix.class.getClassLoader();
        Class<?> soulfireCls = Class.forName("io.redspace.ironsspellbooks.spells.fire.SoulfireRaySpell", false, cl);
        Class<?> sculkCls = Class.forName("io.redspace.ironsspellbooks.spells.eldritch.SculkTentaclesSpell", false, cl);
        Class<?> thunderCls = Class.forName("io.redspace.ironsspellbooks.spells.lightning.ThunderstormSpell", false, cl);
        Class<?> teleportCls = Class.forName("io.redspace.ironsspellbooks.spells.ender.TeleportSpell", false, cl);
        Class<?> counterspellCls = Class.forName("io.redspace.ironsspellbooks.spells.ender.CounterspellSpell", false, cl);
        Class<?> aquaMissilesCls = Class.forName("com.gametechbc.traveloptics.spells.aqua.AquaMissilesSpell", false, cl);
        Class<?> thunderDragonCls = Class.forName("com.grandsigil.spell.ThunderDragonSpell", false, cl);

        assertTrue(DmzSpellbooksFixHelpers.shouldOverridePercentExclusionForClass(soulfireCls), "Real SoulfireRaySpell must override percent exclusion");
        EnumSet empty = DmzSpellbooksFixHelpers.createEmptyAspectSet();
        EnumSet soulfireAspects = DmzSpellbooksFixHelpers.enhanceCapabilitiesForClass(soulfireCls, "irons_spellbooks:soulfire_ray", empty);
        EnumSet sculkAspects = DmzSpellbooksFixHelpers.enhanceCapabilitiesForClass(sculkCls, "irons_spellbooks:sculk_tentacles", empty);
        EnumSet thunderAspects = DmzSpellbooksFixHelpers.enhanceCapabilitiesForClass(thunderCls, "irons_spellbooks:thunderstorm", empty);
        EnumSet teleportAspects = DmzSpellbooksFixHelpers.enhanceCapabilitiesForClass(teleportCls, "irons_spellbooks:teleport", empty);
        EnumSet counterAspects = DmzSpellbooksFixHelpers.enhanceCapabilitiesForClass(counterspellCls, "irons_spellbooks:counterspell", empty);
        EnumSet aquaAspects = DmzSpellbooksFixHelpers.enhanceCapabilitiesForClass(aquaMissilesCls, "traveloptics:aqua_missiles", empty);
        EnumSet grandSigilAspects = DmzSpellbooksFixHelpers.enhanceCapabilitiesForClass(thunderDragonCls, "grandsigil:thunder_dragon", empty);

        assertTrue(!soulfireAspects.isEmpty(), "SoulfireRaySpell must detect DAMAGE capability");
        assertTrue(!sculkAspects.isEmpty(), "SculkTentaclesSpell must detect DAMAGE capability");
        assertTrue(!thunderAspects.isEmpty(), "ThunderstormSpell must detect DAMAGE capability");
        assertTrue(!aquaAspects.isEmpty(), "TravelOptics AquaMissilesSpell must detect DAMAGE capability");
        assertTrue(grandSigilAspects.isEmpty(), "GrandSigil vanilla-damage spell must NOT falsely inflate mana cost");
        assertTrue(teleportAspects.isEmpty(), "TeleportSpell (excluded) must have EMPTY capabilities");
        assertTrue(counterAspects.isEmpty(), "CounterspellSpell (excluded) must have EMPTY capabilities");

        // 7. Unit tests: Fortify absorption cleanup math
        assertClose(DmzSpellbooksFixEvents.computePostRemovalAbsorption(510.0F, 500.0F), 10.0, 1e-5, "Fortify cleanup leaves base/other absorption intact");
        assertClose(DmzSpellbooksFixEvents.computePostRemovalAbsorption(40.0F, 500.0F), 0.0, 1e-5, "Fortify cleanup clamps at 0 when damaged");
        assertClose(DmzSpellbooksFixEvents.computePostRemovalAbsorption(14.0F, 0.0F), 14.0, 1e-5, "Zero tracked extra leaves absorption unchanged");

        // 8. Unit tests: Existing v1.0.0 regen math
        double rNoGear = DmzSpellbooksFixEvents.computeAdjustedEnergyRegen(
                1.308, 25.1f, 25.1f, 1.0, true,
                true, false, 20.0, true, false, 1.0, 0.25, 0.0);
        assertClose(rNoGear, 1.308, 1e-6, "No mage gear leaves DMZ regen untouched");

        double rPassiveDisabled = DmzSpellbooksFixEvents.computeAdjustedEnergyRegen(
                1.308, 100.1f, 25.1f, 3.0, false,
                true, false, 20.0, true, false, 1.0, 0.25, 0.0);
        assertClose(rPassiveDisabled, 1.308, 1e-6, "Passive regen disabled leaves passive DMZ regen untouched");

        double rPumpkin = DmzSpellbooksFixEvents.computeAdjustedEnergyRegen(
                1.308, 100.1f, 25.1f, 1.0, true,
                true, false, 20.0, true, false, 1.0, 0.25, 0.0);
        double expectedPumpkin = 1.308 * (100.1 / 25.1);
        assertClose(rPumpkin, expectedPumpkin, 1e-4, "Pumpkin Chestplate (+75 mana) restores full % charging rate");

        double rManaRegenGear = DmzSpellbooksFixEvents.computeAdjustedEnergyRegen(
                1.308, 100.1f, 25.1f, 1.5, true,
                true, false, 20.0, true, false, 1.0, 0.25, 0.0);
        assertClose(rManaRegenGear, expectedPumpkin * 1.5, 1e-4, "+50% MANA_REGEN boosts charging by 1.5x");

        double rDrain = DmzSpellbooksFixEvents.computeAdjustedEnergyRegen(
                -5.0, 100.1f, 25.1f, 2.0, false,
                true, true, 20.0, true, true, 1.0, 0.25, 0.0);
        assertClose(rDrain, -5.0, 1e-6, "Negative Ki drain is never amplified");

        double rDebuffFloor = DmzSpellbooksFixEvents.computeAdjustedEnergyRegen(
                2.0, 25.1f, 25.1f, 0.0, true,
                true, false, 20.0, true, false, 1.0, 0.25, 0.0);
        assertClose(rDebuffFloor, 0.5, 1e-6, "Mana regen debuff respects minManaRegenFactor=0.25");

        double rCap = DmzSpellbooksFixEvents.computeAdjustedEnergyRegen(
                1.0, 10000.0f, 10.0f, 1.0, true,
                true, false, 20.0, true, false, 1.0, 0.25, 0.0);
        assertClose(rCap, 20.0, 1e-6, "Inflation ratio capped at maxInflationRatioCap=20.0");

        System.out.println("ALL v1.1.0 VERIFY CHECKS PASSED: "
                + "soulfire=" + soulfireAspects
                + ", sculk=" + sculkAspects
                + ", thunder=" + thunderAspects
                + ", kiDamageDefault=" + testAttr.m_22082_()
                + ", softCap100%=" + DmzSpellbooksFixHelpers.applySoftCapReduction(1.0)
                + ", pumpkinCharge=" + String.format("%.3f", rPumpkin) + " Ki/s");
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError("FAILED: " + msg);
        }
    }

    private static void assertClose(double actual, double expected, double tol, String msg) {
        if (Math.abs(actual - expected) > tol) {
            throw new AssertionError("FAILED: " + msg + " expected=" + expected + " actual=" + actual);
        }
    }
}
