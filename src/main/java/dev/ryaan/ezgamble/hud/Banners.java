package dev.ryaan.ezgamble.hud;

import dev.ryaan.ezgamble.auction.AuctionHud;
import dev.ryaan.ezgamble.events.EventHud;
import dev.ryaan.ezgamble.games.rain.RainTable;
import dev.ryaan.ezgamble.hud.HudConfig.Rect;
import dev.ryaan.ezgamble.jackpot.JackpotHud;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.recap.Recap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static dev.ryaan.ezgamble.hud.HudConfig.*;
import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The top-centre column of the HUD, one owner instead of six: a FIFO of transient toasts at y 8 (tier-up, lobby join,
 * badge, event news, the "Session over" hint) and, at y 30, the persistent row (the running event's banner and the
 * jackpot counter side by side). One toast shows at a time: down in 250 ms, hold, up in 250 ms, 120 ms before the next.
 * The queue keeps 4 (a fifth drops the oldest waiting one) and nothing older than 15 s. Both the toasts and the row step
 * aside for the rain cloud and the auction card, which take the same spot: the toasts wait, the row hides.
 * <p>
 * Both slots are centred on the screen, one under the other, as long as that is clear of the placed cards. Only when
 * the centred thing would touch a card do they move: any card reaching the slot's band pushes the free span in from its
 * side by 8 px and the thing is centred in what is left (the event banner compacts, a toast trims its longest text).
 * A card across the centre leaves no span: the row hides, a toast (transient) shows screen-centred anyway.
 */
public final class Banners {
    private Banners() {}

    /** Draws a picture at the text row: y is the text's baseline row (pill top + 5), a 9 px picture goes at y - 1. */
    public interface Painter { void draw(G g, double x, double y); }
    /** A piece of a toast's line: text in a colour, or a picture of a known width. */
    public record Seg(String text, int color, Painter icon, int iconW) {}
    public static Seg seg(String text, int color) { return new Seg(text, color, null, 0); }
    public static Seg icon(Painter p, int w) { return new Seg(null, 0, p, w); }
    /** left: a gem, a badge, a head (nullable, 9 px); accent: the ring; duration in ms (<= 0 = 3 s); onShow: the sound, say. */
    public record Toast(Painter left, List<Seg> segs, int accent, long duration, Runnable onShow) {}

    private static final int MAX = 4;
    private static final long WAIT_MAX = 15_000, SLIDE = 250, GAP = 120, DEFAULT_MS = 3000;
    private static final double Y = 8, H = 17, ROW_Y = 30, ROW_H = 18, MARGIN = 8;
    private record Waiting(Toast t, long at) {}
    private static final ArrayDeque<Waiting> queue = new ArrayDeque<>();
    private static Toast cur;
    private static long curAt, freeAt;
    private static long recapShown = -1;

    public static void show(Toast t) {
        if (t == null) return;
        while (queue.size() >= MAX) queue.pollFirst();   // the queue is a window on the latest news, the oldest waiting one goes
        queue.addLast(new Waiting(t, now()));
    }

    /** The rain cloud and the auction card hang at the same spot: nothing of ours draws over them. */
    private static boolean blocked() { return !RainTable.RN.stage.equals("setup") || AuctionHud.showing(); }

    private static long duration(Toast t) { return t.duration() > 0 ? t.duration() : DEFAULT_MS; }

    private static int width(Painter left, List<Seg> segs) {
        int w = 7 + (left != null ? 9 + 4 : 0) + 7;
        for (Seg s : segs) w += s.text() != null ? F.width(s.text()) : s.iconW();
        return w;
    }

    /**
     * The span {lo, lim} the placed cards leave free in the band top..bottom: a card reaching the band pushes the span in
     * from its side (the side its middle is on), 8 px clear; a card across the centre owns the band, {mid, mid}. The
     * "last" toast needs no entry: it hangs under the session card in the same columns, and it is gone in 3.5 s.
     */
    private static double[] span(double top, double bottom) {
        double lo = 0, lim = sw(), mid = sw() / 2;
        for (String id : new String[]{"session", "pay"}) {
            if (!HUD.get(id).visible) continue;
            Rect r = wrect(id);
            if (r.y() >= bottom || r.y() + r.h() <= top) continue;
            if (r.x() < mid && r.x() + r.w() > mid) return new double[]{mid, mid};
            if (r.x() + r.w() / 2.0 < mid) lo = Math.max(lo, r.x() + r.w() + MARGIN); else lim = Math.min(lim, r.x() - MARGIN);
        }
        return new double[]{lo, lim};
    }

