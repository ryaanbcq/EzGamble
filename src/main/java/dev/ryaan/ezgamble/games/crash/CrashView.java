package dev.ryaan.ezgamble.games.crash;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.games.Chips;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.GlassButton;
import dev.ryaan.ezgamble.proto.GlassField;
import dev.ryaan.ezgamble.proto.Key;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Widget;
import dev.ryaan.ezgamble.ui.GambleScreen;

import java.util.Locale;

import static dev.ryaan.ezgamble.games.crash.CrashTable.*;

/** The curve inside GambleScreen (prototype CrashView). */
public final class CrashView implements GameView {
    private static final CrashTable CX = CrashTable.CX;

    /** One auto cash out tile (Off + 6 targets): a click picks it. */
    private static final class AutoTile extends Widget {
        final double opt;
        AutoTile(double x, double y, double w, double h, double opt) { super(x, y, w, h); this.opt = opt; }
        @Override public void click(double mx, double my) { if (CX.auto != opt) { P.play("click"); CX.auto = opt; } }
    }

    private final GambleScreen screen;
    private String uiKey = "";
    private GlassButton payBtn, startBtn, cashBtn;
    private Widgets.RefundPill refund;

    public CrashView(GambleScreen screen) { this.screen = screen; }

    private String uiState() { return CX.stage + "|" + CX.backer + "|" + CX.backers().size(); }

    private static void felt(G g, double x, double y, double w, double h) {
        P.smooth(g, x, y, w, h, 10, P.v(0xFF2A1B44, 0xFF1A1030), 0x1FFFFFFF);   // one surface, a hairline rim: content, not glass
    }

    @Override
    public void init() {
        GambleScreen s = screen;
        GambleScreen.Geom G = s.geom();
        double lx = G.lx(), rx = G.rx(), hy = G.hy(), fy = G.fy(), fh = G.fh(), by = G.by(), cx = G.cx();
        uiKey = uiState(); payBtn = null; startBtn = null; cashBtn = null; refund = null;
        s.add(new GlassButton(lx, hy, 18, 16, "‹", "GHOST", s::back));
        if (CX.stage.equals("bet")) {
            var list = CX.backers();
            if (CX.backer == null) CX.backer = Host.lastBacker;
            if (list.stream().noneMatch(r -> r.name().equals(CX.backer))) { CX.backer = list.isEmpty() ? null : list.get(0).name(); CX.clearBet(); }
            // auto cash out: the odds tiles ARE the choices (Off + 6 targets), one click picks one
            int n0 = CRASH_TARGETS.length + 1, step0 = 46;
            double x00 = cx - ((n0 * step0 - 4) >> 1);
            for (int i = 0; i < n0; i++) s.add(new AutoTile(x00 + i * step0, fy + 40, step0 - 4, 34, i == 0 ? 0 : CRASH_TARGETS[i - 1]));
            if (!list.isEmpty()) {
                int n = Chips.CHIPS.length, step = 44;
                double x0 = cx - ((n * step - (step - 30)) >> 1);
                for (int i = 0; i < n; i++) s.add(new Widgets.ChipButton(x0 + i * step, fy + fh - 36, Chips.CHIPS[n - 1 - i], CX));
                if (CX.field == null) CX.field = new GlassField(0, 0, 110, 22, "", "Amount", 10);
                CX.field.x = lx; CX.field.y = by + 1; CX.field.focused = false;
                s.add(CX.field);
                s.add(new GlassButton(lx + 116, by, 56, 24, "Clear", "GHOST", CX::clearBet));
            }
            startBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Launch", "PRIMARY", CX::start));
            if (!list.isEmpty()) {
                if (CX.picker == null) CX.picker = new Widgets.BackerPicker(lx + 8, fy + 8, 168, CX);
                CX.picker.place(lx + 8, fy + 8, 168); s.add(CX.picker);
            }
            return;
        }
        if (CX.stage.equals("play")) { cashBtn = s.add(new GlassButton(rx - 150, by, 150, 24, "Cash out", "PRIMARY", CX::cashout)); return; }
        boolean won = CX.payState() != null;
        startBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Play again", won ? "GHOST" : "PRIMARY", CX::again));
        s.add(new GlassButton(rx - 110 - 6 - 80, by, 80, 24, "Change bet", "GHOST", () -> { CX.settle(); CX.stage = "bet"; }));
        if (won) {
            payBtn = s.add(new GlassButton(lx, by, 150, 24, "", "PRIMARY", CX::pay) {
                @Override public void click(double mx, double my) { if (active) action.run(); }   // no click sound: the payment plays its own
            });
            syncPay();
        }
        if (CX.refundPill == null || CX.refundPill.round != CX.round) { CX.refundPill = new Widgets.RefundPill(CX, CX::settle); CX.refundPill.round = CX.round; }
        refund = s.add(CX.refundPill);
    }

