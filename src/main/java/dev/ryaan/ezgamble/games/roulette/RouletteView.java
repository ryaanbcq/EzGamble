package dev.ryaan.ezgamble.games.roulette;

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
import dev.ryaan.ezgamble.proto.Vec;
import dev.ryaan.ezgamble.proto.Widget;
import dev.ryaan.ezgamble.ui.GambleScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.ryaan.ezgamble.games.roulette.RouletteTable.*;

/** The board and the wheel inside GambleScreen (prototype RouletteView). */
public final class RouletteView implements GameView {
    private final GambleScreen screen;
    private String uiKey = "";
    private GlassButton payBtn, spinBtn;
    private Widgets.RefundPill refund;

    public RouletteView(GambleScreen s) { this.screen = s; }

    private GambleScreen.Geom geom() { return screen.geom(); }

    private String uiState() { return RL.stage + "|" + RL.backer + "|" + RL.backers().size(); }

    record Cell(String key, double x, double y, double w, double h) {}

    /** Every cell of the board: the zero on the left, 3 rows x 12 numbers (3 on top, like a real layout), then the outside bets. */
    private List<Cell> cells() {
        var Ge = geom();
        double x0 = Ge.lx() + 8, w0 = 22, cw = Math.floor((Ge.rx() - 8 - x0 - w0) / 12), ch = 15, y0 = Ge.fy() + 30;
        List<Cell> out = new ArrayList<>();
        out.add(new Cell("n0", x0, y0, w0 - 2, ch * 3 - 2));
        for (int c = 0; c < 12; c++) for (int row = 0; row < 3; row++) out.add(new Cell("n" + (c * 3 + 3 - row), x0 + w0 + c * cw, y0 + row * ch, cw - 2, ch - 2));
        double bx = x0 + w0, bw = cw * 12, y1 = y0 + ch * 3 + 1;
        String[] d = {"d1", "d2", "d3"};
        for (int i = 0; i < 3; i++) out.add(new Cell(d[i], bx + i * (bw / 3), y1, bw / 3 - 2, 13));
        String[] o = {"low", "even", "red", "black", "odd", "high"};
        for (int i = 0; i < 6; i++) out.add(new Cell(o[i], bx + i * (bw / 6), y1 + 15, bw / 6 - 2, 13));
        return out;
    }

    @Override
    public void init() {
        GambleScreen s = screen;
        var Ge = geom();
        double lx = Ge.lx(), rx = Ge.rx(), hy = Ge.hy(), fy = Ge.fy(), fh = Ge.fh(), by = Ge.by(), cx = Ge.cx();
        uiKey = uiState(); payBtn = null; spinBtn = null; refund = null;
        s.add(new GlassButton(lx, hy, 18, 16, "‹", "GHOST", s::back));   // back() settles the view
        if (RL.stage.equals("bet")) {
            var list = RL.backers();
            if (RL.backer == null) RL.backer = Host.lastBacker;
            if (list.stream().noneMatch(r -> r.name().equals(RL.backer))) { RL.backer = list.isEmpty() ? null : list.get(0).name(); RL.clearBet(); RL.clearTable(); }
            if (!list.isEmpty()) {
                for (Cell c : cells()) s.add(Widgets.area(c.x, c.y, c.w, c.h, () -> RL.place(c.key)));
                int n = Chips.CHIPS.length, step = 44;
                double x0 = cx - ((n * step - (step - 30)) >> 1);
                for (int i = 0; i < n; i++) s.add(new Widgets.ChipButton(x0 + i * step, fy + fh - 36, Chips.CHIPS[n - 1 - i], RL));
                if (RL.field == null) RL.field = new GlassField(0, 0, 90, 22, "", "Amount", 10);
                RL.field.x = lx; RL.field.y = by + 1; RL.field.w = 90; RL.field.focused = false;
                s.add(RL.field);
                s.add(new GlassButton(lx + 96, by, 50, 24, "Clear", "GHOST", RL::clearBet));
                s.add(new GlassButton(lx + 152, by, 50, 24, "Undo", "GHOST", RL::undo));
            }
            spinBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Spin", "PRIMARY", RL::spin));
            if (!list.isEmpty()) {
                if (RL.picker == null) RL.picker = new Widgets.BackerPicker(lx + 8, fy + 6, 168, RL);
                RL.picker.place(lx + 8, fy + 6, 168);
                s.add(RL.picker);
            }
            return;
        }
        if (RL.stage.equals("done")) {
            boolean won = RL.payState() != null;
            spinBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Spin again", won ? "GHOST" : "PRIMARY", RL::again));
            s.add(new GlassButton(rx - 110 - 6 - 80, by, 80, 24, "New bets", "GHOST", () -> { RL.settle(); RL.stage = "bet"; }));
            if (won) {
                payBtn = s.add(new GlassButton(lx, by, 150, 24, "", "PRIMARY", RL::pay) {
                    @Override public void click(double mx, double my) { if (active) action.run(); }   // no click sound: the pay has its own
                });
                syncPay();
            }
            if (RL.refundPill == null || RL.refundPill.round != RL.round) { RL.refundPill = new Widgets.RefundPill(RL, RL::settle); RL.refundPill.round = RL.round; }
            refund = s.add(RL.refundPill);
        }
    }

