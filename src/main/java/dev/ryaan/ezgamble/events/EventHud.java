package dev.ryaan.ezgamble.events;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.vip.VipBadge;

import java.util.List;

import static dev.ryaan.ezgamble.hud.HudConfig.sw;
import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The running event's banner at the top centre of the HUD: a glass pill with the kind, the countdown mm:ss and, for a
 * race, the leader (head, gem, name, wagered). It pulses in the last 10 s, and says "X is over" for 3 s once it ends.
 * It drops under the VIP tier-up banner while that one has the top line.
 */
public final class EventHud {
    private EventHud() {}

    private static String mmss(long ms) { long sec = Math.max(0, ms) / 1000; return String.format("%02d:%02d", sec / 60, sec % 60); }

    public static void draw(G g) {
        long t = now();
        double y = 8 + (dev.ryaan.ezgamble.vip.Vip.lastTierUp() != null ? 22 : 0);
        Events.Event e = Events.current();
        if (e == null) {   // just over: the news fades out
            Events.Event le = Events.lastEnded;
            long el = le == null ? Long.MAX_VALUE : t - Events.endedAt;
            if (el > 3000) return;
            double k = el < 2600 ? 1 : 1 - easeIn((el - 2600) / 400.0);
            String s = le.kind().label + " is over";
            int w = F.width(s) + 14, h = 17;
            double x = Math.round((sw() - w) / 2.0);
            glass(g, x, y, w, h, 8, 0.75 * k, 0.6 * k);
            ring(g, x, y, w, h, 8, alpha(le.kind().color, 0.55 * k), 1);
            g.drawText(s, x + 7, y + 5, alpha(WHITE, k), true);
            return;
        }
        Events.Kind k = e.kind();
        long left = Math.max(0, e.end - System.currentTimeMillis());
        boolean lastTen = left <= 10_000;
        String cd = mmss(left);
        List<Events.Entry> b = k == Events.Kind.RACE ? Events.board() : List.of();
        Events.Entry lead = b.isEmpty() ? null : b.get(0);
        String ln = lead == null ? null : F.trimToWidth(lead.name(), 80), lw = lead == null ? null : Money.format(lead.wagered());
        int bw = lead == null ? 0 : VipBadge.w(lead.name());
        int w = 7 + F.width(k.label) + 6 + F.width(cd) + (lead == null ? 0 : 9 + 11 + bw + F.width(ln) + 4 + F.width(lw)) + 7, h = 17;
        double x = Math.round((sw() - w) / 2.0), p = 0.5 + 0.5 * Math.sin(t / 500.0 * 2 * Math.PI);   // a beat every half second at the end
        g.push();
        if (lastTen) { double sc = 1 + 0.05 * p; g.translate(x + w / 2.0, y + h / 2.0); g.scale(sc, sc); g.translate(-(x + w / 2.0), -(y + h / 2.0)); }
        glass(g, x, y, w, h, 8, 0.75, 0.6);
        ring(g, x, y, w, h, 8, lastTen ? alpha(RED, 0.4 + 0.5 * p) : alpha(k.color, 0.55), 1);
        double tx = x + 7;
        g.drawText(k.label, tx, y + 5, k.color, true); tx += F.width(k.label) + 6;
        g.drawText(cd, tx, y + 5, lastTen ? RED : WHITE, true); tx += F.width(cd);
        if (lead != null) {   // the leader, after a thin separator
            tx += 9;
            g.fill(tx - 5, y + 4, tx - 4, y + 13, 0x30FFFFFF);
            g.drawHead(lead.name(), tx, y + 4, 9); tx += 11;
            VipBadge.draw(g, lead.name(), tx, y + 5); tx += bw;
            g.drawText(ln, tx, y + 5, WHITE, true); tx += F.width(ln) + 4;
            g.drawText(lw, tx, y + 5, Events.GOLD, true);
        }
        g.pop();
    }
}
