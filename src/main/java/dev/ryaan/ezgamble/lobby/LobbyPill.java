package dev.ryaan.ezgamble.lobby;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Widget;
import dev.ryaan.ezgamble.ui.GambleScreen;
import dev.ryaan.ezgamble.vip.VipBadge;

/**
 * "Next in line" on a felt: a pill top right of the header (the row of the tabs, free on a game) with the next player
 * for this game, his gem and the amount he plays; a click seats him. While Auto-seat counts down it reads
 * "Next: Name in 3...", and for 2.5 s after a refused seating, why. Nothing is drawn when nobody waits for this game.
 */
public final class LobbyPill extends Widget {
    private final GambleScreen s; private final String game;

    /** Added by GambleScreen.init2 when the sub is a game felt. */
    public static void add(GambleScreen s) {
        String game = Lobby.gameOf(GambleScreen.sub);
        if (game != null) s.add(new LobbyPill(s, game));
    }

    private LobbyPill(GambleScreen s, String game) { super(0, s.y0 + 34, 0, 16); this.s = s; this.game = game; }   // 16 px like the buttons of that row

    @Override
    public void render(G g, double mx, double my) {
        Lobby.Seat d = Lobby.next(game); int n = Lobby.pendingFor(game);
        long auto = Lobby.autoEnd(GambleScreen.sub);
        Lobby.Seat es = Lobby.errSeat(); String err = es != null && es.game().equals(game) ? Lobby.errText() : null;
        if (d == null && n == 0 && err == null) { w = 0; return; }
        String label, head, rest = ""; int vc; boolean gem = false;   // value = head + rest, the head (his name) trimmed to the room
        if (err != null) { label = ""; head = err; vc = P.RED; }
        else if (auto > 0 && d != null) { label = "Next:"; head = d.name(); rest = " in " + Math.max(1, (auto - P.now() + 999) / 1000) + "..."; vc = P.PINK; gem = true; }
        else if (d != null) { label = "Next"; head = d.name(); rest = " " + Money.format(Lobby.amountOf(d)) + (n > 1 ? " +" + (n - 1) : ""); vc = P.WHITE; gem = true; }
        else { label = "Next"; head = n + " short of chips"; vc = P.RED; }   // everyone in line for this game is short
        int bw = gem ? VipBadge.w(d.name()) : 0;
        // the room right of the tabs segmented (Dashboard / History / Games / HUD) on the same row, 6 px apart: measured, not assumed
        int room = (int) (s.x0 + GambleScreen.W - GambleScreen.PAD - s.tabsRight() - 6);
        String value = F.trimToWidth(head, room - P.pillW(label, rest) - bw) + rest;
        w = P.pillW(label, value) + bw;
        x = s.x0 + GambleScreen.W - GambleScreen.PAD - w;
        boolean hot = d != null && err == null, hover = hot && over(mx, my);
        // the theme's lavender and pink (Themes.apply rewrites P.LAV / P.PINK): on Emerald the pill stayed purple beside green tabs
        P.smooth(g, x, y, w, 16, 8, hover ? P.alpha(P.LAV, 0.23) : hot ? P.alpha(P.LAV, 0.13) : 0x10FFFFFF, hover ? P.alpha(P.PINK, 0.56) : hot ? P.alpha(P.LAV, 0.31) : 0);
        double tx = x + 7;
        if (!label.isEmpty()) { g.drawText(label, tx, y + 4, P.MUTE, false); tx += F.width(label) + 4; }
        if (bw > 0) { VipBadge.draw(g, d.name(), tx, y + 4); tx += bw; }
        g.drawText(value, tx, y + 4, hover ? P.WHITE : vc, false);
    }

    @Override
    public void click(double mx, double my) {
        Lobby.Seat d = Lobby.next(game);
        if (w == 0 || d == null || Lobby.errText() != null) return;
        P.play("click");
        Lobby.seat(d);
    }
}
