package com.dmzspellbooksfix.mixin;

import com.dmzspellbooksfix.DmzSpellbooksFixConfig;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Utils.class, remap = false)
public abstract class UtilsMixin {
    @Inject(method = "validAntiMagicTarget", at = @At("RETURN"), cancellable = true, remap = false)
    private static void dmzspellbooksfix$allowKiProjectilesInCounterspell(
            Entity entity,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
                && entity instanceof AbstractKiProjectile
                && !entity.m_5833_()
                && DmzSpellbooksFixConfig.isReady()
                && DmzSpellbooksFixConfig.FIX_COUNTERSPELL_VS_KI_PROJECTILES.get()) {
            cir.setReturnValue(true);
        }
    }
}
