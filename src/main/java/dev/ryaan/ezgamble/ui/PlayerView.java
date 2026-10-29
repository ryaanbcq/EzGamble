package dev.ryaan.ezgamble.ui;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.PlayerStats;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.proto.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Player profile: totals, the money between you two over time, and every payment both ways (prototype initPlayer / renderPlayer). */
public final class PlayerView implements GameView {
    /** The player whose profile is open (set by the Chips view). */
    public static String player;
    /** Where ‹ goes back to: the Chips list, or the High rollers podium. */
    public static String backTo = "chips";
    private static long removeArmed;
    private static GlassButton removeBtn;

    private final GambleScreen s;

    public PlayerView(GambleScreen s) { this.s = s; }

    @Override
    public void init() {
        s.add(new GlassButton(s.x0 + GambleScreen.PAD, s.y0 + 80, 18, 16, "‹", "GHOST", () -> { GambleScreen.sub = backTo; backTo = "chips"; s.rebuild(); }));
        // Remove from the player database: first click arms for 3 s, the second removes (he comes back fresh if he pays again)
        if (player != null) {
            double nx = s.x0 + GambleScreen.PAD + 45 + F.width(player) + 8;
            removeBtn = s.add(new GlassButton(nx, s.y0 + 81, 52, 14, removeArmed > P.now() ? "Sure?" : "Remove", "DANGER", () -> {
                if (P.now() < removeArmed) {
                    removeArmed = 0;
                    dev.ryaan.ezgamble.data.GambleData.forget(player);
                    P.play("click");
                    GambleScreen.sub = backTo; backTo = "chips"; player = null; s.rebuild();
                } else { removeArmed = P.now() + 3000; removeBtn.label = "Sure?"; }
            }));
        }
    }

    @Override public void tick() {}
    @Override public void leave() {}

    @Override
    public void render(G g, double mx, double my) {
        if (removeArmed != 0 && P.now() > removeArmed) { removeArmed = 0; if (removeBtn != null) removeBtn.label = "Remove"; }
        if (player == null) return;
        double lx = s.x0 + GambleScreen.PAD, rx = s.x0 + GambleScreen.W - GambleScreen.PAD;
        PlayerStats st = PlayerStats.of(player);
        double y = s.y0 + 80;
        g.drawHead(P.skinOf(st.name), lx + 24, y, 16);
        P.gradientText(g, st.name, lx + 45, y + 4, true);
        P.pillsRight(g, rx, y, new Object[][]{{"Sessions", String.valueOf(st.sessions)}, {"Since", st.first != null ? P.DATE(st.first).substring(0, 5) : "-"}});
        y += 22;
        P.card(g, lx, y, rx - lx, 34, "soft");
        double q = (int) ((rx - lx - 10) / 4);
        P.tile(g, lx + 10, y + 6, "Paid you", Money.format(st.paid), P.WHITE);
        P.tile(g, lx + 10 + q, y + 6, "Sent back", Money.format(st.sent), P.WHITE);
        P.tile(g, lx + 10 + 2 * q, y + 6, "You made", Money.signed(st.made), st.made >= 0 ? P.GREEN : P.RED);
        P.tile(g, lx + 10 + 3 * q, y + 6, "Biggest pay", st.biggest > 0 ? Money.format(st.biggest) : "-", st.biggest > 0 ? P.WHITE : P.DIM);
        y += 38;
        if (st.bj.hands() > 0) {   // his chips at the mod's tables (the chart below stays about the money between you two)
            g.drawText("Blackjack", lx + 8, y + 4, P.TEXT, false);
            long net = st.bj.net();
            P.pillsRight(g, rx, y, new Object[][]{{"Hands", String.valueOf(st.bj.hands())}, {"Wagered", Money.format(st.bj.wagered())}, {"Net", Money.signed(net), net > 0 ? P.GREEN : net < 0 ? P.RED : P.WHITE}});
            y += 19;
        }
        // what you made with him over time: each payment in goes up, each send back goes down
        double ch = st.bj.hands() > 0 ? 44 : 54;
        P.card(g, lx, y, rx - lx, ch, "chart");
        if (!st.ev.isEmpty()) {
            long[] d = new long[st.ev.size()];
            for (int i = 0; i < d.length; i++) { PlayerStats.Ev e = st.ev.get(i); d[i] = e.kind().equals("in") ? e.amount() : -e.amount(); }
            float reveal = (float) P.easeOut((P.now() - s.chartAt) / 600.0);
            ProfitChart.Result r = ProfitChart.draw(g.d, d, (int) lx, (int) y, (int) (rx - lx), (int) ch, (int) mx, (int) my, false, 0, true, reveal);
            if (r.hover() > 0) {
                PlayerStats.Ev e = st.ev.get(r.hover() - 1);
                boolean in = e.kind().equals("in");
                ProfitChart.tooltip(g.d, in ? "Paid you" : "Sent back", Money.format(e.amount()), in ? P.GREEN : P.RED, r.px()[r.hover()], (int) y, (int) lx, (int) rx,
                        P.DATE(e.t()), "Total " + Money.signed(r.cum()[r.hover()]));
            }
        }
        y += ch + 4;
        // history, newest first
        List<PlayerStats.Ev> list = new ArrayList<>(st.ev);
        Collections.reverse(list);
        String lid = "player-" + st.name;
        double lh = s.y0 + GambleScreen.H - GambleScreen.PAD - y, content = list.size() * 18 - 2;
        double off = s.beginList(g, lid, lx, y, rx - lx, lh, content);
        for (int i = 0; i < list.size(); i++) {
            PlayerStats.Ev e = list.get(i);
            double ry = y + i * 18 - off;
            if (ry + 16 < y || ry > y + lh) continue;
            boolean inn = e.kind().equals("in");
            P.card(g, lx, ry, rx - lx, 16, "row");
            g.drawText(inn ? "↓" : "↑", lx + 6, ry + 4, inn ? P.GREEN : P.RED, false);
            g.drawText(inn ? "Paid you" : "You sent back", lx + 16, ry + 4, P.TEXT, false);
            String v = (inn ? "+" : "-") + Money.format(e.amount());
            g.drawText(v, lx + 150 - F.width(v), ry + 4, inn ? P.GREEN : P.RED, false);
            String dd = P.DATE(e.t());
            g.drawText(dd, rx - 10 - F.width(dd), ry + 4, P.DIM, false);
        }
        s.endList(g, lid, content);
    }
}
