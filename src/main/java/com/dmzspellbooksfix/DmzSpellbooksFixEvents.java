package com.dmzspellbooksfix;

import com.dmzspellbooks.DmzIronsIntegrationEvents;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.init.entities.ki.AbstractKiProjectile;
import com.dragonminez.common.network.StatsDeltaSync;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Status;
import io.redspace.ironsspellbooks.api.events.ChangeManaEvent;
import io.redspace.ironsspellbooks.api.events.CounterSpellEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = DmzSpellbooksFixMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DmzSpellbooksFixEvents {
    private static final Map<UUID, Long> LAST_KI_CHARGE_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> FORTIFY_EXTRA_ABSORPTION = new ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> MAGIC_MANAGER_REGEN_DEPTH = ThreadLocal.withInitial(() -> 0);

    private DmzSpellbooksFixEvents() {
    }

    public static void enterMagicManagerRegen() {
        MAGIC_MANAGER_REGEN_DEPTH.set(MAGIC_MANAGER_REGEN_DEPTH.get() + 1);
    }

    public static void exitMagicManagerRegen() {
        int next = MAGIC_MANAGER_REGEN_DEPTH.get() - 1;
        if (next <= 0) {
            MAGIC_MANAGER_REGEN_DEPTH.remove();
        } else {
            MAGIC_MANAGER_REGEN_DEPTH.set(next);
        }
    }

    public static boolean isInMagicManagerRegen() {
        return MAGIC_MANAGER_REGEN_DEPTH.get() > 0;
    }

    public static boolean hasUsableDmzKi(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        return hasUsableKiResource(data);
    }

    public static boolean hasUsableKiResource(StatsData data) {
        return data != null
                && data.getStatus() != null
                && data.getStatus().isHasCreatedCharacter()
                && Float.isFinite(data.getMaxEnergy())
                && data.getMaxEnergy() > 1.0E-4F;
    }

    public static boolean handleExternalPositiveManaChange(ServerPlayer player, ChangeManaEvent event) {
        if (!DmzSpellbooksFixConfig.isReady() || player == null || event == null) {
            return false;
        }

        boolean fromMagicManager = isInMagicManagerRegen();
        if (fromMagicManager) {
            if (!DmzSpellbooksFixConfig.FORWARD_MAGIC_MANAGER_REGEN_TO_KI.get()) {
                return false;
            }
        } else if (!DmzSpellbooksFixConfig.ALLOW_POSITIVE_MANA_GRANTS_TO_KI.get()) {
            return false;
        }

        StatsData data = StatsProvider.get(StatsCapability.INSTANCE, player).orElse(null);
        if (!hasUsableKiResource(data)) {
            return false;
        }

        float maxEnergy = data.getMaxEnergy();
        float currentEnergy = clamp(data.getResources().getCurrentEnergy(), 0.0F, maxEnergy);
        float requestedNewMana = event.getNewMana();
        if (!Float.isFinite(requestedNewMana)) {
            return false;
        }
        float clampedNew = clamp(requestedNewMana, 0.0F, maxEnergy);

        if (clampedNew <= currentEnergy + 1.0E-5F) {
            return false;
        }

        data.getResources().setCurrentEnergy(clampedNew);
        event.setNewMana(clampedNew);
        StatsDeltaSync.markDirty(player);
        return true;
    }

    /**
     * Strips any previously tracked Fortify extra absorption before DMZ Spellbooks adds a new bonus,
     * preventing infinite absorption stacking when recasting Fortify.
     */
    public static float beforeFortifyAdded(LivingEntity entity) {
        if (entity == null || entity.m_9236_().f_46443_) {
            return Float.NaN;
        }
        clearFortifyExtraAbsorption(entity);
        return entity.m_6103_();
    }

    /**
     * Records the exact extra absorption delta added by DMZ Spellbooks so it can be removed cleanly
     * when Fortify expires or is removed.
     */
    public static void afterFortifyAdded(LivingEntity entity, float beforeAbsorption) {
        if (entity == null || entity.m_9236_().f_46443_ || !Float.isFinite(beforeAbsorption)) {
            return;
        }
        float afterAbsorption = entity.m_6103_();
        float addedExtra = afterAbsorption - beforeAbsorption;
        if (Float.isFinite(addedExtra) && addedExtra > 1.0E-4F) {
            FORTIFY_EXTRA_ABSORPTION.put(entity.m_20148_(), addedExtra);
        } else {
            FORTIFY_EXTRA_ABSORPTION.remove(entity.m_20148_());
        }
    }

    public static void clearFortifyExtraAbsorption(LivingEntity entity) {
        if (entity == null || entity.m_9236_().f_46443_) {
            return;
        }
        Float extra = FORTIFY_EXTRA_ABSORPTION.remove(entity.m_20148_());
        if (extra != null && Float.isFinite(extra) && extra > 0.0F) {
            float updated = computePostRemovalAbsorption(entity.m_6103_(), extra);
            entity.m_7911_(updated);
        }
    }

    public static float computePostRemovalAbsorption(float currentAbsorption, float trackedExtra) {
        if (!Float.isFinite(currentAbsorption) || currentAbsorption <= 0.0F) {
            return 0.0F;
        }
        if (!Float.isFinite(trackedExtra) || trackedExtra <= 0.0F) {
            return currentAbsorption;
        }
        return Math.max(0.0F, currentAbsorption - trackedExtra);
    }

    private static boolean isFortifyEffect(MobEffect effect) {
        return effect != null && MobEffectRegistry.FORTIFY.isPresent() && effect == MobEffectRegistry.FORTIFY.get();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobEffectExpired(MobEffectEvent.Expired event) {
        if (!DmzSpellbooksFixConfig.isReady() || !DmzSpellbooksFixConfig.FIX_FORTIFY_PERMANENT_ABSORPTION.get()) {
            return;
        }
        MobEffectInstance inst = event.getEffectInstance();
        if (inst != null && isFortifyEffect(inst.m_19544_())) {
            clearFortifyExtraAbsorption(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobEffectRemoved(MobEffectEvent.Remove event) {
        if (event.isCanceled()
                || !DmzSpellbooksFixConfig.isReady()
                || !DmzSpellbooksFixConfig.FIX_FORTIFY_PERMANENT_ABSORPTION.get()) {
            return;
        }
        if (isFortifyEffect(event.getEffect())) {
            clearFortifyExtraAbsorption(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() != null) {
            FORTIFY_EXTRA_ABSORPTION.remove(event.getEntity().m_20148_());
        }
    }

    @SubscribeEvent
    public static void onCounterSpell(CounterSpellEvent event) {
        if (event.isCanceled()
                || !DmzSpellbooksFixConfig.isReady()
                || !DmzSpellbooksFixConfig.COUNTERSPELL_INTERRUPTS_CHARGING_KI_PLAYER.get()) {
            return;
        }
        if (event.target instanceof ServerPlayer targetPlayer && !targetPlayer.m_9236_().f_46443_) {
            List<AbstractKiProjectile> charging = targetPlayer.m_284548_().m_6443_(
                    AbstractKiProjectile.class,
                    targetPlayer.m_20191_().m_82400_(8.0D),
                    proj -> proj != null && proj.isOwner(targetPlayer) && !proj.isFiring()
            );
            for (AbstractKiProjectile projectile : charging) {
                projectile.m_146870_();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onKiCharge(DMZEvent.KiChargeEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.m_9236_().f_46443_) {
            return;
        }
        LAST_KI_CHARGE_TICK.put(player.m_20148_(), player.m_9236_().m_46467_());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEnergyRegen(DMZEvent.EnergyRegenEvent event) {
        if (!DmzSpellbooksFixConfig.isReady() || event.isCanceled()) {
            return;
        }
        Player rawPlayer = event.getPlayer();
        if (rawPlayer == null || rawPlayer.m_9236_().f_46443_ || !(rawPlayer instanceof ServerPlayer player)) {
            return;
        }

        double baseAmount = event.getAmount();
        if (!Double.isFinite(baseAmount) || baseAmount <= 0.0D) {
            return;
        }

        StatsData statsData = event.getStatsData();
        if (!hasUsableKiResource(statsData)) {
            return;
        }

        UUID playerId = player.m_20148_();
        Long chargeTick = LAST_KI_CHARGE_TICK.remove(playerId);
        long now = player.m_9236_().m_46467_();
        Status status = statsData.getStatus();
        boolean isCharging = (chargeTick != null && chargeTick == now)
                || (status != null && (status.isChargingKi() || status.isForcedCharge()));

        float currentMaxEnergy = statsData.getMaxEnergy();
        float nativeMaxKi = DmzIronsIntegrationEvents.getNativeMaxKi(player, statsData);
        double manaRegenVal = AttributeRegistry.MANA_REGEN.isPresent()
                ? player.m_21133_(AttributeRegistry.MANA_REGEN.get())
                : 1.0D;

        double updatedAmount = computeAdjustedEnergyRegen(
                baseAmount,
                currentMaxEnergy,
                nativeMaxKi,
                manaRegenVal,
                isCharging,
                DmzSpellbooksFixConfig.COMPENSATE_MAX_MANA_WHILE_CHARGING.get(),
                DmzSpellbooksFixConfig.COMPENSATE_MAX_MANA_PASSIVELY.get(),
                DmzSpellbooksFixConfig.MAX_INFLATION_RATIO_CAP.get(),
                DmzSpellbooksFixConfig.APPLY_MANA_REGEN_WHILE_CHARGING.get(),
                DmzSpellbooksFixConfig.APPLY_MANA_REGEN_PASSIVELY.get(),
                DmzSpellbooksFixConfig.MANA_REGEN_ATTRIBUTE_WEIGHT.get(),
                DmzSpellbooksFixConfig.MIN_MANA_REGEN_FACTOR.get(),
                DmzSpellbooksFixConfig.EXTRA_PERCENT_MAX_ENERGY_PER_SEC_WHEN_CHARGING.get()
        );
        if (Double.isFinite(updatedAmount) && updatedAmount >= 0.0D) {
            event.setAmount(updatedAmount);
        }
    }

    /**
     * Pure deterministic Ki regen formula shared by event handling and offline verification.
     */
    public static double computeAdjustedEnergyRegen(
            double baseAmount,
            float currentMaxEnergy,
            float nativeMaxKi,
            double manaRegenValue,
            boolean isCharging,
            boolean compensateCharging,
            boolean compensatePassively,
            double maxInflationRatioCap,
            boolean applyManaRegenCharging,
            boolean applyManaRegenPassively,
            double manaRegenWeight,
            double minManaRegenFactor,
            double extraPercentWhenCharging) {
        if (!Double.isFinite(baseAmount) || baseAmount <= 0.0D) {
            return baseAmount;
        }

        double maxManaRatio = 1.0D;
        boolean shouldScaleRatio = isCharging ? compensateCharging : compensatePassively;
        if (shouldScaleRatio
                && Float.isFinite(nativeMaxKi)
                && Float.isFinite(currentMaxEnergy)
                && nativeMaxKi > 1.0E-4F
                && currentMaxEnergy > nativeMaxKi) {
            double rawRatio = (double) currentMaxEnergy / (double) nativeMaxKi;
            maxManaRatio = Math.min(Math.max(1.0D, maxInflationRatioCap), Math.max(1.0D, rawRatio));
        }

        double manaRegenFactor = 1.0D;
        boolean shouldApplyManaRegen = isCharging ? applyManaRegenCharging : applyManaRegenPassively;
        if (shouldApplyManaRegen && Double.isFinite(manaRegenValue)) {
            manaRegenFactor = Math.max(minManaRegenFactor, 1.0D + (manaRegenValue - 1.0D) * manaRegenWeight);
        }

        double extraPerSec = 0.0D;
        if (isCharging && extraPercentWhenCharging > 0.0D && Float.isFinite(currentMaxEnergy) && currentMaxEnergy > 0.0F) {
            extraPerSec = (double) currentMaxEnergy * (extraPercentWhenCharging / 100.0D) * manaRegenFactor;
        }

        double result = baseAmount * maxManaRatio * manaRegenFactor + extraPerSec;
        return Double.isFinite(result) && result >= 0.0D ? result : baseAmount;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            UUID id = event.getEntity().m_20148_();
            LAST_KI_CHARGE_TICK.remove(id);
            FORTIFY_EXTRA_ABSORPTION.remove(id);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_KI_CHARGE_TICK.clear();
        FORTIFY_EXTRA_ABSORPTION.clear();
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
