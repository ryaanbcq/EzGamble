package dev.ryaan.ezgamble;

import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.ui.GambleScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Unattended smoke test: when config/ezgamble-debug exists, its lines are run one by one once a world is loaded,
 * then the game quits. Commands (GUI coordinates):
 *   size W H, gui N, wait T, open, close, tab ID, sub ID, range ID, mode on|off, demo N, chat LINE,
 *   mouse X Y, click X Y, rclick X Y, key CODE, type TEXT, scroll X Y AMOUNT, shot NAME, perf NAME, run JAVA_HOOK, quit
 * Every step is logged "[ezgamble] debug: ...". perf = average render time of the current screen over 60 frames.
 */
public final class DebugRun {
    private DebugRun() {}

    private static final Path TRIGGER = FabricLoader.getInstance().getConfigDir().resolve("ezgamble-debug");
    public static final boolean ON = Files.exists(TRIGGER);
    private static List<String> lines = new ArrayList<>();
    private static int pc, waitUntil, ticks, startTick = -1;
    private static boolean done;
    /** Screen render timing (filled by GambleScreen.render when ON). */
    public static long renderNanos;
    public static int renderFrames;
    public static long chartNanos, dataNanos;
    /** Forced pointer for the screens (GUI px), -1 = the real one. */
    public static int mx = -1, my = -1;
    private static String perfName;
    /** waitchat TEXT: hold the script until a chat line containing TEXT arrives (2 min at most). */
    private static volatile String waitChat;
    private static volatile boolean heard;

    public static void heard(String line) { String w = waitChat; if (w != null && line.contains(w)) heard = true; }
    private static int perfUntil;

    public static void tick(MinecraftClient client) {
        if (!ON || done) return;
        if (client.player == null) return;
        ticks++;
        if (startTick < 0) {
            startTick = ticks;
            try { lines = Files.readAllLines(TRIGGER); } catch (Exception e) { lines = List.of("quit"); }
            waitUntil = ticks + 60;
            return;
        }
        if (perfName != null && ticks >= perfUntil) {
            double ms = renderFrames == 0 ? -1 : renderNanos / 1e6 / renderFrames;
            EzGambleMod.LOG.info("[ezgamble] debug: perf {} = {} ms per frame over {} frames, fps {}, liquid {} ms and {} fills per frame", perfName, String.format("%.2f", ms), renderFrames, client.getCurrentFps(),
                    String.format("%.2f", dev.ryaan.ezgamble.ui.Liquid.F.nanos / 1e6 / Math.max(1, renderFrames)), dev.ryaan.ezgamble.ui.Liquid.F.n / Math.max(1, renderFrames));
            EzGambleMod.LOG.info("[ezgamble] debug: fills by surface {}, chart {} ms, data {} ms", dev.ryaan.ezgamble.ui.Liquid.F.by, chartNanos / 1e6 / Math.max(1, renderFrames), dataNanos / 1e6 / Math.max(1, renderFrames));
            perfName = null;
        }
        if (waitChat != null) {
            if (!heard && ticks < waitUntil) return;
            EzGambleMod.LOG.info("[ezgamble] debug: waitchat {} -> {}", waitChat, heard ? "heard" : "timeout");
            waitChat = null; waitUntil = ticks;
        }
        while (!done && ticks >= waitUntil && pc < lines.size()) {
            String line = lines.get(pc++).trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            EzGambleMod.LOG.info("[ezgamble] debug: {}", line);
            try { if (!step(client, line)) break; } catch (Exception e) { EzGambleMod.LOG.error("[ezgamble] debug: step failed: " + line, e); }
        }
        if (pc >= lines.size() && ticks >= waitUntil && !done) { done = true; client.scheduleStop(); }
    }

