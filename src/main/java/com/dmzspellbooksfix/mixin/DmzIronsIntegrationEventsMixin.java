package com.dmzspellbooksfix.mixin;

import com.dmzspellbooks.DmzIronsIntegrationEvents;
import com.dmzspellbooksfix.DmzSpellbooksFixConfig;
import com.dmzspellbooksfix.DmzSpellbooksFixEvents;
import com.dmzspellbooksfix.DmzSpellbooksFixHelpers;
import com.dragonminez.common.init.MainDamageTypes;
import io.redspace.ironsspellbooks.api.events.ChangeManaEvent;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.events.SpellSummonEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mixin(value = DmzIronsIntegrationEvents.class, remap = false)
public abstract class DmzIronsIntegrationEventsMixin {
    @Shadow(remap = false)
    @Final
    private static Set<UUID> INTERNAL_MANA_SYNC;

    @Unique
    private static final ThreadLocal<Float> dmzspellbooksfix$beforeFortifyAbsorption = new ThreadLocal<>();

    @Inject(method = "onManaChanged", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$allowPositiveManaChange(ChangeManaEvent event, CallbackInfo ci) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (INTERNAL_MANA_SYNC != null && INTERNAL_MANA_SYNC.contains(player.m_20148_())) {
            return;
        }
        if (DmzSpellbooksFixEvents.handleExternalPositiveManaChange(player, event)) {
            ci.cancel();
        }
    }

