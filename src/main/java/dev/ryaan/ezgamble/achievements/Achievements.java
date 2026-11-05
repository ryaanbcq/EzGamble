package dev.ryaan.ezgamble.achievements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ryaan.ezgamble.EzGambleMod;
import dev.ryaan.ezgamble.data.GambleData;
import dev.ryaan.ezgamble.data.Payouts;
import dev.ryaan.ezgamble.data.PlayerStats;
import dev.ryaan.ezgamble.data.Session;
import dev.ryaan.ezgamble.data.Txn;
import dev.ryaan.ezgamble.hud.Banners;
import dev.ryaan.ezgamble.proto.P;
import dev.ryaan.ezgamble.vip.Vip;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Player achievements: 24 badges a player earns at the host's tables. Everything is DERIVED from the history (the
 * transactions, the payments and sends of every session, the VIP tier, the auction history): nothing is stored except
 * the seen set (config/ezgamble/achievements.json: player -> badge id -> unlock time), so a badge once earned stays
 * even when the condition stops holding (High Roller overtaken). The hooks (GambleData.record, Payments.received)
 * recompute the player's set, compare it with the seen set, persist and celebrate what is new: sound, overlay message
 * in the rarity colour, a HUD toast (hud.Banners). Transactions are the house side: his win is delta < 0.
 */
public final class Achievements {
    private Achievements() {}

    public record Earned(Achievement achievement, long at) {}
    /** One unlock across players, newest first in recent(). */
    public record Recent(String name, Achievement achievement, long at) {}

    public static final int TOTAL = 24;
    private static final long WHALE = 100_000_000L, SPENDER = 1_000_000_000L, COMEBACK = 50_000_000L;

    // the six host tables (Rain is a prize draw, Auction a sale: counted apart)
    private static final Set<String> TABLES = Set.of("Blackjack", "Roulette", "Crash", "Mines", "Crates", "Coinflip");

    // ---- the catalogue (DonutSMP scale: 1M is small, 1B is huge)

