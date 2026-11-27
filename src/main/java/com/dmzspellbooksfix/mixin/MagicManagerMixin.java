package com.dmzspellbooksfix.mixin;

import com.dmzspellbooks.DmzIronsIntegrationEvents;
import com.dmzspellbooksfix.DmzSpellbooksFixConfig;
import com.dmzspellbooksfix.DmzSpellbooksFixEvents;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MagicManager.class, remap = false)
public abstract class MagicManagerMixin {
    @Inject(method = "regenPlayerMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void dmzspellbooksfix$preservePrecisionAndSync(
            ServerPlayer serverPlayer,
            MagicData playerMagicData,
            CallbackInfoReturnable<Boolean> cir) {
        if (!DmzSpellbooksFixConfig.isReady()
                || !DmzSpellbooksFixConfig.FIX_MAGIC_MANAGER_INT_TRUNCATION_CLAMP.get()) {
            return;
        }
        if (serverPlayer == null
                || playerMagicData == null
                || !AttributeRegistry.MAX_MANA.isPresent()
                || !DmzSpellbooksFixEvents.hasUsableDmzKi(serverPlayer)) {
            return;
        }

        float maxMana = (float) serverPlayer.m_21133_(AttributeRegistry.MAX_MANA.get());
        if (!Float.isFinite(maxMana) || maxMana < 0.0F) {
            maxMana = 0.0F;
        }

        float currentMana = playerMagicData.getMana();
        double configMultiplier = ServerConfigs.MANA_REGEN_MULTIPLIER.get();
        boolean forwardRegen = DmzSpellbooksFixConfig.FORWARD_MAGIC_MANAGER_REGEN_TO_KI.get();

        if (!forwardRegen || configMultiplier <= 0.0D) {
            if (currentMana > maxMana + 1.0E-4F) {
                DmzSpellbooksFixEvents.enterMagicManagerRegen();
                try {
                    playerMagicData.setMana(maxMana);
                } finally {
                    DmzSpellbooksFixEvents.exitMagicManagerRegen();
                }
                cir.setReturnValue(true);
            } else {
                cir.setReturnValue(false);
            }
            return;
        }

        if (Math.abs(currentMana - maxMana) < 1.0E-4F) {
            cir.setReturnValue(false);
            return;
        }

        float manaRegen = AttributeRegistry.MANA_REGEN.isPresent()
                ? (float) serverPlayer.m_21133_(AttributeRegistry.MANA_REGEN.get())
                : 1.0F;
        if (!Float.isFinite(manaRegen)) {
            manaRegen = 1.0F;
        }

        float increment = maxMana * manaRegen * 0.01F * (float) configMultiplier;
        float nextMana = Math.max(0.0F, Math.min(maxMana, currentMana + increment));

        if (Math.abs(nextMana - currentMana) < 1.0E-5F) {
            cir.setReturnValue(false);
            return;
        }

        DmzSpellbooksFixEvents.enterMagicManagerRegen();
        try {
            playerMagicData.setMana(nextMana);
        } finally {
            DmzSpellbooksFixEvents.exitMagicManagerRegen();
        }
        cir.setReturnValue(true);
    }

    @Redirect(
            method = "lambda$tick$0",
            at = @At(value = "INVOKE", target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"),
            remap = false,
            require = 0
    )
    private int dmzspellbooksfix$scaleContinuousSpellManaCheck(
            AbstractSpell spell,
            int spellLevel,
            boolean doManaRegen,
            Player player) {
        int baseCost = spell.getManaCost(spellLevel);
        if (player != null
                && DmzSpellbooksFixConfig.isReady()
                && DmzSpellbooksFixConfig.FIX_CONTINUOUS_SPELL_MANA_CHECK.get()) {
            return DmzIronsIntegrationEvents.getScaledSpellManaCost(player, spell, spellLevel, baseCost);
        }
        return baseCost;
    }
}