    private void syncPay() {
        var st = CX.payState(); GlassButton b = payBtn;
        if (st == null) { b.visible = false; return; }
        b.label = st.label(); b.active = st.active(); b.w = Math.max(120, F.width(st.label()) + 20);
    }

    @Override
    public void tick() {
        CX.tick();
        if (!uiState().equals(uiKey)) { screen.rebuild(); return; }
        if (startBtn != null) {
            String warn = CX.warn != null && P.now() < CX.warn.until() ? CX.warn.text() : !GambleData.mode ? "Gambling mode is off" : null;
            GlassButton b = startBtn; boolean again = CX.stage.equals("done");
            b.label = warn != null ? warn : again ? "Play again" : CX.bet > 0 ? "Launch " + Money.format(CX.bet) : "Launch";
            b.kind = warn != null ? "DANGER" : again && CX.payState() != null ? "GHOST" : "PRIMARY";
            b.active = warn != null || (again ? CX.balance() > 0 : GambleData.mode && !CX.backers().isEmpty());
        }
        if (cashBtn != null) {
            var r = CX.round; boolean on = P.now() >= r.launch;
            cashBtn.label = on ? "Cash out " + Money.format(CX.value(CX.mult())) : "Launching...";
            cashBtn.active = on; cashBtn.kind = on ? "PRIMARY" : "GHOST";
        }
        if (payBtn != null) syncPay();
    }

    @Override
    public boolean key(Key e) {
        if (CX.stage.equals("bet")) {
            if (CX.picker != null && CX.picker.key(e)) return true;
            if (CX.field != null && CX.field.focused) {
                if (e.is("Enter")) CX.start();
                else if (e.is("Escape")) CX.field.focused = false;
                else if (CX.field.type(e)) { CX.bet = Math.max(0, Money.parse(CX.field.value)); CX.betChips.clear(); }
                return true;
            }
            if (e.is("Enter")) { CX.start(); return true; }
            return false;
        }
        if (CX.stage.equals("play") && e.key().equals(" ")) { CX.cashout(); return true; }   // the one game where a key must be fast: Space cashes out (the host plays it live)
        if (CX.stage.equals("done") && e.is("Enter")) { CX.again(); return true; }
        return false;
    }

    @Override
    public void clicked(Widget hit) {
        if (CX.field != null && hit != CX.field) CX.field.focused = false;   // a click elsewhere leaves the amount field
        if (CX.picker != null && hit != CX.picker) CX.picker.close();        // and closes the player search
        if (refund != null && hit != refund) refund.fold();                  // and folds Refund all back
    }

    @Override
    public void leave() { CX.settle(); }

    @Override
    public void render(G g, double mx, double my) {
        GambleScreen.Geom G = screen.geom();
        double lx = G.lx(), rx = G.rx(), hy = G.hy();
        g.drawText("Crash", lx + 24, hy + 4, P.WHITE, true);
        if (CX.stage.equals("bet")) { if (CX.backer != null) P.pillsRight(g, rx, hy, new Object[][]{{"Chips", Money.format(CX.balance())}}); }
        else P.pillsRight(g, rx, hy, new Object[][]{{"Auto", CX.round.auto != 0 ? crashText(CX.round.auto) : "Off"}, {"Bet", Money.format(CX.round.bet)}});
        felt(g, lx, G.ft(), rx - lx, G.fth());
        if (CX.stage.equals("bet")) renderBet(g, mx, my); else renderFlight(g);
    }

