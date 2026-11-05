package dev.ryaan.ezgamble.lobby;

import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;

/** The lobby on the in-game HUD: a "Lobby N" pill by the session timer. The join news is a hud.Banners toast (Lobby.onChat). */
public final class LobbyHud {
    private LobbyHud() {}

    /**
     * "Lobby N" on the session card's title row, right against the timer (which sits flush right), when someone waits.
     * The clock's room is reserved by its format class ("00:00" or "0:00:00"), not its live width: the pill does not
     * flip to the short form when the tenth minute makes the clock a digit wider.
     */
    public static void sessionPill(G g, int W, String clock) {
        int n = Lobby.size();
        if (n == 0) return;
        String v = "Lobby " + n; int w = F.width(v) + 6;
        double left = 7 + F.width("Session") + 4, clockW = F.width(clock.length() >= 7 ? "0:00:00" : "00:00"), x = W - 7 - clockW - 4 - w;
        if (x < left) { v = "L" + n; w = F.width(v) + 6; x = W - 7 - clockW - 4 - w; }   // a long clock (hours) or a two-digit line on a narrow card: the short form
        if (x < left) return;
        P.smooth(g, x, 4, w, 12, 6, P.alpha(P.LAV, 0.2), P.alpha(P.LAV, 0.33));
        g.drawText(v, x + 3, 6, P.PINK, true);
    }
}
