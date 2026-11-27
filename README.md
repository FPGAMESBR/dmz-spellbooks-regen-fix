# DMZ Spellbooks Regen Fix

A Minecraft **Forge 1.20.1** companion fix mod (`modId: dmzspellbooksfix`) for **Dragon Mine Z**, **Iron's Spells 'n Spellbooks**, and **DMZ Spellbooks** that resolves integration bugs between Ki and Mana systems.

## Features

- **Ki / Mana Regeneration Scaling (`DmzSpellbooksFixEvents` & `MagicManagerMixin`):**
  - Compensates Dragon Mine Z Ki charging when wearing Iron's Spells `+Max Mana` and `+Mana Regen` gear without breaking passive Ki behavior.
  - Fixes integer-truncation clamping in `MagicManager` and preserves positive Mana-to-Ki grants while preventing unintended passive Mana regen forwarding.
- **Respawn Ki Protection (`ServerPlayerEventsMixin`):**
  - Prevents Iron's Spells respawn handlers from wiping a player's Ki on respawn.
- **Combat & Spell Integration Fixes (`DmzIronsIntegrationEventsMixin`, `UtilsMixin`, `TechniqueTimingBridgeMixin`):**
  - **Counterspell vs. Ki Projectiles:** Recognizes Dragon Mine Z Ki projectiles as valid anti-magic targets and optionally interrupts charging Ki players.
  - **Fortify Permanent Absorption Fix:** Cleans up extra absorption granted by scaled Fortify casts upon expiration instead of leaking permanent golden hearts.
  - **Continuous Spell Mana Check & Capability Detection:** Fixes false-positive percent-based exclusions (e.g., `SoulfireRaySpell`) and improves capability detection for Iron's Spells and addon spells (e.g., TravelOptics).
  - **Excluded Spell IDs Enforcement:** Properly enforces `excludedSpellIds` from `dmz_spellbooks-common.toml`.
  - **SRG `ki_damage` Default Attribute Patch:** Safely patches `Attribute.defaultValue` (`f_22076_`) in production SRG environments.
  - **Soft Caps:** Applies Iron's Spells-style asymptotic soft caps to Ki Spell Resist and Technique Timing reductions (preventing 100% godmode immunity).
- Fully configurable via `config/dmz_spellbooks_regen_fix-common.toml`.

## Requirements

- **Minecraft:** `1.20.1`
- **Forge:** `47+`
- **Dragon Mine Z:** `2.1.2+`
- **Iron's Spells 'n Spellbooks:** `1.20.1-3.16.2+`
- **DMZ Spellbooks:** `1.0.0+`

## Building from Source

Requires **JDK 17** and **Python 3.10+**, plus a local Forge 1.20.1 libraries folder and the companion mod JARs (`dragonminez`, `irons_spellbooks`, `dmz_spellbooks`):

```bash
# Optional overrides (defaults to ~/curseforge/minecraft/Install and JDK 17 on PATH/JAVA_HOME)
set CF_INSTALL=path/to/minecraft/Install
set MODS_DIR=path/to/mods_folder
python build.py
```

The compiled JAR is output to `build/libs/dmz_spellbooks_regen_fix-1.1.0.jar`, followed by metadata verification and unit tests (`VerifyFix`).

## License

Licensed under the [MIT License](LICENSE).
