package dev.ryaan.ezgamble.games.crates;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.games.Chips;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.games.Host;
import dev.ryaan.ezgamble.games.Widgets;
import dev.ryaan.ezgamble.games.crates.CrateTable.CrateType;
import dev.ryaan.ezgamble.games.crates.CrateTable.Prize;
import dev.ryaan.ezgamble.games.crates.CrateTable.Round;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.GlassButton;
import dev.ryaan.ezgamble.proto.GlassField;
import dev.ryaan.ezgamble.proto.GlassSwitch;
import dev.ryaan.ezgamble.proto.Key;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Widget;
import dev.ryaan.ezgamble.ui.GambleScreen;

import java.util.List;

import static dev.ryaan.ezgamble.games.crates.CrateTable.*;
import static dev.ryaan.ezgamble.proto.P.*;

/** The crates inside GambleScreen. */
public final class CratesView implements GameView {
    private final GambleScreen screen;
    private String uiKey = "";
    private GlassButton payBtn, openBtn;
    private Widgets.RefundPill refund;

    /** A crate to pick. */
    static final class CrateTile extends Widget {
        final int idx;
        CrateTile(double x, double y, double w, double h, int idx, Runnable r) { super(x, y, w, h); this.idx = idx; this.r = r; }
        final Runnable r;
        @Override public void click(double mx, double my) { r.run(); }
    }

    public CratesView(GambleScreen s) { this.screen = s; }

    private GambleScreen.Geom geom() { return screen.geom(); }

    private String uiState() { return CR.stage + "|" + CR.backer + "|" + CR.crate + "|" + CR.backers().size(); }

    /** The felt: one surface, a hairline rim (content, not glass). Shared with the other host tables. */
    public static void felt(G g, double x, double y, double w, double h) {
        smooth(g, x, y, w, h, 10, v(0xFF2A1B44, 0xFF1A1030), 0x1FFFFFFF);
    }

    @Override
    public void init() {
        var s = screen;
        var G = geom();
        double lx = G.lx(), rx = G.rx(), hy = G.hy(), fy = G.fy(), fh = G.fh(), by = G.by(), cx = G.cx();
        uiKey = uiState(); payBtn = null; openBtn = null; refund = null;
        s.add(new GlassButton(lx, hy, 18, 16, "‹", "GHOST", s::back));
        if (CR.stage.equals("bet")) {
            List<Payouts.Row> list = CR.backers();
            if (CR.backer == null && Host.lastBacker != null) CR.backer = Host.lastBacker;   // same player as at the last table by default
            final String cur = CR.backer;
            if (list.stream().noneMatch(r -> r.name().equals(cur))) { CR.backer = list.isEmpty() ? null : list.get(0).name(); CR.clearBet(); }
            // the three crates, side by side
            int cw = (int) ((rx - lx - 16 - 12) / 3);
            for (int i = 0; i < CRATE_TYPES.size(); i++) {
                final int ii = i;
                s.add(new CrateTile(lx + 8 + i * (cw + 6), fy + 34, cw, 46, i, () -> { if (CR.crate != ii) { P.play("click"); CR.crate = ii; } }));
            }
            if (!list.isEmpty()) {
                int n = Chips.CHIPS.length, step = 44;
                double x0 = cx - (((n * step - (step - 30))) >> 1);
                for (int i = 0; i < n; i++) {
                    Chips.Chip c = Chips.CHIPS[n - 1 - i];   // CHIPS.slice().reverse()
                    s.add(new Widgets.ChipButton(x0 + i * step, fy + fh - 36, c, CR));
                }
                if (CR.field == null) CR.field = new GlassField(0, 0, 110, 22, "", "Amount", 10);
                CR.field.x = lx; CR.field.y = by + 1; CR.field.focused = false;
                s.add(CR.field);
                s.add(new GlassButton(lx + 116, by, 56, 24, "Clear", "GHOST", CR::clearBet));
            }
            openBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Open", "PRIMARY", CR::open));
            if (!list.isEmpty()) s.add(new GlassSwitch(rx - 110 - 8 - 28, by + 5, 28, 14, () -> GambleData.crateFast, GambleData::setCrateFast));   // "Fast": the next opens spin in 1.5 s
            if (!list.isEmpty()) {   // added last: its result sheet goes over the crates
                if (CR.picker == null) CR.picker = new Widgets.BackerPicker(lx + 8, fy + 8, 168, CR);
                CR.picker.place(lx + 8, fy + 8, 168);
                s.add(CR.picker);
            }
            return;
        }
        if (CR.stage.equals("done")) {
            boolean won = CR.payState() != null;
            openBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Open again", won ? "GHOST" : "PRIMARY", CR::again));
            s.add(new GlassButton(rx - 110 - 6 - 80, by, 80, 24, "Change bet", "GHOST", () -> { CR.settle(); CR.stage = "bet"; }));
            if (won) {
                payBtn = s.add(new GlassButton(lx, by, 150, 24, "", "PRIMARY", CR::pay) {
                    @Override public void click(double mx, double my) { if (active) action.run(); }   // pay() plays the click, once, only when it sends
                });
                syncPay();
            }
            if (!(CR.refundPill instanceof Widgets.RefundPill rp) || rp.round != CR.round) {   // one per crate: survives the rebuilds ("Refunded" stays)
                var np = new Widgets.RefundPill(CR, CR::settle); np.round = CR.round; CR.refundPill = np;
            }
            refund = s.add(CR.refundPill);
        } else refund = null;
    }