    private void syncPay() {
        var st = RL.payState(); var b = payBtn;
        if (st == null) { b.visible = false; return; }
        b.label = st.label(); b.active = st.active(); b.w = Math.max(120, F.width(st.label()) + 20);
    }

    @Override
    public void tick() {
        RL.tick();
        if (!uiState().equals(uiKey)) { screen.rebuild(); return; }
        if (spinBtn != null) {
            String warn = RL.warn != null && P.now() < RL.warn.until() ? RL.warn.text() : !GambleData.mode ? "Gambling mode is off" : null;
            var b = spinBtn;
            boolean again = RL.stage.equals("done");
            long tot = RL.placed();
            b.label = warn != null ? warn : again ? "Spin again" : tot > 0 ? "Spin " + Money.format(tot) : "Spin";
            b.kind = warn != null ? "DANGER" : again && RL.payState() != null ? "GHOST" : "PRIMARY";
            b.active = warn != null || (again ? RL.balance() > 0 : GambleData.mode && !RL.backers().isEmpty());
            if (warn != null) { b.w = Math.max(110, F.width(warn) + 20); b.x = geom().rx() - b.w; }
            else { b.w = 110; b.x = geom().rx() - 110; }
        }
        if (payBtn != null) syncPay();
    }

    @Override
    public boolean key(Key e) {
        if (RL.stage.equals("bet")) {
            if (RL.picker != null && RL.picker.key(e)) return true;
            if (RL.field != null && RL.field.focused) {
                if (e.is("Enter") || e.is("Escape")) RL.field.focused = false;
                else if (RL.field.type(e)) { RL.bet = Math.max(0, Money.parse(RL.field.value)); RL.betChips.clear(); }
                return true;
            }
            if (e.is("Enter")) { RL.spin(); return true; }
            if (e.ctrl() && e.key().equalsIgnoreCase("z")) { RL.undo(); return true; }
            return false;
        }
        if (RL.stage.equals("done") && e.is("Enter")) { RL.again(); return true; }
        return RL.stage.equals("spin") && e.key().equals(" ");
    }

    /** What GambleScreen.mouseDown did after the click: a click elsewhere leaves the amount field, closes the player search, folds Refund all. */
    @Override
    public void clicked(Widget hit) {
        if (RL.field != null && hit != RL.field) RL.field.focused = false;
        if (RL.picker != null && hit != RL.picker) RL.picker.close();
        if (refund != null && hit != refund) refund.fold();
    }

    @Override public void leave() { RL.settle(); }

    @Override
    public void render(G g, double mx, double my) {
        var Ge = geom();
        double lx = Ge.lx(), rx = Ge.rx(), hy = Ge.hy();
        g.drawText("Roulette", lx + 24, hy + 4, P.WHITE, true);
        if (RL.stage.equals("bet")) {
            List<Object[]> p = new ArrayList<>();
            if (RL.backer != null) p.add(new Object[]{"Chips", Money.format(RL.balance() - RL.placed())});
            if (RL.bet > 0) p.add(new Object[]{"Chip", Money.format(RL.bet)});
            if (!p.isEmpty()) P.pillsRight(g, rx, hy, p.toArray(new Object[0][]));
        } else P.pillsRight(g, rx, hy, new Object[][]{{"Bets", Money.format(RL.round.total)}});
        felt(g, lx, Ge.ft(), rx - lx, Ge.fth());
        if (RL.stage.equals("bet")) renderBoard(g, mx, my); else renderSpin(g);
    }

