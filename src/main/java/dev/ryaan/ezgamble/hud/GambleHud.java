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
    // under a moving wave, the lens on top; every ticket's amount flies in from the payment card, the bubble breathes and bursts at the end.

    private static final int TW = 300, TH = 293, BW = 76, BH = 74;   // texture size (Ryan's logo), box in GUI px
    private static final double TK = TW / (double) BW;                 // texels per GUI px
    private static final long POP_MS = 1000, INFLATE_MS = 240, FLY_MS = 720;
    /** The level drawn (a spring toward the pot, with a little overshoot), the wave height, the jelly squash. */
    private static double lvl, lvlV, lvlTarget, amp = 1, jelly, jellyV;
    /**
     * The bubble hangs in a small magnetic field: turning the head or moving pushes it the other way (inertia), a
     * spring brings it back with a few wobbles, the edge of the field bounces it with a squash. The liquid tilts.
     */
    private static double ox, oy, ovx, ovy, tilt, tiltV;
    private static float lastYaw, lastPitch;
    private static double lastSide, lastFwd, lastUp;
    private static boolean wasOnGround = true, physicsInit;
    private static final double FIELD = 11;
    private static long callAt = -1;

    private static void physics(double dt) {
        var p = MinecraftClient.getInstance().player;
        if (p == null || dt <= 0) return;
        float yaw = p.getYaw(), pitch = p.getPitch();
        var v = p.getVelocity();
        double ry = Math.toRadians(yaw);
        double side = v.x * Math.cos(ry) + v.z * Math.sin(ry), fwd = -v.x * Math.sin(ry) + v.z * Math.cos(ry), up = v.y;
        if (!physicsInit) { lastYaw = yaw; lastPitch = pitch; lastSide = side; lastFwd = fwd; lastUp = up; physicsInit = true; }
        double dyaw = net.minecraft.util.math.MathHelper.wrapDegrees(yaw - lastYaw), dpitch = pitch - lastPitch;
        lastYaw = yaw; lastPitch = pitch;
        // inertia: the view turns, the bubble stays behind; the body speeds up, the bubble lags the other way
        ovx += Math.max(-40, Math.min(40, dyaw)) * 2.0;
        ovy += Math.max(-40, Math.min(40, dpitch)) * 1.3;
        ovx += (side - lastSide) * 100; ovy += (up - lastUp) * 120 - (fwd - lastFwd) * 30;
        lastSide = side; lastFwd = fwd; lastUp = up;
        boolean ground = p.isOnGround();
        if (ground && !wasOnGround) { jellyV += 3.2; amp = Math.min(4.5, amp + 1.8); }   // landing: squash and slosh
        wasOnGround = ground;
        // spring back to the anchor, a little bouncy
        ovx += (-ox * 120 - ovx * 10) * dt; ovy += (-oy * 120 - ovy * 10) * dt;   // heavy: stiff and well damped
        ox += ovx * dt; oy += ovy * dt;
        double r = Math.hypot(ox, oy);
        if (r > FIELD) {   // the edge of the field: back inside, bounce, squash
            double nx = ox / r, ny = oy / r, vn = ovx * nx + ovy * ny;
            ox = nx * FIELD; oy = ny * FIELD;
            if (vn > 0) { ovx -= 1.6 * vn * nx; ovy -= 1.6 * vn * ny; jellyV += Math.min(3, vn / 60); amp = Math.min(4.5, amp + vn / 120); }
        }
        // the liquid leans against the motion, then settles
        tiltV += (-ovx * 0.0016 - tilt * 60 - tiltV * 5) * dt; tilt += tiltV * dt;
        tilt = Math.max(-0.25, Math.min(0.25, tilt));
    }

    private static long lastFrame, popDelay;
    /** A ticket's "+$X" flying from the payment card into the cloud: from where, what, the fill level it brings. */
    private record Flyer(long at, double x, double y, double scale, String text, long amount, double k) {}
    private static final java.util.ArrayList<Flyer> flyers = new java.util.ArrayList<>();
    /** The cloud's bounce when an amount lands in it. */
    private static double bnc, bncV;
    private static dev.ryaan.ezgamble.games.rain.RainTable.Round aligned;

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
                double wy = waveY((c + 1) / TK, level, t, a) + tilt * ((c + 1) / TK - BW / 2.0);
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
     * passes the real fill, nothing flying). The wave runs on the clock and on the HUD's slosh.
     */
    public static void drawCloud(G g, double k, long pot, long goal, double fade) {
        drawCloudBody(g, k, fade);
        // in the middle: how long the rain has been open, the pot under it; the goal in a glass pill below the cloud
        long secs = Math.max(0, (now() - dev.ryaan.ezgamble.games.rain.RainTable.RN.openedAt) / 1000);
        String timer = secs >= 3600 ? String.format("%d:%02d:%02d", secs / 3600, secs / 60 % 60, secs % 60) : String.format("%d:%02d", secs / 60, secs % 60);
        g.push(); g.translate(38.5, 31); g.scale(1.75, 1.75);
        g.drawText(timer, -F.width(timer) / 2.0, -4, alpha(0xFF3A2766, fade), false);
        g.pop();
        String amt = Money.format(pot);
        g.drawText(amt, 38.5 - F.width(amt) / 2.0, 42, alpha(0xFF4A3970, fade), false);
        String lab = "Goal ", gl = Money.format(goal);
        double tw = F.width(lab) + F.width(gl), px = BW / 2.0 - tw / 2 - 6;
        smooth(g, px, BH + 2, tw + 12, 13, 6.5, alpha(0xFFFFFFFF, fade * 0.96), alpha(0xFFD9CCF2, fade));
        g.drawText(lab, px + 6, BH + 5, alpha(0xFF7A68A8, fade), false);
        g.drawText(gl, px + 6 + F.width(lab), BH + 5, alpha(0xFF2E1F4F, fade), false);
    }

    /**
     * Top centre of the screen while a rain collects. A ticket's "+$X" leaves the payment card once counted up and
     * arcs into the bubble: there the level springs up, the surface sloshes, the bubble bounces. The goal or Spin now
     * hands over to RainFinale once the last amount has landed; Cancel deflates it into drops and coins.
     */
    static void drawRain(G g) {
        var rn = dev.ryaan.ezgamble.games.rain.RainTable.RN;
        long t = now();
        rn.tick();   // every frame as well: the reel's clicks follow its cells closely
        boolean open = rn.stage.equals("open");
        // tickets shown on the payment card: once its amount has counted up, it flies into the cloud
        while (!rn.feeds.isEmpty()) {
            var fd = rn.feeds.peek();
            var pc = Payments.cur;
            boolean card = HUD.get("pay").visible;
            boolean onCard = card && pc != null && pc.name().equals(fd.name()) && pc.amount() == fd.amount();
            if (onCard && t - pc.at() < 650) break;   // still counting up on the card
            if (card && !onCard && t - fd.at() < 8000) break;   // not on the card yet (payments before it)
            rn.feeds.poll();
            double ks = effScale("pay"), fx, fy;
            String text = "+" + Money.format(fd.amount());
            if (onCard) { Rect r = wrect("pay"); fx = r.x() + ks * (30 + F.width(text) / 2.0); fy = r.y() + ks * 23; }
            else { fx = sw() - 30; fy = 20; ks = 1; }
            flyers.add(new Flyer(t, fx, fy, ks, text, fd.amount(), fd.k()));
        }
        // the goal reached: the last amounts land first, then the bubble bursts
        if (!open && rn.popAt > 0 && (!flyers.isEmpty() || !rn.feeds.isEmpty()) && t - rn.popAt < 9000) popDelay = Math.max(popDelay, t - rn.popAt + 220);
        long popAt = rn.popAt + popDelay;
        // the goal reached (or Spin now): the finale takes the whole screen until the winner has been shown
        boolean finale = !open && rn.round != null && rn.popAt > 0 && t < RainFinale.end(rn.round);
        boolean popping = finale || !open && rn.popAt > 0 && t - rn.popAt < POP_MS + popDelay;
        if (!open && !popping) { lvl = lvlV = lvlTarget = 0; flyers.clear(); lastFrame = 0; amp = 1; popDelay = 0; ox = oy = ovx = ovy = tilt = tiltV = bnc = bncV = 0; physicsInit = false; return; }
        if (open) popDelay = 0;
        if (finale && t >= popAt) {
            // the reel starts when the finale shows it, however long the last amounts took to land
            if (aligned != rn.round) { aligned = rn.round; if (t < rn.round.at) rn.round.at = popAt + dev.ryaan.ezgamble.games.rain.RainTable.LEAD_MS; }
            tilt = 0; RainFinale.draw(g, rn, popAt, t); lastFrame = t; return;
        }
        double dt = lastFrame == 0 ? 0 : Math.min(0.05, (t - lastFrame) / 1000.0);
        lastFrame = t;
        double real = open ? (rn.goal > 0 ? Math.min(1, (double) rn.pot() / rn.goal) : 0) : rn.popK;
        if (flyers.isEmpty() && rn.feeds.isEmpty()) lvlTarget = real;   // nothing on its way: the level is the pot (a refund sinks it)
        // amounts landing: the level they bring, a bounce, a slosh
        for (var it = flyers.iterator(); it.hasNext(); ) {
            Flyer fl = it.next();
            if (t - fl.at() < FLY_MS) continue;
            it.remove();
            lvlTarget = Math.max(lvlTarget, fl.k());
            amp = Math.min(4.5, amp + 2.4); jellyV += 2.2; bncV += 2.6;
            play("mc_orb", 1.3 + ThreadLocalRandom.current().nextDouble() * 0.3, 0.5);
        }
        // springs (real time: same feel at any frame rate)
        lvlV += (lvlTarget - lvl) * 110 * dt; lvlV *= Math.exp(-11 * dt); lvl += lvlV * dt;
        jellyV += -jelly * 260 * dt; jellyV *= Math.exp(-7 * dt); jelly += jellyV * dt;
        bncV += -bnc * 240 * dt; bncV *= Math.exp(-8 * dt); bnc += bncV * dt;
        amp += (1 - amp) * (1 - Math.exp(-1.6 * dt));
        if (open) physics(dt);
        double k = Math.max(0, Math.min(1.02, lvl)), ts = t / 1000.0;
        double breath = Math.sin(ts * 1.15) * (1 + k);
        double sx = (1 + 0.012 * breath + 0.06 * jelly), sy = (1 - 0.010 * breath - 0.06 * jelly);
        double sc = 0.9 + 0.1 * easeOut(k), fade = 1, f = 0;
        // every 30 s the bubble swells quickly on itself and settles: a call to the players around
        long since = open ? t - rn.openedAt : 0, ph = since % 30_000;
        double call = 0;
        if (since >= 30_000 && ph < 900) {
            double p = ph / 900.0;
            call = p < 0.28 ? 0.24 * easeOut(p / 0.28) : 0.24 * Math.cos((p - 0.28) * 14) * Math.exp(-(p - 0.28) * 6);
            if (ph < 60 && callAt != since - ph) { callAt = since - ph; amp = Math.min(4.5, amp + 2); jellyV += 1.5; }
            double gs = 110 * (1 + 0.6 * p);
            g.drawSprite("rain/glow", sw() / 2 + ox - gs / 2, 43 + oy - gs / 2, gs, gs, alpha(0xFFFFD2F0, 0.45 * Math.sin(p * Math.PI)));
        }
        sc *= (1 + call) * (1 + 0.11 * bnc);
        if (popping && t >= popAt) {   // inflate with a wobble, then burst
            long e = t - popAt;
            double in = Math.min(1, e / (double) INFLATE_MS);
            sc *= 1 + 0.16 * easeOut(in) + 0.025 * Math.sin(e / 18.0) * (1 - in);
            if (e > INFLATE_MS) { f = (e - INFLATE_MS) / (double) (POP_MS - INFLATE_MS); sc *= 1 + 0.35 * easeOut(Math.min(1, f * 3)); fade = Math.max(0, 1 - f * 4); }
        }
        g.push(); g.translate(sw() / 2 + ox, 6 + 37 + oy); g.rotate(Math.max(-0.12, Math.min(0.12, ovx * 0.0012))); g.scale(sc * sx, sc * sy); g.translate(-BW / 2.0, -37);
        // the pot in the cloud grows when an amount lands, not when it leaves the card
        long pending = 0;
        for (Flyer fl : flyers) pending += fl.amount();
        for (var fd : rn.feeds) pending += fd.amount();
        if (fade > 0) drawCloud(g, k, Math.max(0, (open || t < popAt ? rn.pot() : rn.popPot) - pending), rn.goal, fade);
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
        // the amounts on their way: an arc from the card to the cloud, growing then shrinking into it, a faint trail
        double cx = sw() / 2 + ox, cy = 6 + 37 + oy;
        for (Flyer fl : flyers) {
            for (int gh = 3; gh >= 0; gh--) {
                double p = Math.max(0, (t - fl.at() - gh * 35) / (double) FLY_MS);
                if (p <= 0) continue;
                double e = p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2;   // ease in-out
                double mx = (fl.x() + cx) / 2, my = Math.max(fl.y(), cy) + 34;   // a swoop under the line: the card and the cloud both sit at the top
                double x = (1 - e) * (1 - e) * fl.x() + 2 * (1 - e) * e * mx + e * e * cx;
                double y = (1 - e) * (1 - e) * fl.y() + 2 * (1 - e) * e * my + e * e * cy;
                double s2 = fl.scale() * (1 + 0.7 * Math.sin(Math.min(1, e * 1.4) * Math.PI)) * (1 - 0.45 * e * e);
                double a = gh == 0 ? 1 : 0.22 / gh;
                g.push(); g.translate(x, y); g.scale(s2, s2);
                g.drawText(fl.text(), -F.width(fl.text()) / 2.0, -4, alpha(GREEN, a), true);
                g.pop();
            }
        }
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
