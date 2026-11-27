package com.dmzspellbooksfix.mixin;

import com.dmzspellbooksfix.DmzSpellbooksFixConfig;
import com.dmzspellbooksfix.DmzSpellbooksFixEvents;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.player.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ServerPlayerEvents.class, remap = false)
public abstract class ServerPlayerEventsMixin {
    @Redirect(
            method = "onPlayerRespawn",
            at = @At(value = "INVOKE", target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;setMana(F)V"),
            remap = false,
            require = 0
    )
    private static void dmzspellbooksfix$preventRespawnKiWipe(
            MagicData magicData,
            float newMana,
            PlayerEvent.PlayerRespawnEvent event) {
        if (DmzSpellbooksFixConfig.isReady()
                && DmzSpellbooksFixConfig.PREVENT_RESPAWN_KI_WIPE.get()
                && event.getEntity() instanceof ServerPlayer player
                && DmzSpellbooksFixEvents.hasUsableDmzKi(player)) {
            return;
        }
        magicData.setMana(newMana);
    }
}
