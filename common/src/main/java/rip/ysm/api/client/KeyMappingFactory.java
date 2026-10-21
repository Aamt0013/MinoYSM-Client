package rip.ysm.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

public final class KeyMappingFactory {

    public static final KeyMapping.Category YSM_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("yes_steve_model", "main"));

    private KeyMappingFactory() {
    }

    @ExpectPlatform
    public static KeyMapping createInGameAlt(String name, InputConstants.Type type, int keyCode, KeyMapping.Category category) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static KeyMapping createInGameNone(String name, InputConstants.Type type, int keyCode, KeyMapping.Category category) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean isActiveAndMatches(KeyMapping keyMapping, KeyEvent event) {
        throw new AssertionError();
    }
}
