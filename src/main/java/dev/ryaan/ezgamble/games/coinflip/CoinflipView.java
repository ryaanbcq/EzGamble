package dev.ryaan.ezgamble.games.coinflip;

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

import java.awt.Color;
import java.awt.LinearGradientPaint;
import java.awt.BasicStroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Locale;

import static dev.ryaan.ezgamble.games.coinflip.CoinTable.*;

/** The coin inside GambleScreen (prototype CoinflipView + drawCoin). */
public final class CoinflipView implements GameView {
    private static final CoinTable CF = CoinTable.CF;

    private static final class SideTile extends Widget {
        final String side;
        SideTile(double x, double y, double w, double h, String side) { super(x, y, w, h); this.side = side; }
        @Override public void click(double mx, double my) { if (!CF.side.equals(side)) { P.play("click"); CF.side = side; } }
    }

    private final GambleScreen screen;
    private String uiKey = "";
    private GlassButton payBtn, flipBtn;
    private Widgets.RefundPill refund;

    public CoinflipView(GambleScreen screen) { this.screen = screen; }

    private String uiState() { return CF.stage + "|" + CF.backer + "|" + CF.backers().size(); }

    private static void felt(G g, double x, double y, double w, double h) {
        P.smooth(g, x, y, w, h, 10, P.v(0xFF2A1B44, 0xFF1A1030), 0x1FFFFFFF);
    }

    private record R(double x, double y, double w, double h) {}

    /** Wide enough for the name and its payout. */
    private R sideRect(String side) {
        GambleScreen.Geom G = screen.geom();
        double w = 110, h = 54, gap = 10;
        return side.equals("heads") ? new R(G.cx() - w - gap / 2, G.fy() + 30, w, h) : new R(G.cx() + gap / 2, G.fy() + 30, w, h);
    }

    @Override
    public void init() {
        GambleScreen s = screen;
        GambleScreen.Geom G = s.geom();
        double lx = G.lx(), rx = G.rx(), hy = G.hy(), fy = G.fy(), fh = G.fh(), by = G.by(), cx = G.cx();
        uiKey = uiState(); payBtn = null; flipBtn = null; refund = null;
        s.add(new GlassButton(lx, hy, 18, 16, "‹", "GHOST", s::back));
        if (CF.stage.equals("bet")) {
            var list = CF.backers();
            if (CF.backer == null) CF.backer = Host.lastBacker;
            if (list.stream().noneMatch(r -> r.name().equals(CF.backer))) { CF.backer = list.isEmpty() ? null : list.get(0).name(); CF.clearBet(); }
            if (list.isEmpty() == false) {
                for (String side : new String[]{"heads", "tails"}) { R q = sideRect(side); s.add(new SideTile(q.x, q.y, q.w, q.h, side)); }
                int n = Chips.CHIPS.length, step = 44;
                double x0 = cx - ((n * step - (step - 30)) >> 1);
                for (int i = 0; i < n; i++) s.add(new Widgets.ChipButton(x0 + i * step, fy + fh - 36, Chips.CHIPS[n - 1 - i], CF));
                if (CF.field == null) CF.field = new GlassField(0, 0, 110, 22, "", "Amount", 10);
                CF.field.x = lx; CF.field.y = by + 1; CF.field.focused = false;
                s.add(CF.field);
                s.add(new GlassButton(lx + 116, by, 56, 24, "Clear", "GHOST", CF::clearBet));
            }
            flipBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Flip", "PRIMARY", CF::start));
            if (!list.isEmpty()) {
                if (CF.picker == null) CF.picker = new Widgets.BackerPicker(lx + 8, fy + 2, 168, CF);
                CF.picker.place(lx + 8, fy + 2, 168); s.add(CF.picker);
            }
            return;
        }
        if (CF.stage.equals("done")) {
            boolean won = CF.payState() != null;
            flipBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Flip again", won ? "GHOST" : "PRIMARY", CF::again));
            s.add(new GlassButton(rx - 110 - 6 - 80, by, 80, 24, "Change bet", "GHOST", () -> { CF.settle(); CF.stage = "bet"; }));
            if (won) {
                payBtn = s.add(new GlassButton(lx, by, 150, 24, "", "PRIMARY", CF::pay) {
                    @Override public void click(double mx, double my) { if (active) action.run(); }   // no click sound: the payment plays its own
                });
                syncPay();
            }
            if (CF.refundPill == null || CF.refundPill.round != CF.round) { CF.refundPill = new Widgets.RefundPill(CF, CF::settle); CF.refundPill.round = CF.round; }
            refund = s.add(CF.refundPill);
        }
    }

