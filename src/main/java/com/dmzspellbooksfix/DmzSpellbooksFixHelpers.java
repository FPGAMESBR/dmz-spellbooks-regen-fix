package com.dmzspellbooksfix;

import com.dmzspellbooks.DmzSpellbooksConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DmzSpellbooksFixHelpers {
    private static final Pattern CLASS_REF_PATTERN = Pattern.compile("([a-zA-Z0-9_$]+(?:/[a-zA-Z0-9_$]+){2,})");
    private static final Pattern UI_KEY_PATTERN = Pattern.compile("ui\\.[a-z0-9_.\\-]+");
    private static final int MAX_PAYLOAD_CLASSES = 10;

    private static volatile boolean aspectsResolved = false;
    private static Class<? extends Enum> aspectEnumClass;
    private static Enum aspectDamage;
    private static Enum aspectHealing;
    private static Enum aspectAbsorption;
    private static Enum aspectBarrier;
    private static Enum aspectSummon;

    private DmzSpellbooksFixHelpers() {
    }

    /**
     * Applies Iron's Spells' official softCapFormula to a positive attribute bonus delta (value - 1.0):
     * - For rawReduction <= 0.5 (<= +50%): linear (unchanged).
     * - For rawReduction > 0.5 (> +50%): asymptotic 1.0 - (0.25 / rawReduction), never reaching 1.0.
     */
    public static double applySoftCapReduction(double rawReduction) {
        if (!Double.isFinite(rawReduction) || rawReduction <= 0.0D) {
            return 0.0D;
        }
        if (rawReduction <= 0.5D) {
            return rawReduction;
        }
        double capped = 1.0D - (0.25D / rawReduction);
        return Math.max(0.0D, Math.min(0.999D, capped));
    }

    /**
     * Computes post-resistance Ki Blast damage using Iron's Spells' soft-cap curve.
     */
    public static float computeSoftCappedKiDamage(float incomingDamage, double rawSpellResistReduction) {
        if (!Float.isFinite(incomingDamage) || incomingDamage <= 0.0F) {
            return incomingDamage;
        }
        double cappedReduction = applySoftCapReduction(rawSpellResistReduction);
        if (cappedReduction <= 0.0D) {
            return incomingDamage;
        }
        return (float) Math.max(0.0D, (double) incomingDamage * (1.0D - cappedReduction));
    }

    /**
     * SRG-safe patch for Attribute.defaultValue (SRG: f_22076_, Mojmap: defaultValue).
     */
    public static boolean patchAttributeDefaultValueSrgSafe(Attribute attribute, double value) {
        if (attribute == null || !Double.isFinite(value)) {
            return false;
        }
        for (String fieldName : new String[]{"f_22076_", "defaultValue"}) {
            try {
                Field field = Attribute.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.setDouble(attribute, value);
                return true;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    public static boolean isSpellExcluded(AbstractSpell spell) {
        if (spell == null) {
            return false;
        }
        return isSpellIdExcluded(spell.getSpellId());
    }

    public static boolean isSpellIdExcluded(String spellId) {
        if (spellId == null || spellId.isBlank()) {
            return false;
        }
        if (!DmzSpellbooksFixConfig.isReady() || !DmzSpellbooksFixConfig.ENFORCE_EXCLUDED_SPELL_IDS.get()) {
            return false;
        }
        if (!DmzSpellbooksConfig.SPEC.isLoaded()) {
            return false;
        }
        List<? extends String> excluded = DmzSpellbooksConfig.EXCLUDED_SPELL_IDS.get();
        if (excluded == null || excluded.isEmpty()) {
            return false;
        }
        String normalized = spellId.trim().toLowerCase(Locale.ROOT);
        for (String entry : excluded) {
            if (entry != null && normalized.equals(entry.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true when a spell was falsely flagged as percent/attack-damage-only
     * (e.g., SoulfireRaySpell mentioning 'reduced_healing' for its debuff while dealing flat SpellDamage).
     */
    public static boolean shouldOverridePercentExclusion(AbstractSpell spell) {
        if (spell == null) {
            return false;
        }
        return shouldOverridePercentExclusionForClass(spell.getClass());
    }

    public static boolean shouldOverridePercentExclusionForClass(Class<?> spellClass) {
        if (spellClass == null) {
            return false;
        }
        String raw = readClassBytesAsText(spellClass);
        return shouldOverridePercentExclusionFromText(raw);
    }

    public static boolean shouldOverridePercentExclusionFromText(String rawClassBytes) {
        if (rawClassBytes == null || rawClassBytes.isEmpty()) {
            return false;
        }
        String low = rawClassBytes.toLowerCase(Locale.ROOT);
        if (hasHardPercentKeyword(low)) {
            return false;
        }
        return hasSoftDebuffKeyword(low) && hasFlatDamageUiKey(low);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static EnumSet createEmptyAspectSet() {
        ensureAspectEnumResolved();
        if (aspectEnumClass == null) {
            return null;
        }
        return EnumSet.noneOf(aspectEnumClass);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static EnumSet enhanceCapabilities(AbstractSpell spell, EnumSet original) {
        if (spell == null) {
            return createEmptyAspectSet();
        }
        return enhanceCapabilitiesForClass(spell.getClass(), spell.getSpellId(), original);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static EnumSet enhanceCapabilitiesForClass(Class<?> spellClass, String spellId, EnumSet original) {
        ensureAspectEnumResolved();
        if (aspectEnumClass == null) {
            return original;
        }
        EnumSet result = EnumSet.noneOf(aspectEnumClass);
        if (spellClass == null) {
            return result;
        }
        if (isSpellIdExcluded(spellId)) {
            return result;
        }
        if (original != null) {
            result.addAll(original);
        }

        String rawSpell = readClassBytesAsText(spellClass);
        if (rawSpell.isEmpty()) {
            return result;
        }

        String combined = collectSpellAndPayloadBytes(spellClass, rawSpell);
        String spellLow = rawSpell.toLowerCase(Locale.ROOT);
        String combinedLow = combined.toLowerCase(Locale.ROOT);

        boolean percentOnly = isTrulyPercentOrAttackDamageOnly(spellLow);

        if (!percentOnly && aspectDamage != null && !result.contains(aspectDamage)) {
            boolean hasPayloadDamage = combined.contains("SpellDamageEvent")
                    || combined.contains("SpellDamageSource")
                    || combined.contains("DamageSources")
                    || combined.contains("setDamage")
                    || combined.contains("setAoeDamageAmount");
            boolean hasIronsDamageUi = hasFlatDamageUiKey(spellLow)
                    && (combined.contains("io/redspace/ironsspellbooks/entity/")
                    || combined.contains("io/redspace/ironsspellbooks/damage/")
                    || rawSpell.contains("getDamage")
                    || rawSpell.contains("getDamageFromAmplifier"));
            if (hasPayloadDamage || hasIronsDamageUi) {
                result.add(aspectDamage);
            }
        }

        if (!percentOnly && aspectHealing != null && !result.contains(aspectHealing)) {
            if (combined.contains("SpellHealEvent") || combined.contains("HealingAoe")) {
                result.add(aspectHealing);
            }
        }

        if (aspectAbsorption != null && !result.contains(aspectAbsorption)) {
            if (combined.contains("FORTIFY")) {
                result.add(aspectAbsorption);
            }
        }

        if (aspectBarrier != null && !result.contains(aspectBarrier)) {
            if (combined.contains("AbstractShieldEntity")
                    || combined.contains("ShieldEntity")
                    || combined.contains("getShieldHP")) {
                result.add(aspectBarrier);
            }
        }

        if (aspectSummon != null && !result.contains(aspectSummon)) {
            if (combined.contains("SpellSummonEvent")
                    || combined.contains("SetSummonOwnerEvent")
                    || combined.contains("SummonManager")
                    || combinedLow.contains("imagic_summon")) {
                result.add(aspectSummon);
            }
        }

        return result;
    }

    private static boolean hasHardPercentKeyword(String low) {
        return low.contains("percent_damage")
                || low.contains("percent_healing")
                || low.contains("healing_received")
                || low.contains("getpercentdamage")
                || low.contains("additional_poisoned_damage")
                || low.contains("attack_damage_per_level")
                || low.contains("echoing_strikes");
    }

    private static boolean hasSoftDebuffKeyword(String low) {
        return low.contains("reduced_damage")
                || low.contains("reduced_healing")
                || low.contains("damage_reduction")
                || low.contains("damage_resistance")
                || low.contains("damage_taken")
                || low.contains("resistance_effect");
    }

    private static boolean isTrulyPercentOrAttackDamageOnly(String spellLow) {
        if (hasHardPercentKeyword(spellLow)) {
            return true;
        }
        return hasSoftDebuffKeyword(spellLow) && !hasFlatDamageUiKey(spellLow);
    }

    private static boolean hasFlatDamageUiKey(String low) {
        Matcher matcher = UI_KEY_PATTERN.matcher(low);
        while (matcher.find()) {
            String key = matcher.group();
            if (key.contains("damage") && !isPercentageTooltipKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPercentageTooltipKey(String key) {
        return key.contains("percent")
                || key.contains("reduced_damage")
                || key.contains("reduced_healing")
                || key.contains("damage_reduction")
                || key.contains("damage_resistance")
                || key.contains("damage_taken")
                || key.contains("reduction")
                || key.contains("additional_poisoned_damage");
    }

    private static String collectSpellAndPayloadBytes(Class<?> spellClass, String rawSpell) {
        String internalName = spellClass.getName().replace('.', '/');
        String[] parts = internalName.split("/");
        if (parts.length < 3) {
            return rawSpell;
        }
        String rootPrefix = parts[0] + "/" + parts[1] + "/";
        StringBuilder sb = new StringBuilder(rawSpell.length() + 4096);
        sb.append(rawSpell);

        Set<String> seen = new HashSet<>();
        seen.add(internalName);
        int loaded = 0;

        Matcher matcher = CLASS_REF_PATTERN.matcher(rawSpell);
        while (matcher.find() && loaded < MAX_PAYLOAD_CLASSES) {
            String candidate = matcher.group(1);
            if (!candidate.startsWith(rootPrefix) || !seen.add(candidate)) {
                continue;
            }
            if (!isSpellPayloadClass(candidate)) {
                continue;
            }
            String payloadBytes = readResourceBytesAsText(spellClass, "/" + candidate + ".class");
            if (!payloadBytes.isEmpty()) {
                sb.append('\n').append(payloadBytes);
                loaded++;
            }
        }
        return sb.toString();
    }

    public static boolean isSpellPayloadClass(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            return false;
        }
        if (internalName.contains("/api/")
                || internalName.contains("/registry/")
                || internalName.contains("/registries/")
                || internalName.contains("/config/")
                || internalName.contains("/init/")
                || internalName.contains("/util/")
                || internalName.contains("/utils/")
                || internalName.contains("/capabilities/")
                || internalName.contains("/network/")
                || internalName.contains("/sound/")
                || internalName.contains("AbstractSpell")) {
            return false;
        }
        String low = internalName.toLowerCase(Locale.ROOT);
        return low.contains("/entity/")
                || low.contains("/entities/")
                || low.contains("/effect/")
                || low.contains("/effects/")
                || low.endsWith("entity")
                || low.endsWith("projectile")
                || low.endsWith("aoe");
    }

    private static String readClassBytesAsText(Class<?> clazz) {
        if (clazz == null) {
            return "";
        }
        String resourcePath = "/" + clazz.getName().replace('.', '/') + ".class";
        return readResourceBytesAsText(clazz, resourcePath);
    }

    private static String readResourceBytesAsText(Class<?> anchor, String resourcePath) {
        try (InputStream is = anchor.getResourceAsStream(resourcePath)) {
            if (is == null) {
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException ignored) {
            return "";
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void ensureAspectEnumResolved() {
        if (aspectsResolved) {
            return;
        }
        synchronized (DmzSpellbooksFixHelpers.class) {
            if (aspectsResolved) {
                return;
            }
            try {
                Class<?> raw = Class.forName("com.dmzspellbooks.DmzIronsIntegrationEvents$SpellScalingAspect");
                if (raw.isEnum()) {
                    Class<? extends Enum> enumCls = (Class<? extends Enum>) raw;
                    aspectEnumClass = enumCls;
                    aspectDamage = Enum.valueOf(enumCls, "DAMAGE");
                    aspectHealing = Enum.valueOf(enumCls, "HEALING");
                    aspectAbsorption = Enum.valueOf(enumCls, "ABSORPTION");
                    aspectBarrier = Enum.valueOf(enumCls, "BARRIER");
                    aspectSummon = Enum.valueOf(enumCls, "SUMMON");
                }
            } catch (Throwable ignored) {
            } finally {
                aspectsResolved = true;
            }
        }
    }
}
