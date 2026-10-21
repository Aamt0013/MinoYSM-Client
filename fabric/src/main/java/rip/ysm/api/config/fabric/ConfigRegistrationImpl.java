package rip.ysm.api.config.fabric;

import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import net.minecraftforge.common.ForgeConfigSpec;
import net.neoforged.fml.config.ModConfig;

public final class ConfigRegistrationImpl {

    private ConfigRegistrationImpl() {
    }

    public static void register(String modId, ModConfig.Type type, ForgeConfigSpec spec) {
        ConfigRegistry.INSTANCE.register(modId, type, spec);
    }
}