    /** One surface, a hairline rim: content, not glass. */
    static void felt(G g, double x, double y, double w, double h) {
        P.smooth(g, x, y, w, h, 10, P.v(0xFF2A1B44, 0xFF1A1030), 0x1FFFFFFF);
    }

    /** One cell of the board: a tint of its colour, the label in that colour; money on it = a real chip (the biggest of the amount) on the cell, the amount in the tooltip. */
    private void drawCell(G g, Cell c, boolean hov, long onIt) {
        int n = c.key.charAt(0) == 'n' ? Integer.parseInt(c.key.substring(1)) : c.key.equals("red") ? 1 : c.key.equals("black") ? 2 : -1;
        boolean colored = n >= 0;
        int fill = colored ? tint(n, hov ? 2.2 : 1) : hov ? 0x26FFFFFF : 0x0DFFFFFF;
        P.smooth(g, c.x, c.y, c.w, c.h, 4, fill, hov ? 0xCCFBC2EB : 0x14FFFFFF);
        String l = BETS.get(c.key).label();
        int ink = colored ? ink(n) : P.TEXT;
        double ty = Math.round(c.y + (c.h - 8) / 2);
        if (onIt <= 0) { g.drawText(l, Math.round(c.x + (c.w - F.width(l)) / 2), ty, hov ? P.WHITE : ink, false); return; }
        var st = Chips.chipsFor(onIt).stacks();
        Chips.Chip chip = st.isEmpty() ? Chips.CHIPS[Chips.CHIPS.length - 1] : st.get(0).chip();
        boolean wide = c.w >= F.width(l) + 22;
        if (wide) {   // a wide cell (dozens, red, odd...): the label moves left, the chip sits on the right, nothing covered
            g.drawText(l, Math.round(c.x + (c.w - 12 - F.width(l)) / 2), ty, hov ? P.WHITE : ink, false);
            chipTop(g, c.x + c.w - 8, c.y + c.h / 2, Math.min(5.5, c.h / 2 - 1), chip);
            return;
        }
        g.drawText(l, Math.round(c.x + (c.w - F.width(l)) / 2), ty, P.alpha(ink, 0.35), false);   // a number cell: the chip on the number, the amount in the tooltip
        chipTop(g, c.x + c.w / 2, c.y + c.h / 2, Math.min(5.5, c.h / 2 - 1), chip);
    }

