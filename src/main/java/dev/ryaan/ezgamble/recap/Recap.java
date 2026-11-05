package dev.ryaan.ezgamble.recap;

import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Stats;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.proto.P;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The story of one session, as numbers: everything the recap screen shows, computed once from a {@link Session} (any
 * session, so an old one can be replayed). Pure data, nothing drawn here. Transactions are the house's side: a positive
 * delta is money the house made.
 */
public final class Recap {
    /** When the last session closed with bets in it (the HUD hint "Session over"), -1 = none. Set by GambleData.setMode. */
    public static long readyAt = -1;

    /** One game of the session: how many bets, what was wagered, what the house made there. */
    public record GameRow(String game, int bets, long volume, long profit) {}
    /** A player who bet his chips at the tables: what he wagered, how many bets, his net (his point of view). */
    public record Roller(String name, long wagered, int bets, long net) {}
    /** One payment received. */
    public record Pay(String name, long amount) {}

    public final Session session;
    public final long start, end, duration;
    public final String server;
    public final int bets;
    public final Stats stats;
    /** The house's deltas in order (the curve). */
    public final long[] deltas;
    /** Highest and lowest point of the cumulative profit curve. */
    public final long peak, trough;
    /** Games by volume, the hottest first. */
    public final List<GameRow> games;
    /** Players by wagered, the high roller first. */
    public final List<Roller> rollers;
    /** Players who paid chips in this session (in the order they first paid), and the money between you and them. */
    public final List<String> players;
    public final long paidIn, sentBack, owed;
    /** Longest run of house wins and of house losses, in bets. */
    public final int winStreak, lossStreak;
    /** The biggest single payment received, null when nobody paid. */
    public final Pay biggestPay;

    private Recap(Session s) {
        session = s;
        start = s.start;
        end = s.end != null ? s.end : (s.txns.isEmpty() ? P.now() : Math.max(P.now(), s.txns.get(s.txns.size() - 1).t()));
        duration = Math.max(0, end - start);
        server = s.server == null ? "" : s.server;
        List<Txn> tx = s.txns;
        bets = tx.size();
        stats = Stats.of(tx);
        deltas = new long[bets];
        long cum = 0, pk = 0, tr = 0;
        for (int i = 0; i < bets; i++) { deltas[i] = tx.get(i).delta(); cum += deltas[i]; pk = Math.max(pk, cum); tr = Math.min(tr, cum); }
        peak = pk; trough = tr;
        // games
        Map<String, long[]> gm = new LinkedHashMap<>();   // game -> {bets, volume, profit}
        for (Txn t : tx) {
            long[] r = gm.computeIfAbsent(t.game() == null ? "Game" : t.game(), k -> new long[3]);
            r[0]++; r[1] += t.bet() != null ? t.bet() : Math.abs(t.delta()); r[2] += t.delta();
        }
        List<GameRow> gl = new ArrayList<>();
        for (var e : gm.entrySet()) gl.add(new GameRow(e.getKey(), (int) e.getValue()[0], e.getValue()[1], e.getValue()[2]));
        gl.sort(Comparator.comparingLong((GameRow r) -> -r.volume()).thenComparing(GameRow::game));
        games = List.copyOf(gl);
        // high rollers of this session (removed players skip what came before, like PlayerStats.highRollers)
        Map<String, Object[]> rm = new LinkedHashMap<>();
        for (Txn t : tx) {
            if (t.player() == null || t.bet() == null || t.bet() <= 0 || t.t() <= GambleData.cutoff(t.player())) continue;
            Object[] r = rm.computeIfAbsent(Payouts.norm(t.player()), k -> new Object[]{t.player(), 0L, 0, 0L});
            r[1] = (long) r[1] + t.bet(); r[2] = (int) r[2] + 1; r[3] = (long) r[3] - t.delta();
        }
        List<Roller> rl = new ArrayList<>();
        for (Object[] r : rm.values()) rl.add(new Roller((String) r[0], (long) r[1], (int) r[2], (long) r[3]));
        rl.sort(Comparator.comparingLong((Roller r) -> -r.wagered()).thenComparing(Roller::name));
        rollers = List.copyOf(rl);
        // the table: who paid, what went in, what went back, what is still owed (chips = credit + won - sent, never negative)
        List<String> pl = new ArrayList<>(s.credit.keySet());
        for (String n : s.won.keySet()) if (!pl.contains(n)) pl.add(n);
        players = List.copyOf(pl);
        long in = 0, back = 0, ow = 0;
        for (String n : pl) {
            long c = s.credit.getOrDefault(n, 0L), w = s.won.getOrDefault(n, 0L), st = s.sent.getOrDefault(n, 0L);
            in += c; back += st; ow += Math.max(0, c + w - st);
        }
        paidIn = in; sentBack = back; owed = ow;
        // streaks
        int ws = 0, ls = 0, w = 0, l = 0;
        for (Txn t : tx) {
            if (t.delta() > 0) { w++; l = 0; } else if (t.delta() < 0) { l++; w = 0; } else { w = 0; l = 0; }
            ws = Math.max(ws, w); ls = Math.max(ls, l);
        }
        winStreak = ws; lossStreak = ls;
        Pay bp = null;
        for (Session.Pay p : s.payments) if (!p.bid() && (bp == null || p.amount() > bp.amount())) bp = new Pay(p.name(), p.amount());
        biggestPay = bp;
    }

    public static Recap of(Session s) { return new Recap(s); }

    /** The last session, or null when there is none. */
    public static Recap last() { Session s = GambleData.last(); return s == null ? null : new Recap(s); }

    public boolean live() { return session.end == null; }
    public GameRow hottest() { return games.isEmpty() ? null : games.get(0); }
    public Roller mvp() { return rollers.isEmpty() ? null : rollers.get(0); }

    /** "05/11 21:14". */
    public String dateText() { return P.DATE(start); }
    /** "1:32:10". */
    public String durationText() { return P.clock(duration); }

    /** One line, plain text, for the clipboard (no markdown). */
    public String summary() {
        StringBuilder b = new StringBuilder();
        b.append("EzGamble session recap, ").append(dateText()).append(", ").append(durationText());
        b.append(" · Profit ").append(Money.signed(stats.profit()));
        b.append(" · ").append(bets).append(bets == 1 ? " bet, " : " bets, ").append(Money.format(stats.volume())).append(" volume");
        if (bets > 0) b.append(", ").append(Math.round(stats.rate() * 100)).append("% win rate");
        GameRow h = hottest();
        if (h != null) b.append(" · Hottest table ").append(h.game());
        Roller m = mvp();
        if (m != null) b.append(" · High roller ").append(m.name()).append(" (").append(Money.format(m.wagered())).append(" wagered)");
        b.append(" · ").append(players.size()).append(players.size() == 1 ? " player, " : " players, ").append(Money.format(paidIn)).append(" in, ")
                .append(Money.format(sentBack)).append(" back, ").append(Money.format(owed)).append(" owed");
        return b.toString();
    }
}
