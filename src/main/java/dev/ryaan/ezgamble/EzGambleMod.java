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
        dev.ryaan.ezgamble.proto.Snd.register();
        OPEN = dev.ryaan.ezgamble.proto.Snd.event("open"); CLOSE = dev.ryaan.ezgamble.proto.Snd.event("close"); PAY = dev.ryaan.ezgamble.proto.Snd.event("pay");
        CASH = dev.ryaan.ezgamble.proto.Snd.event("cash"); FAIL = dev.ryaan.ezgamble.proto.Snd.event("fail");
        GambleData.load();
        dev.ryaan.ezgamble.duel.Duel.register();
        Updater.check();
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ezgamble.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        modeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ezgamble.mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GambleData.tick();
            dev.ryaan.ezgamble.data.Payments.tick();
            dev.ryaan.ezgamble.data.Payouts.tick();
            dev.ryaan.ezgamble.auction.Auction.tick();
            dev.ryaan.ezgamble.duel.Duel.tick();
            dev.ryaan.ezgamble.duel.TableChat.tick();
            dev.ryaan.ezgamble.games.Ticks.tick();
            DebugRun.tick(client);
            while (menuKey.wasPressed()) if (client.currentScreen == null) client.setScreen(new GambleScreen());
            while (modeKey.wasPressed()) GambleData.setMode(!GambleData.mode);
        });
        // read every line BEFORE the other mods: EzChat hides whispers (ALLOW_GAME returns false) and the later GAME /
        // CHAT events never fire for them; duel challenges and table commands are whispers. We only look, never block.
        var allowGame = net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME;
        var allowChat = net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_CHAT;
        Identifier early = Identifier.of("ezgamble", "before_others");
        allowGame.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        allowChat.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.COMMAND.register(c -> dev.ryaan.ezgamble.data.Payouts.typed(c));
        allowGame.register(early, (m, overlay) -> { if (!overlay) onChat(m.getString()); return true; });
        allowChat.register(early, (m, signed, sender, params, ts) -> { onChat(m.getString()); return true; });
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(Identifier.of("ezgamble", "hud"), new dev.ryaan.ezgamble.hud.GambleHud());
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(Identifier.of("ezgamble", "auction"), new dev.ryaan.ezgamble.auction.AuctionHud());
        // /ezgamble demo [n]: fake bets in the current session, to see the dashboard before the chat detection exists
        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(ClientCommandManager.literal("ezgamble")
                .then(ClientCommandManager.literal("demo")
                        .executes(c -> demo(c.getSource(), 20))
                        .then(ClientCommandManager.argument("n", IntegerArgumentType.integer(1, 500))
                                .executes(c -> demo(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))));
    }

    private static int demo(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src, int n) {
        demoBets(n);
        src.sendFeedback(Text.literal("EzGamble: " + n + " demo bets added to this session."));
        return 1;
    }

    /** Every game chat line (system and player chat), plain text: the prototype's serverSays order. */
    public static void onChat(String line) {
        if (DebugRun.ON) DebugRun.heard(line);
        try {
            if (dev.ryaan.ezgamble.data.Payouts.onChat(line)) return;
            if (dev.ryaan.ezgamble.data.Payments.onChat(line)) return;
            boolean duel = dev.ryaan.ezgamble.duel.Duel.onChat(line);   // a challenge counts even while a game is open (it waits for the menu to close)
            if (dev.ryaan.ezgamble.duel.TableChat.onChat(line) || duel) return;
            var tx = dev.ryaan.ezgamble.data.Detector.detect(line, System.currentTimeMillis());
            if (tx != null) GambleData.record(tx);
        } catch (Exception e) {
            LOG.warn("[ezgamble] chat line failed: {}", e.toString());
        }
    }

    public static void demoBets(int n) {
        if (!GambleData.mode) GambleData.setMode(true);
        String[] games = {"Roulette", "Blackjack", "Coinflip", "Crash", "Mines"};
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long t = System.currentTimeMillis() - n * 20_000L;
        for (int i = 0; i < n; i++) {
            long bet = (1 + r.nextInt(40)) * 250_000L;
            long delta = r.nextDouble() < 0.47 ? bet : -bet;
            GambleData.record(new Txn(t + i * 20_000L, games[r.nextInt(games.length)], bet, delta, null, "demo"));
        }
    }

}
