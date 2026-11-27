package com.dmzspellbooksfix.mixin;

import com.dmzspellbooks.spellki.TechniqueTimingBridge;
import com.dmzspellbooksfix.DmzSpellbooksFixConfig;
import com.dmzspellbooksfix.DmzSpellbooksFixHelpers;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TechniqueTimingBridge.class, remap = false)
public abstract class TechniqueTimingBridgeMixin {
    @Inject(method = "getReduction", at = @At("RETURN"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$applySoftCapToTechniqueReduction(
            Attribute attribute,
            CallbackInfoReturnable<Double> cir) {
        if (!DmzSpellbooksFixConfig.isReady()
                || !DmzSpellbooksFixConfig.APPLY_SOFT_CAP_TO_TECHNIQUE_TIMING.get()) {
            return;
        }
        double rawReduction = cir.getReturnValueD();
        if (Double.isFinite(rawReduction) && rawReduction > 0.0D) {
            cir.setReturnValue(DmzSpellbooksFixHelpers.applySoftCapReduction(rawReduction));
        }
    }
}