    private static final List<Achievement> CATALOGUE = List.of(
            new Achievement("first_blood", "First Blood", "Placed a first bet at your tables", "common", 0xFFFF5C5C, 0xFFFFFFFF, new String[]{
                    "....A....", "....A....", "...AAA...", "..AAAAA..", "..ACAAA..", ".AACAAAA.", ".AAAAAAA.", "..AAAAB..", "...BBB..."}),
            new Achievement("regular", "Regular", "Played or paid in 5 sessions", "common", 0xFFFF8A8A, 0xFFF4F0FF, new String[]{
                    ".D.....D.", "AAAAAAAAA", "AAAAAAAAA", "DDDDDDDDD", "DEDEDEDED", "DDDDDDDDD", "DEDEDEDED", "DDDDDDDDD", "........."}),
            new Achievement("night_owl", "Night Owl", "A bet between 2 and 5 in the morning", "rare", 0xFFFFE08A, 0xFFFFFFFF, new String[]{
                    "..AAAA..D", ".AAA.....", "AAA......", "AAC......", "AAC....D.", "AAA......", "AAA......", ".AAA.....", "..AAAA..."}),
            new Achievement("early_bird", "Early Bird", "A bet before 8 in the morning", "rare", 0xFFFFC94D, 0xFFFFFFFF, new String[]{
                    "....A....", ".A.....A.", "...AAA...", "..ACCAA..", "A.ACCAA.A", "..AAAAA..", "...AAA...", ".A.....A.", "....A...."}),
            new Achievement("whale", "Whale", "A single bet of 100M or more", "epic", 0xFF5AA9FF, 0xFFFFFFFF, new String[]{
                    ".........", "......A.A", ".AAAAA.AA", "AAAAAAAAA", "ACAAAAAA.", "AAAAAAAA.", ".AAAAA...", "..A..A...", "........."}),
            new Achievement("big_spender", "Big Spender", "1B wagered at your tables", "epic", 0xFFF2C14E, 0xFFFFFFFF, new String[]{
                    "..AAAAA..", ".ACAAAAB.", ".AAAAAAB.", "..BBBBB..", "..AAAAA..", ".ACAAAAB.", ".AAAAAAB.", "..BBBBB..", "........."}),
            new Achievement("hot_streak", "Hot Streak", "5 wins in a row", "rare", 0xFFFF7A3D, 0xFFFFE08A, new String[]{
                    "....A....", "...AA....", "...AAA...", "..AAAA...", "..AAAAA..", ".AADDAAA.", ".AADDDAA.", ".AAADDAA.", "..AAAAA.."}),
            new Achievement("cold_streak", "Cold Streak", "7 losses in a row", "rare", 0xFF8FE3FF, 0xFFFFFFFF, new String[]{
                    "....A....", ".A..A..A.", "..A.A.A..", "...AAA...", "AAAACAAAA", "...AAA...", "..A.A.A..", ".A..A..A.", "....A...."}),
            new Achievement("comeback", "Comeback", "Down 50M in a session, positive at its end", "epic", 0xFF4BE07A, 0xFFFFFFFF, new String[]{
                    "......AAA", ".......AA", "......A.A", ".....A...", "A...A....", "AA.A.....", ".AAA.....", "..A......", "........."}),
            new Achievement("jackpot", "Jackpot", "A win paying 10x the bet or more", "epic", 0xFFFFD36B, 0xFFFFFFFF, new String[]{
                    "....A....", "...AAA...", ".AAACAAA.", "..AACAA..", "AAACCCAAA", "..AACAA..", ".AAACAAA.", "...AAA...", "....A...."}),
            new Achievement("natural", "Natural", "A Blackjack paying 1.5x", "common", 0xFFFF5C5C, 0xFFF4F0FF, new String[]{
                    ".DDDDDDD.", ".DADDDDD.", ".DDDDDDD.", ".DDDAADD.", ".DDDAADD.", ".DDDDDDD.", ".DDDDDAD.", ".DDDDDDD.", "........."}),
            new Achievement("lucky_seven", "Lucky Seven", "7 Coinflip wins in a row", "epic", 0xFF4BE07A, 0xFF8A5A2B, new String[]{
                    ".AAA.AAA.", ".AAAAAAA.", ".ACAAACA.", "..AAAAA..", ".AAAAAAA.", ".ACAAACA.", ".AAA.AAA.", "....D....", "...D....."}),
            new Achievement("marathon", "Marathon", "100 bets in one session", "rare", 0xFFC7CCD6, 0xFFA18CD1, new String[]{
                    "...DDD...", "....D....", "..AAAAA..", ".AAACAAA.", ".AAACAAA.", ".AAAACCA.", ".AAAAAAA.", "..AAAAA..", "........."}),
            new Achievement("all_tables", "All Tables", "Played the six tables", "rare", 0xFF1A1226, 0xFFF4F0FF, new String[]{
                    ".........", ".DDDDDDD.", ".DADDDAD.", ".DDDDDDD.", ".DDDADDD.", ".DDDDDDD.", ".DADDDAD.", ".DDDDDDD.", "........."}),
            new Achievement("loyal", "Loyal", "Reached Gold VIP", "rare", 0xFFF2C14E, 0xFFFFFFFF, new String[]{
                    ".........", ".AA...AA.", "ACAA.AAAA", "AAAAAAAAA", "AAAAAAAAA", ".AAAAAAA.", "..AAAAA..", "...AAA...", "....A...."}),
            new Achievement("diamond_hands", "Diamond Hands", "Reached Diamond VIP", "legendary", 0xFFC77DFF, 0xFFFFFFFF, new String[]{
                    ".........", "..ACAAA..", ".ACAAAAA.", "ACAAAAAAA", ".BBBBBBB.", "..BBBBB..", "...BBB...", "....B....", "........."}),
            new Achievement("rainmaker", "Rainmaker", "Won a Rain", "rare", 0xFF5AA9FF, 0xFFF4F0FF, new String[]{
                    "..DDDD...", ".DDDDDDD.", "DDDDDDDDD", "DDDDDDDDD", ".DDDDDDD.", ".A..A..A.", ".........", "A..A..A..", "........."}),
            new Achievement("auctioneer", "Auctioneer", "Won an Auction", "rare", 0xFF6B5B95, 0xFFCD8B5A, new String[]{
                    "..AAA....", ".AAAAA...", "..AAAD...", "...ADD...", "....DD...", ".....DD..", "......DD.", ".......D.", "........."}),
            new Achievement("high_roller", "High Roller", "First on the all-time podium", "legendary", 0xFFF2C14E, 0xFFFF5C5C, new String[]{
                    ".........", "A...A...A", "AA.AAA.AA", "AAAAAAAAA", "AAAAAAAAA", "AADAAADAA", "AAAAAAAAA", "BBBBBBBBB", "........."}),
            new Achievement("settled", "Settled", "Chips fully sent back at the end of 10 sessions", "epic", 0xFF4BE07A, 0xFFFFFFFF, new String[]{
                    ".........", ".......A.", "......AA.", ".....AA..", "A...AA...", "AA.AA....", ".AAA.....", "..A......", "........."}),
            new Achievement("weekend_warrior", "Weekend Warrior", "Bets on a Saturday and the Sunday after", "rare", 0xFFC7CCD6, 0xFFCD8B5A, new String[]{
                    ".......AA", "......AAA", ".....AAA.", "....AAA..", ".D.AAA...", "..DAA....", ".DDD.....", "DD.D.....", "D........"}),
            new Achievement("generous", "Generous", "Paid you 10 times", "common", 0xFFF2C14E, 0xFFFBC2EB, new String[]{
                    "..A...A..", ".AAA.AAA.", "DDDDADDDD", "DDDDADDDD", "AAAAAAAAA", "DDDDADDDD", "DDDDADDDD", "DDDDADDDD", "........."}),
            new Achievement("even_steven", "Even Steven", "A session ending at exactly 0 net", "epic", 0xFFF2C14E, 0xFFFFFFFF, new String[]{
                    "....A....", ".AAAAAAA.", ".A..A..A.", "A.A.A.A.A", "AAA.A.AAA", "....A....", "....A....", "...AAA...", "..AAAAA.."}),
            new Achievement("centurion", "Centurion", "100 bets in all", "common", 0xFFA18CD1, 0xFFF2C14E, new String[]{
                    "AAAAAAAAA", "ACAADAAAA", "ACAADAAAA", "ACDDDDDAA", ".AAADAAA.", ".AAADAAA.", "..AAAAA..", "...AAA...", "....A...."}));

