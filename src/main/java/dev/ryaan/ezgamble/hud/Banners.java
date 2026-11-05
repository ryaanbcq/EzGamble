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
import java.util.List;

import static dev.ryaan.ezgamble.hud.HudConfig.*;
import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The top-centre column of the HUD, one owner instead of six: a FIFO of transient toasts at y 8 (tier-up, lobby join,
 * badge, event news, the "Session over" hint) and, at y 30, the persistent row (the running event's banner and the
 * jackpot counter side by side, the pair centred in the span left free by the cards). One toast shows at a time: down
 * in 250 ms, hold, up in 250 ms, 120 ms before the next. The queue keeps 4 (a fifth drops the oldest waiting one) and
 * nothing older than 15 s. Both the toasts and the row step aside for the rain cloud and the auction card, which take
 * the same spot: the toasts wait, the row hides.
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
    private static final double Y = 8, H = 17;
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

    private static int width(Toast t) {
        int w = 7 + (t.left() != null ? 9 + 4 : 0) + 7;
        for (Seg s : t.segs()) w += s.text() != null ? F.width(s.text()) : s.iconW();
        return w;
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
        int w = width(cur);
        double x = Math.round((sw() - w) / 2.0), y = Math.round(Y - (1 - k) * (H + 10));
        glass(g, x, y, w, H, 8, 0.75, 0.6);
        ring(g, x, y, w, H, 8, alpha(cur.accent(), 0.55 * k), 1);
        double tx = x + 7;
        if (cur.left() != null) { cur.left().draw(g, tx, y + 5); tx += 9 + 4; }
        for (Seg s : cur.segs()) {
            if (s.text() != null) { g.drawText(s.text(), tx, y + 5, s.color(), true); tx += F.width(s.text()); }
            else { if (s.icon() != null) s.icon().draw(g, tx, y + 5); tx += s.iconW(); }
        }
    }

    /**
     * The persistent row at y 30: the event banner then the jackpot counter, 8 px apart, centred in the span the top
     * cards leave free (a card in the top 50 px on its half of the screen pushes the span in by 8 px). When the pair
     * does not fit, the event banner compacts (name trimmed and amount dropped, then no leader); the jackpot keeps its width.
     */
    public static void drawPersistent(G g) {
        if (blocked()) return;
        boolean ev = EventHud.showing(), jp = JackpotHud.showing();
        if (!ev && !jp) return;
        double lo = 0, lim = sw();
        if (HUD.get("pay").visible) { Rect p = wrect("pay"); if (p.y() < 50 && p.x() > sw() / 2) lim = Math.min(lim, p.x() - 8); }
        if (HUD.get("session").visible) { Rect s = wrect("session"); if (s.y() < 50 && s.x() + s.w() < sw() / 2) lo = Math.max(lo, s.x() + s.w() + 8); }
        int jw = jp ? JackpotHud.width() : 0, gap = ev && jp ? 8 : 0;
        int compact = 0, ew = 0;
        if (ev) {
            ew = EventHud.width(0);
            while (compact < 2 && ew + gap + jw > lim - lo) ew = EventHud.width(++compact);
        }
        double total = ew + gap + jw, x = Math.round(lo + (lim - lo - total) / 2), y = 30;
        if (ev) { EventHud.draw(g, x, y, compact); x += ew + gap; }
        if (jp) JackpotHud.draw(g, x, y);
    }
}
