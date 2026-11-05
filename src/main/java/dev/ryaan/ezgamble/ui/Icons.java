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
        if (k.equals("books")) { drawBooks(g, x, y, size); return; }   // the Books tile: fills, no sprite
        if (OWN.contains(k)) g.drawSprite("game/" + k, x, y, size, size);
        else g.drawItem(ICON.getOrDefault(game, "paper"), x + (size - 16) / 2, y + (size - 16) / 2);
    }

    /** Books: a ledger (lavender cover, dark spine, a pale page) with three rising bars on the page, 32 px art scaled to `size`, fills only. */
    public static void drawBooks(G g, double x, double y, double size) {
        double u = size / 32;   // one icon pixel
        P.smooth(g, x + 6 * u, y + 3 * u, 21 * u, 26 * u, 3 * u, P.v(0xFF6A58A8, 0xFF4A3A80), 0x66FBC2EB);   // the cover
        g.fill(x + 6 * u, y + 5 * u, x + 9 * u, y + 27 * u, 0xFF2E2350);   // the spine
        g.fill(x + 11 * u, y + 6 * u, x + 25 * u, y + 26 * u, 0xFFEDE6F7);   // the page
        for (int i = 0; i < 3; i++) g.fill(x + 13 * u, y + (9 + i * 3) * u, x + 23 * u, y + (10 + i * 3) * u, 0x30403060);   // ruled lines
        int[] hs = {4, 7, 10};   // three bars rising, the last one the mod's green
        for (int i = 0; i < 3; i++) g.fill(x + (13 + i * 4) * u, y + (24 - hs[i]) * u, x + (16 + i * 4) * u, y + 24 * u, i == 2 ? 0xFF4BE07A : 0xFF3BBE63);
    }
}