    private static final Map<String, Achievement> BY_ID = new HashMap<>();
    static { for (Achievement a : CATALOGUE) BY_ID.put(a.id(), a); }

    public static List<Achievement> catalogue() { return CATALOGUE; }
    public static Achievement byId(String id) { return BY_ID.get(id); }

    // ---- persistence: only the seen set

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("ezgamble").resolve("achievements.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One player's seen set: his display name, the cutoff it was built after (a forgotten player restarts), id -> unlock time. */
    private static final class Player { String name; long cut; Map<String, Long> unlocked = new LinkedHashMap<>(); }
    private static final class State { Map<String, Player> players = new LinkedHashMap<>(); }
    private static State st;

    private static State st() {
        if (st != null) return st;
        boolean bad = false;
        try { if (Files.exists(FILE)) st = GSON.fromJson(Files.readString(FILE), State.class); } catch (Exception e) { bad = true; EzGambleMod.LOG.warn("[ezgamble] achievements.json unreadable: {}", e.toString()); }
        if (bad) {   // kept aside as achievements.json.bad: the next save() would otherwise overwrite every seen set with an empty one
            try { Files.move(FILE, FILE.resolveSibling(FILE.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] achievements.json not set aside: {}", e.toString()); }
        }
        if (st == null) st = new State();
        if (st.players == null) st.players = new LinkedHashMap<>();
        st.players.values().removeIf(p -> p == null);   // a hand-edited file: a null player or a null unlock time would throw outside the try
        for (Player p : st.players.values()) { if (p.unlocked == null) p.unlocked = new LinkedHashMap<>(); p.unlocked.values().removeIf(v -> v == null); }
        return st;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(st()), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) { EzGambleMod.LOG.warn("[ezgamble] achievements.json not saved: {}", e.toString()); }
    }

    /** His seen set, reset when he was removed from the player database since it was built (GambleData.cutoff moved). */
    private static Player player(String name) {
        Player p = st().players.computeIfAbsent(Payouts.norm(name), k -> { Player n = new Player(); n.name = name; n.cut = GambleData.cutoff(name); return n; });
        if (p.cut != GambleData.cutoff(name)) { p.cut = GambleData.cutoff(name); p.unlocked.clear(); }   // forgotten: he restarts from zero
        if (p.name == null) p.name = name;
        return p;
    }

    // ---- derivation

    private static LocalDateTime dt(long t) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneId.systemDefault()); }

