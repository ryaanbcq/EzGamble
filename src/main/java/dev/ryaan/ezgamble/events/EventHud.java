package dev.ryaan.ezgamble.events;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.vip.VipBadge;

import java.util.List;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The running event's banner of the HUD's persistent row: a glass pill with the kind, the countdown mm:ss and, for a
 * race, the leader (head, gem, name, wagered). It pulses in the last 10 s. hud.Banners places it (x, y) and picks the
 * compact level when the row is short of room: 0 full, 1 the leader's name trimmed to 48 px and his amount dropped,
 * 2 no leader at all. The "X is over" news is a Banners toast (Events).
 */
public final class EventHud {
    private EventHud() {}

    private static String mmss(long ms) { long sec = Math.max(0, ms) / 1000; return String.format("%02d:%02d", sec / 60, sec % 60); }

    public static boolean showing() { return Events.current() != null; }

    private static Events.Entry leader(Events.Event e) {
        if (e.kind() != Events.Kind.RACE) return null;
        List<Events.Entry> b = Events.board();
        return b.isEmpty() ? null : b.get(0);
    }

    private static String name(Events.Entry lead, int compact) { return F.trimToWidth(lead.name(), compact == 0 ? 80 : 48); }

    /** The pill's width at that compact level (0 when no event runs). */
    public static int width(int compact) {
        Events.Event e = Events.current();
        if (e == null) return 0;
        Events.Entry lead = leader(e);
        String cd = mmss(Math.max(0, e.end - System.currentTimeMillis()));
        int w = 7 + F.width(e.kind().label) + 6 + F.width(cd) + 7;
        if (lead != null && compact < 2) w += 9 + 11 + VipBadge.w(lead.name()) + F.width(name(lead, compact)) + 4 + (compact == 0 ? F.width(Money.format(lead.wagered())) : 0);
        return w;
    }

    public static void draw(G g, double x, double y, int compact) {
        Events.Event e = Events.current();
        if (e == null) return;
        long t = now();
        Events.Kind k = e.kind();
        long left = Math.max(0, e.end - System.currentTimeMillis());
        boolean lastTen = left <= 10_000;
        String cd = mmss(left);
        Events.Entry lead = compact < 2 ? leader(e) : null;
        String ln = lead == null ? null : name(lead, compact), lw = lead == null || compact > 0 ? null : Money.format(lead.wagered());
        int bw = lead == null ? 0 : VipBadge.w(lead.name());
        int w = width(compact), h = 17;
        double p = 0.5 + 0.5 * Math.sin(t / 500.0 * 2 * Math.PI);   // a beat every half second at the end
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
            if (lw != null) g.drawText(lw, tx, y + 5, Events.GOLD, true);
        }
        g.pop();
    }
}
