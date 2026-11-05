package dev.ryaan.ezgamble.ui;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.PlayerStats;
import dev.ryaan.ezgamble.games.GameView;
import dev.ryaan.ezgamble.proto.*;
import dev.ryaan.ezgamble.vip.Vip;
import dev.ryaan.ezgamble.vip.VipBadge;

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
        payBtn = null;
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
            // VIP cashback owed: one click sends it all (one /pay, confirmed by the server, never his chips)
            long owed = Vip.owed(player);
            if (owed > 0) {
                String lbl = "Pay cashback " + Money.format(owed);
                double bw = F.width(lbl) + 16, rx = s.x0 + GambleScreen.W - GambleScreen.PAD;
                payBtn = s.add(new GlassButton(rx - bw, vipRowY() - 2, bw, 14, lbl, "PRIMARY", () -> { if (!Vip.pay(player)) P.play("fail", 1, 0.5); }));
                payBtn.active = !Vip.paying(player) && dev.ryaan.ezgamble.data.Payouts.pending.stream().noneMatch(p -> dev.ryaan.ezgamble.data.Payouts.same(p.name(), player));
            }
            // the badges row (under the loyalty row) opens the whole catalogue, the player kept (achievements.BadgesView)
            s.add(dev.ryaan.ezgamble.games.Widgets.area(s.x0 + GambleScreen.PAD, badgesRowY(), GambleScreen.W - 2 * GambleScreen.PAD, 16, () -> { P.play("click"); GambleScreen.sub = "badges"; s.rebuild(); }));
        }
    }

    /** The badges row sits under the loyalty row (or where it would be when he has no tier). */
    private double badgesRowY() { return vipRowY() + (Vip.tier(player) != null ? 19 : 0); }

    private static GlassButton payBtn;
    private static int vipVer = Vip.ver;
    /** The XP row sits under the stats card (and under the Blackjack line when there is one). */
    private double vipRowY() { return s.y0 + 80 + 22 + 38 + (dev.ryaan.ezgamble.data.PlayerStats.of(player).bj.hands() > 0 ? 19 : 0); }

    private static int payVer = dev.ryaan.ezgamble.data.Payouts.ver;
    @Override public void tick() {   // a cashback or a chips /pay to him changes the button
        if (vipVer != Vip.ver || payVer != dev.ryaan.ezgamble.data.Payouts.ver) { vipVer = Vip.ver; payVer = dev.ryaan.ezgamble.data.Payouts.ver; s.rebuild(); }
    }
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
        Vip.Tier tier = Vip.tier(player);   // named on the loyalty row below (the header has the Remove button and the pills)
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
        if (tier != null) {   // the loyalty row: tier, wagered, the bar to the next tier, the cashback
            Vip.Tier nx = Vip.next(player);
            String line = tier.name() + " · " + Money.format(Vip.xp(player)) + " wagered" + (nx == null ? ", top tier" : " · " + Money.format(nx.from() - Vip.xp(player)) + " to " + nx.name());
            double lineW = (payBtn != null ? payBtn.x : rx) - 8 - (lx + 18);
            VipBadge.draw(g, tier, lx + 8, y + 2); g.drawText(F.trimToWidth(line, lineW), lx + 18, y + 2, P.TEXT, false);   // never under the cashback button
            double bx = lx + 8, bw2 = (payBtn != null ? payBtn.x - 8 : rx - 8) - bx;
            g.fill(bx, y + 12, bx + bw2, y + 15, 0x1AFFFFFF);
            int fw = (int) Math.round(bw2 * Vip.progress(player));
            if (fw > 0) g.fill(bx, y + 12, bx + fw, y + 15, nx == null ? tier.color() : P.lerp(tier.color(), nx.color(), 0.5));
            if (payBtn == null && Vip.paidCashback(player) > 0) { String pd = "Cashback paid " + Money.format(Vip.paidCashback(player)); g.drawText(pd, rx - 8 - F.width(pd), y + 2, P.MUTE, false); }
            y += 19;
        }
        // the badges row: "Badges N / 24" and the 24 icons (earned lit, locked faint); hovering one names it in a tooltip drawn last
        dev.ryaan.ezgamble.achievements.Achievement badgeHover = dev.ryaan.ezgamble.achievements.BadgeIcons.row(g, player, lx, rx, y, mx, my);
        y += 16;
        // what you made with him over time: each payment in goes up, each send back goes down (16 px shorter: the badges row)
        double ch = (st.bj.hands() > 0 ? 44 : 54) - (tier != null ? 14 : 0) - 16;
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
        if (badgeHover != null) dev.ryaan.ezgamble.achievements.BadgeIcons.tooltip(g, badgeHover, dev.ryaan.ezgamble.achievements.BadgeIcons.earnedAt(player, badgeHover), mx, my, lx, rx, s.y0 + 30);
    }
}