    /** The ids he satisfies right now, from the history alone. */
    static Set<String> compute(String name) {
        Set<String> out = new LinkedHashSet<>();
        if (name == null) return out;
        long cut = GambleData.cutoff(name);
        String nn = Payouts.norm(name);   // once: compared against every payment and chips entry below
        List<Txn> bets = new ArrayList<>();   // his bets at the six tables, in time order
        boolean rain = false, comeback = false, even = false, marathon = false;
        int sessions = 0, payments = 0, settled = 0;
        for (Session s : GambleData.sessions) {
            int n = 0; long run = 0, low = 0; boolean any = false;
            for (Txn t : PlayerStats.txnsOf(name, s)) {   // his lines of that session from the index, not a scan of every bet ever
                if (t.t() <= cut) continue;
                any = true;
                if ("Rain".equals(t.game())) { rain = true; continue; }   // the Rain line carries the pot and the fee, not a bet of his
                if (t.bet() == null || t.bet() <= 0) continue;
                bets.add(t); n++;
                run -= t.delta(); low = Math.min(low, run);   // his running net this session, his point of view
            }
            for (Session.Pay q : s.payments) if (q.t() > cut && Payouts.norm(q.name()).equals(nn)) { payments++; any = true; }
            if (any) sessions++;
            if (n >= 100) marathon = true;
            if (s.end != null && low <= -COMEBACK && run > 0) comeback = true;   // a closed session: positive at its end, as the description says
            if (s.end != null && n >= 2 && run == 0) even = true;   // a closed session: an open one at 0 is only a moment
            if (s.end != null && s.end > cut) {   // chips fully sent back: paid in plus his net, minus what went back, nothing left
                long credit = 0, won = 0, sent = 0;
                for (var e : s.credit.entrySet()) if (Payouts.norm(e.getKey()).equals(nn)) credit += e.getValue();
                for (var e : s.won.entrySet()) if (Payouts.norm(e.getKey()).equals(nn)) won += e.getValue();
                for (var e : s.sent.entrySet()) if (Payouts.norm(e.getKey()).equals(nn)) sent += e.getValue();
                if (credit > 0 && credit + won - sent <= 0) settled++;
            }
        }
        long wagered = 0; int wins = 0, losses = 0, cfWins = 0;
        Set<String> games = new HashSet<>(); Set<LocalDate> days = new HashSet<>();
        for (Txn t : bets) {
            long bet = t.bet();
            wagered += bet;
            games.add(t.game());
            if (bet >= WHALE) out.add("whale");
            if (t.delta() <= -9 * bet) out.add("jackpot");
            if ("Blackjack".equals(t.game()) && -t.delta() == Math.round(bet * 1.5)) out.add("natural");
            if (t.delta() < 0) { wins++; losses = 0; } else if (t.delta() > 0) { losses++; wins = 0; }   // a push changes neither streak
            if (wins >= 5) out.add("hot_streak");
            if (losses >= 7) out.add("cold_streak");
            if ("Coinflip".equals(t.game())) { cfWins = t.delta() < 0 ? cfWins + 1 : 0; if (cfWins >= 7) out.add("lucky_seven"); }
            LocalDateTime d = dt(t.t());
            int h = d.getHour();
            if (h >= 2 && h < 5) out.add("night_owl");
            if (h >= 5 && h < 8) out.add("early_bird");   // the night owls are not early birds
            days.add(d.toLocalDate());
        }
        for (LocalDate d : days) if (d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY && days.contains(d.plusDays(1))) out.add("weekend_warrior");
        if (!bets.isEmpty() || rain) out.add("first_blood");
        if (sessions >= 5) out.add("regular");
        if (wagered >= SPENDER) out.add("big_spender");
        if (comeback) out.add("comeback");
        if (marathon) out.add("marathon");
        if (bets.size() >= 100) out.add("centurion");
        if (games.containsAll(TABLES)) out.add("all_tables");
        Vip.Tier tier = Vip.tier(name);
        if (tier != null && tier.rank() >= 2) out.add("loyal");
        if (tier != null && tier.rank() >= 4) out.add("diamond_hands");
        if (rain) out.add("rainmaker");
        for (var h : dev.ryaan.ezgamble.auction.Auction.history) if (h.winner != null && h.winner.name != null && h.at > cut && Payouts.norm(h.winner.name).equals(nn)) { out.add("auctioneer"); break; }
        List<PlayerStats.Roller> podium = PlayerStats.highRollers("all");   // cached per GambleData.ver
        if (!podium.isEmpty()) { leaderNorm = Payouts.norm(podium.get(0).name()); if (leaderNorm.equals(nn)) out.add("high_roller"); }
        if (settled >= 10) out.add("settled");
        if (payments >= 10) out.add("generous");
        if (even) out.add("even_steven");
        return out;
    }