    private void syncPay() {
        var st = CR.payState(); var b = payBtn;
        if (st == null) { b.visible = false; return; }
        b.label = st.label(); b.active = st.active(); b.w = Math.max(120, F.width(st.label()) + 20);
    }

    @Override
    public void tick() {
        CR.tick();
        if (!uiState().equals(uiKey)) { screen.rebuild(); return; }
        if (openBtn != null) {   // an error shows ON the button that was clicked, in red, for 2 s (never written over a crate)
            String warn = CR.warn != null && P.now() < CR.warn.until() ? CR.warn.text() : !GambleData.mode ? "Gambling mode is off" : null;
            var b = openBtn;
            boolean again = CR.stage.equals("done");
            String calm = again ? (CR.payState() != null ? "GHOST" : "PRIMARY") : "PRIMARY";
            b.label = warn != null ? warn : again ? "Open again" : CR.bet > 0 ? "Open " + Money.format(CR.bet) : "Open";
            b.kind = warn != null ? "DANGER" : calm;
            b.active = warn != null || (again ? CR.balance() > 0 : GambleData.mode && !CR.backers().isEmpty());
        }
        if (payBtn != null) syncPay();
    }

    @Override
    public boolean key(Key e) {
        if (CR.stage.equals("bet")) {
            if (CR.picker != null && CR.picker.key(e)) return true;
            if (CR.field != null && CR.field.focused) {
                if (e.is("Enter")) CR.open();
                else if (e.is("Escape")) CR.field.focused = false;
                else if (CR.field.type(e)) { CR.bet = Math.max(0, Money.parse(CR.field.value)); CR.betChips.clear(); }
                return true;
            }
            if (e.is("Enter")) { CR.open(); return true; }
            return false;
        }
        if (CR.stage.equals("done") && (e.is("Enter") || e.key().equals(" "))) { CR.again(); return true; }
        return CR.stage.equals("spin") && e.key().equals(" ");
    }

    /** What mouseDown did after the click: a click elsewhere leaves the amount field, closes the player search, folds Refund all. */
    @Override
    public void clicked(Widget hit) {
        if (CR.field != null && hit != CR.field) CR.field.focused = false;
        if (CR.picker != null && hit != CR.picker) CR.picker.close();
        if (CR.refundPill != null && hit != CR.refundPill) CR.refundPill.fold();
    }

    @Override public void leave() { CR.settle(); }

    @Override
    public void render(G g, double mx, double my) {
        var G = geom();
        double lx = G.lx(), rx = G.rx(), hy = G.hy();
        g.drawText("Crates", lx + 24, hy + 4, WHITE, true);
        if (CR.stage.equals("bet")) { if (CR.backer != null) pillsRight(g, rx, hy, new Object[][]{{"Chips", Money.format(CR.balance())}}); }
        else pillsRight(g, rx, hy, new Object[][]{{"Bet", Money.format(CR.round.bet)}});
        felt(g, lx, G.ft(), rx - lx, G.fth());
        if (CR.stage.equals("bet")) renderBet(g, mx, my); else renderSpin(g);
        if (CR.stage.equals("bet") && !CR.backers().isEmpty()) g.drawText("Fast", rx - 110 - 8 - 28 - 4 - F.width("Fast"), G.by() + 8, GambleData.crateFast ? PINK : MUTE, false);
    }