    private void renderBoard(G g, double mx, double my) {
        var Ge = geom();
        double lx = Ge.lx(), fy = Ge.fy(), cx = Ge.cx();
        var list = RL.backers();
        if (list.isEmpty()) {
            g.drawText("No chips on the table yet.", lx + 8, fy + 44, P.TEXT, false);
            g.drawText("Players who /pay " + G.mc().getSession().getUsername() + " during a session can play roulette with you.", lx + 8, fy + 58, P.MUTE, false);
            if (!GambleData.mode) g.drawText("Turn gambling mode on to start a session.", lx + 8, fy + 74, P.RED, false);
            return;
        }
        double tipX = 0, tipY = 0; String tip = null;
        for (Cell c : cells()) {
            boolean hov = mx >= c.x && mx < c.x + c.w && my >= c.y && my < c.y + c.h;
            long on = RL.bets.getOrDefault(c.key, 0L);
            drawCell(g, c, hov, on);
            if (hov) {
                Bet b = BETS.get(c.key);
                tipX = c.x + c.w / 2; tipY = c.y;
                tip = on > 0 ? b.label() + ": " + Money.format(on) + " on it, pays " + Money.format(on * b.ret())
                        : b.label() + (RL.bet > 0 ? ": " + Money.format(RL.bet) + " pays " + Money.format(RL.bet * b.ret()) : ": pays " + (b.ret() - 1) + " to 1");
            }
        }
        // the last numbers, top right (what viewers ask for)
        List<Integer> res = RL.lastResults;
        List<Integer> last = res.subList(Math.max(0, res.size() - 8), res.size());
        double x = Ge.rx() - 8;
        for (int i = last.size() - 1; i >= 0; i--) {
            int n = last.get(i); String l = String.valueOf(n);
            double w = Math.max(14, F.width(l) + 6);
            x -= w;
            P.smooth(g, x, fy + 8, w, 13, 6.5, tint(n, 1.6), 0x1AFFFFFF);
            g.drawText(l, x + ((int) (w - F.width(l)) >> 1), fy + 11, P.alpha(ink(n), i == last.size() - 1 ? 1 : 0.75), false);
            x -= 3;
        }
        if (tip != null) {
            double tw = F.width(tip) + 12, tx = Math.max(lx, Math.min(Ge.rx() - tw, tipX - (int) tw / 2)), ty = tipY - 17;
            P.glass(g, tx, ty, tw, 15, 7, 0.85, 0.8);
            g.drawText(tip, tx + 6, ty + 4, P.WHITE, false);
        }
        if (RL.placed() == 0) {
            String h = RL.bet > 0 ? "Click a cell to put " + Money.format(RL.bet) + " on it" : "Pick an amount, then click the cells";
            g.drawText(h, cx - (F.width(h) >> 1), fy + 107, P.DIM, false);
        }
    }

    /** The edge of the reel fading into the felt (Crates' reel): 40 px, from 0.95 alpha to nothing; mirrored on the right. */
    private static void edgeFade(G g, double ex, int dir, double ry, double rh) {
        double x = dir > 0 ? ex : ex - 40;
        Vec.draw(g, "rl/fade/" + dir + "/" + rh, x, ry, 40, rh, 0, g2 -> {
            float a = dir > 0 ? 0 : 40, b = dir > 0 ? 40 : 0;
            g2.setPaint(new java.awt.GradientPaint(a, 0, new java.awt.Color(30, 19, 48, 242), b, 0, new java.awt.Color(30, 19, 48, 0)));
            g2.fill(new java.awt.geom.Rectangle2D.Double(0, 0, 40, rh));
        });
    }

