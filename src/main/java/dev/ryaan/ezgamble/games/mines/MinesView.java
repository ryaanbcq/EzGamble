package dev.ryaan.ezgamble.games.mines;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.games.Chips;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.games.crates.CratesView;
import dev.ryaan.ezgamble.games.mines.MinesTable.Round;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.GlassButton;
import dev.ryaan.ezgamble.proto.GlassField;
import dev.ryaan.ezgamble.proto.Key;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Widget;
import dev.ryaan.ezgamble.ui.GambleScreen;
import dev.ryaan.ezgamble.ui.Liquid;

import java.util.List;

import static dev.ryaan.ezgamble.games.mines.MinesTable.*;
import static dev.ryaan.ezgamble.proto.P.*;

/** The board inside GambleScreen. */
public final class MinesView implements GameView {
    static final int MINE_CELL = 24, MINE_GAP = 3;
    private final GambleScreen screen;
    private String uiKey = "";
    private GlassButton payBtn, startBtn, cashBtn;
    private Widgets.RefundPill refund;

    /** One "how many mines" choice. */
    static final class MinesOpt extends Widget {
        final int opt; final Runnable r;
        MinesOpt(double x, double y, double w, double h, int opt, Runnable r) { super(x, y, w, h); this.opt = opt; this.r = r; }
        @Override public void click(double mx, double my) { r.run(); }
    }

    public MinesView(GambleScreen s) { this.screen = s; }

    private GambleScreen.Geom geom() { return screen.geom(); }

    private String uiState() { return MN.stage + "|" + MN.backer + "|" + MN.mines + "|" + MN.backers().size(); }

    /** The board's top left: row numbers on its left, column letters above (the names viewers whisper). */
    private double[] board() { var G = geom(); return new double[]{G.lx() + 22, G.fy() + 17}; }

    private double[] tileRect(int i) {
        double[] b = board(); int s = MINE_CELL + MINE_GAP;
        return new double[]{b[0] + (i % 5) * s, b[1] + (i / 5) * s, MINE_CELL, MINE_CELL};
    }

    /** x, w, cx of the right panel. */
    private double[] panel() {
        var G = geom();
        double x = board()[0] + 5 * (MINE_CELL + MINE_GAP) + 12, w = G.rx() - 8 - x;
        return new double[]{x, w, x + ((int) w >> 1)};
    }

    @Override
    public void init() {
        var s = screen; var G = geom();
        double lx = G.lx(), rx = G.rx(), hy = G.hy(), fy = G.fy(), fh = G.fh(), by = G.by(), cx = G.cx();
        uiKey = uiState(); payBtn = null; startBtn = null; cashBtn = null; refund = null;
        s.add(new GlassButton(lx, hy, 18, 16, "‹", "GHOST", s::back));
        if (MN.stage.equals("bet")) {
            List<Payouts.Row> list = MN.backers();
            if (MN.backer == null) MN.backer = Host.lastBacker;   // the player of the last game by default
            final String cur = MN.backer;
            if (list.stream().noneMatch(r -> r.name().equals(cur))) { MN.backer = list.isEmpty() ? null : list.get(0).name(); MN.clearBet(); }
            // how many mines: a row of choices, top right of the felt
            double x = rx - 8;
            for (int k = MINES_OPTIONS.length - 1; k >= 0; k--) {
                final int m = MINES_OPTIONS[k];
                int w = F.width(String.valueOf(m)) + 12; x -= w;
                s.add(new MinesOpt(x, fy + 8, w, 15, m, () -> { if (MN.mines != m) { P.play("click"); MN.mines = m; } }));
                x -= 3;
            }
            if (!list.isEmpty()) {
                int n = Chips.CHIPS.length, step = 44;
                double x0 = cx - (((n * step - (step - 30))) >> 1);
                for (int i = 0; i < n; i++) s.add(new Widgets.ChipButton(x0 + i * step, fy + fh - 36, Chips.CHIPS[n - 1 - i], MN));
                if (MN.field == null) MN.field = new GlassField(0, 0, 110, 22, "", "Amount", 10);
                MN.field.x = lx; MN.field.y = by + 1; MN.field.focused = false;
                s.add(MN.field);
                s.add(new GlassButton(lx + 116, by, 56, 24, "Clear", "GHOST", MN::clearBet));
            }
            startBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Start", "PRIMARY", MN::start));
            if (!list.isEmpty()) {
                if (MN.picker == null) MN.picker = new Widgets.BackerPicker(lx + 8, fy + 8, 168, MN);
                MN.picker.place(lx + 8, fy + 8, 168); s.add(MN.picker);
            }
            return;
        }
        if (MN.stage.equals("play")) {
            for (int i = 0; i < MINES_N; i++) {
                final int ii = i; double[] r = tileRect(i);
                s.add(Widgets.area(r[0], r[1], r[2], r[3], () -> MN.pick(ii)));
            }
            cashBtn = s.add(new GlassButton(rx - 150, by, 150, 24, "Cash out", "PRIMARY", MN::cashout));
            s.add(new GlassButton(rx - 150 - 6 - 80, by, 80, 24, "Random", "GHOST", MN::pickRandom));
            return;
        }
        // done
        boolean won = MN.payState() != null;
        startBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Play again", won ? "GHOST" : "PRIMARY", MN::again));
        s.add(new GlassButton(rx - 110 - 6 - 80, by, 80, 24, "Change bet", "GHOST", () -> { MN.settle(); MN.stage = "bet"; }));
        if (won) {
            payBtn = s.add(new GlassButton(lx, by, 150, 24, "", "PRIMARY", MN::pay) {
                @Override public void click(double mx, double my) { if (active) action.run(); }
            });
            syncPay();
        }
        if (!(MN.refundPill instanceof Widgets.RefundPill rp) || rp.round != MN.round) {
            var np = new Widgets.RefundPill(MN, MN::settle); np.round = MN.round; MN.refundPill = np;
        }
        refund = s.add(MN.refundPill);
    }