    private void renderBet(G g, double mx, double my) {
        var G = geom();
        double lx = G.lx(), rx = G.rx(), fy = G.fy(), cx = G.cx();
        List<Payouts.Row> list = CR.backers();
        if (list.isEmpty()) {
            g.drawText("No chips on the table yet.", lx + 8, fy + 44, TEXT, false);
            g.drawText("Players who /pay " + GambleData.name + " during a session can open crates with you.", lx + 8, fy + 58, MUTE, false);
            if (!GambleData.mode) g.drawText("Turn gambling mode on to start a session.", lx + 8, fy + 74, RED, false);
            return;
        }
        // the crates: icon, name, what kind of ride; the picked one glows
        for (Widget wd : screen.children) {
            if (!(wd instanceof CrateTile w)) continue;
            CrateType c = CRATE_TYPES.get(w.idx);
            boolean sel = CR.crate == w.idx, hov = w.over(mx, my);
            smooth(g, w.x, w.y, w.w, w.h, 7, sel ? h(0x33A18CD1, 0x26FBC2EB) : dev.ryaan.ezgamble.ui.Liquid.solid(hov ? 0x14FFFFFF : 0x0AFFFFFF), sel ? 0xCCFBC2EB : hov ? 0x40FFFFFF : 0x18FFFFFF);
            g.drawSprite(c.icon, w.x + 6, w.y + 7, 32, 32);
            g.drawText(c.name, w.x + 42, w.y + 7, sel ? WHITE : TEXT, true);
            g.drawText(c.desc, w.x + 42, w.y + 18, MUTE, false);
            g.drawText("Returns " + (int) Math.floor(c.rtp) + "%", w.x + 42, w.y + 30, DIM, false);   // said, not hidden; rounded DOWN (never promise more)
        }
        // what is inside the picked crate: every prize, its multiplier, its chance on hover
        List<Prize> prizes = CR.type().prizes;
        int step = 32;
        double x0 = cx - (((prizes.size() * step - (step - 16))) >> 1), py = fy + 86;
        double tipX = 0, tipY = 0; String tip = null;
        for (int i = 0; i < prizes.size(); i++) {
            Prize p = prizes.get(i);
            double x = x0 + i * step;
            boolean hov = mx >= x - 6 && mx < x + 22 && my >= py - 2 && my < py + 28;
            g.drawItem(p.item(), x, py, p.shiny());
            g.fill(x + 1, py + 17, x + 15, py + 18, alpha(p.color(), 0.8));
            String m = Widgets.multText(p.mult());
            g.drawText(m, x + 8 - (F.width(m) >> 1), py + 20, p.color(), false);
            if (hov) { tip = itemName(p.item()) + ": " + trimNum(p.chance()) + "% chance"; tipX = x + 8; tipY = py - 4; }
        }
        if (tip != null) {
            double tw = F.width(tip) + 12, tx = Math.max(lx, Math.min(rx - tw, tipX - ((int) tw >> 1))), ty = tipY - 16;
            glass(g, tx, ty, tw, 15, 7, 0.85, 0.8);
            g.drawText(tip, tx + 6, ty + 4, WHITE, false);
        }
    }