    private void syncPay() {
        var st = CF.payState(); GlassButton b = payBtn;
        if (st == null) { b.visible = false; return; }
        b.label = st.label(); b.active = st.active(); b.w = Math.max(120, F.width(st.label()) + 20);
    }

    @Override
    public void tick() {
        CF.tick();
        if (!uiState().equals(uiKey)) { screen.rebuild(); return; }
        if (flipBtn != null) {
            String warn = CF.warn != null && P.now() < CF.warn.until() ? CF.warn.text() : !GambleData.mode ? "Gambling mode is off" : null;
            GlassButton b = flipBtn; boolean again = CF.stage.equals("done");
            b.label = warn != null ? warn : again ? "Flip again" : CF.bet > 0 ? "Flip " + Money.format(CF.bet) : "Flip";
            b.kind = warn != null ? "DANGER" : again && CF.payState() != null ? "GHOST" : "PRIMARY";
            b.active = warn != null || (again ? CF.balance() > 0 : GambleData.mode && !CF.backers().isEmpty());
        }
        if (payBtn != null) syncPay();
    }

    @Override
    public boolean key(Key e) {
        if (CF.stage.equals("bet")) {
            if (CF.picker != null && CF.picker.key(e)) return true;
            if (CF.field != null && CF.field.focused) {
                if (e.is("Enter")) CF.start();
                else if (e.is("Escape")) CF.field.focused = false;
                else if (CF.field.type(e)) { CF.bet = Math.max(0, Money.parse(CF.field.value)); CF.betChips.clear(); }
                return true;
            }
            String k = e.key().toLowerCase(Locale.ROOT);
            if (k.equals("h") || k.equals("t")) { CF.side = k.equals("h") ? "heads" : "tails"; P.play("click"); return true; }
            if (e.is("Enter")) { CF.start(); return true; }
            return false;
        }
        if (CF.stage.equals("done") && e.is("Enter")) { CF.again(); return true; }
        return CF.stage.equals("spin") && e.key().equals(" ");
    }

    @Override
    public void clicked(Widget hit) {
        if (CF.field != null && hit != CF.field) CF.field.focused = false;
        if (CF.picker != null && hit != CF.picker) CF.picker.close();
        if (refund != null && hit != refund) refund.fold();
    }

    /** A board in play stays open, it waits for him. */
    @Override
    public void leave() { CF.settle(); }

    @Override
    public void render(G g, double mx, double my) {
        GambleScreen.Geom G = screen.geom();
        double lx = G.lx(), rx = G.rx(), hy = G.hy();
        g.drawText("Coinflip", lx + 24, hy + 4, P.WHITE, true);
        if (CF.stage.equals("bet")) { if (CF.backer != null) P.pillsRight(g, rx, hy, new Object[][]{{"Chips", Money.format(CF.balance())}}); }
        else P.pillsRight(g, rx, hy, new Object[][]{{"On", cfName(CF.round.side)}, {"Bet", Money.format(CF.round.bet)}});
        felt(g, lx, G.ft(), rx - lx, G.fth());
        if (CF.stage.equals("bet")) renderBet(g, mx, my); else renderFlip(g);
    }

    /** The last results, top right: what everyone asks before calling a side. */
    private void renderStreak(G g, double y) {
        double x = screen.geom().rx() - 8;
        List<String> all = CF.results, last = all.subList(Math.max(0, all.size() - 10), all.size());
        for (int i = last.size() - 1; i >= 0; i--) {
            boolean h = last.get(i).equals("heads");
            String l = h ? "H" : "T"; int w = 13; x -= w;
            P.smooth(g, x, y, w, 13, 6.5, h ? 0x33F2C14E : 0x1AFFFFFF, 0x1AFFFFFF);
            g.drawText(l, x + ((w - F.width(l)) >> 1), y + 3, P.alpha(h ? 0xFFF2C14E : P.TEXT, i == last.size() - 1 ? 1 : 0.7), false); x -= 3;
        }
    }

