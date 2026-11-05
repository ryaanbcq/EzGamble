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
        if (k.equals("events")) { drawEvents(g, x, y, size); return; }   // drawn with fills, no sprite
        if (k.equals("books")) { drawBooks(g, x, y, size); return; }   // the Books tile: fills, no sprite
        if (OWN.contains(k)) g.drawSprite("game/" + k, x, y, size, size);
        else g.drawItem(ICON.getOrDefault(game, "paper"), x + (size - 16) / 2, y + (size - 16) / 2);
    }

    /** Events (events.Events): a gold stopwatch on a 32 px grid, its hand near the top of the hour, a pink pin at the centre. */
    private static void drawEvents(G g, double x, double y, double size) {
        double u = size / 32;
        int gold = 0xFFF2C14E, dark = 0xFFC8902E, face = 0x2EF2C14E;
        g.fill(x + 13 * u, y + 1 * u, x + 19 * u, y + 3 * u, gold);   // the crown button and its stem
        g.fill(x + 14.5 * u, y + 3 * u, x + 17.5 * u, y + 6 * u, dark);
        g.fill(x + 23 * u, y + 5 * u, x + 26 * u, y + 7 * u, dark);   // the side button
        dev.ryaan.ezgamble.proto.P.smooth(g, x + 4 * u, y + 6 * u, 24 * u, 24 * u, 12 * u, face, gold);   // the case
        dev.ryaan.ezgamble.proto.P.smooth(g, x + 7 * u, y + 9 * u, 18 * u, 18 * u, 9 * u, 0x30000000, 0x40FFFFFF);   // the dial
        int tick = 0xFFCFC8DE;
        g.fill(x + 15.5 * u, y + 10 * u, x + 16.5 * u, y + 12 * u, tick); g.fill(x + 15.5 * u, y + 24 * u, x + 16.5 * u, y + 26 * u, tick);   // 12 and 6
        g.fill(x + 8 * u, y + 17.5 * u, x + 10 * u, y + 18.5 * u, tick); g.fill(x + 22 * u, y + 17.5 * u, x + 24 * u, y + 18.5 * u, tick);   // 9 and 3
        g.fill(x + 15.5 * u, y + 12 * u, x + 16.5 * u, y + 18 * u, 0xFFFFFFFF);   // the big hand, up
        g.fill(x + 16 * u, y + 17.5 * u, x + 21 * u, y + 18.5 * u, 0xFFFFFFFF);   // the small hand, to 3
        g.fill(x + 15 * u, y + 17 * u, x + 17 * u, y + 19 * u, 0xFFFBC2EB);   // the pin
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
