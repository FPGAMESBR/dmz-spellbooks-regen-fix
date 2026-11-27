package com.dmzspellbooksfix;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(DmzSpellbooksFixMod.MOD_ID)
public final class DmzSpellbooksFixMod {
    public static final String MOD_ID = "dmzspellbooksfix";

    public DmzSpellbooksFixMod() {
        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                DmzSpellbooksFixConfig.SPEC,
                "dmz_spellbooks_regen_fix-common.toml"
        );
    }
}
