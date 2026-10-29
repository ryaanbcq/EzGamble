package dev.ryaan.ezgamble.auction;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.P;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Slides in from the top (400 ms), out at the end (1 s), shakes harder every second under 5 s, red and blinking
 * under 10 s, "+10s" when the soft close extends, shimmering bar and "And the winner is..." during the stream delay.
 */
public final class AuctionHud implements HudElement {
    static final double W = 230, H = 38;
    static final long SHOW_MS = 6500;
    private long shownAt;
    private boolean wasVisible;

    @Override
    public void render(DrawContext d, RenderTickCounter tc) {
        var st = Auction.state;
        long t = P.now();
        boolean visible = st == Auction.State.RUNNING || (st == Auction.State.ENDED && t - Auction.endedAt < SHOW_MS);
        if (!visible) { wasVisible = false; return; }
        G g = new G(d);
        try { draw(g, t, st); } finally { g.closeAll(); }
    }

    private void draw(G g, long t, Auction.State st) {
        var mc = MinecraftClient.getInstance();
        double sw = mc.getWindow().getScaledWidth();
        if (!wasVisible) { shownAt = t; wasVisible = true; }
        double x = (int) ((sw - W) / 2), y = 8;
        boolean grace = Auction.inGrace();
        long ms = Auction.remaining();
        boolean running = st == Auction.State.RUNNING;
        boolean urgent = running && !grace && ms < 10000, blink = urgent && (t / 250) % 2 == 0;
        Auction.Bid w = Auction.winner;
        double dx = 0, dy = 0;
        long inn = t - shownAt;
        if (inn < 400) { double k = inn / 400.0; dy = -(y + H + 16) * (1 - k) * (1 - k); }
        if (st == Auction.State.ENDED) {
            long left = SHOW_MS - (t - Auction.endedAt);
            if (left < 1000) { double k = 1 - left / 1000.0; dy = -(y + H + 16) * k * k; }
        }
        if (running && !grace && ms < 5000) {   // real shake, harder every second
            double amp = 1.5 + (5 - ms / 1000.0) / 5 * 2.5;
            dx = (Math.random() * 2 - 1) * amp; dy += (Math.random() * 2 - 1) * amp;
        }
        g.push(); g.translate(dx, dy);
        P.glass(g, x, y, W, H, 8, 0.7);
        if (urgent) P.ring(g, x, y, W, H, 8, 0x99FF5C5C, 1.4);
        else if (grace) P.ring(g, x, y, W, H, 8, P.alpha(P.LAV, 0.35 + 0.25 * P.pulse(t)), 1.2);
        else if (st == Auction.State.ENDED) P.ring(g, x, y, W, H, 8, P.alpha(w != null ? P.GREEN : P.RED, 0.5), 1.2);
        g.drawItem(Auction.item, x + 8, y + 6, true);
        if (Auction.count > 1) { String c = String.valueOf(Auction.count); g.drawText(c, x + 25 - F.width(c), y + 15, P.WHITE, true); }
        double tx = x + 30, rx = x + W - 10;
        String tag = running ? P.clock(ms) : w != null ? "SOLD" : "✖";
        String name = F.trimToWidth(Auction.label(), W - 10 - 30 - F.width(tag) - 10);
        P.gradientText(g, name, tx, y + 8, true);
        double ry = y + 22;
        if (running) {
            int tcol = grace ? P.WHITE : blink ? P.RED : urgent ? 0xFFFF8A80 : P.WHITE;
            double tw = F.width(tag);
            g.drawText(tag, rx - tw, y + 8, tcol, true);
            long since = t - Auction.extendedAt;
            if (Auction.extendedAt != 0 && since < 1500) {   // soft close: "+10s" next to the timer, fading
                String ext = "+" + Math.max(1, Math.round(Auction.extendedBy / 1000.0)) + "s";
                g.drawText(ext, rx - tw - 6 - F.width(ext), y + 8, P.alpha(P.GREEN, Math.max(0.05, 1 - since / 1500.0)), true);
            }
            g.fill(tx, ry, rx, ry + 3, 0x1AFFFFFF);
            double pw = Math.round((rx - tx) * (1 - ms / (double) Math.max(1, Auction.duration())));
            if (grace) shimmer(g, tx, ry, rx - tx, t); else if (urgent) g.fill(tx, ry, tx + pw, ry + 3, P.RED); else P.accentBar(g, tx, ry, pw, 3, 1);
            Auction.Bid lead = Auction.leader();
            long pot = Auction.pot();
            double rw = 0;
            if (pot > 0) { String ps = "Pot " + Money.format(pot); rw = F.width(ps); g.drawText(ps, rx - rw, y + 28, P.SOFT_GREY, true); }
            double lw = rx - tx - rw - (rw != 0 ? 8 : 0);
            if (lead == null) g.drawText(F.trimToWidth("Min " + Money.format(Auction.min) + (Auction.rows().isEmpty() ? "   No bids yet" : ""), lw), tx, y + 28, P.SOFT_GREY, true);
            else { g.drawText("✔", tx, y + 28, P.GREEN, true); g.drawText(F.trimToWidth(lead.name + "  " + Money.format(lead.total), lw - 11), tx + 11, y + 28, P.WHITE, true); }
        } else {
            int c = w != null ? P.GREEN : P.RED;
            g.drawText(tag, rx - F.width(tag), y + 8, c, true);
            g.fill(tx, ry, rx, ry + 3, c);
            if (w == null) g.drawText(F.trimToWidth("Nobody reached the " + Money.format(Auction.min) + " minimum.", rx - tx), tx, y + 28, P.SOFT_GREY, true);
            else { g.drawText("✔", tx, y + 28, P.GREEN, true); g.drawText(F.trimToWidth(w.name + "  " + Money.format(w.total), rx - tx - 11), tx + 11, y + 28, P.WHITE, true); }
        }
        // line under the card: the command to bid, the latest bid, or the drum roll's "And the winner is..."
        double ly = y + H + 6;
        if (running && grace) {
            String s = "And the winner is" + ".".repeat((int) (t / 300 % 4));
            double sw2 = F.width("And the winner is..."), sc = 1 + 0.06 * P.pulse(t * 3);
            g.push(); g.translate(x + W / 2, ly + 4); g.scale(sc, sc); P.gradientText(g, s, -((int) sw2 >> 1), -4, true); g.pop();
        } else if (running && Auction.lastBid == null) {
            String s = "/pay " + me();
            g.drawText(s, x + (int) ((W - F.width(s)) / 2), ly, P.WHITE, true);
        } else if (running) {
            var lb = Auction.lastBid;
            boolean fresh = t - lb.at() < 1500;
            String who = lb.name(), rest = " paid " + Money.format(lb.amount());
            double sx = x + (int) ((W - F.width(who) - F.width(rest)) / 2);
            g.drawText(who, sx, ly, P.WHITE, true); g.drawText(rest, sx + F.width(who), ly, fresh ? P.GREEN : P.SOFT_GREY, true);
        }
        g.pop();
    }

    static String me() { return MinecraftClient.getInstance().getSession().getUsername(); }

    /** Full bar dimmed, a bright band sweeping across it: "still counting" without a number. */
    private static void shimmer(G g, double x, double y, double w, long t) {
        P.accentBar(g, x, y, w, 3, 0.35);
        double band = 40, bx = x - band + Math.round((w + band) * ((t % 1100) / 1100.0)), from = Math.max(x, bx), to = Math.min(x + w, bx + band);
        if (to > from) P.accentBar(g, from, y, to - from, 3, 1);
    }
}
