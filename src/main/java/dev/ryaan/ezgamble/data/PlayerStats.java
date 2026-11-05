package dev.ryaan.ezgamble.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one player did with the gambler, across all sessions (prototype playerStats / allPlayers). The views ask
 * every frame and the history changes once per bet, so everything here is derived from one index built per
 * GambleData.ver: his lines by player (and by session), then of(), all() and highRollers() memoised on it.
 */
public final class PlayerStats {
    public record Ev(long t, long amount, String kind) {}   // kind: in / out
    public record Bj(int hands, long wagered, long net) {}

    public String name;
    public List<Ev> ev = new ArrayList<>();
    public int sessions;
    public long paid, sent, made, biggest;
    public Long first, last;
    public Bj bj;

    // ---- the index, one per version of the history

    private static int idxVer = -1;
    private static Map<String, List<Txn>> byPlayer;   // norm -> his lines, every session in order (first seen first: the podium's tie order)
    private static Map<Session, Map<String, List<Txn>>> bySession;   // the same per session (a Session compares by identity)
    private static Map<String, PlayerStats> ofCache;
    private static List<PlayerStats> allCache;
    private static List<Roller> rollersAll, rollersSession;

    private static void sync() {
        if (idxVer == GambleData.ver) return;
        idxVer = GambleData.ver;
        byPlayer = new LinkedHashMap<>(); bySession = new IdentityHashMap<>();
        for (Session s : GambleData.sessions) {
            Map<String, List<Txn>> m = new LinkedHashMap<>();
            for (Txn t : s.txns) if (t.player() != null) {
                String k = Payouts.norm(t.player());
                byPlayer.computeIfAbsent(k, x -> new ArrayList<>()).add(t);
                m.computeIfAbsent(k, x -> new ArrayList<>()).add(t);
            }
            bySession.put(s, m);
        }
        ofCache = new HashMap<>();
        allCache = null;
        rollersAll = rollersSession = null;
    }

    /** His lines (bets placed with his chips, rains, jackpots), every session in order. Nothing filtered by the cutoff. */
    public static List<Txn> txnsOf(String name) { sync(); return byPlayer.getOrDefault(Payouts.norm(name), List.of()); }

    /** His lines inside one session, in order. */
    public static List<Txn> txnsOf(String name, Session s) {
        sync();
        Map<String, List<Txn>> m = bySession.get(s);
        return m == null ? List.of() : m.getOrDefault(Payouts.norm(name), List.of());
    }

    public static PlayerStats of(String name) {
        sync();
        String k = Payouts.norm(name);
        PlayerStats c = ofCache.get(k);
        if (c == null) { c = build(name, k); ofCache.put(k, c); }
        return c;
    }

    private static PlayerStats build(String name, String k) {
        PlayerStats p = new PlayerStats();
        p.name = name;
        long cut = GambleData.cutoff(name);   // removed from the player database: only what came after counts
        for (Session s : GambleData.sessions) {
            boolean any = false;
            for (Session.Pay q : s.payments) if (q.t() > cut && Payouts.norm(q.name()).equals(k)) { p.ev.add(new Ev(q.t(), q.amount(), "in")); any = true; }
            for (Session.Pay q : s.sends) if (q.t() > cut && Payouts.norm(q.name()).equals(k)) { p.ev.add(new Ev(q.t(), q.amount(), "out")); any = true; }
            if (any) p.sessions++;
        }
        p.ev.sort(Comparator.comparingLong(Ev::t));
        for (Ev e : p.ev) { if (e.kind.equals("in")) { p.paid += e.amount; p.biggest = Math.max(p.biggest, e.amount); } else p.sent += e.amount; }
        p.made = p.paid - p.sent;
        int hands = 0; long wag = 0, net = 0;
        for (Txn t : byPlayer.getOrDefault(k, List.of())) if ("Blackjack".equals(t.game()) && t.t() > cut) { hands++; wag += t.bet() == null ? 0 : t.bet(); net -= t.delta(); }
        p.bj = new Bj(hands, wag, net);
        if (!p.ev.isEmpty()) { p.first = p.ev.get(0).t; p.last = p.ev.get(p.ev.size() - 1).t; }
        return p;
    }

    /** Every player who ever paid the gambler, biggest backer first. */
    public static List<PlayerStats> all() {
        sync();
        if (allCache != null) return allCache;
        Map<String, String> names = new LinkedHashMap<>();
        for (Session s : GambleData.sessions) for (Session.Pay q : s.payments) if (q.t() > GambleData.cutoff(q.name())) names.putIfAbsent(Payouts.norm(q.name()), q.name());
        List<PlayerStats> out = new ArrayList<>();
        for (String n : names.values()) out.add(of(n));
        out.sort(Comparator.comparingLong((PlayerStats x) -> -x.paid));
        allCache = List.copyOf(out);
        return allCache;
    }

    /** One high roller: what he wagered at the gambler's tables (bets placed with his chips). */
    public record Roller(String name, long wagered, int bets, long net) {}

    /** The biggest wagers, biggest first: "session" = the last session, "all" = every session. Removed players skip what came before. */
    public static List<Roller> highRollers(String range) {
        sync();
        boolean ses = range.equals("session");
        List<Roller> c = ses ? rollersSession : rollersAll;
        if (c != null) return c;
        Map<String, List<Txn>> src = byPlayer;
        if (ses) { Session l = GambleData.last(); Map<String, List<Txn>> m = l == null ? null : bySession.get(l); src = m == null ? Map.of() : m; }
        List<Roller> out = new ArrayList<>();
        for (List<Txn> lines : src.values()) {
            String name = null; long wagered = 0, net = 0, cut = -1; int bets = 0;
            for (Txn t : lines) {
                if (t.bet() == null || t.bet() <= 0) continue;
                if (cut < 0) cut = GambleData.cutoff(t.player());   // once per player, not per bet
                if (t.t() <= cut) continue;
                if (name == null) name = t.player();
                wagered += t.bet(); bets++; net -= t.delta();   // transactions are the house's side: flip
            }
            if (bets > 0) out.add(new Roller(name, wagered, bets, net));
        }
        out.sort(Comparator.comparingLong((Roller x) -> -x.wagered()));
        List<Roller> done = List.copyOf(out);
        if (ses) rollersSession = done; else rollersAll = done;
        return done;
    }
}
