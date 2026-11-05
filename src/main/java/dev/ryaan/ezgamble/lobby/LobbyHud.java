package dev.ryaan.ezgamble.lobby;

import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;

/** The lobby on the in-game HUD: a "Lobby N" pill by the session timer. The join news is a hud.Banners toast (Lobby.onChat). */
public final class LobbyHud {
    private LobbyHud() {}

    /**
     * "Lobby N" on the session card's title row, right against the timer (which sits flush right), when someone waits.
     * The clock's room is reserved by its format class with every digit zeroed ("00:00", "0:00:00", "00:00:00"), not its
     * live width: the pill does not flip to the short form when the tenth minute makes the clock a digit wider, and it keeps
     * the clock's digit count so it can never sit on the timer once the session passes ten hours (digits are all 6 px).
     */
    public static void sessionPill(G g, int W, String clock) {
        int n = Lobby.size();
        if (n == 0) return;
        String v = "Lobby " + n; int w = F.width(v) + 6;
        double left = 7 + F.width("Session") + 4, clockW = F.width(clock.length() <= 4 ? "00:00" : clock.replaceAll("\\d", "0")), x = W - 7 - clockW - 4 - w;
        if (x < left) { v = "L" + n; w = F.width(v) + 6; x = W - 7 - clockW - 4 - w; }   // a long clock (hours) or a two-digit line on a narrow card: the short form
        if (x < left) return;
        P.smooth(g, x, 4, w, 12, 6, P.alpha(P.LAV, 0.2), P.alpha(P.LAV, 0.33));
        g.drawText(v, x + 3, 6, P.PINK, true);
    }
}
