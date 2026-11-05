package dev.ryaan.ezgamble.ui;

import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A game's own 32 px icon drawn at `size`; a game without one (Slots, Buckshot) keeps its item. */
public final class Icons {
    private Icons() {}
    private static final Set<String> OWN = Set.of("blackjack", "auction", "mines", "crates", "roulette", "crash", "coinflip");
    private static final Map<String, String> ICON = Map.of("Roulette", "clock", "Blackjack", "paper", "Coinflip", "gold_nugget", "Crash", "firework_rocket",
            "Slots", "emerald", "Buckshot", "crossbow", "Crates", "chest_minecart");

    public static void drawGameIcon(G g, String game, double x, double y, double size) {
        String k = String.valueOf(game).toLowerCase(Locale.ROOT);
        if (k.equals("jackpot")) { drawJackpot(g, x, y, size); return; }   // the Jackpot tile and its rows: fills, no sprite
        if (OWN.contains(k)) g.drawSprite("game/" + k, x, y, size, size);
        else g.drawItem(ICON.getOrDefault(game, "paper"), x + (size - 16) / 2, y + (size - 16) / 2);
    }

    /** Jackpot: a big gold coin (rim, face, a crescent of light) over two coins lying behind it, a sparkle top right. 32 px art scaled to `size`, fills only. */
    public static void drawJackpot(G g, double x, double y, double size) {
        double u = size / 32;   // one icon pixel
        int gold = 0xFFF2C14E, dark = 0xFF9A6A1C, light = 0xFFFFF0B0;
        P.smooth(g, x + 2 * u, y + 22 * u, 18 * u, 8 * u, 4 * u, 0xFFC8902E, dark);    // the coins lying behind, edge on
        P.smooth(g, x + 12 * u, y + 24 * u, 18 * u, 8 * u, 4 * u, 0xFFD9A62E, dark);
        P.smooth(g, x + 5 * u, y + 3 * u, 22 * u, 22 * u, 11 * u, P.v(light, gold), dark);   // the big coin: rim and face
        P.ring(g, x + 8 * u, y + 6 * u, 16 * u, 16 * u, 8 * u, 0x80B8821E, Math.max(1, u));   // the inner ring of the face
        g.fill(x + 15 * u, y + 9 * u, x + 17 * u, y + 19 * u, dark);                      // a "$" stroke: the bar and its two bowls
        g.fill(x + 12 * u, y + 10 * u, x + 20 * u, y + 12 * u, dark); g.fill(x + 12 * u, y + 16 * u, x + 20 * u, y + 18 * u, dark);
        g.fill(x + 12 * u, y + 12 * u, x + 14 * u, y + 14 * u, dark); g.fill(x + 18 * u, y + 14 * u, x + 20 * u, y + 16 * u, dark);
        g.fill(x + 12 * u, y + 13 * u, x + 20 * u, y + 15 * u, dark);
        g.fill(x + 26 * u, y + 1 * u, x + 28 * u, y + 7 * u, light); g.fill(x + 24 * u, y + 3 * u, x + 30 * u, y + 5 * u, light);   // the sparkle
    }
}