    /** JS number to string: 23, 10.5, 0.2. */
    static String trimNum(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    private void renderSpin(G g) {
        var G = geom();
        double lx = G.lx(), rx = G.rx(), fy = G.fy(), cx = G.cx();
        Round r = CR.round;
        long t = P.now();
        boolean done = CR.stage.equals("done");
        // who and which crate, above the reel
        String name = F.trimToWidth(r.backer, 70), bal = Money.format(CR.balance());
        int tw = seatTagW(name, r.type.name), cw = pillW("Chips", bal), sx = (int) cx - ((tw + 4 + cw) >> 1);
        seatTag(g, sx + (tw >> 1), fy + 10, skinOf(r.backer), name, r.type.name, WHITE);
        int chipsCol = done && t - r.landedAt < 1800 ? (CR.payoutOf(r) >= r.bet ? GREEN : RED) : WHITE;
        if (refund != null) refund.place(sx + tw + 4, fy + 10, chipsCol);   // the round is over: the pill can refund (drawn as a widget)
        else pill(g, sx + tw + 4, fy + 10, "Chips", bal, chipsCol);
        // the reel
        double ry = fy + 36, rh = 58, x0 = lx + 6, x1 = rx - 6, pos = CR.stage.equals("spin") ? CR.pos(t) : r.land;
        rounded(g, x0, ry, x1 - x0, rh, 0x330A0710);
        g.enableScissor(x0, ry, x1, ry + rh);
        int first = Math.max(0, (int) Math.floor((pos - (cx - x0)) / CRATE_CELL) - 1), last = Math.min(r.strip.size() - 1, (int) Math.ceil((pos + (x1 - cx)) / CRATE_CELL) + 1);
        for (int i = first; i <= last; i++) {
            Prize p = r.strip.get(i);
            double x = Math.round(cx - pos + i * CRATE_CELL);
            boolean win = done && i == CRATE_WIN_AT;
            int col = p.color();
            double k = win ? easeOut(Math.min(1, (t - r.landedAt) / 300.0)) : 0;
            smooth(g, x + 2, ry + 4, CRATE_CELL - 4, rh - 8, 6, alpha(col, 0.07 + 0.2 * k), alpha(col, 0.25 + 0.6 * k));
            g.fill(x + 6, ry + rh - 8, x + CRATE_CELL - 6, ry + rh - 7, alpha(col, 0.9));
            double sc = 2 * (1 + 0.12 * k * Math.max(0, Math.sin(Math.min(1, (t - r.landedAt) / 400.0) * Math.PI)));
            g.push(); g.translate(x + CRATE_CELL / 2.0, ry + 22); g.scale(sc, sc); g.drawItem(p.item(), -8, -8, p.shiny()); g.pop();
            String m = Widgets.multText(p.mult());
            g.drawText(m, x + ((CRATE_CELL - F.width(m)) >> 1), ry + rh - 18, win ? WHITE : alpha(col, 0.9), false);
        }
        // edges fade into the felt (rgba(30,19,48,0.95) to 0 over 48 px)
        smooth(g, x0, ry, 48, rh, 0, h(0xF21E1330, 0x001E1330));
        smooth(g, x1 - 48, ry, 48, rh, 0, h(0x001E1330, 0xF21E1330));
        g.disableScissor();
        // the marker: a pink needle over the reel
        g.fill(cx, ry - 3, cx + 1, ry + rh + 3, PINK);
        for (int i = 0; i < 4; i++) { g.fill(cx - 3 + i, ry - 6 + i, cx + 4 - i, ry - 5 + i, PINK); g.fill(cx - 3 + i, ry + rh + 5 - i, cx + 4 - i, ry + rh + 6 - i, PINK); }
        if (!done) return;
        // the verdict, in the player's colours
        long pay = CR.payoutOf(r), net = pay - r.bet;
        double e = easeOut(Math.min(1, (t - r.landedAt) / 220.0));
        boolean big = r.prize.shiny();
        String word = net > 0 ? name + " wins " + Money.format(pay) : net < 0 ? (pay > 0 ? name + " gets " + Money.format(pay) : name + " loses " + Money.format(r.bet)) : "Bet returned";
        double sc = 1.6 * (0.7 + 0.3 * e);
        g.push(); g.translate(cx, fy + 112); g.scale(sc, sc);
        if (big) gradientText(g, word, -(F.width(word) >> 1), -4, true, e);
        else g.drawText(word, -(F.width(word) >> 1), -4, alpha(net > 0 ? GREEN : net < 0 ? RED : WHITE, e), true);
        g.pop();
        String sub = Widgets.multText(r.prize.mult()) + " " + itemName(r.prize.item());
        g.drawText(sub, cx - (F.width(sub) >> 1), fy + 126, alpha(r.prize.color(), e), false);
    }
}
