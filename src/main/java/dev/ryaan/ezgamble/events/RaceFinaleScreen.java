package dev.ryaan.ezgamble.events;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.GlassButton;
import dev.ryaan.ezgamble.proto.Key;
import dev.ryaan.ezgamble.proto.ProtoScreen;
import dev.ryaan.ezgamble.vip.VipBadge;
import net.minecraft.client.MinecraftClient;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The end of a wager race, over the blurred world: the top three drop onto a podium (chibis of their skins, the winner
 * crowned with sparkles, medal colours like the High rollers page) for 6 s, then the podium shrinks up and the results
 * card shows the three shares with one Pay button each (Events.pay: one /pay per click, confirmed by the server) and
 * Close. Space, Enter or a click skips to the results; Esc closes. Opened by Events.tick once no screen is up.
 */
public final class RaceFinaleScreen extends ProtoScreen {
    private static final long PODIUM_MS = 6000, MOVE_MS = 450;
    private static final int[] ORDER = {1, 0, 2};
    private static final double[] STEP_BIG = {48, 36, 28}, STEP_SMALL = {26, 19, 14};
    private static final long[] DELAY = {500, 250, 0};
    private static final double CARD_W = 300, ROW_H = 22;

    private final Events.Event ev;
    private final long openedAt = now();
    private long resultsAt = -1, closeAt;
    private boolean closing, crowned;
    private int payVer = Payouts.ver, evVer = Events.ver;

    public RaceFinaleScreen(Events.Event ev) {
        super("Wager race");
        this.ev = ev;
        dev.ryaan.ezgamble.proto.Faces.prefetch(ev.top.stream().map(w -> w.name).toList());
        play("open", 0.9);
    }

    public static RaceFinaleScreen current() { return MinecraftClient.getInstance().currentScreen instanceof RaceFinaleScreen s ? s : null; }

    private boolean results() { return resultsAt >= 0; }
    /** 0 on the podium, 1 once the results card has settled. */
    private double moveK() { return results() ? easeOut((now() - resultsAt) / (double) MOVE_MS) : 0; }
    /** The shrunken podium's chibi scale: 2, or 1.5 on a short screen (1080p at GUI scale 4 is 270 px high). */
    private double smallScale() { return SH >= 320 ? 2 : 1.5; }
    /**
     * The card sits mid-screen, or lower when the shrunken podium needs the room above it: title at y 6 (18 above the
     * name), the name, 12 to the crowned, hopping chibi (CHIBI_H + 3 + 4 texels), the first step, 14 to the card.
     */
    private double cardTop() { return Math.max(SH / 2 - 30, 24 + 12 + (CHIBI_H + 7) * smallScale() + 1 + STEP_SMALL[0] + 14); }
    private double cardH() { return 40 + ev.top.size() * ROW_H + 6; }

    @Override
    protected void init2() {
        if (!results()) return;
        double cx = SW / 2, x = cx - CARD_W / 2, y = cardTop();
        for (int i = 0; i < ev.top.size(); i++) {
            Events.Winner w = ev.top.get(i);
            Events.PayState ps = Events.payState(w);
            if (ps == null || ps.done()) continue;
            String label = ps.retry() ? "Retry" : "Pay";   // the share column already shows the amount; a long label would run into it (the reason is drawn under the name)
            double bw = F.width(label) + 14;
            GlassButton b = add(new GlassButton(x + CARD_W - 8 - bw, y + 40 + i * ROW_H + 2, bw, 16, label, ps.retry() ? "DANGER" : "PRIMARY", () -> { if (!Events.pay(w)) play("fail", 1, 0.5); rebuild(); }));
            b.active = ps.active();
        }
        add(new GlassButton(cx - 32, y + cardH() + 10, 64, 20, "Close", "GHOST", this::close));
    }

    private void showResults() {
        if (results() || closing) return;
        resultsAt = now();
        play("mc_chime", 1.2, 0.5);
        rebuild();
    }

    @Override
    public void tick() {
        if (closing) { if (now() - closeAt >= 220 && client != null) client.setScreen(null); return; }
        if (!results() && now() - openedAt >= PODIUM_MS) showResults();
        if (results() && (payVer != Payouts.ver || evVer != Events.ver)) { payVer = Payouts.ver; evVer = Events.ver; rebuild(); }   // a Pay went out or came back
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true; closeAt = now();
        play("close", 0.8);
    }

    @Override
    public void key(Key e) {
        if (e.is("Escape")) { close(); return; }
        if (e.is(" ") || e.is("Enter")) showResults();
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (super.mouseDown(mx, my, button)) return true;
        if (!closing && button == 0) showResults();
        return true;
    }

    // ---- drawing

