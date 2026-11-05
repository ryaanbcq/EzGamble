package dev.ryaan.ezgamble.books;

import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.Money;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.games.coinflip.CoinTable;
import dev.ryaan.ezgamble.games.crash.CrashTable;
import dev.ryaan.ezgamble.games.crates.CrateTable;
import dev.ryaan.ezgamble.games.mines.MinesTable;
import dev.ryaan.ezgamble.proto.P;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The accounting side: every session's transactions tallied by table, by hour of the day, by day over the last five
 * weeks and by session, with the all-time totals and a rolling 7-day profit. Pure data computed from
 * {@link GambleData#sessions} and cached until something in the data changes (bets, sessions, the day). Transactions
 * are the house's side: a positive delta is money the house made. The exports (CSV) and the clipboard summary live
 * here too, so the view only draws.
 */
public final class Books {
    /** One table over every session: bets, what was wagered, what the house made, and the table's theoretical RTP (null = unknown, a server game). */
    public record GameRow(String game, int bets, long volume, long profit, Double theory) {
        /** What the players got back of what they wagered: 1 - profit / volume (NaN with no volume). */
        public double realised() { return volume <= 0 ? Double.NaN : 1 - (double) profit / volume; }
    }

    /** One session as a line of the ledger; end = now while it is still open. */
    public record SessionRow(Session session, long start, long end, int bets, long volume, long profit, int players, boolean live) {
        public long duration() { return Math.max(0, (live ? P.now() : end) - start); }
    }

    public static final int WEEKS = 5, DAYS = 7 * WEEKS;

    /** Tables by volume, the hottest first. */
    public final List<GameRow> games;
    /** Activity by hour of the day (local time): bets placed and house profit, 24 buckets. */
    public final int[] hourBets = new int[24];
    public final long[] hourProfit = new long[24];
    /** The hour with the most bets, -1 without any bet. */
    public final int bestHour;
    public final int hourMax;
    /** The calendar: DAYS days, Monday first, ending with the current week; index = (date - firstDay) in days. */
    public final LocalDate firstDay, today;
    public final int[] dayBets = new int[DAYS];
    public final long[] dayProfit = new long[DAYS];
    /** The biggest |profit| of a day: the heatmap's full tint. */
    public final long dayMax;
    public final int daysPlayed;
    /** The best day of the calendar (most profit), -1 without any bet. */
    public final int bestDay;
    /** Every session, the newest first. */
    public final List<SessionRow> sessions;
    /** The most and the least profitable sessions with bets in them, null without any. */
    public final SessionRow best, worst;
    /** All time: bets, distinct players who paid or played, what was wagered, what the house made, and the last 7 days' profit. */
    public final int bets, players;
    public final long volume, profit, week;

    private Books() {
        ZoneId z = ZoneId.systemDefault();
        today = LocalDate.now(z);
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1);
        firstDay = monday.minusWeeks(WEEKS - 1);
        long weekAgo = System.currentTimeMillis() - 7L * 86_400_000L;
        Map<String, long[]> gm = new LinkedHashMap<>();   // game -> {bets, volume, profit}
        Set<String> pl = new HashSet<>();
        List<SessionRow> sl = new ArrayList<>();
        int b = 0; long vol = 0, prof = 0, wk = 0;
        for (Session s : GambleData.sessions) {
            long sv = 0, sp = 0;
            Set<String> sPl = new HashSet<>();
            for (String n : s.credit.keySet()) sPl.add(Payouts.norm(n));
            for (String n : s.won.keySet()) sPl.add(Payouts.norm(n));
            for (Txn t : s.txns) {
                long bet = t.bet() != null ? t.bet() : Math.abs(t.delta());   // a server game line without a bet counts its swing, like Stats
                sv += bet; sp += t.delta();
                if (t.player() != null) sPl.add(Payouts.norm(t.player()));
                long[] r = gm.computeIfAbsent(t.game() == null ? "Game" : t.game(), k -> new long[3]);
                r[0]++; r[1] += bet; r[2] += t.delta();
                LocalDateTime d = LocalDateTime.ofInstant(Instant.ofEpochMilli(t.t()), z);
                hourBets[d.getHour()]++; hourProfit[d.getHour()] += t.delta();
                int di = (int) (d.toLocalDate().toEpochDay() - firstDay.toEpochDay());
                if (di >= 0 && di < DAYS) { dayBets[di]++; dayProfit[di] += t.delta(); }
                if (t.t() >= weekAgo) wk += t.delta();
            }
            pl.addAll(sPl);
            long end = s.end != null ? s.end : (s.txns.isEmpty() ? s.start : Math.max(s.start, s.txns.get(s.txns.size() - 1).t()));
            sl.add(new SessionRow(s, s.start, end, s.txns.size(), sv, sp, sPl.size(), s.end == null));
            b += s.txns.size(); vol += sv; prof += sp;
        }
        bets = b; volume = vol; profit = prof; week = wk; players = pl.size();
        List<GameRow> gl = new ArrayList<>();
        for (var e : gm.entrySet()) gl.add(new GameRow(e.getKey(), (int) e.getValue()[0], e.getValue()[1], e.getValue()[2], theory(e.getKey())));
        gl.sort(Comparator.comparingLong((GameRow r) -> -r.volume()).thenComparing(GameRow::game));
        games = List.copyOf(gl);
        int bh = -1, hm = 0;
        for (int h = 0; h < 24; h++) if (hourBets[h] > hm) { hm = hourBets[h]; bh = h; }
        bestHour = bh; hourMax = hm;
        long dm = 0; int dp = 0, bd = -1;
        for (int i = 0; i < DAYS; i++) {
            dm = Math.max(dm, Math.abs(dayProfit[i]));
            if (dayBets[i] > 0) { dp++; if (bd < 0 || dayProfit[i] > dayProfit[bd]) bd = i; }
        }
        dayMax = dm; daysPlayed = dp; bestDay = bd;
        Collections.reverse(sl);
        sessions = List.copyOf(sl);
        SessionRow be = null, wo = null;
        for (SessionRow r : sl) {
            if (r.bets() == 0) continue;
            if (be == null || r.profit() > be.profit()) be = r;
            if (wo == null || r.profit() < wo.profit()) wo = r;
        }
        best = be; worst = wo;
    }

    /** The RTP the mod's tables are built to return, read from the tables; null for a game the mod does not run (server games). */
    public static Double theory(String game) {
        return switch (game) {
            case "Coinflip" -> CoinTable.CF_PAY / 2;   // a fair coin paid x1.94
            case "Roulette" -> 36 / 37.0;   // the single zero
            case "Crash" -> 1 - CrashTable.CRASH_EDGE;
            case "Mines" -> 1 - MinesTable.MINES_EDGE;   // every cash-out point returns the same (MinesTable), so one number says it
            case "Crates" -> { double r = 0; for (CrateTable.CrateType c : CrateTable.CRATE_TYPES) r += c.rtp; yield r / 100 / CrateTable.CRATE_TYPES.size(); }   // rtp is in %, the average over the three crates
            case "Blackjack" -> 0.995;   // basic strategy against the mod's rules, about
            default -> null;
        };
    }

    // ---- cache: rebuilt when the data changed (a bet, a session, a payment) or the day turned

    private static Books cached;
    private static String key;

    public static Books get() {
        String k = keyNow();
        if (cached == null || !k.equals(key)) { cached = new Books(); key = k; }
        return cached;
    }

    private static String keyNow() {
        List<Session> ss = GambleData.sessions;
        int n = 0; long last = 0;
        for (Session s : ss) n += s.txns.size();
        Session l = ss.isEmpty() ? null : ss.get(ss.size() - 1);
        if (l != null && !l.txns.isEmpty()) last = l.txns.get(l.txns.size() - 1).t();
        return ss.size() + ":" + n + ":" + last + ":" + (l == null ? "" : l.end + ":" + l.credit.size() + ":" + l.won.size()) + ":" + LocalDate.now();
    }

    // ---- texts

    /** "96.1%" (one decimal), "97%" when whole; "-" for NaN or null. */
    public static String pct(Double v) {
        if (v == null || v.isNaN()) return "-";
        double p = Math.round(v * 1000) / 10.0;
        return (p == Math.rint(p) ? String.valueOf((long) p) : String.format(Locale.ROOT, "%.1f", p)) + "%";
    }

    /** "21:00 to 22:00". */
    public static String hourText(int h) { return P.pad2(h) + ":00 to " + P.pad2((h + 1) % 24) + ":00"; }

    /** "Mon 03/11". */
    public static String dayText(LocalDate d) {
        String n = d.getDayOfWeek().name();
        return n.charAt(0) + n.substring(1, 3).toLowerCase(Locale.ROOT) + " " + P.pad2(d.getDayOfMonth()) + "/" + P.pad2(d.getMonthValue());
    }

    public double realised() { return volume <= 0 ? Double.NaN : 1 - (double) profit / volume; }

    /** The plain-text totals for the clipboard (Copy totals): one paragraph, no markdown. */
    public String summary() {
        StringBuilder b = new StringBuilder();
        b.append("EzGamble books, ").append(P.DATE(System.currentTimeMillis())).append(": ");
        b.append(sessions.size()).append(sessions.size() == 1 ? " session, " : " sessions, ").append(bets).append(bets == 1 ? " bet, " : " bets, ");
        b.append(Money.format(volume)).append(" wagered, ").append(Money.signed(profit)).append(" house profit");
        if (bets > 0) b.append(", players got back ").append(pct(realised()));
        b.append(", ").append(players).append(players == 1 ? " player" : " players").append(".");
        if (best != null) b.append(" Best session ").append(P.DATE(best.start())).append(" ").append(Money.signed(best.profit()));
        if (worst != null) b.append(", worst ").append(P.DATE(worst.start())).append(" ").append(Money.signed(worst.profit()));
        if (best != null || worst != null) b.append(".");
        b.append(" Last 7 days ").append(Money.signed(week)).append(".");
        if (bestHour >= 0) b.append(" Busiest hour ").append(hourText(bestHour)).append(" (").append(hourBets[bestHour]).append(" bets).");
        if (!games.isEmpty()) {
            b.append(" Tables: ");
            for (int i = 0; i < games.size(); i++) {
                GameRow g = games.get(i);
                if (i > 0) b.append("; ");
                b.append(g.game()).append(" ").append(g.bets()).append(g.bets() == 1 ? " bet " : " bets ").append(Money.format(g.volume())).append(" wagered ").append(Money.signed(g.profit()))
                        .append(" (").append(pct(g.realised())).append(" / ").append(pct(g.theory())).append(")");
            }
            b.append(".");
        }
        return b.toString();
    }

    /** One line for the log (DebugRun books). */
    public String describe() {
        return "sessions " + sessions.size() + " bets " + bets + " volume " + volume + " profit " + profit + " players " + players + " week " + week
                + " rtp " + pct(realised()) + " bestHour " + bestHour + " daysPlayed " + daysPlayed + " games " + games.size()
                + (best == null ? "" : " best " + best.profit()) + (worst == null ? "" : " worst " + worst.profit());
    }

    // ---- exports: config/ezgamble/export/ezgamble-<stamp>.csv (one line per transaction) and -sessions.csv (one per session)

    public record Export(Path txns, Path sessions, int rows) {}

    private static final Path EXPORT = FabricLoader.getInstance().getConfigDir().resolve("ezgamble").resolve("export");
    private static final String TS = "yyyy-MM-dd HH:mm:ss";

    /** Writes both files; null when a write failed (logged). */
    public static Export export() {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date());
        SimpleDateFormat ts = new SimpleDateFormat(TS);
        StringBuilder a = new StringBuilder("session_start,time,game,player,bet,delta,raw\n");
        int rows = 0;
        for (Session s : GambleData.sessions) {
            String st = ts.format(new Date(s.start));
            for (Txn t : s.txns) {
                a.append(st).append(',').append(ts.format(new Date(t.t()))).append(',').append(csv(t.game())).append(',').append(csv(t.player()))
                        .append(',').append(t.bet() == null ? "" : String.valueOf(t.bet())).append(',').append(t.delta()).append(',').append(csv(t.raw())).append('\n');
                rows++;
            }
        }
        StringBuilder b = new StringBuilder("start,end,duration_s,bets,volume,profit,players,paid_in,sent_back,server\n");
        for (SessionRow r : get().sessions) {
            Session s = r.session();
            long in = 0, back = 0;
            for (long v : s.credit.values()) in += v;
            for (long v : s.sent.values()) back += v;
            b.append(ts.format(new Date(r.start()))).append(',').append(r.live() ? "" : ts.format(new Date(r.end()))).append(',').append(r.duration() / 1000)
                    .append(',').append(r.bets()).append(',').append(r.volume()).append(',').append(r.profit()).append(',').append(r.players())
                    .append(',').append(in).append(',').append(back).append(',').append(csv(s.server)).append('\n');
        }
        Path f1 = EXPORT.resolve("ezgamble-" + stamp + ".csv"), f2 = EXPORT.resolve("ezgamble-" + stamp + "-sessions.csv");
        if (!write(f1, a.toString()) || !write(f2, b.toString())) return null;
        return new Export(f1, f2, rows);
    }

    /** A CSV field: quoted when it holds a comma, a quote or a line break; null = empty. */
    private static String csv(String s) {
        if (s == null) return "";
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0 && s.indexOf('\r') < 0) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** Atomic like GambleData.write: the tmp file, then one move. */
    private static boolean write(Path p, String text) {
        try {
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception e) {
            EzGambleMod.LOG.warn("[ezgamble] export {} failed: {}", p, e.toString());
            return false;
        }
    }
}
