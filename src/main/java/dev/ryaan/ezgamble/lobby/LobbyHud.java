package dev.ryaan.ezgamble.lobby;

import dev.ryaan.ezgamble.hud.HudConfig;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.ui.Icons;
import dev.ryaan.ezgamble.vip.Vip;
import dev.ryaan.ezgamble.vip.VipBadge;

/** The lobby on the in-game HUD: a "Lobby N" pill by the session timer, and a slide when somebody joins the line. */
public final class LobbyHud {
    private LobbyHud() {}

    /** "Lobby N" on the session card's title row, right against the timer (which sits flush right), when someone waits. */
    public static void sessionPill(G g, int W, String clock) {
        int n = Lobby.size();
        if (n == 0) return;
        String v = "Lobby " + n; int w = F.width(v) + 8;
        double x = W - 7 - F.width(clock) - 5 - w;
        P.smooth(g, x, 4, w, 12, 6, 0x33A18CD1, 0x55A18CD1);
        g.drawText(v, x + 4, 6, P.PINK, true);
    }

    /**
     * "Name joined the line for Blackjack": a glass pill slides down from the top of the screen for 2.5 s (GambleHud.drawTierUp's
     * pattern); it takes the row under the tier-up banner when both show at once.
     */
    public static void drawJoin(G g) {
        Lobby.Seat d = Lobby.lastJoin;
        if (d == null) return;
        long e = P.now() - Lobby.joinAt;
        double k = e < 250 ? P.easeOut(e / 250.0) : e < Lobby.JOIN_SLIDE_MS - 250 ? 1 : e < Lobby.JOIN_SLIDE_MS ? 1 - P.easeIn((e - (Lobby.JOIN_SLIDE_MS - 250)) / 250.0) : 0;
        if (k <= 0) return;
        String a = d.name(), b = " joined the line for ", c = d.game();
        int bw = VipBadge.w(a), w = 7 + bw + F.width(a) + F.width(b) + 14 + F.width(c) + 7, h = 17;
        var up = Vip.lastTierUp();
        boolean under = up != null && P.now() - up.at() < 3000;
        double x = Math.round((HudConfig.sw() - w) / 2.0), y = Math.round((under ? 8 + h + 4 : 8) - (1 - k) * (h + 10));
        P.glass(g, x, y, w, h, 8, 0.75, 0.6);
        P.ring(g, x, y, w, h, 8, P.alpha(P.LAV, 0.55 * k), 1);
        double tx = x + 7;
        if (bw > 0) { VipBadge.draw(g, a, tx, y + 5); tx += bw; }
        g.drawText(a, tx, y + 5, P.WHITE, true); tx += F.width(a);
        g.drawText(b, tx, y + 5, P.MUTE, true); tx += F.width(b);
        Icons.drawGameIcon(g, c, tx, y + 3, 11); tx += 14;
        g.drawText(c, tx, y + 5, P.PINK, true);
    }
}