    private double pop() {
        if (closing) { double f = Math.min(1, (now() - closeAt) / 220.0); return 1 - f * f; }
        return easeOut((now() - openedAt) / 300.0);
    }

    @Override
    protected void renderBg(G g, double mx, double my) {
        double p = pop();
        if (client != null && client.world != null) g.d.applyBlur();
        g.fill(0, 0, SW, SH, ((int) (0x7A * p) << 24) | 0x0A0710);
    }

    private void text(G g, String s, double cx, double y, double sc, int color, double k) {
        if (k <= 0.01) return;
        g.push(); g.translate(cx - F.width(s) * sc / 2, y + (1 - k) * 8); g.scale(sc, sc);
        g.drawText(s, 0, 0, alpha(color, k), true);
        g.pop();
    }

    @Override
    protected void renderFg(G g, double mx, double my) {
        double p = pop();
        g.push();
        if (p < 1) { double cx = SW / 2, cy = SH / 2, sc = 0.92 + 0.08 * p; g.translate(cx, cy); g.scale(sc, sc); g.translate(-cx, -cy); }
        podium(g);
        if (results()) card(g, mx, my);
        renderChildren(g, mx, my);
        String hint = results() ? "Esc to close" : "Click to see the results · Esc to close";
        if (!results() || SH - 11 >= cardTop() + cardH() + 32) g.drawText(hint, SW / 2 - F.width(hint) / 2.0, SH - 11, alpha(DIM, 0.8), false);   // never under the Close button
        g.pop();
    }

    /** The podium, big and centred first, then shrinking to the top of the screen over the results card. */
    private void podium(G g) {
        double cx = SW / 2, cy = SH / 2; long t = now(), e = t - openedAt;
        double mk = moveK();
        double pBig = SH >= 500 ? 5 : SH >= 400 ? 4 : 3, pSmall = smallScale();
        double p = pBig + (pSmall - pBig) * mk, ps = Math.max(1.5, Math.min(Math.max(2, p - 2), p - 0.5));   // the winner's chibi a little bigger than the others
        double stepW = (Math.min(120, (SW - 40) / 3.2)) * (1 - 0.35 * mk), gap = 8;
        double baseBig = Math.min(SH - 70, cy + 92), baseSmall = cardTop() - 14, base = baseBig + (baseSmall - baseBig) * mk;
        int n = ev.top.size();
        double topName = Double.MAX_VALUE;
        for (int col = 0; col < 3; col++) {
            int rank = ORDER[col], m = Events.MEDAL[rank];
            if (rank >= n) continue;
            Events.Winner w = ev.top.get(rank);
            double sx = cx - stepW * 1.5 - gap + col * (stepW + gap), scx = sx + stepW / 2;
            double stepFull = STEP_BIG[rank] + (STEP_SMALL[rank] - STEP_BIG[rank]) * mk;
            double grow = easeOut((e - DELAY[rank]) / 700.0), sh = stepFull * grow, top = base - sh;
            double pp = rank == 0 ? p : ps, kk = rank == 0 ? 1 : 0.55;
            // the name sits 12 above the chibi, plus the crown (3 texels) and the hop (4 texels) of the winner, who always has both;
            // the title tracks where the names rest once every step is full, not the growing steps or the falling chibis
            double lift = CHIBI_H * pp + (rank == 0 ? 7 * pp : 0) + 12;
            topName = Math.min(topName, base - stepFull - 1 - lift);
            if (sh < 1) continue;
            double hy = top - 1 - CHIBI_H / 2.0 * pp;
            if (mk < 1) for (int rr : new int[]{15, 11, 7}) smooth(g, scx - rr * pp, hy - rr * pp * 1.2, 2 * rr * pp, 2.4 * rr * pp, rr * pp * 0.9, alpha(m, 0.06 * kk * grow * (1 - mk)));
            smooth(g, sx, top, stepW, sh, 6, v(alpha(m, 0.2), alpha(m, 0.03)), alpha(m, 0.5));
            g.fill(sx + 8, top + 1, sx + stepW - 8, top + 2, alpha(m, 0.9));
            String num = String.valueOf(rank + 1);
            if (sh > 20) { g.push(); g.translate(scx - F.width(num), top + 5); g.scale(2, 2); g.drawText(num, 0, 0, m, true); g.pop(); }
            else if (sh > 12) g.drawText(num, scx - F.width(num) / 2.0, top + 4, m, true);
            String v = Money.format(w.wagered);
            if (sh > 32) { if (rank == 0) gradientText(g, v, scx - F.width(v) / 2.0, top + 24, true); else g.drawText(v, scx - F.width(v) / 2.0, top + 24, WHITE, true); }
            if (sh > 44 && w.share > 0) g.drawText("wins " + Money.format(w.share), scx - F.width("wins " + Money.format(w.share)) / 2.0, top + 36, m, false);
            // the chibi drops onto his step once it has grown
            double land = easeOut((e - DELAY[rank] - 600) / 380.0);
            if (land <= 0.01) continue;
            double feet = top - 1 - (1 - land) * 140;
            boolean down = land >= 1;
            if (rank == 0 && down && !crowned) { crowned = true; play("mc_levelup", 1.2, 0.7); }
            chibi(g, w.name, scx, feet, pp, t, rank == 0 && down, rank == 0 && down);
            if (rank == 0 && down && mk < 1) sparkles(g, scx, feet, pp, t);
            String nm = F.trimToWidth(w.name, stepW - 6);
            double ny = feet - lift;
            if (rank == 0) gradientText(g, nm, scx - F.width(nm) / 2.0, ny, true, land); else g.drawText(nm, scx - F.width(nm) / 2.0, ny, alpha(TEXT, land), true);
        }
        // the title above the tallest name, the pool under the podium (until the card takes that place)
        double ty = topName - 18;   // rank 0 always exists: Events only opens the finale when ev.top is non-empty
        text(g, "Wager race", cx, Math.max(6, ty), 1, MUTE, easeOut(e / 500.0));
        if (mk < 1) {
            String pool = Money.format(ev.prize) + " prize pool · " + ev.minutes() + " min";
            text(g, pool, cx, base + 10, 1, TEXT, easeOut((e - 1500) / 500.0) * (1 - mk));   // fades as the card takes over
            Events.Winner win = ev.top.get(0);
            String line = win.name + " wagered " + Money.format(win.wagered) + " and takes " + Money.format(win.share);
            text(g, line, cx, base + 22, 1, Events.GOLD, easeOut((e - 1900) / 500.0) * (1 - mk));
        }
    }

