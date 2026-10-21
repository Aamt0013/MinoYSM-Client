package rip.ysm.api.client.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;

public final class KeyMappingFactoryImpl {

    private KeyMappingFactoryImpl() {
    }

    public static KeyMapping createInGameAlt(String name, InputConstants.Type type, int keyCode, KeyMapping.Category category) {
        return new KeyMapping(name, type, keyCode, category);
    }

    public static KeyMapping createInGameNone(String name, InputConstants.Type type, int keyCode, KeyMapping.Category category) {
        return new KeyMapping(name, type, keyCode, category);
    }

    public static boolean isActiveAndMatches(KeyMapping keyMapping, KeyEvent event) {
        return keyMapping.matches(event);
    }
}
