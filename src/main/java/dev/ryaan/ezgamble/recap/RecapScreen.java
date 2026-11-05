package dev.ryaan.ezgamble.recap;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.GlassButton;
import dev.ryaan.ezgamble.proto.Key;
import dev.ryaan.ezgamble.proto.ProtoScreen;
import dev.ryaan.ezgamble.ui.Icons;
import dev.ryaan.ezgamble.ui.ProfitChart;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static dev.ryaan.ezgamble.proto.P.*;

/**
 * The Session Recap: the session's story over the blurred world, one slide at a time (Space, Enter or a click to go
 * on, slides also move on by themselves, Esc closes), then a share card the host can save as a PNG (screenshots
 * folder) or copy as one line of text. Every animation runs on the slide's own clock (P.now() - slideAt): a slide
 * replays the same way whenever it shows.
 *   1 Title   2 The number (profit + curve)   3 Hottest table   4 High roller podium   5 Moments   6 The table   7 Share
 */
public final class RecapScreen extends ProtoScreen {
    private static final int N = 7;
    /** How long each slide holds before moving on by itself; the last one waits. */
    private static final long[] DUR = {4000, 5600, 4800, 5600, 5000, 4400, -1};
    private static final int GOLD = 0xFFF2C14E, SILVER = 0xFFC7CCD6, BRONZE = 0xFFCD8B5A;
    private static final int[] ORDER = {1, 0, 2}, MEDAL = {GOLD, SILVER, BRONZE};
    /** The share card's size (GUI px). */
    private static final int CW = 300, CH = 170;

    private final Recap r;
    private int slide;
    private long slideAt = now();
    private final long openedAt = now();
    private boolean closing; private long closeAt;
    /** The card's "landed" moments, so their sounds play once per slide showing. */
    private boolean landed, crowned;
    /** 0 = normal, 1 = the next frame draws the card alone, 2 = that frame is on screen, the tick saves it. */
    private int saveStage;
    private long savedAt = -1, copiedAt = -1;
    private static boolean openPending;

    public RecapScreen(Session s) { super("Recap"); r = Recap.of(s); dev.ryaan.ezgamble.proto.Faces.prefetch(r.players); play("open", 0.9); }

    public static RecapScreen current() { return MinecraftClient.getInstance().currentScreen instanceof RecapScreen s ? s : null; }

    /** Opens the recap of the last session now (the menu's Recap button). */
    public static boolean openLast(MinecraftClient c) {
        Session s = GambleData.last();
        if (s == null) return false;
        c.setScreen(new RecapScreen(s));
        return true;
    }

    /** From a chat command: the chat screen is still closing, the recap opens on the next tick with no screen. */
    public static void request() { openPending = true; }

    public static void tickOpen(MinecraftClient c) {
        if (openPending && c.currentScreen == null && c.player != null) { openPending = false; openLast(c); }
    }

    // ---- flow

    public int slide() { return slide; }

    /** Jump to slide i (0-based), its clock restarts. */
    public void go(int i) {
        if (i < 0 || i >= N || closing) return;
        boolean moved = i != slide;
        slide = i; slideAt = now(); landed = crowned = false;
        if (moved) play("mc_chime", 1.3 + 0.1 * i, 0.5);
        rebuild();
    }

    private void next() { if (slide < N - 1) go(slide + 1); }

    private double e() { return now() - slideAt; }
    /** 0..1 eased, starting `delay` ms into the slide and taking `dur` ms. */
    private double k(double delay, double dur) { return easeOut((e() - delay) / dur); }
    private static double smoothstep(double v) { v = Math.max(0, Math.min(1, v)); return v * v * (3 - 2 * v); }

    @Override
    protected void init2() {
        if (slide != N - 1) return;
        double cx = SW / 2, y = SH / 2 - CH / 2.0 + CH + 12;
        double w1 = 96, w2 = 110, w3 = 64, gap = 6, x = cx - (w1 + w2 + w3 + 2 * gap) / 2;
        add(new GlassButton(x, y, w1, 22, "Save card", "PRIMARY", this::save));
        add(new GlassButton(x + w1 + gap, y, w2, 22, "Copy summary", "GHOST", () -> { copy(r.summary()); copiedAt = now(); }));
        add(new GlassButton(x + w1 + gap + w2 + gap, y, w3, 22, "Close", "GHOST", this::close));
    }