    /** x of a thing of width w: screen-centred when that keeps clear of the cards (the two slots line up), else centred in the free span. */
    private static double place(double w, double[] s) {
        double x = Math.round((sw() - w) / 2);
        return x >= s[0] && x + w <= s[1] ? x : Math.round(s[0] + (s[1] - s[0] - w) / 2);
    }

    /** The segments with the widest text cut by `over` px (the name, usually): records are immutable, so a copy. */
    private static List<Seg> trimmed(List<Seg> segs, double over) {
        int at = -1, best = 0;
        for (int i = 0; i < segs.size(); i++) {
            Seg s = segs.get(i);
            if (s.text() != null && F.width(s.text()) > best) { best = F.width(s.text()); at = i; }
        }
        if (at < 0) return segs;
        List<Seg> out = new ArrayList<>(segs);
        out.set(at, seg(F.trimToWidth(segs.get(at).text(), best - over), segs.get(at).color()));
        return out;
    }

    /** Every HUD frame, BEFORE the mode check (GambleHud.render): the recap hint must show with the mode off. */
    public static void draw(G g) {
        long t = now();
        // the recap hint, once per session end (readyAt is wall-clock: only ever compared with itself)
        if (Recap.readyAt > recapShown) {
            recapShown = Recap.readyAt;
            show(new Toast(null, List.of(seg("Session over", WHITE), seg(", press K then Recap", PINK)), PINK, 6000, null));
        }
        while (!queue.isEmpty() && t - queue.peekFirst().at() > WAIT_MAX) queue.pollFirst();   // stale news is no news
        if (cur != null && t - curAt >= duration(cur)) { cur = null; freeAt = t + GAP; }
        if (cur == null) {
            if (blocked() || t < freeAt || queue.isEmpty()) return;
            cur = queue.pollFirst().t(); curAt = t;
            if (cur.onShow() != null) cur.onShow().run();
        }
        if (blocked()) return;   // a rain or an auction started under a showing toast: it finishes its time unseen
        long e = t - curAt, d = duration(cur);
        double k = e < SLIDE ? easeOut(e / (double) SLIDE) : e < d - SLIDE ? 1 : 1 - easeIn((e - (d - SLIDE)) / (double) SLIDE);
        if (k <= 0) return;
        List<Seg> segs = cur.segs();
        int w = width(cur.left(), segs);
        double[] s = span(Y, Y + H);   // the resting rect: the slide only moves it up, away from the cards
        if (s[1] - s[0] <= 0) s = new double[]{0, sw()};   // a card across the centre: a toast is news, it shows over it rather than never
        if (w > s[1] - s[0]) { segs = trimmed(segs, w - (s[1] - s[0])); w = width(cur.left(), segs); }
        double x = place(w, s), y = Math.round(Y - (1 - k) * (H + 10));
        glass(g, x, y, w, H, 8, 0.75, 0.6);
        ring(g, x, y, w, H, 8, alpha(cur.accent(), 0.55 * k), 1);
        double tx = x + 7;
        if (cur.left() != null) { cur.left().draw(g, tx, y + 5); tx += 9 + 4; }
        for (Seg sg : segs) {
            if (sg.text() != null) { g.drawText(sg.text(), tx, y + 5, sg.color(), true); tx += F.width(sg.text()); }
            else { if (sg.icon() != null) sg.icon().draw(g, tx, y + 5); tx += sg.iconW(); }
        }
    }

    /**
     * The persistent row at y 30: the event banner then the jackpot counter, 8 px apart. Centred under the toasts when
     * that is clear of the cards; otherwise centred in the span they leave free, the event banner compacting first (name
     * trimmed and amount dropped, then no leader) while the jackpot keeps its width. Nothing is drawn over a card: when
     * even the compact pair does not fit, or a card sits across the centre, the row hides.
     */
    public static void drawPersistent(G g) {
        if (blocked()) return;
        boolean ev = EventHud.showing(), jp = JackpotHud.showing();
        if (!ev && !jp) return;
        int jw = jp ? JackpotHud.width() : 0, gap = ev && jp ? 8 : 0;
        int compact = 0, ew = ev ? EventHud.width(0) : 0;
        double[] s = span(ROW_Y, ROW_Y + ROW_H);
        double free = s[1] - s[0];
        while (ev && compact < 2 && ew + gap + jw > free) ew = EventHud.width(++compact);
        double total = ew + gap + jw;
        if (total > free) return;
        double x = place(total, s), y = ROW_Y;
        if (ev) { EventHud.draw(g, x, y, compact); x += ew + gap; }
        if (jp) JackpotHud.draw(g, x, y);
    }
}
