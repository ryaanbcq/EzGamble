package dev.ryaan.ezgamble.hud;

import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payments;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Stats;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.hud.HudConfig.Rect;
import dev.ryaan.ezgamble.hud.HudConfig.Style;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.ui.Icons;
import dev.ryaan.ezgamble.ui.ProfitChart;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static dev.ryaan.ezgamble.hud.HudConfig.*;
import static dev.ryaan.ezgamble.proto.P.*;

/** The in-game HUD (prototype GambleHud + widgets): session card, last-bet toast, payments card. */
public final class GambleHud implements HudElement {
    private static boolean logged;

    @Override
    public void render(DrawContext d, RenderTickCounter t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.currentScreen instanceof HudEditorScreen) return;   // F1, or the editor draws its own copy
        if (!GambleData.mode) return;   // gambling mode off: no HUD at all
        G g = new G(d);
        long t0 = System.nanoTime();
        try {
            drawToast(g, toastK());
            for (String id : new String[]{"session", "pay"}) if (HUD.get(id).visible) { Rect r = wrect(id); drawWidget(g, id, r.x(), r.y()); }
        } catch (Throwable e) {
            if (!logged) { logged = true; EzGambleMod.LOG.error("[ezgamble] HUD render failed (not logged again)", e); }
        } finally {
            g.closeAll();
            if (dev.ryaan.ezgamble.DebugRun.ON && mc.currentScreen == null) { dev.ryaan.ezgamble.DebugRun.renderNanos += System.nanoTime() - t0; dev.ryaan.ezgamble.DebugRun.renderFrames++; }
        }
    }

    // ---- helpers

    private static void valueText(G g, String s, double x, double y, Style c, long v, boolean signed) {
        if (signed && c.autoSignColor) g.drawText(s, x, y, v >= 0 ? GREEN : RED, true);
        else if (c.gradient()) gradientText(g, s, x, y, true);
        else g.drawText(s, x, y, c.rgb(), true);
    }

    private static String money(long v, Style c, boolean signed) {
        boolean full = c.numbers.equals("full");
        if (!signed) return full ? Money.full(v) : Money.format(v);
        return full ? (v >= 0 ? "+" : "-") + Money.full(Math.abs(v)) : Money.signed(v);
    }

    private static void right(G g, int W, String s, double yy, int col) { g.drawText(s, W - 7 - F.width(s), yy, col, true); }

    private static Txn lastTx() {
        List<Session> ss = GambleData.sessions;
        for (int i = ss.size() - 1; i >= 0; i--) { List<Txn> t = ss.get(i).txns; if (!t.isEmpty()) return t.get(t.size() - 1); }
        return null;
    }

    private static long allProfit() {
        long p = 0;
        for (Session s : GambleData.sessions) for (Txn t : s.txns) p += t.delta();
        return p;
    }

    // the session's deltas as an array, rebuilt only when the list changes (the chart caches its texture by content)
    private static Session dSess; private static int dN = -1; private static long[] dArr = new long[0];
    private static long[] deltas(Session s, List<Txn> tx) {
        if (s != dSess || tx.size() != dN) {
            dSess = s; dN = tx.size();
            dArr = new long[dN];
            for (int i = 0; i < dN; i++) dArr[i] = tx.get(i).delta();
        }
        return dArr;
    }

    // ---- widgets

    public static void drawWidget(G g, String id, int x, int y) {
        Style c = style(id);
        int W = baseW(id), H = baseH(id);
        long t = now();
        double k = effScale(id);
        g.push(); g.translate(x, y); g.scale(k, k);
        Payments.Paid pc = Payments.cur;
        long pel = pc != null ? t - pc.at() : -1;
        if (id.equals("pay") && pc != null && pc.amount() >= Payments.PAY_BIG && pel < 400) {   // big payment: AuctionHud's shake, 400 ms
            double amp = 2 * (1 - pel / 400.0);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            g.translate((r.nextDouble() * 2 - 1) * amp, (r.nextDouble() * 2 - 1) * amp);
        }
        if (c.background > 0) glass(g, 0, 0, W, H, 8, 0.7 * c.background, c.background);
        Session sess = GambleData.last();
        List<Txn> sTx = sess != null ? sess.txns : List.of();
        switch (id) {
            case "session" -> {
                Stats st = Stats.of(sTx);
                g.drawText("Session", 7, 6, MUTE, true);
                Session cur = GambleData.current();
                if (cur != null) right(g, W, clock(t - cur.start), 6, WHITE); else right(g, W, "OFF", 6, RED);
                // big: your balance (sidebar / last /bal), the session's profit small on its right; before the balance is
                // known, the profit alone as before. x2 like the menu, shrunk only if the full format does not fit
                long bal = dev.ryaan.ezgamble.data.Balance.value();
                if (bal >= 0) {
                    String pr = money(st.profit(), c, true), big = money(bal, c, false);
                    int pw = F.width(pr);
                    double sc = Math.min(2, (W - 14.0 - pw - 6) / F.width(big));
                    g.push(); g.translate(7, 17 + (16 - 8 * sc) / 2); g.scale(sc, sc); valueText(g, big, 0, 0, c, bal, false); g.pop();
                    if (c.autoSignColor) right(g, W, pr, 21, st.profit() >= 0 ? GREEN : RED); else valueText(g, pr, W - 7 - pw, 21, c, st.profit(), false);
                } else {
                    String big = money(st.profit(), c, true);
                    double sc = Math.min(2, (W - 14.0) / F.width(big));
                    g.push(); g.translate(7, 17 + (16 - 8 * sc) / 2); g.scale(sc, sc); valueText(g, big, 0, 0, c, st.profit(), true); g.pop();
                }
                HudConfig.Rows r = HUD.session.rows;
                int yy = 37;
                if (!(r.curve || r.winrate || r.total)) break;
                g.fill(7, yy, W - 7, yy + 1, 0x14FFFFFF);
                yy += 4;
                if (r.curve) {
                    smooth(g, 6, yy, W - 12, 24, 5, 0x0AFFFFFF);
                    if (!sTx.isEmpty()) ProfitChart.draw(g.d, deltas(sess, sTx), 6, yy, W - 12, 24, -1, -1, true, c.gradient() ? LAV : c.rgb(), c.gradient(), 1f);
                    yy += CURVE_H;
                }
                if (r.winrate) {
                    String pct = st.n() > 0 ? Math.round(st.rate() * 100) + "%" : "-";
                    g.drawText("Win", 7, yy, MUTE, true);
                    int pc2 = st.n() == 0 ? DIM : c.autoSignColor ? (st.rate() >= 0.5 ? GREEN : RED) : c.gradient() ? PINK : c.rgb();
                    g.drawText(pct, 7 + F.width("Win "), yy, pc2, true);
                    String bets = st.n() + " bet" + (st.n() == 1 ? "" : "s");
                    right(g, W, bets, yy, DIM);
                    int bx = 7 + F.width("Win 100%") + 4, bw = W - 7 - F.width(bets) - 6 - bx;
                    if (bw > 8) {
                        g.fill(bx, yy + 3, bx + bw, yy + 6, 0x1AFFFFFF);
                        int fw = (int) Math.round(bw * st.rate());
                        if (fw > 0) { if (c.gradient()) accentBar(g, bx, yy + 3, fw, 3, 1); else g.fill(bx, yy + 3, bx + fw, yy + 6, c.rgb()); }
                    }
                    yy += WINRATE_H;
                }
                if (r.total) {
                    long p = allProfit();
                    g.drawText("All-time", 7, yy, MUTE, true);
                    String v = money(p, c, true);
                    if (c.autoSignColor) right(g, W, v, yy, p >= 0 ? GREEN : RED); else valueText(g, v, W - 7 - F.width(v), yy, c, p, false);
                }
            }
            case "last" -> {   // the toast
                Txn last = lastTx();
                if (last == null) { g.drawText("No bet yet.", 7, 8, DIM, true); break; }
                if (t - GambleData.lastTxAt < 1500) ring(g, 0, 0, W, H, 8, alpha(last.delta() >= 0 ? GREEN : RED, 0.7), 1.4);
                Icons.drawGameIcon(g, last.game(), 4, 4, 16);
                String v = money(last.delta(), c, true);
                int vw = F.width(v);
                g.drawText(last.game(), 24, 8, WHITE, true);
                if (c.autoSignColor) right(g, W, v, 8, last.delta() >= 0 ? GREEN : RED); else valueText(g, v, W - 7 - vw, 8, c, last.delta(), false);
            }
            case "pay" -> {
                // the idle layer (you) slides up and out, the payment slides in from below, then back
                double kk = 0;
                if (pc != null) kk = pel < Payments.PAY_IN ? easeOut(pel / (double) Payments.PAY_IN)
                        : pel > Payments.PAY_MS - Payments.PAY_OUT ? 1 - easeIn((pel - (Payments.PAY_MS - Payments.PAY_OUT)) / (double) Payments.PAY_OUT) : 1;
                int off = (int) Math.round(kk * H);
                boolean big = pc != null && pc.amount() >= Payments.PAY_BIG;
                String me = MinecraftClient.getInstance().getSession().getUsername();
                g.enableScissor(1, 1, W - 1, H - 1);
                if (kk < 1) {
                    g.push(); g.translate(0, -off);
                    g.drawHead(skinOf(me), 8, 9, 16);
                    if (c.gradient()) gradientText(g, me, 30, 8, true); else g.drawText(me, 30, 8, c.rgb(), true);
                    Session cs2 = GambleData.mode ? GambleData.current() : null;
                    long cr = 0;
                    if (cs2 != null) for (long v : cs2.credit.values()) cr += v;
                    String sub = cr != 0 ? "Credit " + money(cr, c, false) : Payments.count > 0 ? "Received " + money(Payments.total, c, false) : "/pay " + me;
                    g.drawText(F.trimToWidth(sub, W - 37), 30, 19, SOFT_GREY, true);
                    g.pop();
                }
                if (pc != null) {
                    g.push(); g.translate(0, H - off);
                    double sc = pel < 250 ? 1 + 0.4 * (1 - easeOut(pel / 250.0)) : 1;   // head pops in
                    g.push(); g.translate(16, 17); g.scale(sc, sc); g.drawHead(skinOf(pc.name()), -8, -8, 16); g.pop();
                    // line 1: the name, then " paid you" only if it fits; a long name is cut, never runs under the edge
                    int avail = W - 37;
                    if (F.width(pc.name() + " paid you") <= avail) { g.drawText(pc.name(), 30, 8, WHITE, true); g.drawText(" paid you", 30 + F.width(pc.name()), 8, MUTE, true); }
                    else g.drawText(F.trimToWidth(pc.name(), avail), 30, 8, WHITE, true);
                    if (!Payments.queue.isEmpty()) right(g, W, "+" + Payments.queue.size() + " more", 19, DIM);   // waiting payments: on the amount line, out of the name's way
                    long v = Math.round(pc.amount() * easeOut(Math.min(1, pel / 650.0)));   // amount counts up
                    String amt = "+" + money(v, c, false);
                    if (big) gradientText(g, amt, 30, 19, true);
                    else if (c.autoSignColor || c.gradient()) g.drawText(amt, 30, 19, GREEN, true);
                    else g.drawText(amt, 30, 19, c.rgb(), true);
                    if (pel < 700) {   // light sweep across the card (AuctionHud.shimmer, full height, faint)
                        int band = 36, bx = -band + (int) Math.round((W + band) * (pel / 700.0));
                        int from = Math.max(0, bx), to = Math.min(W, bx + band);
                        if (to > from) accentBar(g, Math.max(3, from), 2, Math.min(W - 3, to) - Math.max(3, from), H - 4, 0.16);
                    }
                    g.pop();
                }
                g.disableScissor();
                if (pc != null) {
                    double f = Math.max(0, 1 - pel / 1200.0);
                    if (f > 0) ring(g, 0, 0, W, H, 8, alpha(big ? PINK : GREEN, 0.95 * f), 1.6);
                    if (pel < 1400) {   // "+$X" floating up out of the card, XP style
                        String s2 = "+" + Money.format(pc.amount());
                        double a = 1 - pel / 1400.0;
                        g.drawText(s2, W - 7 - F.width(s2), -10 - Math.round(pel / 1400.0 * 14), alpha(big ? PINK : GREEN, a), true);
                    }
                }
            }
            default -> {}
        }
        g.pop();
    }

    /** 0..1: how far the toast is out of the card. In 250 ms, stays 3.5 s, back in 250 ms. */
    public static double toastK() {
        if (GambleData.lastTxAt == 0) return 0;
        long t = now() - GambleData.lastTxAt;
        return t < 250 ? easeOut(t / 250.0) : t < 3500 ? 1 : t < 3750 ? 1 - easeIn((t - 3500) / 250.0) : 0;
    }

    /** Toast drawn BEFORE the card, so it slides out from under it. */
    public static void drawToast(G g, double k) {
        if (k <= 0 || !HUD.last.visible) return;
        Rect r = wrect("last");
        int off = (int) Math.round((1 - k) * (r.h() + 4)) * (Boolean.FALSE.equals(r.below()) ? 1 : -1);
        g.push(); g.translate(0, off); drawWidget(g, "last", r.x(), r.y()); g.pop();
    }
}