    private void renderBet(G g, double mx, double my) {
        GambleScreen.Geom G = screen.geom();
        double lx = G.lx(), fy = G.fy(), cx = G.cx();
        var list = CF.backers();
        if (list.isEmpty()) {
            String me = net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername();
            g.drawText("No chips on the table yet.", lx + 8, fy + 44, P.TEXT, false);
            g.drawText("Players who /pay " + me + " during a session can flip with you.", lx + 8, fy + 58, P.MUTE, false);
            if (!GambleData.mode) g.drawText("Turn gambling mode on to start a session.", lx + 8, fy + 74, P.RED, false);
            return;
        }
        renderStreak(g, fy + 4);
        String skin = P.skinOf(CF.backer != null ? CF.backer : net.minecraft.client.MinecraftClient.getInstance().getSession().getUsername());
        // the two sides: a small coin showing that face, its name; the picked one glows
        for (String side : new String[]{"heads", "tails"}) {
            R q = sideRect(side);
            boolean sel = CF.side.equals(side), hov = mx >= q.x && mx < q.x + q.w && my >= q.y && my < q.y + q.h;
            int stroke = sel ? 0xCCFBC2EB : hov ? 0x40FFFFFF : 0x18FFFFFF;
            if (sel) P.smooth(g, q.x, q.y, q.w, q.h, 8, P.h(0x33A18CD1, 0x26FBC2EB), stroke);
            else P.smooth(g, q.x, q.y, q.w, q.h, 8, hov ? 0x14FFFFFF : 0x0AFFFFFF, stroke);
            drawCoin(g, q.x + 20, q.y + q.h / 2, 12, side.equals("heads") ? 0 : Math.PI, skin);
            g.drawText(cfName(side), q.x + 40, q.y + 16, sel ? P.WHITE : P.TEXT, sel);
            g.drawText("Pays x" + CF_PAY, q.x + 40, q.y + 30, P.MUTE, false);   // 40 + text <= w - 6: never past the card's edge
        }
        String p1 = "A fair 50 / 50, the house keeps 3%";
        String p2 = CF.bet > 0 ? "Win " + Money.format((long) Math.floor(CF.bet * CF_PAY)) + " on " + cfName(CF.side) : "Pick a side and a bet";
        g.drawText(p1, cx - (F.width(p1) >> 1), fy + 92, P.DIM, false);
        g.drawText(p2, cx - (F.width(p2) >> 1), fy + 104, CF.bet > 0 ? P.TEXT : P.DIM, false);
    }