    /** @return false to stop for this tick (a wait). */
    private static boolean step(MinecraftClient c, String line) {
        String[] a = line.split(" ", 2);
        String cmd = a[0], arg = a.length > 1 ? a[1] : "";
        String[] n = arg.split(" ");
        double s = c.getWindow().getScaleFactor();
        switch (cmd) {
            case "size" -> GLFW.glfwSetWindowSize(c.getWindow().getHandle(), Integer.parseInt(n[0]), Integer.parseInt(n[1]));
            case "gui" -> { c.options.getGuiScale().setValue(Integer.parseInt(arg)); c.onResolutionChanged(); }
            case "waitchat" -> { heard = false; waitChat = arg; waitUntil = ticks + 2400; return false; }
            case "wait" -> { waitUntil = ticks + Integer.parseInt(arg); return false; }
            case "open" -> c.setScreen(new GambleScreen());
            case "close" -> c.setScreen(null);
            case "tab", "sub", "range", "hook" -> GambleScreen.debug(cmd, arg);
            case "name" -> GambleData.setName(arg);
            case "paid" -> dev.ryaan.ezgamble.data.Payments.received(n[0], dev.ryaan.ezgamble.Money.parse(n[1]));
            case "mode" -> GambleData.setMode(arg.equals("on"));
            case "lens" -> GambleData.lens = arg.equals("on");
            case "cmd" -> c.getNetworkHandler().sendChatCommand(arg);
            case "srv" -> {   // run as the integrated server's console (full permission): tellraw reaches us as a real chat packet
                var srv = c.getServer();
                if (srv != null) srv.execute(() -> srv.getCommandManager().parseAndExecute(srv.getCommandSource(), arg));
            }
            case "wager" -> {   // wager NAME AMOUNT [N]: N bets of AMOUNT placed with NAME's chips (High rollers)
                int k = n.length > 2 ? Integer.parseInt(n[2]) : 1; long amt = dev.ryaan.ezgamble.Money.parse(n[1]);
                for (int i = 0; i < k; i++) GambleData.record(new dev.ryaan.ezgamble.data.Txn(System.currentTimeMillis() + i, "Blackjack", amt, i % 2 == 0 ? amt : -amt, n[0], "debug " + n[0] + " " + i));
            }
            case "demo" -> EzGambleMod.demoBets(Integer.parseInt(arg));
            case "chat" -> { c.inGameHud.getChatHud().addMessage(Text.literal(arg)); EzGambleMod.onChat(arg); }
            case "mouse" -> { mx = (int) Double.parseDouble(n[0]); my = (int) Double.parseDouble(n[1]); dev.ryaan.ezgamble.proto.ProtoScreen.forceMx = mx; dev.ryaan.ezgamble.proto.ProtoScreen.forceMy = my; }
            case "click", "rclick" -> {
                double x = coord(n[0], true), y = coord(n[1], false);
                mx = (int) x; my = (int) y; dev.ryaan.ezgamble.proto.ProtoScreen.forceMx = mx; dev.ryaan.ezgamble.proto.ProtoScreen.forceMy = my;
                if (c.currentScreen != null) {
                    Click ck = new Click(x, y, new MouseInput(cmd.equals("click") ? 0 : 1, 0));
                    c.currentScreen.mouseClicked(ck, false);
                    c.currentScreen.mouseReleased(ck);
                }
            }
            case "press", "move", "up" -> {   // a held click in three steps (the lens drag)
                double x = coord(n[0], true), y = coord(n[1], false);
                mx = (int) x; my = (int) y; dev.ryaan.ezgamble.proto.ProtoScreen.forceMx = mx; dev.ryaan.ezgamble.proto.ProtoScreen.forceMy = my;
                if (c.currentScreen != null) {
                    Click ck = new Click(x, y, new MouseInput(0, 0));
                    if (cmd.equals("press")) c.currentScreen.mouseClicked(ck, false);
                    else if (cmd.equals("move")) c.currentScreen.mouseDragged(ck, 0, 0);
                    else c.currentScreen.mouseReleased(ck);
                }
            }
            case "key" -> { if (c.currentScreen != null) c.currentScreen.keyPressed(new KeyInput(Integer.parseInt(arg), 0, 0)); }
            case "type" -> { if (c.currentScreen != null) for (int i = 0; i < arg.length(); i++) c.currentScreen.charTyped(new CharInput(arg.charAt(i), 0)); }
            case "scroll" -> { if (c.currentScreen != null) c.currentScreen.mouseScrolled(coord(n[0], true), coord(n[1], false), 0, Double.parseDouble(n[2])); }
            case "shot" -> ScreenshotRecorder.saveScreenshot(c.runDirectory, "ezgamble-" + arg + ".png", c.getFramebuffer(), 1, t -> {});
            case "perf" -> { if (c.currentScreen instanceof dev.ryaan.ezgamble.proto.ProtoScreen) {} renderNanos = 0; renderFrames = 0; dev.ryaan.ezgamble.ui.Liquid.F.n = 0; dev.ryaan.ezgamble.ui.Liquid.F.nanos = 0; dev.ryaan.ezgamble.ui.Liquid.F.by.clear(); chartNanos = 0; dataNanos = 0; perfName = arg; perfUntil = ticks + 60; waitUntil = ticks + 62; return false; }
            case "quit" -> { done = true; c.scheduleStop(); return false; }
            default -> EzGambleMod.LOG.warn("[ezgamble] debug: unknown command {}", cmd);
        }
        return true;
    }

    /** "123" = GUI px; "@49" = relative to the menu panel (x0 / y0), the same whatever the window size. */
    private static double coord(String s, boolean isX) {
        if (!s.startsWith("@")) return Double.parseDouble(s);
        dev.ryaan.ezgamble.ui.GambleScreen g = dev.ryaan.ezgamble.ui.GambleScreen.current();
        double base = g == null ? 0 : isX ? g.x0 : g.y0;
        return base + Double.parseDouble(s.substring(1));
    }
}