    private static String num(double v, int d) {
        String s = String.format(Locale.ROOT, "%." + d + "f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    private void renderBet(G g, double mx, double my) {
        GambleScreen.Geom G = screen.geom();
        double lx = G.lx(), fy = G.fy(), cx = G.cx();
        var list = CX.backers();
        if (list.isEmpty()) {
            String me = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();
            g.drawText("No chips on the table yet.", lx + 8, fy + 44, P.TEXT, false);
            g.drawText("Players who /pay " + me + " during a session can play Crash with you.", lx + 8, fy + 58, P.MUTE, false);
            if (!GambleData.mode) g.drawText("Turn gambling mode on to start a session.", lx + 8, fy + 74, P.RED, false);
            return;
        }
        // auto cash out: each target with its real chance to be reached (the tiles are the choices)
        String lab = "Auto cash out"; g.drawText(lab, cx - (F.width(lab) >> 1), fy + 29, P.MUTE, false);
        double y = fy + 40;
        for (Widget w0 : screen.children) {
            if (!(w0 instanceof AutoTile w)) continue;
            double x = w.opt; boolean sel = CX.auto == x, hov = w.over(mx, my);
            String a = x != 0 ? Widgets.multText(x) : "Off";
            double p = x != 0 ? ((1 - CRASH_EDGE) / x) * 100 : 0;
            String ps = x != 0 ? (p >= 10 ? String.valueOf(Math.round(p)) : num(p, 1)) + "%" : "manual";
            int stroke = sel ? 0xCCFBC2EB : hov ? 0x40FFFFFF : 0x18FFFFFF;
            if (sel) P.smooth(g, w.x, w.y, w.w, w.h, 6, P.h(0x33A18CD1, 0x26FBC2EB), stroke);
            else P.smooth(g, w.x, w.y, w.w, w.h, 6, hov ? 0x14FFFFFF : 0x0AFFFFFF, stroke);
            g.drawText(a, w.x + ((int) (w.w - F.width(a)) >> 1), w.y + 6, sel ? P.WHITE : x >= 10 ? P.PINK : P.TEXT, sel);
            g.drawText(ps, w.x + ((int) (w.w - F.width(ps)) >> 1), w.y + 19, P.MUTE, false);
        }
        String p1 = "Returns 96% wherever you cash out", p2 = CX.auto != 0 ? "Cashes out by itself at " + Widgets.multText(CX.auto) + ", no lag" : "No auto: whispers take a second to arrive";
        g.drawText(p1, cx - (F.width(p1) >> 1), y + 44, P.DIM, false);
        g.drawText(p2, cx - (F.width(p2) >> 1), y + 56, CX.auto != 0 ? P.DIM : P.alpha(P.RED, 0.8), false);
    }

    private void renderFlight(G g) {
        GambleScreen.Geom G = screen.geom();
        double lx = G.lx(), rx = G.rx(), fy = G.fy(), fh = G.fh(), cx = G.cx();
        CrashTable.Rnd r = CX.round; long t = P.now();
        // who, his chips (the refund pill once it is over for him)
        String name = F.trimToWidth(r.backer, 70), bal = Money.format(CX.balance()), autoT = r.auto != 0 ? "auto " + crashText(r.auto) : "no auto";
        int tw = P.seatTagW(name, autoT), cw = P.pillW("Chips", bal);
        double sx = cx - ((tw + 4 + cw) >> 1);
        P.seatTag(g, sx + (tw >> 1), fy + 8, P.skinOf(r.backer), name, autoT, P.WHITE);
        long since = r.cashAt != 0 ? r.cashAt : r.crashedAt != 0 ? r.crashedAt : t;
        int chipsCol = CX.stage.equals("done") && r.applied && t - since < 1800 ? (r.cashX != 0 ? P.GREEN : P.RED) : P.WHITE;
        if (refund != null) refund.place(sx + tw + 4, fy + 8, chipsCol); else P.pill(g, sx + tw + 4, fy + 8, "Chips", bal, chipsCol);
        // the graph: time right, multiplier up; both scales grow with the flight
        double gx0 = lx + 30, gx1 = rx - 12, gy0 = fy + 30, gy1 = fy + fh - 14;
        double ms = r.crashed ? r.crashMs : Math.max(0, Math.min(t - r.launch, r.crashMs)), x = CX.mult(t);
        double tMax = Math.max(8000, ms * 1.15), yMax = Math.max(2, x * 1.6);   // headroom: the big number sits top left
        // grid: a few multipliers, faint
        double pw = Math.pow(10, Math.floor(Math.log10(yMax)));
        double stepY = yMax <= 3 ? 0.5 : yMax <= 6 ? 1 : yMax <= 15 ? 2 : pw / (yMax / pw > 4 ? 1 : 2);
        for (double v = 1; v <= yMax; v += stepY) {
            long yy = Math.round(py(v, gy0, gy1, yMax));
            g.fill(gx0, yy, gx1, yy + 1, 0x14FFFFFF);
            String l = "x" + num(v, 1);
            g.drawText(l, gx0 - 4 - F.width(l), yy - 4, P.DIM, false);
        }
        if (r.auto != 0 && r.auto <= yMax) {
            long yy = Math.round(py(r.auto, gy0, gy1, yMax));
            for (double xx = gx0; xx < gx1; xx += 6) g.fill(xx, yy, Math.min(gx1, xx + 3), yy + 1, P.alpha(P.PINK, 0.55));
            g.drawText("auto", gx1 - F.width("auto"), yy - 10, P.alpha(P.PINK, 0.8), false);
        }
        // the curve: Outmind gradient, filled under; red once crashed. Drawn with a few fills (2 px columns under the
        // curve, 60 rotated segments for the line), never pixel by pixel.
        if (t >= r.launch) {
            boolean dead = r.crashed;
            double tipX = gx0 + (ms / tMax) * (gx1 - gx0), tipY = py(Math.min(r.crash, Math.exp(CRASH_RATE * ms / 1000)), gy0, gy1, yMax);
            int fillTop = dead ? 0xFF5C5C : 0xFBC2EB, fillRim = 0xA18CD1;
            double span = Math.max(1, gy1 - tipY);
            for (double xx = gx0; xx < tipX; xx += 2) {
                double m = ((xx + 1 - gx0) / (gx1 - gx0)) * tMax;
                double yc = py(Math.min(r.crash, Math.exp(CRASH_RATE * Math.min(m, ms) / 1000)), gy0, gy1, yMax);
                if (yc >= gy1) continue;
                double f = Math.max(0, Math.min(1, (yc - tipY) / span));
                int top = P.alpha(P.lerp(0xFF000000 | fillTop, 0xFF000000 | fillRim, f), (dead ? 0.28 : 0.30) * (1 - f));
                g.fillGradient(xx, yc, Math.min(xx + 2, tipX), gy1, top, P.alpha(0xFF000000 | fillRim, 0));
            }
            int n = 60;
            double[] qx = new double[n + 1], qy = new double[n + 1];
            for (int i = 0; i <= n; i++) {
                double m = (ms * i) / n;
                qx[i] = gx0 + (m / tMax) * (gx1 - gx0);
                qy[i] = py(Math.min(r.crash, Math.exp(CRASH_RATE * m / 1000)), gy0, gy1, yMax);
            }
            for (int i = 0; i < n; i++) {
                double dx = qx[i + 1] - qx[i], dy = qy[i + 1] - qy[i], len = Math.hypot(dx, dy);
                if (len < 0.01) continue;
                double k = Math.max(0, Math.min(1, (qx[i] - gx0) / (gx1 - gx0)));
                int col = dead ? P.lerp(P.alpha(P.RED, 0.9), P.RED, k) : P.lerp(P.LAV, P.PINK, k);
                g.push(); g.translate(qx[i], qy[i]); g.rotate(Math.atan2(dy, dx));
                g.fill(-1, -1, len + 1, 1, col);   // 2 px wide, overlapping ends = round-ish joins
                g.pop();
            }
            // the rocket at the tip, then the blast
            if (!r.crashed) { g.push(); g.translate(tipX, tipY); g.drawItem("firework_rocket", -8, -8, false); g.pop(); }
            else if (t - r.crashedAt < 400) { double e = (t - r.crashedAt) / 400.0; P.ring(g, tipX - 12 * e, tipY - 12 * e, 24 * e, 24 * e, 12 * e, P.alpha(P.RED, 1 - e), 2); }
            // where he cashed out: a green dot on the curve
            if (r.cashX != 0) {
                double cxp = gx0 + (crashMsOf(r.cashX) / tMax) * (gx1 - gx0), cyp = py(r.cashX, gy0, gy1, yMax);
                P.smooth(g, cxp - 3, cyp - 3, 6, 6, 3, P.GREEN, 0xFFFFFFFF);
            }
        }
        // the big number
        String big = t < r.launch ? String.format(Locale.ROOT, "%.1f", ((r.launch - t) / 1000.0) + 0.05) + "s" : crashText(x);
        long ca = t - r.crashedAt;
        int shake = r.crashed && ca < 400 ? (int) Math.round(Math.sin(ca / 18.0) * 3 * (1 - ca / 400.0)) : 0;
        g.push(); g.translate(gx0 + 8 + shake, fy + 34); g.scale(3, 3);   // top left of the graph: the curve climbs from the bottom left to the right, never under it
        if (r.crashed) g.drawText(big, 0, 0, P.RED, true); else if (t < r.launch) g.drawText(big, 0, 0, P.TEXT, true); else P.gradientText(g, big, 0, 0, true);
        g.pop();
        String cashed = name + " cashed " + crashText(r.cashX) + ", " + Money.format(r.cashX != 0 ? CX.value(r.cashX) : 0);
        String sub = t < r.launch ? "Launching" : r.crashed ? (r.cashX != 0 ? cashed : "Crashed, " + name + " loses " + Money.format(r.bet)) : r.cashX != 0 ? cashed : "";
        if (!sub.isEmpty()) g.drawText(sub, gx0 + 8, fy + 62, r.cashX != 0 ? P.GREEN : r.crashed ? P.RED : P.MUTE, false);
    }

    private static double py(double v, double gy0, double gy1, double yMax) { return gy1 - ((v - 1) / (yMax - 1)) * (gy1 - gy0); }
}