    private void renderFlip(G g) {
        GambleScreen.Geom G = screen.geom();
        double fy = G.fy(), cx = G.cx();
        CoinTable.Rnd r = CF.round; long t = P.now(); boolean done = CF.stage.equals("done");
        String name = F.trimToWidth(r.backer, 70), bal = Money.format(CF.balance());
        int tw = P.seatTagW(name, cfName(r.side)), cw = P.pillW("Chips", bal);
        double sx = cx - ((tw + 4 + cw) >> 1);
        P.seatTag(g, sx + (tw >> 1), fy + 2, P.skinOf(r.backer), name, cfName(r.side), P.WHITE);
        int chipsCol = done && t - r.landedAt < 1800 ? (CF.won(r) ? P.GREEN : P.RED) : P.WHITE;
        if (refund != null) refund.place(sx + tw + 4, fy + 2, chipsCol); else P.pill(g, sx + tw + 4, fy + 2, "Chips", bal, chipsCol);
        // the coin goes up and comes down while it turns; its shadow on the felt shrinks as it rises
        double f = done ? 1 : Math.min(1, (double) (t - r.at) / CF_MS);
        double lift = Math.sin(Math.min(1, f * 1.05) * Math.PI) * 22, a = done ? r.halves * Math.PI : CF.angle(t);
        double land = done ? P.easeOut(Math.min(1, (t - r.landedAt) / 250.0)) : 0, R = 22 + 3 * Math.sin(land * Math.PI);
        double shy = fy + 88, sw = 26 * (1 - lift / 40);
        P.smooth(g, cx - sw, shy - 2, sw * 2, 5, 2.5, 0x40000000, 0);
        drawCoin(g, cx, fy + 60 - lift, R, a, P.skinOf(r.backer));
        if (done && CF.won(r)) { double e = Math.min(1, (t - r.landedAt) / 600.0); P.ring(g, cx - R - 6 * e, fy + 60 - R - 6 * e, 2 * R + 12 * e, 2 * R + 12 * e, R + 6 * e, P.alpha(0xFFF2C14E, 1 - e), 2); }
        if (!done) return;
        double e = P.easeOut(Math.min(1, (t - r.landedAt) / 220.0));
        String head = cfName(r.result) + "!";
        g.push(); g.translate(cx, fy + 106); double sc = 1.8 * (0.7 + 0.3 * e); g.scale(sc, sc);
        g.drawText(head, -(F.width(head) >> 1), -4, P.alpha(r.result.equals("heads") ? 0xFFF2C14E : P.WHITE, e), true);
        g.pop();
        String sub = CF.won(r) ? name + " wins " + Money.format(CF.payoutOf(r)) : name + " loses " + Money.format(r.bet);
        g.drawText(sub, cx - (F.width(sub) >> 1), fy + 122, P.alpha(CF.won(r) ? P.GREEN : P.RED, e), true);
    }

    /**
     * The coin, centre (x, y), radius R (GUI px), seen turning around its horizontal axis: angle a (0 = heads up).
     * Heads = the player's own head on gold; tails = a "T". Its edge shows as it turns. The gold body is a Vec sprite
     * (squash and radius quantised: 32 steps / half pixels), the face is drawn squashed on top.
     */
    static void drawCoin(G g, double x, double y, double R, double a, String skin) {
        double c = Math.cos(a), heads = c >= 0 ? 1 : 0;
        double sy = Math.max(2 / 32.0, Math.round(Math.abs(c) * 32) / 32.0);   // quantised squash
        double Rq = Math.round(R * 2) / 2.0;
        double th = Math.max(1.5, Rq * 0.16) * (1 - sy), thMax = Math.max(1.5, Rq * 0.16);
        final double rx = Rq, ry = Rq * sy;
        Vec.draw(g, "coin/" + Rq + "/" + sy, x - Rq, y - Rq, 2 * Rq, 2 * Rq + thMax, 2, g2 -> {
            double cx = rx, cy = Rq;
            // the edge (the coin's thickness, below the face)
            g2.setColor(Vec.c(0xFF9C6A1E));
            g2.fill(new Ellipse2D.Double(cx - rx, cy + th - ry, rx * 2, ry * 2));
            g2.fill(new Rectangle2D.Double(cx - rx, cy, rx * 2, th));
            // the face: gold, a lighter rim, a soft shine across
            g2.setPaint(new LinearGradientPaint(new Point2D.Double(cx - rx, cy - ry), new Point2D.Double(cx + rx, cy + ry),
                    new float[]{0f, 0.5f, 1f}, new Color[]{Vec.c(0xFFFFE9A3), Vec.c(0xFFF2C14E), Vec.c(0xFFC98B2B)}));
            g2.fill(new Ellipse2D.Double(cx - rx, cy - ry, rx * 2, ry * 2));
            g2.setColor(new Color(255, 240, 200, 230));
            g2.setStroke(new BasicStroke((float) Math.max(1, Rq * 0.09)));
            g2.draw(new Ellipse2D.Double(cx - rx * 0.84, cy - ry * 0.84, rx * 1.68, ry * 1.68));
        });
        // what is on the face, squashed with it
        g.push(); g.translate(x, y); g.scale(1, sy);
        if (heads == 1) { int hs = (int) Math.round(R * 0.9); g.drawHead(skin, -(hs >> 1), -(hs >> 1), hs); }
        else { String s = "T"; double sc = R / 10; g.push(); g.scale(sc, sc); g.drawText(s, -(F.width(s) / 2.0), -4, 0xFF8A5A14, false); g.pop(); }
        g.pop();
    }
}