    private static void copy(String s) { MinecraftClient.getInstance().keyboard.setClipboard(s); }

    /** Save card: the chrome hides, the next frame shows the card alone, the tick after it takes the screenshot. */
    public void save() { if (saveStage == 0) saveStage = 1; }

    @Override
    public void tick() {
        if (closing) { if (now() - closeAt >= 220 && client != null) client.setScreen(null); return; }
        if (saveStage == 2 && client != null) {
            String name = "ezgamble-recap-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm").format(new Date(r.start)) + ".png";
            ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), 1, t -> {});
            saveStage = 0; savedAt = now();
            play("cash", 1, 0.6);
        }
        if (DUR[slide] > 0 && e() >= DUR[slide]) next();
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
        if (e.is(" ") || e.is("Enter") || e.is("ArrowRight")) next();
        else if (e.is("ArrowLeft")) go(slide - 1);
        else if (e.key().length() == 1 && Character.isDigit(e.key().charAt(0))) { int i = e.key().charAt(0) - '1'; if (i >= 0 && i < N) go(i); }
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (super.mouseDown(mx, my, button)) return true;
        if (!closing && button == 0 && slide < N - 1) next();
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

    @Override
    protected void renderFg(G g, double mx, double my) {
        double p = pop();
        g.push();
        if (p < 1) { double cx = SW / 2, cy = SH / 2, sc = 0.92 + 0.08 * p; g.translate(cx, cy); g.scale(sc, sc); g.translate(-cx, -cy); }
        if (saveStage != 0) {   // the card alone, for the screenshot
            shareCard(g, SW / 2 - CW / 2.0, SH / 2 - CH / 2.0, 1);
            g.pop();
            if (saveStage == 1) saveStage = 2;
            return;
        }
        switch (slide) {
            case 0 -> title(g);
            case 1 -> number(g, mx, my);
            case 2 -> hottest(g);
            case 3 -> podium(g);
            case 4 -> moments(g);
            case 5 -> table(g);
            default -> share(g);
        }
        renderChildren(g, mx, my);
        // progress dots and the hint, at the very bottom
        double dx = SW / 2 - (N * 10 - 4) / 2.0, dy = SH - 20;
        for (int i = 0; i < N; i++) {
            boolean on = i == slide;
            smooth(g, dx + i * 10 + (on ? 0 : 1), dy + (on ? 0 : 1), on ? 6 : 4, on ? 6 : 4, on ? 3 : 2, on ? PINK : 0x40FFFFFF);
        }
        String hint = slide < N - 1 ? "Space to continue · Esc to close" : "Esc to close";
        g.drawText(hint, SW / 2 - F.width(hint) / 2.0, SH - 11, alpha(DIM, 0.8), false);
        g.pop();
    }

    // ---- text helpers: centred, scaled, sliding in 8 px while fading in

    private void text(G g, String s, double cx, double y, double sc, int color, double k) {
        if (k <= 0.01) return;
        g.push(); g.translate(cx - F.width(s) * sc / 2, y + (1 - k) * 8); g.scale(sc, sc);
        g.drawText(s, 0, 0, alpha(color, k), true);
        g.pop();
    }

    private void grad(G g, String s, double cx, double y, double sc, double k) {
        if (k <= 0.01) return;
        g.push(); g.translate(cx - F.width(s) * sc / 2, y + (1 - k) * 8); g.scale(sc, sc);
        gradientText(g, s, 0, 0, true, k);
        g.pop();
    }

    /** A soft glow behind a scene (the rain's glow sprite, tinted). */
    private void glow(G g, double cx, double cy, double size, int tint, double a) {
        if (a <= 0.01) return;
        g.drawSprite("rain/glow", cx - size / 2, cy - size / 2, size, size, alpha(tint, a));
    }

    /** A number that counts up: 0..v along k. */
    private static long count(long v, double k) { return Math.round(v * k); }

    private static String pct(Recap r) { return r.bets > 0 ? Math.round(r.stats.rate() * 100) + "%" : "-"; }

    /** A centred row of pills: {label, value[, color]}. */
    private void pillsCentred(G g, double cx, double y, Object[][] items, double k) {
        if (k <= 0.01) return;
        double w = -4;
        for (Object[] it : items) w += pillW((String) it[0], (String) it[1]) + 4;
        double x = cx - w / 2;
        g.push(); g.translate(0, (1 - k) * 8);
        for (Object[] it : items) {
            int c = it.length > 2 ? (int) it[2] : WHITE;
            pill(g, x, y, (String) it[0], (String) it[1], alpha(c, k));
            x += pillW((String) it[0], (String) it[1]) + 4;
        }
        g.pop();
    }

    // ---- 1 title

    private void title(G g) {
        double cx = SW / 2, cy = SH / 2;
        double k1 = k(0, 700), k2 = k(500, 600), k3 = k(900, 600);
        glow(g, cx, cy - 10, Math.min(SW, SH) * 0.95, lerp(LAV, WHITE, 0.3), 0.5 * smoothstep(e() / 900.0));   // the theme's lavender, lifted: the halo must match the gradient title
        double sc = Math.min(3, SW * 0.85 / F.width("SESSION RECAP"));
        grad(g, "SESSION RECAP", cx, cy - 26 - 4 * sc, sc, k1);
        String when = r.dateText() + "  ·  " + r.durationText() + (r.live() ? "  ·  still running" : "");
        text(g, when, cx, cy + 10, 1, TEXT, k2);
        String srv = r.server.isEmpty() ? "Singleplayer" : r.server;
        text(g, srv, cx, cy + 24, 1, DIM, k3);
        String by = "by " + (GambleData.name != null ? GambleData.name : MinecraftClient.getInstance().getSession().getUsername());
        text(g, by, cx, cy + 40, 1, MUTE, k(1200, 600));
    }

    // ---- 2 the number

    private void number(G g, double mx, double my) {
        double cx = SW / 2, cy = SH / 2;
        long profit = r.stats.profit();
        double kl = k(0, 500), kn = k(300, 1600);
        if (kn >= 1 && !landed) { landed = true; if (profit > 0) play("pay", 1, 0.6); else if (profit < 0) play("fail", 1.1, 0.3); }
        int col = profit > 0 ? GREEN : profit < 0 ? RED : WHITE;
        glow(g, cx, cy - 50, Math.min(SW, SH) * 0.8, profit >= 0 ? 0xFF4BE07A : 0xFFFF8A8A, 0.22 * kl);
        String label = r.bets == 0 ? "No bet this session" : profit > 0 ? "You made" : profit < 0 ? "You lost" : "You broke even";
        double top = cy - 92;
        text(g, label, cx, top, 1, MUTE, kl);
        String full = Money.signed(profit);
        double sc = Math.min(3, SW * 0.8 / Math.max(1, F.width(full)));
        String shown = profit == 0 ? "$0" : Money.signed(count(profit, kn));
        double bump = kn >= 1 && e() < 300 + 1600 + 260 ? 1 + 0.08 * Math.max(0, 1 - (e() - 1900) / 260.0) : 1;
        g.push(); g.translate(cx, top + 14 + 4 * sc); g.scale(sc * bump, sc * bump);
        g.drawText(shown, -F.width(shown) / 2.0, -4, alpha(col, Math.max(0.02, k(300, 300))), true);
        g.pop();
        // the curve drawing itself in a glass card
        double w = Math.min(SW - 40, 520), h = Math.max(70, Math.min(140, SH * 0.3)), x = cx - w / 2, y = top + 14 + 8 * sc + 16;
        double kc = k(700, 400);
        if (kc > 0.01) {
            g.push(); g.translate(0, (1 - kc) * 10);
            card(g, x, y, w, h, "chart");
            if (r.bets > 0) {
                float reveal = (float) k(900, 1600);
                ProfitChart.Result res = ProfitChart.draw(g.d, r.deltas, (int) x, (int) y, (int) w, (int) h, (int) mx, (int) my, false, 0, true, reveal);
                if (res.hover() > 0) {
                    var t = r.session.txns.get(res.hover() - 1);
                    ProfitChart.tooltip(g.d, t.game(), Money.signed(t.delta()), t.delta() >= 0 ? GREEN : RED, res.px()[res.hover()], (int) y, (int) x, (int) (x + w),
                            DATE_S(t.t()), t.bet() != null && t.bet() > 0 ? "Bet " + Money.format(t.bet()) : "");
                }
                // peak and trough, small, at the card's corners
                String pk = "Peak " + Money.signed(r.peak), tr = "Low " + Money.signed(r.trough);
                g.drawText(pk, x + 6, y + 4, alpha(GREEN, 0.8 * reveal), false);
                g.drawText(tr, x + w - 6 - F.width(tr), y + 4, alpha(RED, 0.8 * reveal), false);
            } else g.drawText("Nothing to draw", x + 8, y + h / 2 - 4, DIM, false);
            g.pop();
        }
        pillsCentred(g, cx, y + h + 8, new Object[][]{{"Bets", String.valueOf(r.bets)}, {"Wagered", Money.format(r.stats.volume())}, {"Win rate", pct(r)}}, k(1500, 500));
    }

    // ---- 3 hottest table

    private void hottest(G g) {
        double cx = SW / 2, cy = SH / 2;
        Recap.GameRow h = r.hottest();
        text(g, "Hottest table", cx, cy - 104, 1, MUTE, k(0, 500));
        if (h == null) { text(g, "No table was played.", cx, cy - 20, 1, TEXT, k(300, 500)); return; }
        double ki = k(200, 700);
        glow(g, cx, cy - 56, 160, PINK, 0.35 * ki);
        g.push(); g.translate(cx, cy - 56); double sc = 0.6 + 0.4 * ki; g.scale(sc, sc);
        Icons.drawGameIcon(g, h.game(), -24, -24, 48);
        g.pop();
        grad(g, h.game(), cx, cy - 22, 2, k(500, 500));
        String vol = Money.format(h.volume()) + " wagered in " + h.bets() + (h.bets() == 1 ? " bet" : " bets");
        text(g, vol, cx, cy, 1, TEXT, k(800, 500));
        double kp = k(1000, 900);
        String pr = "House " + Money.signed(count(h.profit(), kp));
        text(g, pr, cx, cy + 12, 1, h.profit() >= 0 ? GREEN : RED, k(1000, 400));
        // the other tables, ranked
        List<Recap.GameRow> rest = r.games.subList(1, Math.min(r.games.size(), 7));
        double y = cy + 34, w = Math.min(280, SW - 40), x = cx - w / 2;
        if (rest.isEmpty()) { text(g, "The only table of the session.", cx, y + 6, 1, DIM, k(1300, 500)); return; }
        for (int i = 0; i < rest.size(); i++) {
            Recap.GameRow q = rest.get(i);
            double kr = k(1300 + i * 120, 400);
            if (kr <= 0.01) continue;
            double ry = y + i * 18;
            g.push(); g.translate((1 - kr) * 16, 0);
            card(g, x, ry, w, 16, "row");
            Icons.drawGameIcon(g, q.game(), x + 3, ry + 2, 12);
            g.drawText("#" + (i + 2), x + 18, ry + 4, alpha(DIM, kr), false);
            g.drawText(F.trimToWidth(q.game(), 90), x + 36, ry + 4, alpha(TEXT, kr), false);
            String bets = q.bets() + (q.bets() == 1 ? " bet" : " bets"), p = Money.signed(q.profit());
            g.drawText(p, x + w - 6 - F.width(p), ry + 4, alpha(q.profit() >= 0 ? GREEN : RED, kr), false);
            g.drawText(bets, x + w - 6 - F.width(p) - 8 - F.width(bets), ry + 4, alpha(MUTE, kr), false);
            g.pop();
        }
    }

    // ---- 4 high roller podium

    private void podium(G g) {
        double cx = SW / 2, cy = SH / 2; long t = now();
        if (r.rollers.isEmpty()) {
            text(g, "High roller", cx, cy - 110, 1, MUTE, k(0, 500));
            chibiGhost(g, cx, cy + 30, 4);
            text(g, "No table player this session", cx, cy + 44, 1, TEXT, k(300, 500));
            text(g, "Bets placed with a player's chips put him here.", cx, cy + 58, 1, DIM, k(600, 500));
            return;
        }
        double p = SH >= 500 ? 5 : SH >= 400 ? 4 : 3, ps = Math.max(2, p - 2);
        double stepW = Math.min(120, (SW - 40) / 3.2), gap = 8, base = Math.min(SH - 70, cy + 92);
        double[] stepH = {48, 36, 28};
        long[] delay = {500, 250, 0};
        double topName = Double.MAX_VALUE;   // where the highest name rests once landed: the caption sits above it
        for (int col = 0; col < 3; col++) {
            int rank = ORDER[col], m = MEDAL[rank];
            if (rank >= r.rollers.size()) continue;
            Recap.Roller ro = r.rollers.get(rank);
            double sx = cx - stepW * 1.5 - gap + col * (stepW + gap), scx = sx + stepW / 2;
            double grow = k(delay[rank], 700), sh = stepH[rank] * grow, top = base - sh;
            if (sh < 1) continue;
            double pp = rank == 0 ? p : ps;
            double kk = rank == 0 ? 1 : 0.55, hy = top - 1 - CHIBI_H / 2.0 * pp;
            for (int rr : new int[]{15, 11, 7}) smooth(g, scx - rr * pp, hy - rr * pp * 1.2, 2 * rr * pp, 2.4 * rr * pp, rr * pp * 0.9, alpha(m, 0.06 * kk * grow));
            smooth(g, sx, top, stepW, sh, 6, v(alpha(m, 0.2), alpha(m, 0.03)), alpha(m, 0.5));
            g.fill(sx + 8, top + 1, sx + stepW - 8, top + 2, alpha(m, 0.9));
            String num = String.valueOf(rank + 1);
            if (sh > 20) { g.push(); g.translate(scx - F.width(num), top + 5); g.scale(2, 2); g.drawText(num, 0, 0, m, true); g.pop(); }
            String v = Money.format(ro.wagered());
            if (sh > 32) { if (rank == 0) gradientText(g, v, scx - F.width(v) / 2.0, top + 24, true); else g.drawText(v, scx - F.width(v) / 2.0, top + 24, WHITE, true); }
            String bets = ro.bets() + (ro.bets() == 1 ? " bet" : " bets");
            if (sh > 44) g.drawText(bets, scx - F.width(bets) / 2.0, top + 36, MUTE, false);
            // the name sits 12 above the chibi, plus the crown (3 texels) and the hop (4 texels) of the winner, who always has both
            double lift = CHIBI_H * pp + (rank == 0 ? 7 * pp : 0) + 12;
            topName = Math.min(topName, top - 1 - lift);
            // the chibi drops onto his step once it has grown
            double land = k(delay[rank] + 600, 380);
            if (land <= 0.01) continue;
            double feet = top - 1 - (1 - land) * 140;
            boolean down = land >= 1;
            if (rank == 0 && down && !crowned) { crowned = true; play("mc_levelup", 1.2, 0.6); }
            chibi(g, ro.name(), scx, feet, pp, t, rank == 0 && down, rank == 0 && down);
            if (rank == 0 && down) sparkles(g, scx, feet, pp, t);
            String nm = F.trimToWidth(ro.name(), stepW - 6);
            double ny = feet - lift;
            if (rank == 0) gradientText(g, nm, scx - F.width(nm) / 2.0, ny, true, land); else g.drawText(nm, scx - F.width(nm) / 2.0, ny, alpha(TEXT, land), true);
        }
        // the caption above the tallest name (the crowned winner's at 108 on 960 x 540), never inside the crown
        double ty = topName == Double.MAX_VALUE ? cy - 110 : topName - 18;
        text(g, "High roller", cx, Math.max(6, ty), 1, MUTE, k(0, 500));
        Recap.Roller m = r.rollers.get(0);
        String line = m.name() + " wagered " + Money.format(m.wagered()) + " in " + m.bets() + (m.bets() == 1 ? " bet" : " bets");
        text(g, line, cx, base + 10, 1, TEXT, k(1500, 500));
        String net = m.net() > 0 ? "and left " + Money.format(m.net()) + " richer" : m.net() < 0 ? "and left " + Money.format(-m.net()) + " at your table" : "and left even";
        text(g, net, cx, base + 22, 1, m.net() > 0 ? GREEN : m.net() < 0 ? RED : MUTE, k(1900, 500));
    }

    // ---- 5 moments

    private void moments(G g) {
        double cx = SW / 2, cy = SH / 2;
        text(g, "Moments", cx, cy - 96, 1, MUTE, k(0, 500));
        double w = Math.min(150, (SW - 60) / 3), h = 56, gap = 8, x0 = cx - (3 * w + 2 * gap) / 2, y = cy - 70;
        Long best = r.stats.best(), worst = r.stats.worst();
        boolean winRun = r.winStreak >= r.lossStreak;
        int streak = Math.max(r.winStreak, r.lossStreak);
        String[] labels = {"Best win", "Worst loss", "Longest streak"};
        for (int i = 0; i < 3; i++) {
            double kc = k(200 + i * 250, 500), kv = k(500 + i * 250, 900);
            if (kc <= 0.01) continue;
            double x = x0 + i * (w + gap);
            g.push(); g.translate(0, (1 - kc) * 12);
            card(g, x, y, w, h, i == 0 ? "accent" : i == 1 ? "red" : "soft");
            g.drawText(labels[i], x + 10, y + 8, alpha(MUTE, kc), false);
            String val; int col;
            if (i == 0) { val = best == null ? "-" : Money.signed(count(best, kv)); col = best == null ? DIM : GREEN; }
            else if (i == 1) { val = worst == null ? "-" : Money.signed(-count(-worst, kv)); col = worst == null ? DIM : RED; }
            else { val = streak == 0 ? "-" : count(streak, kv) + (winRun ? " wins" : " losses"); col = streak == 0 ? DIM : winRun ? GREEN : RED; }
            double sc = Math.min(2, (w - 20) / Math.max(1, F.width(val)));
            g.push(); g.translate(x + 10, y + 24); g.scale(sc, sc); g.drawText(val, 0, 0, alpha(col, kc), true); g.pop();
            if (i == 2 && streak > 0) g.drawText("in a row", x + 10, y + 44, alpha(DIM, kc), false);
            g.pop();
        }
        double ky = y + h + 16, kb = k(1200, 600);
        if (r.biggestPay != null) {
            String a = "Biggest payment: ", b = r.biggestPay.name() + " paid " + Money.format(r.biggestPay.amount());
            double tw = 12 + F.width(a) + F.width(b), x = cx - tw / 2;
            g.push(); g.translate(0, (1 - kb) * 8);
            g.drawHead(r.biggestPay.name(), x, ky - 2, 10);
            g.drawText(a, x + 14, ky, alpha(MUTE, kb), false);
            g.drawText(b, x + 14 + F.width(a), ky, alpha(WHITE, kb), true);
            g.pop();
        } else text(g, "No payment received this session.", cx, ky, 1, DIM, kb);
        // the curve's extremes, one line
        if (r.bets > 0) text(g, "Peak " + Money.signed(r.peak) + "   ·   Low " + Money.signed(r.trough), cx, ky + 16, 1, DIM, k(1600, 600));
    }

    // ---- 6 the table

    private void table(G g) {
        double cx = SW / 2, cy = SH / 2;
        int n = r.players.size();
        text(g, "The table", cx, cy - 100, 1, MUTE, k(0, 500));
        String who = n == 0 ? "Nobody sat at your table" : n == 1 ? "1 player sat at your table" : n + " players sat at your table";
        text(g, who, cx, cy - 80, Math.min(2, SW * 0.9 / F.width(who)), WHITE, k(200, 600));
        double kt = k(600, 600);
        Object[][] pills = r.owed > 0
                ? new Object[][]{{"Paid in", Money.format(r.paidIn)}, {"Sent back", Money.format(r.sentBack)}, {"Still owed", Money.format(r.owed), RED}}
                : new Object[][]{{"Paid in", Money.format(r.paidIn)}, {"Sent back", Money.format(r.sentBack)}, {"", "All settled", GREEN}};
        pillsCentred(g, cx, cy - 50, pills, kt);
        if (n == 0) { text(g, "Players who /pay you during a session land here.", cx, cy - 10, 1, DIM, k(900, 500)); return; }
        // up to 8 heads in a row, biggest payer first
        List<String> heads = r.heads;
        double sp = Math.min(52, (SW - 40) / Math.max(1, heads.size())), size = 20, x0 = cx - (heads.size() * sp) / 2 + sp / 2, hy = cy - 16;
        for (int i = 0; i < heads.size(); i++) {
            double kh = k(900 + i * 110, 450);
            if (kh <= 0.01) continue;
            String name = heads.get(i);
            double hx = x0 + i * sp;
            long c = r.session.credit.getOrDefault(name, 0L), w = r.session.won.getOrDefault(name, 0L), st = r.session.sent.getOrDefault(name, 0L);
            long bal = Math.max(0, c + w - st);
            g.push(); g.translate(0, (1 - kh) * 12);
            double sc = 0.7 + 0.3 * kh;
            g.push(); g.translate(hx, hy + size / 2); g.scale(sc, sc); g.drawHead(name, -size / 2, -size / 2, size); g.pop();
            if (bal > 0) ring(g, hx - size / 2 - 1, hy - 1, size + 2, size + 2, 3, alpha(RED, 0.8 * kh), 1);
            String nm = F.trimToWidth(name, sp - 4);
            g.drawText(nm, hx - F.width(nm) / 2.0, hy + size + 5, alpha(TEXT, kh), false);
            String v = Money.format(c);
            g.drawText(v, hx - F.width(v) / 2.0, hy + size + 16, alpha(MUTE, kh), false);
            g.pop();
        }
        if (n > heads.size()) text(g, "and " + (n - heads.size()) + " more", cx, hy + size + 32, 1, DIM, k(1800, 500));
        if (r.owed > 0) text(g, "A red ring: chips still to send back.", cx, hy + size + 46, 1, DIM, k(2000, 500));
    }

    // ---- 7 share

    private void share(G g) {
        double cx = SW / 2, x = cx - CW / 2.0, y = SH / 2 - CH / 2.0;
        double kc = k(0, 500);
        g.push(); g.translate(0, (1 - kc) * 12);
        shareCard(g, x, y, kc);
        g.pop();
        long t = now();
        String st = savedAt >= 0 && t - savedAt < 2000 ? "Saved to screenshots" : copiedAt >= 0 && t - copiedAt < 2000 ? "Copied" : null;
        if (st != null) text(g, st, cx, y + CH + 40, 1, GREEN, 1);
    }

    /** The share card: title, the profit, three pills, the curve, the high roller. Drawn alone for the screenshot. */
    private void shareCard(G g, double x, double y, double a) {
        glass(g, x, y, CW, CH, 12, 0.92 * a, a);
        ring(g, x, y, CW, CH, 12, alpha(PINK, 0.35 * a), 1);
        gradientText(g, "EzGamble · Session recap", x + 12, y + 10, true, a);
        String d = r.dateText() + " · " + r.durationText();
        g.drawText(d, x + CW - 12 - F.width(d), y + 10, alpha(DIM, a), false);
        g.drawText("Profit", x + 12, y + 26, alpha(MUTE, a), false);
        long profit = r.stats.profit();
        String pr = Money.signed(profit);
        // the three pills right-aligned (the card never loses its win rate), the profit at scale 2 or smaller to fit the room left
        Object[][] pills = {{"Bets", String.valueOf(r.bets), alpha(WHITE, a)}, {"Wagered", Money.format(r.stats.volume()), alpha(WHITE, a)}, {"Win", pct(r), alpha(WHITE, a)}};
        int pw = -4;
        for (Object[] it : pills) pw += pillW((String) it[0], (String) it[1]) + 4;
        double pillsX = x + CW - 12 - pw;
        double sc = Math.min(2, (pillsX - 10 - (x + 12)) / Math.max(1, F.width(pr)));
        g.push(); g.translate(x + 12, y + 36 + (2 - sc) * 4); g.scale(sc, sc); g.drawText(pr, 0, 0, alpha(profit > 0 ? GREEN : profit < 0 ? RED : WHITE, a), true); g.pop();
        pillsRight(g, x + CW - 12, y + 36, pills);
        double chy = y + 60, chh = 62;
        card(g, x + 12, chy, CW - 24, chh, "chart");
        if (r.bets > 0) ProfitChart.draw(g.d, r.deltas, (int) (x + 12), (int) chy, CW - 24, (int) chh, -1, -1, false, 0, true, 1f);
        else g.drawText("No bet", x + 20, chy + chh / 2 - 4, alpha(DIM, a), false);
        Recap.Roller m = r.mvp();
        double ly = y + CH - 20;
        String by = "by " + (GambleData.name != null ? GambleData.name : MinecraftClient.getInstance().getSession().getUsername());
        double byX = x + CW - 12 - F.width(by);
        if (m != null) {
            g.drawHead(m.name(), x + 12, ly - 2, 12);
            g.drawText("High roller", x + 28, ly, alpha(MUTE, a), false);
            // the name takes what is left between the label and "by <host>": a long name and a long amount share one line
            double lx2 = x + 28 + F.width("High roller") + 6;
            String amt = Money.format(m.wagered());
            String s = F.trimToWidth(m.name(), byX - 8 - lx2 - 8 - F.width(amt)) + "  " + amt;
            g.drawText(s, lx2, ly, alpha(WHITE, a), true);
        } else {
            String s = r.players.size() + (r.players.size() == 1 ? " player" : " players") + " at the table";
            g.drawText(s, x + 12, ly, alpha(MUTE, a), false);
        }
        g.drawText(by, byX, ly, alpha(DIM, a), false);
    }
}
