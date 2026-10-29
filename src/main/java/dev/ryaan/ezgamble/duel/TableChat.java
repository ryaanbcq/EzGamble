package dev.ryaan.ezgamble.duel;

import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.ui.GambleScreen;

/** Placeholder until the port lands: whispers during a game, beside the menu. */
public final class TableChat {
    private TableChat() {}
    public static boolean onChat(String line) { return false; }
    public static void tick() {}
    public static void render(G g, GambleScreen s, double pop) {}
}