    private void renderSpin(G g) {
        var Ge = geom();
        double lx = Ge.lx(), rx = Ge.rx(), fy = Ge.fy(), cx = Ge.cx();
        Spin r = RL.round; long t = P.now(); boolean done = RL.stage.equals("done");
        String name = F.trimToWidth(r.backer, 70), bal = Money.format(RL.balance());
        int tw = P.seatTagW(name, Money.format(r.total)), cw = P.pillW("Chips", bal);
        double sx = cx - ((tw + 4 + cw) >> 1);
        P.seatTag(g, sx + (tw >> 1), fy + 8, P.skinOf(r.backer), name, Money.format(r.total), P.WHITE);
        long pay = RL.payoutOf(r);
        int chipsCol = done && t - r.landedAt < 1800 ? (pay > r.total ? P.GREEN : pay < r.total ? P.RED : P.WHITE) : P.WHITE;
        if (refund != null) refund.place(sx + tw + 4, fy + 8, chipsCol); else P.pill(g, sx + tw + 4, fy + 8, "Chips", bal, chipsCol);
        // the reel: the wheel's own order, lap after lap
        double ry = fy + 30, rh = 34, x0 = lx + 6, x1 = rx - 6, pos = done ? r.land : RL.pos(t);
        P.rounded(g, x0, ry, x1 - x0, rh, 0x330A0710);
        g.enableScissor(x0, ry, x1, ry + rh);
        int first = Math.max(0, (int) Math.floor((pos - (cx - x0)) / CELL) - 1), lastI = (int) Math.ceil((pos + (x1 - cx)) / CELL) + 1;
        int winI = LAPS * 37 + indexOf(r.n);
        for (int i = first; i <= lastI; i++) {
            int n = ORDER[i % 37];
            double x = Math.round(cx - pos + i * CELL);
            boolean win = done && i == winI;
            double k = win ? P.easeOut(Math.min(1, (t - r.landedAt) / 300.0)) : 0;
            P.smooth(g, x + 2, ry + 4, CELL - 4, rh - 8, 5, tint(n, 1.4 + 1.6 * k), win ? P.alpha(0xFFFFFFFF, 0.3 + 0.7 * k) : 0x14FFFFFF);
            g.fill(x + 7, ry + rh - 7, x + CELL - 7, ry + rh - 6, color(n).equals("black") ? 0x40FFFFFF : P.alpha(fill(n), 0.8));
            String l = String.valueOf(n);
            g.push(); g.translate(x + CELL / 2.0, ry + rh / 2 - 1);
            double sc = 1 + 0.3 * k; g.scale(sc, sc);
            g.drawText(l, -(F.width(l) / 2.0), -4, win ? P.WHITE : ink(n), true);
            g.pop();
        }
        edgeFade(g, x0, 1, ry, rh); edgeFade(g, x1, -1, ry, rh);   // edges fade into the felt
        g.disableScissor();
        // the marker: two pink notches, never a line across the number
        for (int i = 0; i < 4; i++) { g.fill(cx - 3 + i, ry - 6 + i, cx + 4 - i, ry - 5 + i, P.PINK); g.fill(cx - 3 + i, ry + rh + 5 - i, cx + 4 - i, ry + rh + 6 - i, P.PINK); }
        // his bets, under the reel; the ones that won light up green; winners first once it landed; what does not fit becomes "+N more"
        List<String> keys = new ArrayList<>(r.bets.keySet());
        if (done) keys.sort((a, b) -> Boolean.compare(BETS.get(b).wins().test(r.n), BETS.get(a).wins().test(r.n)));
        double room = rx - lx - 16;
        int[] shown = {keys.size()};
        java.util.function.IntSupplier tot = () -> {
            int s = 0; for (String k : keys.subList(0, shown[0])) s += wOf(r, k) + 4;
            return s + (shown[0] < keys.size() ? P.pillW("", "+" + (keys.size() - shown[0]) + " more") + 4 : 0) - 4;
        };
        while (shown[0] > 1 && tot.getAsInt() > room) shown[0]--;
        double bx = cx - (tot.getAsInt() >> 1);
        for (String k : keys.subList(0, shown[0])) {
            String v = Money.format(r.bets.get(k));
            boolean won = done && BETS.get(k).wins().test(r.n);
            P.pill(g, bx, fy + 70, BETS.get(k).label(), v, won ? P.GREEN : done ? P.DIM : P.WHITE, won ? "selected" : "ghost");
            bx += wOf(r, k) + 4;
        }
        if (shown[0] < keys.size()) P.pill(g, bx, fy + 70, "", "+" + (keys.size() - shown[0]) + " more", P.DIM);
        if (!done) return;
        double e = P.easeOut(Math.min(1, (t - r.landedAt) / 220.0));
        long net = pay - r.total;
        // the number in its colour, then what it means for him (net, never a "gets $1M" that is really a loss)
        String col = color(r.n), head = r.n + " " + Character.toUpperCase(col.charAt(0)) + col.substring(1);
        g.push(); g.translate(cx, fy + 98);
        double sc = 1.8 * (0.7 + 0.3 * e); g.scale(sc, sc);
        g.drawText(head, -(F.width(head) >> 1), -4, P.alpha(ink(r.n), e), true);
        g.pop();
        String sub = net > 0 ? name + " wins " + Money.format(pay) + " (+" + Money.format(net) + ")"
                : net < 0 ? (pay > 0 ? name + " gets " + Money.format(pay) + " back, -" + Money.format(-net) : name + " loses " + Money.format(r.total)) : "Bets returned";
        if (pay >= r.total * 10) P.gradientText(g, sub, cx - (F.width(sub) >> 1), fy + 114, true, e);
        else g.drawText(sub, cx - (F.width(sub) >> 1), fy + 114, P.alpha(net > 0 ? P.GREEN : net < 0 ? P.RED : P.WHITE, e), true);
    }

    private static int wOf(Spin r, String k) { return P.pillW(BETS.get(k).label(), Money.format(r.bets.get(k))); }
}