    // ---- the seen set and the cache

    private static final Map<String, List<Earned>> cache = new HashMap<>();   // norm -> earned, in catalogue order
    /** Who tops the all-time podium, as compute() last saw it: High Roller is the one badge another player's bet can move. */
    private static String leaderNorm;
    public static int ver;

    /**
     * Recomputes his set and folds what is new into the seen set (persisted). celebrate = the hooks: the new badges get
     * the fanfare; the views (of) only seed silently, so a player with history from before this feature shows his badges
     * without a show on the first profile opened.
     */
    private static List<Earned> check(String name, boolean celebrate) {
        Set<String> now = compute(name);
        Player p = player(name);
        List<Achievement> fresh = new ArrayList<>();
        long t = System.currentTimeMillis();   // a real date on the badge (P.now is frozen by the demo recorder)
        for (String id : now) if (!p.unlocked.containsKey(id) && BY_ID.containsKey(id)) { p.unlocked.put(id, t); fresh.add(BY_ID.get(id)); }
        if (!fresh.isEmpty()) { save(); ver++; }
        List<Earned> out = new ArrayList<>();
        for (Achievement a : CATALOGUE) { Long at = p.unlocked.get(a.id()); if (at != null) out.add(new Earned(a, at)); }
        cache.put(Payouts.norm(name), out);
        if (celebrate && !fresh.isEmpty()) {
            // a long history folded in at once (a first hook for an old regular): the four rarest get the show, the rest unlock quietly
            fresh.sort(Comparator.comparingInt((Achievement a) -> -a.rank()));
            // Vip.onTxn ran just before us on this bet: its "X reached Gold" toast is the oldest in Banners' 4-slot queue and a
            // fifth show() would evict it, so a tier-up on this bet leaves room for three badges, not four
            Vip.TierUp tu = Vip.lastTierUp();
            int room = tu != null && P.now() - tu.at() < 100 ? 3 : 4;
            for (int i = 0; i < Math.min(room, fresh.size()); i++) enqueue(name, fresh.get(i));
        }
        return out;
    }

