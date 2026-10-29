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
        for (Session s : GambleData.sessions) {
            boolean any = false;
            for (Session.Pay q : s.payments) if (Payouts.same(q.name(), name)) { p.ev.add(new Ev(q.t(), q.amount(), "in")); any = true; }
            for (Session.Pay q : s.sends) if (Payouts.same(q.name(), name)) { p.ev.add(new Ev(q.t(), q.amount(), "out")); any = true; }
            if (any) p.sessions++;
        }
        p.ev.sort(Comparator.comparingLong(Ev::t));
        for (Ev e : p.ev) { if (e.kind.equals("in")) { p.paid += e.amount; p.biggest = Math.max(p.biggest, e.amount); } else p.sent += e.amount; }
        p.made = p.paid - p.sent;
        int hands = 0; long wag = 0, net = 0;
        for (Txn t : GambleData.all()) if ("Blackjack".equals(t.game()) && t.player() != null && Payouts.same(t.player(), name)) { hands++; wag += t.bet() == null ? 0 : t.bet(); net -= t.delta(); }
        p.bj = new Bj(hands, wag, net);
        if (!p.ev.isEmpty()) { p.first = p.ev.get(0).t; p.last = p.ev.get(p.ev.size() - 1).t; }
        return p;
    }

    /** Every player who ever paid the gambler, biggest backer first. */
    public static List<PlayerStats> all() {
        Map<String, String> names = new LinkedHashMap<>();
        for (Session s : GambleData.sessions) for (Session.Pay q : s.payments) names.putIfAbsent(Payouts.norm(q.name()), q.name());
        List<PlayerStats> out = new ArrayList<>();
        for (String n : names.values()) out.add(of(n));
        out.sort(Comparator.comparingLong((PlayerStats x) -> -x.paid));
        return out;
    }
}