    private void syncPay() {
        var st = MN.payState(); var b = payBtn;
        if (st == null) { b.visible = false; return; }
        b.label = st.label(); b.active = st.active(); b.w = Math.max(120, F.width(st.label()) + 20);
    }

    @Override
    public void tick() {
        if (!uiState().equals(uiKey)) { screen.rebuild(); return; }
        if (startBtn != null) {   // an error shows ON the button, in red, for 2 s (Crates' rule)
            String warn = MN.warn != null && P.now() < MN.warn.until() ? MN.warn.text() : !GambleData.mode ? "Gambling mode is off" : null;
            var b = startBtn; boolean again = MN.stage.equals("done");
            b.label = warn != null ? warn : again ? "Play again" : MN.bet > 0 ? "Start " + Money.format(MN.bet) : "Start";
            b.kind = warn != null ? "DANGER" : again && MN.payState() != null ? "GHOST" : "PRIMARY";
            b.active = warn != null || (again ? MN.balance() > 0 : GambleData.mode && !MN.backers().isEmpty());
        }
        if (cashBtn != null) {
            int n = MN.round.picked.size();
            cashBtn.label = n > 0 ? "Cash out " + Money.format(MN.value()) : "Pick a tile";
            cashBtn.active = n > 0; cashBtn.kind = n > 0 ? "PRIMARY" : "GHOST";
        }
        if (payBtn != null) syncPay();
    }

    @Override
    public boolean key(Key e) {
        if (MN.stage.equals("bet")) {
            if (MN.picker != null && MN.picker.key(e)) return true;
            if (MN.field != null && MN.field.focused) {
                if (e.is("Enter")) MN.start();
                else if (e.is("Escape")) MN.field.focused = false;
                else if (MN.field.type(e)) { MN.bet = Math.max(0, Money.parse(MN.field.value)); MN.betChips.clear(); }
                return true;
            }
            if (e.is("Enter")) { MN.start(); return true; }
            return false;
        }
        if (MN.stage.equals("play") && e.key().equalsIgnoreCase("r")) { MN.pickRandom(); return true; }   // never a key for Cash out: a slip would end his round
        if (MN.stage.equals("done") && e.is("Enter")) { MN.again(); return true; }
        return false;
    }

    @Override
    public void clicked(Widget hit) {
        if (MN.field != null && hit != MN.field) MN.field.focused = false;
        if (MN.picker != null && hit != MN.picker) MN.picker.close();
        if (MN.refundPill != null && hit != MN.refundPill) MN.refundPill.fold();
    }

    @Override public void leave() { MN.settle(); }

    @Override
    public void render(G g, double mx, double my) {
        var G = geom();
        double lx = G.lx(), rx = G.rx(), hy = G.hy();
        g.drawText("Mines", lx + 24, hy + 4, WHITE, true);
        if (MN.stage.equals("bet")) { if (MN.backer != null) pillsRight(g, rx, hy, new Object[][]{{"Chips", Money.format(MN.balance())}}); }
        else pillsRight(g, rx, hy, new Object[][]{{"Mines", String.valueOf(MN.round.mines)}, {"Bet", Money.format(MN.round.bet)}});
        CratesView.felt(g, lx, G.ft(), rx - lx, G.fth());
        if (MN.stage.equals("bet")) renderBet(g, mx, my); else renderBoard(g, mx, my);
    }