    /** His badges, catalogue order, with the unlock time. */
    public static List<Earned> of(String name) {
        if (name == null) return List.of();
        List<Earned> l = cache.get(Payouts.norm(name));
        return l != null ? l : check(name, false);
    }

    public static int count(String name) { return of(name).size(); }

    /** The rarest badge he earned, null when none. */
    public static Achievement rarest(String name) {
        Achievement best = null;
        for (Earned e : of(name)) if (best == null || e.achievement().rank() > best.rank()) best = e.achievement();
        return best;
    }

    /** The latest unlocks across every player, newest first. */
    public static List<Recent> recent(int limit) {
        List<Recent> all = new ArrayList<>();
        for (Player p : st().players.values()) for (var e : p.unlocked.entrySet()) { Achievement a = BY_ID.get(e.getKey()); if (a != null) all.add(new Recent(p.name, a, e.getValue())); }
        all.sort(Comparator.comparingLong((Recent r) -> -r.at()));
        return all.size() > limit ? new ArrayList<>(all.subList(0, Math.max(0, limit))) : all;
    }

    // ---- hooks

    /** GambleData.record, once the bet is in the session. Only his set and the podium leader's can move: the rest of the cache stays. */
    public static void onTxn(Txn t) {
        if (t.player() == null) return;   // the host's own game: nobody's badges
        String me = Payouts.norm(t.player()), lead = leaderNorm;
        cache.remove(me);
        if (lead != null) cache.remove(lead);
        if (t.t() <= GambleData.cutoff(t.player())) return;
        check(t.player(), true);
    }

    /** Payments.received, once the chips are credited. */
    public static void onPaid(String name) {
        cache.remove(Payouts.norm(name));
        check(name, true);
    }

    /** GambleData.forget: his seen set restarts and the all-time podium may change hands, so every cached list goes (a forget is rare, the views rebuild lazily). */
    public static void onForget(String name) { cache.clear(); leaderNorm = null; }

    /** GambleData.setMode(false), once s.end is set: the badges that need a closed session (Even Steven, Settled, Comeback) get their show now, not at his next bet. */
    public static void onSessionEnd(Session s) {
        if (s == null || s.end == null) return;
        cache.clear();
        Map<String, String> names = new LinkedHashMap<>();   // norm -> display name: every player of the session once (paid in or played)
        for (String n : s.credit.keySet()) if (n != null) names.putIfAbsent(Payouts.norm(n), n);
        for (Txn t : s.txns) if (t.player() != null) names.putIfAbsent(Payouts.norm(t.player()), t.player());
        for (String n : names.values()) check(n, true);
    }

    // ---- celebrations: one toast at a time in the HUD's column (hud.Banners holds the queue)

    /**
     * The badge icon, "X unlocked " and the badge name in its colour, 3 s. The sound and the overlay play when the toast
     * shows, not when it is queued: four unlocks folded in at once (check caps the show at 4, or 3 when the same bet queued a
     * tier-up, Banners' queue holds 4) chime one after the other.
     */
    private static void enqueue(String name, Achievement a) {
        Banners.show(new Banners.Toast((g, x, y) -> BadgeIcons.draw(g, a, x, y - 1, 1, true),
                List.of(Banners.seg(name + " unlocked ", P.WHITE), Banners.seg(a.name(), a.color())), a.color(), 3000, () -> {
                    P.play("mc_levelup", 1.4, 0.7);
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.literal(name + " unlocked " + a.name()).withColor(a.color() & 0xFFFFFF), false);
                }));
    }

    /** Debug: "7/24 first_blood@12/03 10:11, whale@..." */
    public static String describe(String name) {
        StringBuilder sb = new StringBuilder();
        List<Earned> l = of(name);
        sb.append(l.size()).append('/').append(TOTAL);
        boolean first = true;
        for (Earned e : l) { sb.append(first ? " " : ", ").append(e.achievement().id()).append('@').append(P.DATE(e.at())); first = false; }
        return sb.toString();
    }
}
