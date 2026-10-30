package dev.ryaan.ezgamble.games.rain;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.games.crates.CratesView;
import dev.ryaan.ezgamble.proto.F;
import dev.ryaan.ezgamble.proto.G;
import dev.ryaan.ezgamble.proto.GlassButton;
import dev.ryaan.ezgamble.proto.GlassField;
import dev.ryaan.ezgamble.proto.Key;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.proto.Widget;
import dev.ryaan.ezgamble.ui.GambleScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.ryaan.ezgamble.games.rain.RainTable.*;
import static dev.ryaan.ezgamble.proto.P.*;

/** The rain inside GambleScreen: set it up, watch the tickets come in, the reel, the winner. */
public final class RainView implements GameView {
    private final GambleScreen screen;
    private String uiKey = "";
    private GlassButton mainBtn, payBtn;
    private GlassField entryF, goalF, feeF;

    public RainView(GambleScreen s) { this.screen = s; }

    private GambleScreen.Geom geom() { return screen.geom(); }
    private String uiState() { return RN.stage + "|" + (RN.count() >= 2); }

    @Override
    public void init() {
        var s = screen;
        var G = geom();
        double lx = G.lx(), rx = G.rx(), hy = G.hy(), fy = G.fy(), by = G.by();
        uiKey = uiState(); mainBtn = null; payBtn = null; entryF = goalF = feeF = null;
        s.add(new GlassButton(lx, hy, 18, 16, "‹", "GHOST", s::back));
        switch (RN.stage) {
            case "setup" -> {
                double fx = lx + 8, w3 = (int) ((rx - lx - 16 - 12) / 3), y = fy + 52;
                entryF = field(fx, y, w3, Money.format(RN.entry), "1M");
                goalF = field(fx + w3 + 6, y, w3, Money.format(RN.goal), "50M");
                feeF = field(fx + 2 * (w3 + 6), y, w3, trim(RN.fee), "5");
                entryF.focused = false; goalF.focused = false; feeF.focused = false;
                mainBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Start rain", "PRIMARY", () -> { read(); RN.start(); }));
            }
            case "open" -> {
                s.add(new GlassButton(lx, by, 104, 24, "Copy for chat", "GHOST", () -> copy(RN.count() == 0 ? RN.announceText() : RN.progressText())));
                s.add(new GlassButton(lx + 110, by, 64, 24, "Cancel", "GHOST", RN::cancel));
                mainBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "Spin now", RN.count() >= 2 ? "PRIMARY" : "GHOST", () -> { if (RN.count() >= 2) RN.spin(); else P.play("fail", 1, 0.5); }));
            }
            case "done" -> {
                payBtn = s.add(new GlassButton(lx, by, 150, 24, "", "PRIMARY", RN::pay) {
                    @Override public void click(double mx, double my) { if (active) action.run(); }   // pay() plays the click, once, only when it sends
                });
                syncPay();
                s.add(new GlassButton(rx - 110 - 6 - 104, by, 104, 24, "Copy for chat", "GHOST", () -> copy(RN.winnerText())));
                mainBtn = s.add(new GlassButton(rx - 110, by, 110, 24, "New rain", "GHOST", RN::again));
            }
            default -> {}
        }
    }

    private GlassField field(double x, double y, double w, String v, String ph) {
        GlassField f = new GlassField(x, y, w, 22, v, ph, 10);
        f.allowed = "[0-9.,kKmMbB]";
        return screen.add(f);
    }

    private static String trim(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v); }

    /** The three fields into the table (on every key and before starting). */
    private void read() {
        if (entryF != null) RN.entry = Math.max(0, Money.parse(entryF.value));
        if (goalF != null) RN.goal = Math.max(0, Money.parse(goalF.value));
        if (feeF != null) try { RN.fee = Double.parseDouble(feeF.value.replace(',', '.')); } catch (NumberFormatException e) { RN.fee = -1; }
    }

    private void syncPay() {
        if (payBtn == null) return;
        var st = RN.payState();
        if (st == null) { payBtn.visible = false; return; }
        payBtn.label = st.label(); payBtn.active = st.active(); payBtn.w = Math.max(120, F.width(st.label()) + 20);
    }

    @Override
    public void tick() {
        RN.tick();
        if (!uiState().equals(uiKey)) { screen.rebuild(); return; }
        if (mainBtn != null && RN.stage.equals("setup")) {
            boolean w = RN.warn != null && P.now() < RN.warnUntil;
            String warn = w ? RN.warn : !GambleData.mode ? "Gambling mode is off" : null;
            mainBtn.label = warn != null ? warn : "Start rain";
            mainBtn.kind = warn != null ? "DANGER" : "PRIMARY";
        }
        syncPay();
    }

    @Override
    public boolean key(Key e) {
        for (GlassField f : new GlassField[]{entryF, goalF, feeF}) {
            if (f == null || !f.focused) continue;
            if (e.is("Enter")) { f.focused = false; read(); RN.start(); }
            else if (e.is("Escape")) f.focused = false;
            else if (e.is("Tab")) { f.focused = false; GlassField nx = f == entryF ? goalF : f == goalF ? feeF : entryF; nx.focused = true; nx.typedAt = P.now(); }
            else { f.type(e); read(); }
            return true;
        }
        return false;
    }

    @Override
    public void clicked(Widget hit) {
        for (GlassField f : new GlassField[]{entryF, goalF, feeF}) if (f != null && hit != f) f.focused = false;
        read();
    }

    @Override public void leave() { RN.settle(); }

    @Override
    public void render(G g, double mx, double my) {
        var G = geom();
        double lx = G.lx(), rx = G.rx(), hy = G.hy();
        g.drawText("Rain", lx + 24, hy + 4, WHITE, true);
        if (!RN.stage.equals("setup")) pillsRight(g, rx, hy, new Object[][]{{"Ticket", Money.format(RN.entry)}, {"Fee", trim(RN.fee) + "%"}});
        CratesView.felt(g, lx, G.ft(), rx - lx, G.fth());
        switch (RN.stage) {
            case "setup" -> renderSetup(g);
            case "open" -> renderOpen(g, mx, my);
            default -> renderReel(g);
        }
    }

    private void renderSetup(G g) {
        var G = geom();
        double lx = G.lx(), rx = G.rx(), fy = G.fy();
        g.drawSprite("game/rain", lx + 8, fy, 32, 32);
        g.drawText("Make it rain", lx + 46, fy + 6, WHITE, true);
        g.drawText("Players /pay the ticket price, one ticket wins the whole pot.", lx + 46, fy + 18, MUTE, false);
        double w3 = (int) ((rx - lx - 16 - 12) / 3), y = fy + 52;
        String[] lab = {"Ticket price", "Goal", "Your fee %"};
        for (int i = 0; i < 3; i++) g.drawText(lab[i], lx + 8 + i * (w3 + 6) + 2, y - 11, MUTE, false);
        // what these numbers give
        long tickets = RN.entry > 0 ? (RN.goal + RN.entry - 1) / RN.entry : 0, pot = tickets * RN.entry, cut = Math.round(pot * Math.max(0, RN.fee) / 100.0);
        if (tickets >= 2 && RN.fee >= 0 && RN.fee <= 50) {
            String a = tickets + " tickets fill the pot.", b = "The winner takes " + Money.format(pot - cut) + ", you keep " + Money.format(cut) + ".";
            g.drawText(a, lx + 8, y + 34, TEXT, false);
            g.drawText(b, lx + 8, y + 46, MUTE, false);
        }
        g.drawText("Every /pay of the exact price (or a multiple) is a ticket, not chips.", lx + 8, y + 70, DIM, false);
        g.drawText("Copy the announcement once it starts: nothing is sent for you.", lx + 8, y + 82, DIM, false);
    }

    private void renderOpen(G g, double mx, double my) {
        var G = geom();
        double lx = G.lx(), rx = G.rx(), fy = G.fy();
        long pot = RN.pot();
        String a = Money.format(pot);
        g.push(); g.translate(lx + 8, fy); g.scale(2, 2); gradientText(g, a, 0, 0, true); g.pop();
        g.drawText("of " + Money.format(RN.goal), lx + 8 + F.width(a) * 2 + 6, fy + 7, MUTE, false);
        int players = RN.tickets.size(), n = RN.count();
        String c = n + " ticket" + (n == 1 ? "" : "s") + ", " + players + " player" + (players == 1 ? "" : "s");
        g.drawText(c, rx - 8 - F.width(c), fy + 7, TEXT, false);
        // progress: a glass track, the Outmind gradient up to the pot
        double bx = lx + 8, bw = rx - lx - 16, by = fy + 22, k = Math.min(1, RN.goal > 0 ? (double) pot / RN.goal : 0);
        smooth(g, bx, by, bw, 6, 3, 0x22FFFFFF, 0x14FFFFFF);
        if (k > 0) smooth(g, bx, by, Math.max(6, bw * k), 6, 3, h(0xFFA18CD1, 0xFFFBC2EB), 0);
        // who is in: biggest first, his share of the tickets = his chance
        List<Map.Entry<String, Integer>> rows = new ArrayList<>(RN.tickets.entrySet());
        rows.sort((x, y) -> y.getValue() - x.getValue());
        double ly = fy + 36, lh = G.ft() + G.fth() - 6 - ly;
        if (rows.isEmpty()) {
            g.drawText("Waiting for the first ticket.", lx + 8, ly + 6, TEXT, false);
            g.drawText("Copy for chat, paste it, players /pay " + Money.format(RN.entry) + " to get in.", lx + 8, ly + 18, MUTE, false);
            return;
        }
        double off = screen.beginList(g, "rain", lx + 4, ly, rx - lx - 8, lh, rows.size() * 15);
        for (int i = 0; i < rows.size(); i++) {
            var e = rows.get(i);
            double y = ly + i * 15 - off;
            if (y + 15 < ly || y > ly + lh) continue;
            g.drawHead(e.getKey(), lx + 8, y + 3, 9);
            g.drawText(F.trimToWidth(e.getKey(), 120), lx + 21, y + 4, TEXT, false);
            String pct = Math.round(100.0 * e.getValue() / n) + "%", tk = "×" + e.getValue();
            g.drawText(pct, rx - 12 - F.width(pct), y + 4, PINK, false);
            g.drawText(tk, rx - 12 - 34 - F.width(tk), y + 4, MUTE, false);
        }
        screen.endList(g, "rain", rows.size() * 15);
    }

    private void renderReel(G g) {
        var G = geom();
        double lx = G.lx(), rx = G.rx(), fy = G.fy(), cx = G.cx();
        Round r = RN.round;
        if (r == null) return;
        long t = P.now();
        boolean done = RN.stage.equals("done");
        String head = Money.format(r.pot) + " pot, " + r.total + " tickets";
        g.drawText(head, cx - (F.width(head) >> 1), fy + 6, MUTE, false);
        double ry = fy + 28, rh = 58, x0 = lx + 6, x1 = rx - 6, pos = done ? r.land : RN.pos(t);
        rounded(g, x0, ry, x1 - x0, rh, 0x330A0710);
        g.enableScissor(x0, ry, x1, ry + rh);
        int first = Math.max(0, (int) Math.floor((pos - (cx - x0)) / CELL) - 1), last = Math.min(r.strip.size() - 1, (int) Math.ceil((pos + (x1 - cx)) / CELL) + 1);
        for (int i = first; i <= last; i++) {
            String name = r.strip.get(i);
            double x = Math.round(cx - pos + i * CELL);
            boolean win = done && i == WIN_AT;
            double k = win ? easeOut(Math.min(1, (t - r.landedAt) / 300.0)) : 0;
            smooth(g, x + 2, ry + 4, CELL - 4, rh - 8, 6, alpha(0xFFA18CD1, 0.07 + 0.25 * k), alpha(win ? 0xFFFBC2EB : 0xFFA18CD1, 0.25 + 0.6 * k));
            double hs = 18 * (1 + 0.15 * k);
            g.drawHead(name, x + (CELL - hs) / 2, ry + 10 - 0.15 * k * 9, hs);
            String nm = F.trimToWidth(name, CELL - 6);
            g.drawText(nm, x + ((CELL - F.width(nm)) >> 1), ry + rh - 18, win ? WHITE : MUTE, false);
        }
        smooth(g, x0, ry, 48, rh, 0, h(0xF21E1330, 0x001E1330));
        smooth(g, x1 - 48, ry, 48, rh, 0, h(0x001E1330, 0xF21E1330));
        g.disableScissor();
        g.fill(cx, ry - 3, cx + 1, ry + rh + 3, PINK);
        for (int i = 0; i < 4; i++) { g.fill(cx - 3 + i, ry - 6 + i, cx + 4 - i, ry - 5 + i, PINK); g.fill(cx - 3 + i, ry + rh + 5 - i, cx + 4 - i, ry + rh + 6 - i, PINK); }
        if (!done) return;
        double e = easeOut(Math.min(1, (t - r.landedAt) / 220.0));
        String word = r.winner + " wins " + Money.format(r.prize);
        double sc = 1.6 * (0.7 + 0.3 * e);
        g.push(); g.translate(cx, fy + 104); g.scale(sc, sc); gradientText(g, word, -(F.width(word) >> 1), -4, true, e); g.pop();
        String sub = "Ticket " + (r.ticket + 1) + " of " + r.total + ", you keep " + Money.format(r.cut);
        g.drawText(sub, cx - (F.width(sub) >> 1), fy + 120, alpha(MUTE, e), false);
    }
}
