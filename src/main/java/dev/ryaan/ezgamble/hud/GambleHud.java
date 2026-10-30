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
            drawRain(g);   // last: its finale covers the whole screen
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

    // ---- the rain bubble: Agendium's chat cloud in Liquid Glass (gen_rain_cloud.py). Glass body, the pot as a liquid
    // under a moving wave, the lens on top; drops fall in for every ticket, the bubble breathes and bursts at the end.

    private static final int TW = 300, TH = 293, BW = 76, BH = 74;   // texture size (Ryan's logo), box in GUI px
    private static final double TK = TW / (double) BW;                 // texels per GUI px
    private static final long POP_MS = 1000, INFLATE_MS = 240, FALL_MS = 340;
    /** The level drawn (a spring toward the pot, with a little overshoot), the wave height, the jelly squash. */
    private static double lvl, lvlV, lvlTarget, amp = 1, jelly, jellyV;
    private static long lastFrame, popDelay;
    private static int seenTickets;
    /** Drops falling into the bubble: when, where, the fill level they bring. */
    private record Drop(long at, double x, double k) {}
    private static final java.util.ArrayList<Drop> drops = new java.util.ArrayList<>();
    private record Splash(long at, double x, double y) {}
    private static final java.util.ArrayList<Splash> splashes = new java.util.ArrayList<>();

    private static double waveY(double x, double level, double t, double a) {
        return level + a * Math.sin(x * 0.13 + t * 2.2) + a * 0.5 * Math.sin(x * 0.29 - t * 3.1);
    }

    /** Fill 0..1 to the surface's height in the 76 x 74 box: the bottom lobes (72) to the top one (2). */
    private static double levelOf(double k) { return 72 - 70 * k; }

    /** The cloud alone (glass, liquid under its wave, bubbles), no label: the HUD, the menu, and the finale's big sky. */
    public static void drawCloudBody(G g, double k, double fade) {
        int tint = alpha(0xFFFFFFFF, fade);
        double t = now() / 1000.0;
        g.drawSprite("rain/back", 0, 0, BW, BH, tint);
        if (k > 0.004) {
            double level = levelOf(k), a = Math.min(amp, 1 + 6 * k) * (k > 0.97 ? 0.4 : 1);   // a nearly full cloud barely moves
            for (int c = 0; c < TW; c += 2) {
                double wy = waveY((c + 1) / TK, level, t, a);
                int top = Math.max(0, (int) Math.round(wy * TK));
                if (top >= TH) continue;
                g.drawSpritePart("rain/liquid", c / TK, top / TK, c, top, 2, TH - top, TW, TH, 1 / TK, tint);
                g.drawSpritePart("rain/surface", c / TK, top / TK, c, top, 2, Math.min(5, TH - top), TW, TH, 1 / TK, tint);
            }
            // bubbles rising through the liquid, never above its surface
            for (int i = 0; i < 4; i++) {
                double period = 2.6 + i * 0.7, ph = (t / period + i * 0.37) % 1;
                double bx = 16 + ((i * 53) % 44) + Math.sin(t * 2 + i) * 1.5, by = 68 - ph * (68 - level);
                if (by < level + 2 || k < 0.06) continue;
                double r = 0.7 + 0.25 * i % 0.8;
                smooth(g, bx - r, by - r, 2 * r, 2 * r, r, alpha(0xFFFFFFFF, fade * 0.6 * Math.sin(ph * Math.PI)));
            }
        }
    }

    /**
     * The bubble in its 100 x 64 box at (0, 0), filled to k. Shared by the HUD and the Rain screen of the menu (which
     * passes the real fill, no drops). The wave runs on the clock and on the HUD's slosh.
     */
    public static void drawCloud(G g, double k, long pot, long goal, double fade) {
        drawCloudBody(g, k, fade);
        // the fill in the middle of the cloud, the amounts in a glass pill under it
        String pc = Math.min(100, (int) Math.floor(100.0 * pot / Math.max(1, goal))) + "%";
        g.push(); g.translate(38.5, 37); g.scale(1.75, 1.75);
        g.drawText(pc, -F.width(pc) / 2.0, -4, alpha(0xFF3A2766, fade), false);
        g.pop();
        String amt = Money.format(pot), sub = " of " + Money.format(goal);
        double tw = F.width(amt) + F.width(sub), px = BW / 2.0 - tw / 2 - 6;
        smooth(g, px, BH + 2, tw + 12, 13, 6.5, alpha(0xFFFFFFFF, fade * 0.96), alpha(0xFFD9CCF2, fade));
        g.drawText(amt, px + 6, BH + 5, alpha(0xFF2E1F4F, fade), false);
        g.drawText(sub, px + 6 + F.width(amt), BH + 5, alpha(0xFF7A68A8, fade), false);
    }

    /**
     * Top centre of the screen while a rain collects. A ticket is a drop falling into the bubble: on impact the
     * level springs up, the surface sloshes and the bubble squashes like jelly. The end (goal, Spin now, Cancel)
     * inflates it, then it bursts into drops and coins.
     */
    static void drawRain(G g) {
        var rn = dev.ryaan.ezgamble.games.rain.RainTable.RN;
        long t = now();
        boolean open = rn.stage.equals("open");
        // the goal reached by a payment: its drop falls in first, then the bubble bursts
        if (!open && rn.popAt > 0 && t - rn.popAt < 60 && popDelay == 0 && seenTickets > 0 && rn.count() > seenTickets)
            popDelay = FALL_MS + 110L * (Math.min(rn.count() - seenTickets, 5) - 1) + 120;
        long popAt = rn.popAt + popDelay;
        // the goal reached (or Spin now): the finale takes the whole screen until the winner has been shown
        boolean finale = !open && rn.round != null && rn.popAt > 0 && t < RainFinale.end(rn.round);
        boolean popping = finale || !open && rn.popAt > 0 && t - rn.popAt < POP_MS + popDelay;
        if (!open && !popping) { lvl = lvlV = lvlTarget = 0; seenTickets = 0; drops.clear(); splashes.clear(); lastFrame = 0; amp = 1; popDelay = 0; return; }
        if (open) popDelay = 0;
        if (finale && t >= popAt) { RainFinale.draw(g, rn, popAt, t); lastFrame = t; return; }
        double dt = lastFrame == 0 ? 0 : Math.min(0.05, (t - lastFrame) / 1000.0);
        lastFrame = t;
        double real = open ? (rn.goal > 0 ? Math.min(1, (double) rn.pot() / rn.goal) : 0) : rn.popK;
        if (rn.count() != seenTickets && (open || popDelay > 0)) {
            int n = rn.count() - seenTickets;
            if (n > 0) {
                var rnd = ThreadLocalRandom.current();
                int shown = Math.min(n, 5);
                for (int i = 0; i < shown; i++) drops.add(new Drop(t + i * 110L, 14 + rnd.nextDouble() * 48, i == shown - 1 ? real : lvlTarget + (real - lvlTarget) * (i + 1) / shown));
            } else lvlTarget = real;   // a refund: no drop, the level just sinks
            seenTickets = rn.count();
        }
        if (popping && drops.isEmpty()) lvlTarget = real;
        // drops landing: the level they bring, a slosh, a squash
        for (var it = drops.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            if (t - d.at() < FALL_MS) continue;
            it.remove();
            lvlTarget = Math.max(lvlTarget, d.k());
            amp = Math.min(4.5, amp + 2.2); jellyV += 2.4;
            splashes.add(new Splash(t, d.x(), waveY(d.x(), levelOf(lvl), t / 1000.0, amp)));
            play("ui_tick_close", 1.5 + ThreadLocalRandom.current().nextDouble() * 0.3, 0.35);
        }
        splashes.removeIf(s -> t - s.at() > 420);
        // springs (real time: same feel at any frame rate)
        lvlV += (lvlTarget - lvl) * 110 * dt; lvlV *= Math.exp(-11 * dt); lvl += lvlV * dt;
        jellyV += -jelly * 260 * dt; jellyV *= Math.exp(-7 * dt); jelly += jellyV * dt;
        amp += (1 - amp) * (1 - Math.exp(-1.6 * dt));
        double k = Math.max(0, Math.min(1.02, lvl)), ts = t / 1000.0;
        double breath = Math.sin(ts * 1.15) * (1 + k);
        double sx = (1 + 0.012 * breath + 0.06 * jelly), sy = (1 - 0.010 * breath - 0.06 * jelly);
        double sc = 0.9 + 0.1 * easeOut(k), fade = 1, f = 0;
        if (popping && t >= popAt) {   // inflate with a wobble, then burst
            long e = t - popAt;
            double in = Math.min(1, e / (double) INFLATE_MS);
            sc *= 1 + 0.16 * easeOut(in) + 0.025 * Math.sin(e / 18.0) * (1 - in);
            if (e > INFLATE_MS) { f = (e - INFLATE_MS) / (double) (POP_MS - INFLATE_MS); sc *= 1 + 0.35 * easeOut(Math.min(1, f * 3)); fade = Math.max(0, 1 - f * 4); }
        }
        g.push(); g.translate(sw() / 2, 6 + 37); g.scale(sc * sx, sc * sy); g.translate(-BW / 2.0, -37);
        if (fade > 0) drawCloud(g, k, open || t < popAt ? rn.pot() : rn.popPot, rn.goal, fade);
        // falling drops, accelerating, and the splash rings where they land
        for (Drop d : drops) {
            double e = (t - d.at()) / (double) FALL_MS;
            if (e < 0) continue;
            double y = -16 + (waveY(d.x(), levelOf(lvl), ts, amp) + 16) * e * e;
            g.drawSprite("rain/drop", d.x() - 4, y - 11, 8, 11, alpha(0xFFFFFFFF, Math.min(1, e * 4)));
        }
        if (k < 0.85) for (Splash s : splashes) {   // a nearly full cloud has no room above its surface
            double e = (t - s.at()) / 420.0;
            for (int i = 0; i < 4; i++) {
                double dir = (i < 2 ? -1 : 1) * (0.5 + 0.6 * (i % 2)), x = s.x() + dir * 7 * e, y = s.y() - 7 * Math.sin(e * Math.PI) * (1 - 0.3 * (i % 2));
                if (y < 6) continue;   // inside the glass only (a full bubble has no room above its surface)
                smooth(g, x - 0.9, y - 0.9, 1.8, 1.8, 0.9, alpha(i % 2 == 0 ? 0xFFFFFFFF : LAV, 1 - e));
            }
        }
        if (f > 0) {   // the burst: drops of the liquid flung out (tail behind them) and a few coins, then falling
            for (int i = 0; i < 18; i++) {
                double ang = i * Math.PI * 2 / 18 + 0.3, sp = 36 + (i * 37 % 23);
                double e = easeOut(Math.min(1, f * 1.5));
                double x = 38 + Math.cos(ang) * sp * e, y = 37 + Math.sin(ang) * sp * 0.75 * e + 34 * f * f;
                double a = Math.max(0, 1 - f);
                if (i % 3 == 0) { smooth(g, x - 1.8, y - 1.8, 3.6, 3.6, 1.8, alpha(0xFFF2C14E, a)); continue; }
                double vx = Math.cos(ang) * sp * 0.6 * (1 - f), vy = Math.sin(ang) * sp * 0.4 * (1 - f) + 68 * f;
                g.push(); g.translate(x, y); g.rotate(Math.atan2(vy, vx) + Math.PI / 2 * 3); double s2 = 0.55 + 0.15 * (i % 2);
                g.scale(s2, s2); g.drawSprite("rain/drop", -4, -6, 8, 11, alpha(0xFFFFFFFF, a)); g.pop();
            }
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