    private void renderBet(G g, double mx, double my) {
        var G = geom();
        double lx = G.lx(), fy = G.fy(), cx = G.cx();
        List<Payouts.Row> list = MN.backers();
        // the mines choice: label + capsules
        MinesOpt first = null;
        for (Widget wd : screen.children) {
            if (!(wd instanceof MinesOpt w)) continue;
            boolean sel = MN.mines == w.opt, hov = w.over(mx, my);
            capsule(g, w.x, w.y, w.w, w.h, sel ? "primary" : hov ? "selected" : "ghost", hov);
            g.drawText(String.valueOf(w.opt), w.x + 6, w.y + 4, sel ? INK : hov ? WHITE : TEXT, !sel);
            if (first == null || w.x < first.x) first = w;   // the leftmost choice
        }
        if (first != null) g.drawText("Mines", first.x - 5 - F.width("Mines"), fy + 12, MUTE, false);
        if (list.isEmpty()) {
            g.drawText("No chips on the table yet.", lx + 8, fy + 44, TEXT, false);
            g.drawText("Players who /pay " + GambleData.name + " during a session can play Mines with you.", lx + 8, fy + 58, MUTE, false);
            if (!GambleData.mode) g.drawText("Turn gambling mode on to start a session.", lx + 8, fy + 74, RED, false);
            return;
        }
        // what each safe tile is worth with this many mines: the ladder, then the promise
        int steps = Math.min(7, MINES_N - MN.mines), step = 44;
        double x0 = cx - (((steps * step - 4)) >> 1), y = fy + 40;
        for (int k = 1; k <= steps; k++) {
            double x = x0 + (k - 1) * step;
            String mt = minesText(minesMult(k, MN.mines));
            boolean big = minesMult(k, MN.mines) >= 10;
            smooth(g, x, y, step - 4, 34, 6, 0x0AFFFFFF, 0x18FFFFFF);
            String lab = k + (k == 1 ? " tile" : " tiles");
            g.drawText(lab, x + ((step - 4 - F.width(lab)) >> 1), y + 6, MUTE, false);
            if (big) gradientText(g, mt, x + ((step - 4 - F.width(mt)) >> 1), y + 19, false);
            else g.drawText(mt, x + ((step - 4 - F.width(mt)) >> 1), y + 19, WHITE, false);
        }
        String p1 = "Returns 96% whatever you do", p2 = "The board is drawn before the first tile";
        g.drawText(p1, cx - (F.width(p1) >> 1), y + 44, DIM, false);
        g.drawText(p2, cx - (F.width(p2) >> 1), y + 56, DIM, false);
    }

    private void renderBoard(G g, double mx, double my) {
        Round r = MN.round;
        long t = P.now();
        boolean done = MN.stage.equals("done");
        double[] b = board();
        // a mine shakes the board for 0.4 s
        double shake = r.boom >= 0 && t - r.endAt < 400 ? Math.round(Math.sin((t - r.endAt) / 18.0) * 3 * (1 - (t - r.endAt) / 400.0)) : 0;
        g.push(); g.translate(shake, 0);
        for (int c = 0; c < 5; c++) {
            String l = String.valueOf("ABCDE".charAt(c));
            g.drawText(l, b[0] + c * (MINE_CELL + MINE_GAP) + ((MINE_CELL - F.width(l)) >> 1), b[1] - 10, DIM, false);
        }
        for (int rr = 0; rr < 5; rr++) g.drawText(String.valueOf(rr + 1), b[0] - 10, b[1] + rr * (MINE_CELL + MINE_GAP) + 8, DIM, false);
        for (int i = 0; i < MINES_N; i++) {
            double[] q = tileRect(i);
            boolean open = r.picked.contains(i), bomb = r.bombs.contains(i), hit = i == r.boom;
            // at the end the rest of the board turns over, tile by tile (30 ms apart): what he would have found
            boolean shown = open || (done && t - r.endAt >= 250 + i * 30L);
            boolean hov = !done && !open && mx >= q[0] && mx < q[0] + q[2] && my >= q[1] && my < q[1] + q[3];
            if (!shown) {
                smooth(g, q[0], q[1], q[2], q[3], 5, hov ? h(0x33A18CD1, 0x33FBC2EB) : Liquid.solid(0x14FFFFFF), hov ? 0xCCFBC2EB : 0x26FFFFFF);
                continue;
            }
            double k = open ? easeOut(Math.min(1, (t - r.pickedAt[i]) / 200.0)) : 1;
            boolean faded = !open;
            int fill = hit ? alpha(RED, 0.4) : bomb ? alpha(RED, faded ? 0.08 : 0.2) : alpha(GREEN, faded ? 0.05 : 0.16);
            int line = hit ? alpha(RED, 0.9) : bomb ? alpha(RED, faded ? 0.25 : 0.5) : alpha(GREEN, faded ? 0.18 : 0.6);
            smooth(g, q[0], q[1], q[2], q[3], 5, fill, line);
            double sc = 0.6 + 0.4 * k + (open ? 0.15 * Math.sin(k * Math.PI) : 0);
            g.push(); g.translate(q[0] + q[2] / 2, q[1] + q[3] / 2); g.scale(sc, sc);
            g.drawItem(bomb ? "tnt_side" : "emerald", -8, -8, false);
            // the prototype's globalAlpha 0.4 on the tiles he did not open: no item alpha in a DrawContext, so a felt veil over the item
            if (faded) g.fill(-8, -8, 8, 8, 0x99221638);
            g.pop();
        }
        if (r.boom >= 0 && t - r.endAt < 300) {   // the blast
            double[] q = tileRect(r.boom); double e = (t - r.endAt) / 300.0;
            ring(g, q[0] - 6 * e, q[1] - 6 * e, q[2] + 12 * e, q[3] + 12 * e, 6, alpha(RED, 1 - e), 2);
        }
        g.pop();
        renderPanel(g);
    }

