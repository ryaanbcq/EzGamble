package dev.ryaan.ezgamble;

import dev.ryaan.ezgamble.ui.GambleScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EzGambleMod implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("ezgamble");
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("ezgamble", "main"));
    public static SoundEvent OPEN, CLOSE, PAY, CASH, FAIL;
    private static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        OPEN = sound("open"); CLOSE = sound("close"); PAY = sound("pay"); CASH = sound("cash"); FAIL = sound("fail");
        Updater.check();
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ezgamble.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) if (client.currentScreen == null) client.setScreen(new GambleScreen());
        });
    }

    private static SoundEvent sound(String name) {
        Identifier id = Identifier.of("ezgamble", name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
