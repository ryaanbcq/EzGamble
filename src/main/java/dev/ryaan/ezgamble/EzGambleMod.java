package dev.ryaan.ezgamble;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.ui.GambleScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public final class EzGambleMod implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("ezgamble");
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("ezgamble", "main"));
    public static SoundEvent OPEN, CLOSE, PAY, CASH, FAIL;
    private static KeyBinding menuKey, modeKey;

    @Override
    public void onInitializeClient() {
        OPEN = sound("open"); CLOSE = sound("close"); PAY = sound("pay"); CASH = sound("cash"); FAIL = sound("fail");
        GambleData.load();
        Updater.check();
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ezgamble.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        modeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ezgamble.mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GambleData.tick();
            while (menuKey.wasPressed()) if (client.currentScreen == null) client.setScreen(new GambleScreen());
            while (modeKey.wasPressed()) GambleData.setMode(!GambleData.mode);
        });
        // /ezgamble demo [n]: fake bets in the current session, to see the dashboard before the chat detection exists
        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(ClientCommandManager.literal("ezgamble")
                .then(ClientCommandManager.literal("demo")
                        .executes(c -> demo(c.getSource(), 20))
                        .then(ClientCommandManager.argument("n", IntegerArgumentType.integer(1, 500))
                                .executes(c -> demo(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))));
    }

    private static int demo(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src, int n) {
        if (!GambleData.mode) GambleData.setMode(true);
        String[] games = {"Roulette", "Blackjack", "Coinflip", "Crash", "Mines"};
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long t = System.currentTimeMillis() - n * 20_000L;
        for (int i = 0; i < n; i++) {
            long bet = (1 + r.nextInt(40)) * 250_000L;
            long delta = r.nextDouble() < 0.47 ? bet : -bet;
            GambleData.record(new Txn(t + i * 20_000L, games[r.nextInt(games.length)], bet, delta, null, "demo"));
        }
        src.sendFeedback(Text.literal("EzGamble: " + n + " demo bets added to this session."));
        return 1;
    }

    private static SoundEvent sound(String name) {
        Identifier id = Identifier.of("ezgamble", name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
}