    /** Right of the board: who plays, his chips, the multiplier, what the next tile is worth and its chance. */
    private void renderPanel(G g) {
        double fy = geom().fy();
        Round r = MN.round;
        long t = P.now();
        double[] p = panel();
        double pw = p[1], pcx = p[2];
        boolean done = MN.stage.equals("done");
        String name = F.trimToWidth(r.backer, 70), bal = Money.format(MN.balance());
        seatTag(g, pcx, fy + 8, skinOf(r.backer), name, r.mines + " mines", WHITE);
        int chipsCol = done && t - r.endAt < 1800 ? (r.cashed ? GREEN : RED) : WHITE;
        int cw = refund != null ? 0 : pillW("Chips", bal);
        if (refund != null) { refund.place(0, fy + 27, chipsCol); refund.x = pcx - ((int) refund.w >> 1); }
        else pill(g, pcx - (cw >> 1), fy + 27, "Chips", bal, chipsCol);
        if (!done) {
            int k = r.picked.size(), left = MN.safeLeft(), closed = MINES_N - k;
            String mt = minesText(MN.mult());
            String lab = k > 0 ? "Multiplier" : "Pick a tile";
            g.drawText(lab, pcx - (F.width(lab) >> 1), fy + 52, MUTE, false);
            g.push(); g.translate(pcx, fy + 72); g.scale(2.2, 2.2); gradientText(g, mt, -(F.width(mt) >> 1), -4, true); g.pop();
            if (left > 0) {
                String nx = "Next " + minesText(minesMult(k + 1, r.mines)), ch = "Safe " + Math.round(((double) left / closed) * 100) + "%";
                int w1 = F.width(nx) + 14, w2 = F.width(ch) + 14;
                double x = pcx - ((w1 + 4 + w2) >> 1);
                pill(g, x, fy + 92, "", nx);
                pill(g, x + w1 + 4, fy + 92, "", ch);
            }
            String tl = k + " / " + (MINES_N - r.mines) + " safe tiles found";
            g.drawText(tl, pcx - (F.width(tl) >> 1), fy + 114, DIM, false);
            return;
        }
        // the verdict
        double e = easeOut(Math.min(1, (t - r.endAt) / 220.0));
        long v = MN.value();
        boolean big = r.cashed && MN.mult() >= 5;
        String word = r.cashed ? name + " wins " + Money.format(v) : name + " hit a mine";
        double sc = 1.4 * (0.7 + 0.3 * e), fit = Math.min(sc, (pw - 4) / Math.max(1, F.width(word)));
        g.push(); g.translate(pcx, fy + 62); g.scale(fit, fit);
        if (big) gradientText(g, word, -(F.width(word) >> 1), -4, true, e);
        else g.drawText(word, -(F.width(word) >> 1), -4, alpha(r.cashed ? GREEN : RED, e), true);
        g.pop();
        int k = r.picked.size() - (r.cashed ? 0 : 1);
        String sub = r.cashed ? minesText(MN.mult()) + " after " + k + (k == 1 ? " tile" : " tiles") : "Lost " + Money.format(r.bet) + " after " + k + (k == 1 ? " tile" : " tiles");
        g.drawText(sub, pcx - (F.width(sub) >> 1), fy + 78, alpha(TEXT, e), false);
    }
}
