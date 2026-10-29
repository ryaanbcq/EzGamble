package dev.ryaan.ezgamble.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything one player did with the gambler, across all sessions (prototype playerStats / allPlayers). */
public final class PlayerStats {
    public record Ev(long t, long amount, String kind) {}   // kind: in / out
    public record Bj(int hands, long wagered, long net) {}

    public String name;
    public List<Ev> ev = new ArrayList<>();
    public int sessions;
    public long paid, sent, made, biggest;
    public Long first, last;
    public Bj bj;

    public static PlayerStats of(String name) {
        PlayerStats p = new PlayerStats();
        p.name = name;
        long cut = GambleData.cutoff(name);   // removed from the player database: only what came after counts
        for (Session s : GambleData.sessions) {
            boolean any = false;
            for (Session.Pay q : s.payments) if (q.t() > cut && Payouts.same(q.name(), name)) { p.ev.add(new Ev(q.t(), q.amount(), "in")); any = true; }
            for (Session.Pay q : s.sends) if (q.t() > cut && Payouts.same(q.name(), name)) { p.ev.add(new Ev(q.t(), q.amount(), "out")); any = true; }
            if (any) p.sessions++;
        }
        p.ev.sort(Comparator.comparingLong(Ev::t));
        for (Ev e : p.ev) { if (e.kind.equals("in")) { p.paid += e.amount; p.biggest = Math.max(p.biggest, e.amount); } else p.sent += e.amount; }
        p.made = p.paid - p.sent;
        int hands = 0; long wag = 0, net = 0;
        for (Txn t : GambleData.all()) if ("Blackjack".equals(t.game()) && t.t() > cut && t.player() != null && Payouts.same(t.player(), name)) { hands++; wag += t.bet() == null ? 0 : t.bet(); net -= t.delta(); }
        p.bj = new Bj(hands, wag, net);
        if (!p.ev.isEmpty()) { p.first = p.ev.get(0).t; p.last = p.ev.get(p.ev.size() - 1).t; }
        return p;
    }

    /** Every player who ever paid the gambler, biggest backer first. */
    public static List<PlayerStats> all() {
        Map<String, String> names = new LinkedHashMap<>();
        for (Session s : GambleData.sessions) for (Session.Pay q : s.payments) if (q.t() > GambleData.cutoff(q.name())) names.putIfAbsent(Payouts.norm(q.name()), q.name());
        List<PlayerStats> out = new ArrayList<>();
        for (String n : names.values()) out.add(of(n));
        out.sort(Comparator.comparingLong((PlayerStats x) -> -x.paid));
        return out;
    }

    /** One high roller: what he wagered at the gambler's tables (bets placed with his chips). */
    public record Roller(String name, long wagered, int bets, long net) {}

    /** The biggest wagers, biggest first: "session" = the last session, "all" = every session. Removed players skip what came before. */
    public static List<Roller> highRollers(String range) {
        Map<String, Object[]> m = new LinkedHashMap<>();   // norm -> {name, wagered, bets, net}
        List<Txn> txns = range.equals("session") ? (GambleData.last() == null ? List.of() : GambleData.last().txns) : GambleData.all();
        for (Txn t : txns) {
            if (t.player() == null || t.bet() == null || t.bet() <= 0 || t.t() <= GambleData.cutoff(t.player())) continue;
            Object[] r = m.computeIfAbsent(Payouts.norm(t.player()), k -> new Object[]{t.player(), 0L, 0, 0L});
            r[1] = (long) r[1] + t.bet(); r[2] = (int) r[2] + 1; r[3] = (long) r[3] - t.delta();   // transactions are the house's side: flip
        }
        List<Roller> out = new ArrayList<>();
        for (Object[] r : m.values()) out.add(new Roller((String) r[0], (long) r[1], (int) r[2], (long) r[3]));
        out.sort(Comparator.comparingLong((Roller x) -> -x.wagered()));
        return out;
    }
}