    @Inject(method = "patchAttributeDefaultValue", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$patchAttributeDefaultSrgSafe(
            Attribute attribute,
            double defaultValue,
            CallbackInfo ci) {
        if (!DmzSpellbooksFixConfig.isReady()
                || !DmzSpellbooksFixConfig.FIX_KI_DAMAGE_DEFAULT_ATTRIBUTE_PATCH.get()) {
            return;
        }
        DmzSpellbooksFixHelpers.patchAttributeDefaultValueSrgSafe(attribute, defaultValue);
        ci.cancel();
    }

    @Inject(method = "isPercentOrAttackDamageBasedSpell", at = @At("RETURN"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$fixFalsePositivePercentExclusion(
            AbstractSpell spell,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()
                && DmzSpellbooksFixConfig.isReady()
                && DmzSpellbooksFixConfig.FIX_SPELL_CAPABILITY_DETECTION.get()
                && DmzSpellbooksFixHelpers.shouldOverridePercentExclusion(spell)) {
            cir.setReturnValue(false);
        }
    }

    @SuppressWarnings("rawtypes")
    @Inject(method = "detectCapabilities", at = @At("RETURN"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$enhanceDetectedCapabilities(
            AbstractSpell spell,
            CallbackInfoReturnable<EnumSet> cir) {
        if (!DmzSpellbooksFixConfig.isReady()) {
            return;
        }
        if (DmzSpellbooksFixHelpers.isSpellExcluded(spell)) {
            EnumSet empty = DmzSpellbooksFixHelpers.createEmptyAspectSet();
            if (empty != null) {
                cir.setReturnValue(empty);
            }
            return;
        }
        if (DmzSpellbooksFixConfig.FIX_SPELL_CAPABILITY_DETECTION.get()) {
            EnumSet enhanced = DmzSpellbooksFixHelpers.enhanceCapabilities(spell, cir.getReturnValue());
            if (enhanced != null) {
                cir.setReturnValue(enhanced);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @Inject(method = "getCapabilities", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$skipExcludedGetCapabilities(
            AbstractSpell spell,
            CallbackInfoReturnable<EnumSet> cir) {
        if (DmzSpellbooksFixHelpers.isSpellExcluded(spell)) {
            EnumSet empty = DmzSpellbooksFixHelpers.createEmptyAspectSet();
            if (empty != null) {
                cir.setReturnValue(empty);
            }
        }
    }

    @Inject(method = "hasScalableAspect", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$skipExcludedScalableAspect(
            AbstractSpell spell,
            CallbackInfoReturnable<Boolean> cir) {
        if (DmzSpellbooksFixHelpers.isSpellExcluded(spell)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getScaledUniqueInfo", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$skipExcludedTooltipScaling(
            AbstractSpell spell,
            int spellLevel,
            LivingEntity caster,
            List<MutableComponent> original,
            CallbackInfoReturnable<List<MutableComponent>> cir) {
        if (DmzSpellbooksFixHelpers.isSpellExcluded(spell)) {
            cir.setReturnValue(original);
        }
    }

    @Inject(method = "onSpellDamage", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$skipExcludedSpellDamage(SpellDamageEvent event, CallbackInfo ci) {
        if (event != null
                && event.getSpellDamageSource() != null
                && DmzSpellbooksFixHelpers.isSpellExcluded(event.getSpellDamageSource().spell())) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "onSpellDamage",
            at = @At(value = "INVOKE", target = "Lio/redspace/ironsspellbooks/api/events/SpellDamageEvent;getOriginalAmount()F"),
            remap = false,
            require = 0
    )
    private static float dmzspellbooksfix$preservePriorSpellDamageModifiers(
            SpellDamageEvent receiver,
            SpellDamageEvent event) {
        if (DmzSpellbooksFixConfig.isReady()
                && DmzSpellbooksFixConfig.PRESERVE_PRIOR_SPELL_DAMAGE_MODIFIERS.get()) {
            return receiver.getAmount();
        }
        return receiver.getOriginalAmount();
    }

    @Inject(method = "onSpellSummon", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$skipExcludedSpellSummon(SpellSummonEvent<?> event, CallbackInfo ci) {
        if (event != null
                && event.getSpellId() != null
                && DmzSpellbooksFixHelpers.isSpellIdExcluded(event.getSpellId().toString())) {
            ci.cancel();
        }
    }

    @Inject(method = "onMobEffectAdded", at = @At("HEAD"), remap = false)
    private static void dmzspellbooksfix$beforeFortifyAdded(MobEffectEvent.Added event, CallbackInfo ci) {
        dmzspellbooksfix$beforeFortifyAbsorption.remove();
        if (!DmzSpellbooksFixConfig.isReady()
                || !DmzSpellbooksFixConfig.FIX_FORTIFY_PERMANENT_ABSORPTION.get()
                || event == null) {
            return;
        }
        MobEffectInstance effectInstance = event.getEffectInstance();
        if (effectInstance != null
                && MobEffectRegistry.FORTIFY.isPresent()
                && effectInstance.m_19544_() == MobEffectRegistry.FORTIFY.get()) {
            float before = DmzSpellbooksFixEvents.beforeFortifyAdded(event.getEntity());
            if (Float.isFinite(before)) {
                dmzspellbooksfix$beforeFortifyAbsorption.set(before);
            }
        }
    }

    @Inject(method = "onMobEffectAdded", at = @At("RETURN"), remap = false)
    private static void dmzspellbooksfix$afterFortifyAdded(MobEffectEvent.Added event, CallbackInfo ci) {
        Float before = dmzspellbooksfix$beforeFortifyAbsorption.get();
        dmzspellbooksfix$beforeFortifyAbsorption.remove();
        if (before != null && event != null) {
            DmzSpellbooksFixEvents.afterFortifyAdded(event.getEntity(), before);
        }
    }

    @Inject(method = "onKiAttackHurt", at = @At("HEAD"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$softCapKiSpellResist(LivingHurtEvent event, CallbackInfo ci) {
        if (!DmzSpellbooksFixConfig.isReady()
                || !DmzSpellbooksFixConfig.APPLY_SOFT_CAP_TO_KI_SPELL_RESIST.get()
                || event == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player player) || event.getAmount() <= 0.0F) {
            return;
        }
        if (!MainDamageTypes.isKiblastDamage(event.getSource()) || !AttributeRegistry.SPELL_RESIST.isPresent()) {
            return;
        }
        AttributeInstance attr = player.m_21051_(AttributeRegistry.SPELL_RESIST.get());
        if (attr == null) {
            return;
        }
        double rawReduction = Math.max(0.0D, attr.m_22135_() - attr.m_22099_().m_22082_());
        if (rawReduction > 0.0D) {
            float updated = DmzSpellbooksFixHelpers.computeSoftCappedKiDamage(event.getAmount(), rawReduction);
            event.setAmount(updated);
        }
        ci.cancel();
    }

    @ModifyConstant(
            method = {"findActiveSpell", "lambda$findNearbyActiveCast$14"},
            constant = @Constant(longValue = 5L),
            remap = false,
            require = 0
    )
    private static long dmzspellbooksfix$extendActiveCastWindow(long original) {
        if (DmzSpellbooksFixConfig.isReady()) {
            return DmzSpellbooksFixConfig.ACTIVE_CAST_WINDOW_TICKS.get();
        }
        return original;
    }
}