    /** "Not confirmed, retry" -> "Not confirmed": the reason alone, the button says Retry. */
    private static String reason(Events.PayState ps) { String l = ps.label(); return l.endsWith(", retry") ? l.substring(0, l.length() - 7) : l; }

    /** The results card: rank, head, gem, name, wagered, share, and a Pay button per unpaid winner (widgets). */
    private void card(G g, double mx, double my) {
        double k = moveK();
        if (k <= 0.01) return;
        double x = SW / 2 - CARD_W / 2, y = cardTop(), h = cardH();
        g.push(); g.translate(0, (1 - k) * 12);
        dev.ryaan.ezgamble.proto.P.card(g, x, y, CARD_W, h, "accent");
        g.drawText("Results", x + 10, y + 8, alpha(WHITE, k), true);
        String pool = Money.format(ev.prize) + " pool, split 50 / 30 / 20";
        g.drawText(pool, x + CARD_W - 10 - F.width(pool), y + 8, alpha(MUTE, k), false);
        g.drawText("One Pay per winner, confirmed by the server", x + 10, y + 20, alpha(DIM, k), false);
        g.fill(x + 8, y + 33, x + CARD_W - 8, y + 34, 0x14FFFFFF);
        for (int i = 0; i < ev.top.size(); i++) {
            Events.Winner w = ev.top.get(i);
            double ry = y + 40 + i * ROW_H;
            int m = Events.MEDAL[i];
            g.drawText(String.valueOf(i + 1), x + 12, ry + 6, alpha(m, k), true);
            g.drawHead(w.name, x + 22, ry + 4, 10);
            int bw = VipBadge.w(w.name); VipBadge.draw(g, w.name, x + 36, ry + 6);
            g.drawText(F.trimToWidth(w.name, 82 - bw), x + 36 + bw, ry + 6, alpha(i == 0 ? WHITE : TEXT, k), i == 0);   // columns 4 px apart: name, wagered at 122, share at 170, Pay at 260
            g.drawText(Money.format(w.wagered), x + 122, ry + 6, alpha(MUTE, k), false);
            String sh = Money.format(w.share);
            if (i == 0) gradientText(g, sh, x + 170, ry + 6, true, k); else g.drawText(sh, x + 170, ry + 6, alpha(WHITE, k), false);
            Events.PayState ps = Events.payState(w);
            if (ps != null && ps.done()) g.drawText(ps.label(), x + CARD_W - 8 - F.width(ps.label()), ry + 6, alpha(GREEN, k), false);   // else the Pay button is there
            else if (ps != null && ps.retry()) g.drawText(reason(ps), x + 36, ry + 14, alpha(RED, k), false);   // the Retry button only says "Retry"
            else if (ps == null) g.drawText("-", x + CARD_W - 8 - F.width("-"), ry + 6, alpha(DIM, k), false);
        }
        g.pop();
    }
}
