# DMZ Spellbooks Regen Fix

[![CurseForge](https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/dmz-spellbooks-regen-fix)
[![Minecraft 1.20.1](https://img.shields.io/badge/Minecraft-1.20.1-62B47A?style=for-the-badge)](https://www.curseforge.com/minecraft/mc-mods/dmz-spellbooks-regen-fix)
[![Forge 47+](https://img.shields.io/badge/Loader-Forge%2047%2B-1E3A5F?style=for-the-badge)](https://files.minecraftforge.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](LICENSE)

> **Official Download (CurseForge):** https://www.curseforge.com/minecraft/mc-mods/dmz-spellbooks-regen-fix

A Minecraft **Forge 1.20.1** companion fix mod (`modId: dmzspellbooksfix`) for **Dragon Mine Z**, **Iron's Spells 'n Spellbooks**, and **DMZ Spellbooks** that fixes Ki/Mana synchronization, combat interaction, SRG reflection, and balance bugs across all three mods.

---

## Why This Mod Exists

**DMZ Spellbooks** unifies Iron's Spells Mana with Dragon Mine Z's Ki pool (`Ki = Mana`), but several subtle edge cases occur in production Forge 1.20.1 modpacks:
- Wearing `+Max Mana` mage armor inflates max energy without scaling DMZ's native Ki charge rate, making Ki charging feel dramatically slower while `+Mana Regeneration` gear has no effect on active Ki charging.
- `MagicManager.regenPlayerMana` truncates Max Mana to `int`, pulling DMZ's `CurrentEnergy` downward every 10 ticks, and `manaSpawnPercent=0` wipes refilled Ki on respawn.
- Counterspell cannot raycast DMZ Ki projectiles (`AbstractKiProjectile`), scaled Fortify leaks permanent golden absorption hearts, continuous spells drain inconsistently, `+100% Spell Resist` grants 100% godmode immunity against Ki attacks, and Mojmap-only reflection (`Attribute.defaultValue`) fails in production SRG.

---

## Features & Technical Fixes

### 1. Ki Charging & Mana Regeneration Scaling (`DmzSpellbooksFixEvents`, `MagicManagerMixin`, `ServerPlayerEventsMixin`)
- **Max Mana Charge Compensation:** Scales active DMZ Ki charging by `currentMaxEnergy / nativeMaxKi` (capped at `20.0×`) so `+Max Mana` gear never slows down percentage Ki charging.
- **Mana Regeneration Attribute Integration:** Applies Iron's Spells `MANA_REGEN` attribute (from armor, curios, enchantments, potions) to active Ki charging (passive Ki regen stays untouched by default to preserve DMZ balance).
- **Int-Truncation Clamp Fix:** Prevents `MagicManager` from truncating Max Mana and dragging DMZ `CurrentEnergy` downward every 10 ticks.
- **Positive Mana Grants Preserved:** Allows instant external mana gains (Mana Potions, Gluttony, Soul Siphon, Instant Mana) to restore DMZ Ki instead of being silently dropped.
- **Respawn Ki Protection:** Prevents Iron's Spells respawn handlers (`manaSpawnPercent=0`) from wiping DMZ's refilled Ki back to `0`.

### 2. Combat, Spell Scaling & SRG Bug Fixes (`DmzIronsIntegrationEventsMixin`, `UtilsMixin`, `TechniqueTimingBridgeMixin`, `DmzSpellbooksFixHelpers`)
- **Counterspell vs. Ki Projectiles:** Extends `Utils.validAntiMagicTarget(Entity)` to recognize `AbstractKiProjectile` and optionally interrupts charging `ServerPlayer` Ki attacks.
- **Fortify Permanent Absorption Leak Fix:** Tracks Ki-scaled bonus absorption granted by Fortify and cleanly removes it on expiration, removal, or recast.
- **Continuous Spell Mana Checks:** Uses Ki-scaled mana costs inside `MagicManager` continuous-cast ticks (fixing Flamethrower, Electrocute, Ray of Frost, etc.).
- **Spell Capability Detection & Addon Support:** Performs 1-hop payload/entity/effect inspection, fixes false-positive percent exclusions (e.g., `SoulfireRaySpell`), and improves scaling for Iron's Spells addons such as **T.O Magic 'n Extras** while avoiding false inflation on vanilla-damage spells.
- **`excludedSpellIds` Enforcement:** Properly enforces `integration.excludedSpellIds` from `dmz_spellbooks-common.toml` across damage, healing, summons, tooltips, and mana cost scaling.
- **SRG `ki_damage` Default Attribute Patch:** Adds `f_22076_` SRG fallback alongside Mojmap `defaultValue` so `dragonminez:ki_damage` patches reliably in production Forge 1.20.1.
- **Asymptotic Soft Caps:** Applies Iron's Spells' official `Utils.softCapFormula` to Ki Spell Resist (`+100%` -> `75%` reduction instead of `100%` immunity) and DMZ Technique Cast/Cooldown Reduction.
- **Prior Damage Modifier Preservation & Extended Cast Window:** Uses `SpellDamageEvent.getAmount()` instead of `getOriginalAmount()` and extends delayed-cast matching from `5` ticks to `40` ticks.

---

## Configuration (`config/dmz_spellbooks_regen_fix-common.toml`)

### `[KiRegenCompensation]` & `[ManaRegenAttribute]`
| Option | Default | Description |
|---|---:|---|
| `compensateMaxManaWhileCharging` | `true` | Scales active Ki charging by `currentMaxEnergy / nativeMaxKi` |
| `compensateMaxManaPassively` | `false` | Optional passive Ki regen compensation |
| `maxInflationRatioCap` | `20.0` | Upper cap for Max Mana compensation multiplier |
| `applyManaRegenWhileCharging` | `true` | Applies Iron's Spells `MANA_REGEN` to active Ki charging |
| `applyManaRegenPassively` | `false` | Optional passive Ki regen scaling via `MANA_REGEN` |
| `manaRegenAttributeWeight` / `minManaRegenFactor` | `1.0` / `0.25` | Weight and debuff floor for `MANA_REGEN` scaling |
| `extraPercentMaxEnergyPerSecondWhenCharging` | `0.0` | Optional extra `%` max energy/sec while charging (`0.0` disables) |

### `[IronsManaSyncFixes]`
| Option | Default | Description |
|---|---:|---|
| `fixMagicManagerIntTruncationClamp` | `true` | Stops `MagicManager` int-truncation clamp from draining Ki |
| `allowPositiveManaGrantsToKi` | `true` | Forwards positive external mana gains (potions, siphon) to Ki |
| `forwardMagicManagerRegenToKi` | `false` | Optional forwarding of Iron's passive Mana regen into Ki |
| `preventRespawnKiWipe` | `true` | Stops `manaSpawnPercent=0` from zeroing Ki on respawn |

### `[DmzSpellbooksBugFixes]`
| Option | Default | Description |
|---|---:|---|
| `fixCounterspellVsKiProjectiles` | `true` | Allows Counterspell raycasts to cancel DMZ Ki projectiles |
| `counterspellInterruptsChargingKiPlayer` | `true` | Interrupts charging Ki projectiles when Counterspell hits the caster |
| `fixFortifyPermanentAbsorption` | `true` | Removes Ki-scaled bonus absorption cleanly when Fortify ends |
| `fixContinuousSpellManaCheck` | `true` | Aligns continuous-cast tick mana checks with Ki-scaled cost |
| `fixSpellCapabilityDetection` | `true` | Enhances spell capability detection and fixes false-positive exclusions |
| `enforceExcludedSpellIds` | `true` | Enforces `excludedSpellIds` from `dmz_spellbooks-common.toml` |
| `fixKiDamageDefaultAttributePatch` | `true` | Fixes SRG `f_22076_` reflection for `dragonminez:ki_damage` |
| `applySoftCapToKiSpellResist` | `true` | Applies soft cap to Spell Resist vs. Ki attacks |
| `applySoftCapToTechniqueTiming` | `true` | Applies soft cap to Technique Cast/Cooldown reduction |
| `preservePriorSpellDamageModifiers` | `true` | Preserves buffs/crits from earlier `SpellDamageEvent` listeners |
| `activeCastWindowTicks` | `40` | Tick window to match delayed heals, barriers, Fortify, and summons |

---

## Requirements & Compatibility

- **Minecraft:** `1.20.1`
- **Mod Loader:** Forge `47+`
- **Required Mods:** Dragon Mine Z (`2.1.2+`), Iron's Spells 'n Spellbooks (`1.20.1-3.16.2+`), DMZ Spellbooks (`1.0.0+`)
- **Side:** Server & Client (safe to add or remove anytime)

---

## Building from Source

Requires **JDK 17** and **Python 3.10+**, plus a local Forge 1.20.1 installation and the companion mod JARs (`dragonminez`, `irons_spellbooks`, `dmz_spellbooks`):

```bash
# Optional overrides (defaults to ~/curseforge/minecraft/Install and JDK 17 on PATH/JAVA_HOME)
set CF_INSTALL=path/to/minecraft/Install
set MODS_DIR=path/to/mods_folder
python build.py
```

Outputs `build/libs/dmz_spellbooks_regen_fix-1.1.0.jar`, verifies Mixin metadata, and runs `VerifyFix` (validating runtime Mixin signatures, SRG attribute patching, soft caps, capability detection, Fortify cleanup, and regen math).

---

## Related Dragon Mine Z Companion Mods

| Mod | CurseForge | GitHub |
|---|---|---|
| **DMZCA Perf Fix** | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/dmzca-perf-fix) | [GitHub](https://github.com/FPGAMESBR/dmzca-perf-fix) |
| **DMZ Ultra WorldGen Fix** | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/dmz-ultra-worldgen-fix) | [GitHub](https://github.com/FPGAMESBR/dmz-ultra-worldgen-fix) |
| **DMZ Spellbooks Regen Fix** | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/dmz-spellbooks-regen-fix) | [GitHub](https://github.com/FPGAMESBR/dmz-spellbooks-regen-fix) |
| **DMZ Apothic & Vanilla Scaling** | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/dmz-apothic-vanilla-scaling) | [GitHub](https://github.com/FPGAMESBR/dmz-apothic-vanilla-scaling) |

## License

Licensed under the [MIT License](LICENSE).
