package dev.ryaan.ezgamble.recap;

import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.hud.HudConfig;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The only thing the HUD draws with gambling mode off: for 6 s after a session with bets closes, a small glass pill
 * at the top of the screen, "Session over · press K then Recap", sliding in from above and back out.
 */
public final class RecapHint {
    private RecapHint() {}

    private static final long SHOW = 6000, IN = 300, OUT = 350;

    public static void draw(G g) {
        long at = Recap.readyAt;
        if (at < 0) return;
        long e = now() - at;
        if (e > SHOW) return;
        double k = e < IN ? easeOut(e / (double) IN) : e > SHOW - OUT ? 1 - easeIn((e - (SHOW - OUT)) / (double) OUT) : 1;
        String a = "Session over", b = "press K then Recap";
        double w = F.width(a) + F.width(b) + 10 + 20, h = 18, x = HudConfig.sw() / 2 - w / 2, y = 8 - (1 - k) * (h + 10);
        g.push();
        try {
            glass(g, x, y, w, h, 9, 0.8, 0.8);
            ring(g, x, y, w, h, 9, alpha(PINK, 0.3), 1);
            g.drawText(a, x + 10, y + 5, WHITE, true);
            g.drawText("·", x + 10 + F.width(a) + 3, y + 5, DIM, false);
            gradientText(g, b, x + 10 + F.width(a) + 10, y + 5, true);
        } finally { g.pop(); g.closeAll(); }
    }
}
